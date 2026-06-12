package com.continuum.app.android.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import java.io.File
import java.util.zip.ZipFile

/**
 * Comic / manga reader for CBZ (zip-of-images) archives. CBR/RAR remains
 * external-only so users can open the original file in another reader.
 *
 * Flow:
 *   1. Resolve the URL to a local file (download to cache once,
 *      identical pattern to the PDF reader).
 *   2. Open as [ZipFile], list image entries, sort by name (standard
 *      comic-archive page ordering).
 *   3. HorizontalPager swipes between pages; each page lazily decodes
 *      its image from the zip entry stream.
 *
 * Direction-of-reading (LTR vs RTL for manga) defaults to LTR; a
 * `reading_direction` field on [BookMetadata] can override later.
 */
@Composable
fun ComicReader(
    fileUrl: String,
    title: String,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit,
    onPageCountKnown: (Int) -> Unit,
) {
    val context = LocalContext.current
    val okHttp = koinInject<OkHttpClient>()
    val tokenManager = koinInject<TokenManager>()

    val localFileResult by produceState<Result<File>?>(initialValue = null, fileUrl) {
        value = runCatching {
            resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "cbz")
        }
    }
    val fileResult = localFileResult
    if (fileResult == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    fileResult.exceptionOrNull()?.let { throwable ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(readerLoadErrorMessage(throwable), modifier = Modifier.padding(32.dp))
        }
        return
    }
    val file = fileResult.getOrThrow()

    // Decode pages at roughly the display size instead of full-res.
    val targetMaxDimension = remember(context) {
        val metrics = context.resources.displayMetrics
        maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1080)
    }

    // One ZipFile per reader lifetime: every page decodes from this
    // handle instead of re-opening (and re-parsing the central
    // directory of) the archive on each swipe.
    val archiveState by produceState<OpenedComicArchive?>(initialValue = null, file) {
        val opened = withContext(Dispatchers.IO) {
            runCatching { ZipFile(file) }.fold(
                onSuccess = { zip -> OpenedComicArchive(zip = zip, result = listComicArchivePages(zip)) },
                onFailure = { throwable ->
                    OpenedComicArchive(
                        zip = null,
                        result = ComicArchiveLoadResult.Error(
                            throwable.message?.takeIf { it.isNotBlank() } ?: "Could not open comic archive.",
                        ),
                    )
                },
            )
        }
        value = opened
        awaitDispose {
            // Close off-main, matching PdfReader's dispose path — awaitDispose
            // itself runs synchronously on the UI thread.
            opened.zip?.let { zip ->
                CoroutineScope(Dispatchers.IO).launch { runCatching { zip.close() } }
            }
        }
    }
    val archive = archiveState
    val pages = when (val result = archive?.result) {
        null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        is ComicArchiveLoadResult.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Could not open this comic archive.")
            }
            return
        }
        ComicArchiveLoadResult.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No readable images found in this comic archive.")
            }
            return
        }
        is ComicArchiveLoadResult.Loaded -> result.pages
    }
    val zip = archive?.zip ?: return
    LaunchedEffect(pages.size) {
        onPageCountKnown(pages.size)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pages.lastIndex),
        pageCount = { pages.size },
    )
    LaunchedEffect(initialPage, pages.size) {
        val targetPage = initialPage.coerceIn(0, pages.lastIndex)
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
    ) { pageIndex ->
        ComicPage(zip = zip, entryName = pages[pageIndex].entryName, targetMaxDimension = targetMaxDimension)
    }
}

@Composable
private fun ComicPage(zip: ZipFile, entryName: String, targetMaxDimension: Int) {
    var bitmapResult by remember(entryName) { mutableStateOf<Result<Bitmap>?>(null) }
    LaunchedEffect(entryName) {
        bitmapResult = withContext(Dispatchers.IO) {
            decodeComicPageBitmap(zip, entryName, targetMaxDimension)
        }
    }
    Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        when (val result = bitmapResult) {
            null -> CircularProgressIndicator()
            else -> result.fold(
                onSuccess = { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = entryName,
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                        contentScale = ContentScale.Fit,
                    )
                },
                onFailure = { throwable ->
                    Text(readerLoadErrorMessage(throwable), modifier = Modifier.padding(32.dp))
                },
            )
        }
    }
}

/** The open archive handle paired with its page listing; the zip stays
 *  open for the reader's lifetime and is closed on dispose. */
private class OpenedComicArchive(
    val zip: ZipFile?,
    val result: ComicArchiveLoadResult,
)

/** Power-of-two BitmapFactory.inSampleSize so the decoded bitmap's max
 *  dimension stays at or just above [targetMaxDimension]. */
internal fun comicSampleSize(width: Int, height: Int, targetMaxDimension: Int): Int {
    if (width <= 0 || height <= 0 || targetMaxDimension <= 0) return 1
    var sampleSize = 1
    val maxDimension = maxOf(width, height)
    while (maxDimension / (sampleSize * 2) >= targetMaxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun decodeComicPageBitmap(
    zip: ZipFile,
    entryName: String,
    targetMaxDimension: Int,
): Result<Bitmap> =
    requiredReaderLoadResult("Could not decode comic page.") {
        val entry = zip.getEntry(entryName) ?: return@requiredReaderLoadResult null
        // Pass 1: bounds only, so we can downsample to roughly the
        // display size instead of decoding full-res ARGB_8888.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = comicSampleSize(bounds.outWidth, bounds.outHeight, targetMaxDimension)
        }
        zip.getInputStream(entry).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
    }

internal data class ComicArchivePage(
    val index: Int,
    val entryName: String,
)

internal sealed interface ComicArchiveLoadResult {
    data class Loaded(val pages: List<ComicArchivePage>) : ComicArchiveLoadResult
    data object Empty : ComicArchiveLoadResult
    data class Error(val message: String) : ComicArchiveLoadResult
}

internal fun loadComicArchivePages(file: File): ComicArchiveLoadResult =
    runCatching { ZipFile(file).use(::listComicArchivePages) }
        .getOrElse { throwable ->
            ComicArchiveLoadResult.Error(
                throwable.message?.takeIf { it.isNotBlank() } ?: "Could not open comic archive.",
            )
        }

/** Image entries inside a CBZ, sorted lexicographically. We accept the
 *  common comic formats (.jpg/.jpeg/.png/.webp). Folder-style archives
 *  (Vol1/page01.png) just work since the sort handles them. */
internal fun listComicArchivePages(zip: ZipFile): ComicArchiveLoadResult {
    val exts = setOf("jpg", "jpeg", "png", "webp", "gif")
    val entries = zip.entries().toList()
        .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in exts }
        .map { it.name }
        .sorted()
    return if (entries.isEmpty()) {
        ComicArchiveLoadResult.Empty
    } else {
        ComicArchiveLoadResult.Loaded(
            entries.mapIndexed { index, entryName ->
                ComicArchivePage(index = index, entryName = entryName)
            },
        )
    }
}

