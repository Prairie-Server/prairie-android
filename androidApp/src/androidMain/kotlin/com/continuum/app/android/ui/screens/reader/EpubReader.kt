package com.continuum.app.android.ui.screens.reader

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderSection
import com.continuum.app.common.ebook.ReaderTheme
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import java.io.File

/**
 * EPUB renderer using just stdlib (zip + minimal XML parsing) + a
 * WebView per chapter. Approach:
 *
 *   1. Resolve URL to local EPUB file (cache once).
 *   2. Parse `META-INF/container.xml` → locate OPF package file.
 *   3. Parse the OPF spine — ordered list of chapter href entries.
 *   4. HorizontalPager swipes between chapters; each chapter's HTML
 *      gets piped into a WebView via `loadDataWithBaseURL`. Image /
 *      CSS references resolve through `baseUrl = file://<epubDir>/`
 *      since EPUBs reference assets relative to the OPF.
 *
 * Bones-level: no pagination *within* a chapter (the WebView scrolls
 * vertically inside each page). Real fixed-page reflow lands when
 * we wire epub.js or Readium.
 */
@Composable
fun EpubReader(
    fileUrl: String,
    title: String,
    initialPage: Int = 0,
    settings: ReaderDisplaySettings,
    onPageChanged: (Int) -> Unit,
    onPageCountKnown: (Int) -> Unit,
    onSectionsKnown: (List<ReaderSection>) -> Unit,
) {
    val context = LocalContext.current
    val okHttp = koinInject<OkHttpClient>()
    val tokenManager = koinInject<TokenManager>()

    val bookResult by produceState<Result<EpubBook>?>(initialValue = null, fileUrl) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "epub")
                EpubBook.open(file, context.cacheDir)
            }
        }
    }

    val result = bookResult
    if (result == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    result.exceptionOrNull()?.let { throwable ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(readerLoadErrorMessage(throwable), modifier = Modifier.padding(32.dp))
        }
        return
    }
    val b = result.getOrThrow()
    if (b.spine.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("EPUB has no readable chapters")
        }
        return
    }
    LaunchedEffect(b.spine) {
        onPageCountKnown(b.spine.size)
        onSectionsKnown(
            b.spine.mapIndexed { index, href ->
                ReaderSection(
                    index = index,
                    title = href.substringAfterLast('/').substringBeforeLast('.'),
                    location = "page:$index",
                )
            },
        )
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, b.spine.lastIndex),
        pageCount = { b.spine.size },
    )
    LaunchedEffect(initialPage, b.spine.size) {
        val targetPage = initialPage.coerceIn(0, b.spine.lastIndex)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect {
            onPageChanged(it)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    ) { idx ->
        EpubChapter(book = b, chapterIndex = idx, settings = settings)
    }
}

/** Styled chapter HTML; null [html] means the spine entry's file is
 *  missing from the archive. */
private class EpubChapterContent(val html: String?)

@Composable
private fun EpubChapter(book: EpubBook, chapterIndex: Int, settings: ReaderDisplaySettings) {
    val href = book.spine.getOrNull(chapterIndex)
    // Read + style the chapter off the main thread; re-runs only when
    // the chapter or display settings actually change, not on every
    // recomposition like the old AndroidView update lambda did.
    // Resolve the "System" reader theme against the actual device dark mode so
    // the page isn't a white slab inside a dark-first app.
    val systemDark = isSystemInDarkTheme()
    val content by produceState<EpubChapterContent?>(initialValue = null, book, href, settings, systemDark) {
        value = withContext(Dispatchers.IO) {
            EpubChapterContent(href?.let { book.readChapterHtml(it)?.withReaderCss(settings, systemDark) })
        }
    }
    val loaded = content
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val html = loaded.html
    if (html == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Could not load this chapter.", modifier = Modifier.padding(32.dp))
        }
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                // Transparent so the dark Pager surface shows through while a
                // chapter loads, instead of a white WebView flash.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.settings.javaScriptEnabled = false  // chapters are static HTML
                this.settings.allowFileAccess = true
                this.settings.allowContentAccess = true
                this.settings.builtInZoomControls = true
                this.settings.displayZoomControls = false
            }
        },
        update = { web ->
            // Only reload when the produced HTML actually changed —
            // update runs on every recomposition, and an unconditional
            // loadDataWithBaseURL resets the WebView scroll position.
            if (web.tag != html) {
                web.tag = html
                // baseUrl lets relative <img src> / <link rel='stylesheet'>
                // refs inside the chapter resolve against the unpacked epub
                // root on disk.
                val base = "file://${book.unpackedRoot.absolutePath}/"
                web.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
            }
        },
    )
}

private fun String.withReaderCss(settings: ReaderDisplaySettings, systemDark: Boolean): String {
    val normalized = settings.normalized()
    val marginEm = normalized.marginScale * 1.2f
    val fontPercent = (normalized.textScale * 100).toInt()
    val light = "color: #1c1b1f; background: #fffbfe;"
    val dark = "color: #e6e1e5; background: #1c1b1f;"
    val colors = when (normalized.theme) {
        ReaderTheme.System -> if (systemDark) dark else light
        ReaderTheme.Light -> light
        ReaderTheme.Sepia -> "color: #2b2118; background: #f4ecd8;"
        ReaderTheme.Dark -> dark
    }
    val style = """
        <style>
        html, body { $colors }
        body { font-size: ${fontPercent}%; margin: ${marginEm}em; line-height: 1.55; }
        img { max-width: 100%; height: auto; }
        </style>
    """.trimIndent()
    val headMatch = Regex("""<head(\s[^>]*)?>""", RegexOption.IGNORE_CASE).find(this)
        ?: return "$style\n$this"
    val insertAt = headMatch.range.last + 1
    return replaceRange(insertAt, insertAt, "\n$style")
}

