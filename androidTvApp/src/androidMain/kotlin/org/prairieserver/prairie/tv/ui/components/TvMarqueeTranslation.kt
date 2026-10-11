package org.prairieserver.prairie.tv.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.prairieserver.prairie.metadata.DescriptionTranslationController
import org.prairieserver.prairie.metadata.DescriptionTranslationPhase
import org.prairieserver.prairie.model.catalog.hasMachineTranslatedOverview
import org.prairieserver.prairie.model.feature.MetadataAiFeatureStore
import org.prairieserver.prairie.model.metadata.MetadataAiOnView
import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.model.section.SectionItem
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.MetadataAiRepository

/**
 * How the focus marquee shows the rested item's description translation:
 * the landed text (if any), whether it is machine-translated, and whether a
 * translation of this item is running. Applied only to the item it names, so
 * one card's progress never shows on another.
 */
data class TvMarqueeTranslation(
    val contentId: String,
    val synopsis: String?,
    val machineTranslated: Boolean,
    val translating: Boolean,
)

/**
 * On-view description translation for the focus marquee, the Android TV
 * counterpart of the web Featured hero: in `auto` mode a Featured-section item
 * the viewer rests on translates once per (item, language), then its detail is re-read
 * until the server reports the description available. The marquee is never
 * focusable, so `button` mode leaves the action to the item's detail page.
 */
@Stable
internal class TvMarqueeTranslationState(
    private val repository: MetadataAiRepository,
    private val scope: CoroutineScope,
) {
    private val controller = DescriptionTranslationController(
        repository = repository,
        delayMs = { delay(it) },
    )
    val phase = controller.phase
    val runningContentId = controller.runningContentId

    private data class Landed(val overview: String?, val machineTranslated: Boolean, val pending: String?)

    /** Descriptions re-read while this feed was on screen, by content id. */
    private val landed = mutableStateMapOf<String, Landed>()
    private var translationJob: Job? = null
    private var focusedContentId: String? = null
    private var failedPreflightContentId: String? = null
    val working = MutableStateFlow(false)

    fun onFocusChanged(contentId: String?) {
        if (focusedContentId != contentId) failedPreflightContentId = null
        focusedContentId = contentId
    }

    fun close() {
        translationJob?.cancel()
    }

    fun pendingLanguage(item: SectionItem): String? =
        if (item.contentId in landed) landed[item.contentId]?.pending
        else item.pendingTranslationLanguage?.takeIf { it.isNotBlank() }

    fun translateOnView(item: SectionItem, onLanded: () -> Unit) {
        if (translationJob?.isActive == true) return
        val contentId = item.contentId
        val hintedTarget = pendingLanguage(item) ?: return
        if (failedPreflightContentId == contentId || !controller.shouldAutoFire(contentId, hintedTarget)) return
        working.value = true
        translationJob = scope.launch {
            try {
                // Retained cards can still name the previous metadata language.
                // Read the current profile's description before queuing work.
                val owner = repository.captureAuthority()
                val latest = repository.refreshDetail(contentId, owner)
                currentCoroutineContext().ensureActive()
                if (latest !is ApiResult.Success || !repository.isCurrent(owner)) {
                    // Retry on refocus without repeatedly reading a failing endpoint
                    // while focus rests here. No translation claim has been consumed.
                    failedPreflightContentId = contentId
                    return@launch
                }
                fun publish(detail: org.prairieserver.prairie.model.catalog.ItemDetail) {
                    landed[contentId] = Landed(
                        overview = detail.overview?.takeIf { it.isNotBlank() },
                        machineTranslated = hasMachineTranslatedOverview(detail.machineTranslatedFields),
                        pending = detail.pendingTranslationLanguage?.takeIf { it.isNotBlank() },
                    )
                }
                publish(latest.data)
                val target = latest.data.pendingTranslationLanguage?.takeIf { it.isNotBlank() }
                if (target == null) {
                    onLanded()
                    return@launch
                }
                if (!controller.claimAutoFire(contentId, target)) return@launch
                controller.resetFailure()
                controller.translate(
                    contentId = contentId,
                    targetLanguage = target,
                    refetchPendingLanguage = { currentOwner ->
                        when (val result = repository.refreshDetail(contentId, currentOwner)) {
                            is ApiResult.Success -> if (repository.isCurrent(currentOwner)) {
                                currentCoroutineContext().ensureActive()
                                publish(result.data)
                                landed[contentId]?.pending
                            } else target
                            else -> target
                        }
                    },
                    onTranslated = { onLanded() },
                )
            } finally {
                working.value = false
            }
        }
    }

    fun presentation(item: SectionItem, phase: DescriptionTranslationPhase, runningId: String?): TvMarqueeTranslation {
        val update = landed[item.contentId]
        return TvMarqueeTranslation(
            contentId = item.contentId,
            synopsis = update?.overview ?: item.overview?.takeIf { it.isNotBlank() },
            machineTranslated = update?.machineTranslated ?: hasMachineTranslatedOverview(item.machineTranslatedFields),
            translating = phase == DescriptionTranslationPhase.Translating && runningId == item.contentId,
        )
    }
}

