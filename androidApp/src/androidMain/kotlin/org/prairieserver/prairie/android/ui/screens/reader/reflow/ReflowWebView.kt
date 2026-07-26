package org.prairieserver.prairie.android.ui.screens.reader.reflow

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLConnection

/**
 * Thin wrapper around the reflow [WebView] exposing the JS paginator API.
 *
 * All strings are JSON-encoded via [JSONObject.quote] so HTML/CSS containing
 * quotes or newlines produces a safe JS string literal.
 */
class ReflowController(private val web: WebView) {
    fun load(html: String, baseUrl: String) {
        web.evaluateJavascript(
            "window.ReflowApi.load(${jsString(html)},${jsString(baseUrl)})",
            null,
        )
    }

    fun goToPage(n: Int) {
        web.evaluateJavascript("window.ReflowApi.goToPage($n)", null)
    }

    fun applyStyle(css: String) {
        web.evaluateJavascript("window.ReflowApi.applyStyle(${jsString(css)})", null)
    }

    private fun jsString(s: String): String = JSONObject.quote(s)
}

/**
 * Serves EPUB chapter CSS/images from the unpacked readers cache without
 * enabling [android.webkit.WebSettings.allowFileAccessFromFileURLs].
 *
 * Only paths under [readersRoot] (typically `cacheDir/readers/`) are returned.
 * `file:///android_asset/...` / `android_res` pass through to WebView (they do
 * not require [android.webkit.WebSettings.allowFileAccess]). Other rejected
 * `file://` URLs get an explicit empty response so WebView cannot fall back to
 * the filesystem loader.
 */
internal fun interceptEpubCacheRequest(
    url: Uri,
    readersRoot: File,
): WebResourceResponse? {
    if (url.scheme != "file") return null
    val path = url.path ?: return blockedFileResponse()
    // Asset/res schemes remain accessible with allowFileAccess=false.
    if (path.startsWith("/android_asset/") || path.startsWith("/android_res/")) return null

    val file = try {
        File(path).canonicalFile
    } catch (_: Exception) {
        return blockedFileResponse()
    }
    val root = try {
        readersRoot.canonicalFile
    } catch (_: Exception) {
        return blockedFileResponse()
    }
    val rootPath = root.path
    if (file.path != rootPath && !file.path.startsWith(rootPath + File.separator)) {
        return blockedFileResponse()
    }
    if (!file.isFile) return blockedFileResponse()

    val mime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase())
        ?: URLConnection.guessContentTypeFromName(file.name)
        ?: "application/octet-stream"
    return WebResourceResponse(mime, /* encoding = */ null, file.inputStream())
}

/** Empty response that stops WebView from loading a rejected file:// URL itself. */
internal fun blockedFileResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "utf-8",
        403,
        "Forbidden",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0)),
    )

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReflowWebView(
    modifier: Modifier,
    onTap: (Float) -> Unit,
    onScale: (Float) -> Unit,
    onEvent: (ReflowEvent) -> Unit,
    onCrash: () -> Unit,
    onReady: (ReflowController) -> Unit,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnScale by rememberUpdatedState(onScale)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnCrash by rememberUpdatedState(onCrash)
    val currentOnReady by rememberUpdatedState(onReady)

    // Create the WebView once and keep it stable across recompositions so the
    // page state survives. The bridge below posts back to the main thread.
    val webView = remember {
        WebView(context).apply {
            @Suppress("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            // Asset/res URLs work without filesystem access; EPUB cache files are
            // served exclusively via shouldInterceptRequest below.
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
    }

    DisposableEffect(webView) {
        val mainHandler = Handler(Looper.getMainLooper())
        var disposed = false
        var readyDelivered = false
        val density = context.resources.displayMetrics.density
        val readersRoot = File(context.cacheDir, "readers")
        val tapDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val width = webView.width.takeIf { it > 0 } ?: return false
                    currentOnTap((e.x / width).coerceIn(0f, 1f))
                    return true
                }
            },
        )
        val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    currentOnScale(detector.scaleFactor)
                    return true
                }
            },
        )

        // Push the WebView's real, Compose-measured size into the page as CSS px.
        // This is what gives every page a non-zero height; the WebView's own
        // viewport (`100vh`) latches to 0 because the page loads before layout.
        fun pushViewport() {
            val w = (webView.width / density).toInt()
            val h = (webView.height / density).toInt()
            if (w > 0 && h > 0) {
                webView.evaluateJavascript(
                    "window.ReflowApi&&window.ReflowApi.setViewport($w,$h)",
                    null,
                )
            }
        }

        val bridge = object {
            @JavascriptInterface
            fun onEvent(message: String) {
                val event = decodeReflowEvent(message) ?: return
                // JS interface callbacks arrive on a background thread; hop to main.
                mainHandler.post {
                    // The JS bridge can deliver after dispose destroyed the
                    // WebView; invoking callbacks against torn-down state is
                    // pointless at best.
                    if (disposed) return@post
                    if (event is ReflowEvent.Ready && !readyDelivered) {
                        readyDelivered = true
                        currentOnReady(ReflowController(webView))
                    }
                    if (event is ReflowEvent.Ready) pushViewport()
                    currentOnEvent(event)
                }
            }
        }

        // Re-push whenever the view is (re)laid out, e.g. rotation or the very
        // first measure pass that happens after the page has already loaded.
        val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or2, ob ->
            if (r - l != or2 - ol || b - t != ob - ot) pushViewport()
        }
        webView.addOnLayoutChangeListener(layoutListener)

        webView.addJavascriptInterface(bridge, "AndroidReflow")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                return interceptEpubCacheRequest(url, readersRoot)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                currentOnCrash()
                return true
            }
        }
        webView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) {
                tapDetector.onTouchEvent(event)
            }
            true
        }
        webView.loadUrl("file:///android_asset/reader/reflow/reader.html")

        onDispose {
            disposed = true
            webView.setOnTouchListener(null)
            webView.removeOnLayoutChangeListener(layoutListener)
            webView.removeJavascriptInterface("AndroidReflow")
            webView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
    )
}
