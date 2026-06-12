package com.continuum.app.android.ui.screens.reader.reflow

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.continuum.app.android.ui.screens.reader.resolveReaderFile
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderSection
import com.continuum.app.model.book.BookFormat
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Orchestrates a reflowable-reader session: resolves the source file, builds a
 * [ReflowableSource], drives the [ReflowWebView] paginator, persists locators,
 * and maps tap-zones / pinch-zoom gestures onto page turns + text-scale nudges.
 *
 * Sections are loaded lazily (one at a time) so unseen sections are never
 * pre-rendered; book-level progress is estimated from per-section text lengths
 * via [SectionWeights]. Restore happens by re-paginating the saved section and
 * seeking to the saved page progression once the WebView reports its page count.
 */
@Composable
fun ReflowableReader(
    format: BookFormat,
    fileUrl: String,
    settings: ReaderDisplaySettings,
    initialLocator: String?,
    onLocatorChanged: (locationJson: String, progress: Double) -> Unit,
    onSectionsKnown: (List<ReaderSection>) -> Unit,
    onTextScaleNudge: (Float) -> Unit,
) {
    val context = LocalContext.current
    val okHttp = koinInject<OkHttpClient>()
    val tokenManager = koinInject<TokenManager>()
    val systemDark = isSystemInDarkTheme()

    val sourceResult by produceState<Result<ReflowableSource>?>(null, fileUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveReaderFile(
                    context,
                    okHttp,
                    fileUrl,
                    tokenManager.getServerUrl(),
                    extension = format.wire,
                )
                buildReflowableSource(format, file)
            }
        }
    }

    val result = sourceResult
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    result.exceptionOrNull()?.let { throwable ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(throwable.message ?: "Could not open this book.", modifier = Modifier.padding(32.dp))
        }
        return
    }
    val source = result.getOrThrow()

    val weights = remember(source) { SectionWeights(source.sections.map { it.approxChars }) }

    LaunchedEffect(source) {
        onSectionsKnown(
            source.tableOfContents.map {
                ReaderSection(
                    index = it.sectionIndex,
                    title = it.title,
                    location = ReflowLocatorCodec.encode(
                        ReflowLocator(
                            it.sectionIndex,
                            0.0,
                            weights.bookProgression(it.sectionIndex, 0.0),
                        ),
                    ),
                )
            },
        )
    }

    var sectionIndex by remember(source) {
        mutableStateOf(ReflowLocatorCodec.decode(initialLocator)?.sectionIndex ?: 0)
    }
    var pendingPageProgression by remember(source) {
        mutableStateOf(ReflowLocatorCodec.decode(initialLocator)?.pageProgression ?: 0.0)
    }
    var pageCount by remember(source) { mutableStateOf(1) }
    var page by remember(source) { mutableStateOf(0) }
    var controller by remember(source) { mutableStateOf<ReflowController?>(null) }

    val scope = rememberCoroutineScope()
    val loadSection: () -> Unit = {
        scope.launch {
            val html = source.html(sectionIndex) ?: "<p>This section could not be loaded.</p>"
            controller?.load(html, source.baseUrl(sectionIndex))
            controller?.applyStyle(settings.toReflowStyle(systemDark).toCss())
        }
    }

    // Drives the initial load (once the WebView is ready) and every section change.
    LaunchedEffect(sectionIndex, controller, source) {
        if (controller != null) loadSection()
    }

    // Re-style live when display settings change.
    LaunchedEffect(settings, controller) {
        controller?.applyStyle(settings.toReflowStyle(systemDark).toCss())
    }

    val nextPage: () -> Unit = {
        if (page < pageCount - 1) {
            controller?.goToPage(page + 1)
        } else if (sectionIndex < source.sections.lastIndex) {
            pendingPageProgression = 0.0
            sectionIndex++
        }
    }
    val prevPage: () -> Unit = {
        if (page > 0) {
            controller?.goToPage(page - 1)
        } else if (sectionIndex > 0) {
            pendingPageProgression = 1.0
            sectionIndex--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(source) {
                detectTapGestures { o ->
                    when {
                        o.x < size.width / 3f -> prevPage()
                        o.x > size.width * 2f / 3f -> nextPage()
                        else -> {}
                    }
                }
            }
            .pointerInput(source) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) onTextScaleNudge(zoom)
                }
            },
    ) {
        ReflowWebView(
            modifier = Modifier.fillMaxSize(),
            onReady = { c -> controller = c },
            onCrash = { controller?.let { loadSection() } },
            onEvent = { ev ->
                when (ev) {
                    is ReflowEvent.Paginated -> {
                        pageCount = ev.pageCount.coerceAtLeast(1)
                        val target = (pendingPageProgression * (pageCount - 1)).roundToInt()
                        controller?.goToPage(target)
                        pendingPageProgression = 0.0
                    }
                    is ReflowEvent.Relocated -> {
                        page = ev.page
                        val bp = weights.bookProgression(sectionIndex, ev.pageProgression)
                        onLocatorChanged(
                            ReflowLocatorCodec.encode(
                                ReflowLocator(sectionIndex, ev.pageProgression, bp),
                            ),
                            bp,
                        )
                    }
                    ReflowEvent.Ready, is ReflowEvent.Error -> {}
                }
            },
        )
    }
}
