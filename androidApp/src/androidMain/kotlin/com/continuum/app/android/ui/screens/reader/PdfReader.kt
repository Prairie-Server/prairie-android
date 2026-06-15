package com.continuum.app.android.ui.screens.reader

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import java.io.File

/**
 * PDF renderer backed by Android's [android.graphics.pdf.PdfRenderer]
 * (API 21+, no external deps). Flow:
 *
 *   1. Resolve the URL to a local file. file:// → use as-is.
 *      http(s):// → fetch via OkHttp into a cached copy keyed by URL
 *      hash so subsequent opens are instant.
 *   2. Open as ParcelFileDescriptor + wrap in PdfRenderer.
 *   3. Render the active page to a Bitmap; HorizontalPager swipes
 *      between pages.
 *
 * Off-the-shelf, no zoom or rotation in the bones pass — those are
 * lazy add-ons once the user has actual PDFs in hand. The pager step
 * notifies the VM via [onPageChanged] so position-resume is
 * straightforward later.
 */
@Composable
fun PdfReader(
    fileUrl: String,
    title: String,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit,
    onPageCountKnown: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val context = LocalContext.current
    val okHttp = koinInject<OkHttpClient>()
    val tokenManager = koinInject<TokenManager>()
    val memoryClassMb = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
    }

    // Resolve the file AND open the renderer in one IO step so the
    // ParcelFileDescriptor + PdfRenderer construction never runs in
    // composition on the main thread.
    val handleResult by produceState<Result<PdfDocumentHandle>?>(initialValue = null, fileUrl) {
        val produced = withContext(Dispatchers.IO) {
            readerLoadResult {
                val file = resolveReaderFile(context, okHttp, fileUrl, tokenManager.getServerUrl(), "pdf")
                val renderer = openRenderer(file)
                PdfDocumentHandle(SerializedCloseable(renderer), renderer.pageCount)
            }
        }
        value = produced
        // Close under the render mutex from a scope that outlives this
        // composition: a render blocked in native page.render() holds
        // the mutex, so close waits for it instead of yanking the
        // renderer away mid-render (the old onDispose race).
        awaitDispose {
            produced.getOrNull()?.let { handle ->
                CoroutineScope(Dispatchers.IO).launch { handle.renderer.close() }
            }
        }
    }

    val result = handleResult
    if (result == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toggleChromeOnTap(onToggleChrome),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }
    result.exceptionOrNull()?.let { throwable ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toggleChromeOnTap(onToggleChrome),
            contentAlignment = Alignment.Center,
        ) {
            Text(readerLoadErrorMessage(throwable), modifier = Modifier.padding(32.dp))
        }
        return
    }
    val handle = result.getOrThrow()

    if (handle.pageCount == 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .toggleChromeOnTap(onToggleChrome),
            contentAlignment = Alignment.Center,
        ) {
            Text("Empty PDF")
        }
        return
    }
    LaunchedEffect(handle.pageCount) {
        onPageCountKnown(handle.pageCount)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, handle.pageCount - 1),
        pageCount = { handle.pageCount },
    )
    LaunchedEffect(initialPage, handle.pageCount) {
        val targetPage = initialPage.coerceIn(0, handle.pageCount - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    // Notify VM on page change so position-resume works once we wire it up.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged().collect {
            onPageChanged(it)
        }
    }
    val scope = rememberCoroutineScope()
    val onPageTap: (Float) -> Unit = { xFraction ->
        when {
            xFraction < 1f / 3f -> {
                if (pagerState.currentPage > 0) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                } else {
                    onToggleChrome()
                }
            }
            xFraction > 2f / 3f -> {
                if (pagerState.currentPage < handle.pageCount - 1) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onToggleChrome()
                }
            }
            else -> onToggleChrome()
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) { page ->
        PdfPage(handle = handle, pageIndex = page, memoryClassMb = memoryClassMb, onPageTap = onPageTap)
    }
}

@Composable
private fun PdfPage(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    memoryClassMb: Int,
    onPageTap: (Float) -> Unit,
) {
    var bitmapResult by remember(pageIndex) { mutableStateOf<Result<Bitmap>?>(null) }
    LaunchedEffect(pageIndex) {
        bitmapResult = withContext(Dispatchers.IO) { renderPdfPageBitmap(handle, pageIndex, memoryClassMb) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(onPageTap) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width
                        val xFraction = if (width > 0) {
                            (offset.x / width).coerceIn(0f, 1f)
                        } else {
                            0.5f
                        }
                        onPageTap(xFraction)
                    },
                )
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val result = bitmapResult) {
            null -> CircularProgressIndicator()
            else -> result.fold(
                onSuccess = { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
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

/**
 * Serializes every touch of an [AutoCloseable] resource — including
 * close — through one mutex. PdfRenderer is not thread-safe and
 * crashes (ISE or native abort) when closed while a page render is in
 * flight, so close waits for the active render to release the lock and
 * any later use fails fast instead of reaching native code.
 */
internal class SerializedCloseable<T : AutoCloseable>(private val resource: T) {
    private val mutex = Mutex()
    private var closed = false

    suspend fun <R> withResource(block: (T) -> R): R = mutex.withLock {
        check(!closed) { "Resource is closed" }
        block(resource)
    }

    suspend fun close() {
        mutex.withLock {
            if (closed) return
            closed = true
            runCatching { resource.close() }
        }
    }
}

/** Renderer plus its page count, captured at open time so the UI never
 *  needs the render lock just to size the pager. */
internal class PdfDocumentHandle(
    val renderer: SerializedCloseable<PdfRenderer>,
    val pageCount: Int,
)

private fun openRenderer(file: File): PdfRenderer {
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    return try {
        PdfRenderer(pfd)
    } catch (throwable: Throwable) {
        runCatching { pfd.close() }
        throw throwable
    }
}

private fun Modifier.toggleChromeOnTap(onToggleChrome: () -> Unit): Modifier =
    pointerInput(onToggleChrome) {
        detectTapGestures(onTap = { onToggleChrome() })
    }

private suspend fun renderPdfPageBitmap(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    memoryClassMb: Int,
): Result<Bitmap> =
    readerLoadResult {
        handle.renderer.withResource { renderer ->
            renderer.openPage(pageIndex).use { page ->
                val budget = pdfRenderBudget(page.width, page.height, memoryClassMb)
                val scale = budget.targetWidth.toFloat() / page.width
                val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(budget.targetWidth, targetHeight, budget.config)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
