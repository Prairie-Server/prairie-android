package com.continuum.app.tv.ui.screens.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.continuum.app.model.subtitles.SubtitleResult
import com.continuum.app.tv.ui.theme.DarkBackground
import com.continuum.app.tv.ui.theme.FocusedContainer
import com.continuum.app.tv.ui.theme.FocusedContent
import java.util.Locale

/**
 * Language codes offered by the search/translate pickers — matches the web's
 * common-language set. Cycled left/right on a [TvDialogCyclerRow]; no text
 * input anywhere (TV constraint that put this feature in scope).
 */
internal val TvSubtitleLanguageOptions: List<String> = listOf(
    "en", "es", "fr", "de", "it", "pt", "nl", "pl", "ru", "ja", "ko", "zh",
    "ar", "tr", "sv", "no", "da", "fi", "cs", "el", "he", "hi", "hu", "id",
    "ro", "th", "uk", "vi",
)

/** ISO 639-1 → English display name, fallback uppercased code (spec LanguageNames behavior). */
internal fun tvLanguageDisplayName(code: String): String =
    Locale(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code.uppercase() }

/**
 * Score bucket colors — web/mobile parity (SubtitleScoreBadge): >=70 green
 * (#22c55e), >=40 amber (#eab308), else red (#ef4444). [SubtitleResult.score]
 * is a 0–100 Double on the wire; we bucket on the rounded-down value.
 */
internal fun subtitleScoreColor(score: Double): Color = when {
    score >= 70 -> Color(0xFF22C55E)
    score >= 40 -> Color(0xFFEAB308)
    else -> Color(0xFFEF4444)
}

internal fun subtitleProviderAbbreviation(provider: String): String =
    when (provider.lowercase()) {
        "opensubtitles" -> "OS"
        "subdl" -> "SDL"
        "subsource" -> "SS"
        else -> provider.take(3).uppercase()
    }

/**
 * Provider badge colors — web/mobile parity (SubtitleSearchModal.tsx
 * providerInfo / SubtitleSearchSheet ProviderBadges): opensubtitles #EAB308,
 * subdl #3B82F6, subsource #EF4444.
 */
internal fun subtitleProviderColor(provider: String): Color =
    when (provider.lowercase()) {
        "opensubtitles" -> Color(0xFFEAB308)
        "subdl" -> Color(0xFF3B82F6)
        "subsource" -> Color(0xFFEF4444)
        else -> Color.White.copy(alpha = 0.40f)
    }