/**
 * Whether the marquee's item came from a Featured section (the section's
 * `featured` flag). Only those translate on view, matching the web client,
 * which translates the visible Featured slide only; cards in ordinary rows
 * keep their text and marker but never start a job when focus rests on them.
 * [TvMarqueeContent.id] is `"<section id>#<content id>"`.
 */
internal fun isFeaturedMarqueeContent(content: TvMarqueeContent?, sections: List<ResolvedSection>): Boolean =
    content != null && sections.any { section ->
        section.featured && content.id == "${section.id}#${content.contentId}"
    }

/** How long focus rests on a card before `auto` mode counts it as viewed. */
private const val TvMarqueeTranslateDwellMs = 1_000L

/**
 * Drives [TvMarqueeTranslationState] for the item the marquee shows and
 * returns what the marquee should render for it.
 */
@Composable
internal fun rememberTvMarqueeTranslation(
    item: SectionItem?,
    /** Start on-view translation for this item; false only displays it. */
    autoTranslate: Boolean,
    /** Called once a translation lands, to re-read the rows that hold the card. */
    onTranslated: () -> Unit = {},
): TvMarqueeTranslation? {
    val latestOnTranslated by rememberUpdatedState(onTranslated)
    val repository: MetadataAiRepository = koinInject()
    val metadataAiStore: MetadataAiFeatureStore = koinInject()
    val status by metadataAiStore.status.collectAsState()
    val language: org.prairieserver.prairie.domain.settings.CatalogLanguageRevision = koinInject()
    val languageRevision by language.revision.collectAsState()
    val scope = rememberCoroutineScope()
    val state = remember(repository, scope, languageRevision) { TvMarqueeTranslationState(repository, scope) }
    DisposableEffect(state) { onDispose { state.close() } }
    val phase by state.phase.collectAsState()
    val runningId by state.runningContentId.collectAsState()
    val pending = item?.let(state::pendingLanguage)
    val working by state.working.collectAsState()
    val busy = working || phase == DescriptionTranslationPhase.Translating
    // Re-run when a running translation ends, so an item skipped meanwhile
    // gets its turn while the viewer is still resting on it.
    LaunchedEffect(state, item?.contentId, pending, status.onView, busy, autoTranslate) {
        state.onFocusChanged(item?.contentId)
        if (!autoTranslate || item == null || pending == null || status.onView != MetadataAiOnView.Auto) {
            return@LaunchedEffect
        }
        delay(TvMarqueeTranslateDwellMs)
        state.translateOnView(item) { latestOnTranslated() }
    }
    return item?.let { state.presentation(it, phase, runningId) }
}