/**
 * D-pad subtitle provider-search dialog. Panel + row idiom mirrors
 * TvOptionDialog (Popup, dark panel, ClickableSurface rows). Flow: cycle
 * language ← →, Select on "Search", focus a result row, Select to download
 * (inline spinner), then the VM refreshes + auto-selects the track and bumps
 * `completedNonce` — observed here to self-dismiss.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSubtitleSearchDialog(
    state: SubtitleSearchUiState,
    onLanguageChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onDownload: (SubtitleResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val languageRowFocus = remember { FocusRequester() }
    val initialNonce = remember { state.completedNonce }

    LaunchedEffect(Unit) { runCatching { languageRowFocus.requestFocus() } }

    // Download finished → track merged + auto-selected by the VM → close.
    LaunchedEffect(state.completedNonce) {
        if (state.completedNonce != initialNonce) onDismiss()
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 36.dp, top = 50.dp, end = 36.dp, bottom = 42.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelShape = RoundedCornerShape(14.dp)
            Column(
                modifier = Modifier
                    .width(340.dp)
                    .background(color = DarkBackground.copy(alpha = 0.68f), shape = panelShape)
                    .border(0.6.dp, Color.White.copy(alpha = 0.20f), panelShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "SEARCH SUBTITLES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 16.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White.copy(alpha = 0.58f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )

                val langIndex = TvSubtitleLanguageOptions.indexOf(state.language)
                    .takeIf { it >= 0 } ?: 0
                TvDialogCyclerRow(
                    title = "Language",
                    value = tvLanguageDisplayName(TvSubtitleLanguageOptions[langIndex]),
                    onPrevious = {
                        val prev = (langIndex - 1 + TvSubtitleLanguageOptions.size) %
                            TvSubtitleLanguageOptions.size
                        onLanguageChanged(TvSubtitleLanguageOptions[prev])
                    },
                    onNext = {
                        val next = (langIndex + 1) % TvSubtitleLanguageOptions.size
                        onLanguageChanged(TvSubtitleLanguageOptions[next])
                    },
                    modifier = Modifier.focusRequester(languageRowFocus),
                )

                TvDialogActionRow(
                    title = if (state.isSearching) "Searching…" else "Search",
                    enabled = !state.isSearching && state.downloadingResultId == null,
                    onClick = onSearch,
                )

                state.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                // Provider warnings (e.g. a provider was skipped/unconfigured)
                // surfaced from the search response — mirrors the phone sheet.
                state.warnings.forEach { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFBBF24),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }

                if (state.hasSearched && !state.isSearching &&
                    state.results.isEmpty() && state.error == null
                ) {
                    Text(
                        text = "No subtitles found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.56f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }

                if (state.results.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            state.results,
                            key = { "${it.provider}:${it.id}" },
                        ) { result ->
                            TvSubtitleResultRow(
                                result = result,
                                isDownloading = state.downloadingResultId == result.id,
                                enabled = state.downloadingResultId == null,
                                onClick = { onDownload(result) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One provider search hit: score badge (bucket-colored), release name, and a
 * meta line with provider abbreviation badge, optional HI marker, and
 * download count. OK downloads; an inline spinner replaces the chevron slot
 * while this row's download is in flight.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSubtitleResultRow(
    result: SubtitleResult,
    isDownloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = { if (enabled) onClick() },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            contentColor = Color.White,
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 16.dp,
            ),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Score badge — bucket color, white score text.
            Box(
                modifier = Modifier
                    .background(subtitleScoreColor(result.score), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = result.score.toInt().toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = result.releaseName.ifBlank { "Unnamed release" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isFocused) FocusedContent else Color.White,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                subtitleProviderColor(result.provider),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = subtitleProviderAbbreviation(result.provider),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                        )
                    }
                    if (result.hearingImpaired) {
                        Text(
                            text = "HI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (isFocused) {
                                FocusedContent.copy(alpha = 0.70f)
                            } else {
                                Color.White.copy(alpha = 0.66f)
                            },
                        )
                    }
                    Text(
                        text = "${result.downloads} downloads · ${tvLanguageDisplayName(result.language)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFocused) {
                            FocusedContent.copy(alpha = 0.70f)
                        } else {
                            Color.White.copy(alpha = 0.56f)
                        },
                    )
                }
            }
            if (isDownloading) {
                CircularProgressIndicator(
                    color = if (isFocused) FocusedContent else Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Focusable "Title        ‹ Value ›" row — left/right cycles the value while
 * the row holds focus, Select also advances. Shared by the search dialog's
 * language picker and the AI dialog's mode/source/target pickers.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvDialogCyclerRow(
    title: String,
    value: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = { if (enabled) onNext() },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 16.dp,
            ),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .onPreviewKeyEvent { ev ->
                if (!enabled || ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { onPrevious(); true }
                    Key.DirectionRight -> { onNext(); true }
                    else -> false
                }
            }
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
                color = if (isFocused) FocusedContent else Color.White,
            )
            Text(
                text = "‹  $value  ›",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = if (isFocused) FocusedContent else Color.White.copy(alpha = 0.80f),
            )
        }
    }
}

/** Centered full-width action row (Search / Start / Cancel) — TvOptionDialog row idiom. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvDialogActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        onClick = { if (enabled) onClick() },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
            focusedContainerColor = FocusedContainer,
            focusedContentColor = FocusedContent,
            pressedContainerColor = FocusedContainer,
            pressedContentColor = FocusedContent,
            disabledContainerColor = Color.White.copy(alpha = 0.03f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, DarkBackground.copy(alpha = 0.82f)),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.White.copy(alpha = 0.18f),
                elevation = 16.dp,
            ),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.98f), shape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (isFocused) FocusedContent else Color.White,
            )
        }
    }
}
