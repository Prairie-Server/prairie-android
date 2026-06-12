package com.continuum.app.android.ui.screens.reader.reflow

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReflowWebView(
    modifier: Modifier,
    onEvent: (ReflowEvent) -> Unit,
    onCrash: () -> Unit,
    onReady: (ReflowController) -> Unit,
) {
    val context = LocalContext.current

    // Create the WebView once and keep it stable across recompositions so the
    // page state survives. The bridge below posts back to the main thread.
    val webView = remember {
        WebView(context).apply {
            @Suppress("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
    }

    DisposableEffect(webView) {
        val mainHandler = Handler(Looper.getMainLooper())
        var readyDelivered = false

        val bridge = object {
            @JavascriptInterface
            fun onEvent(message: String) {
                val event = decodeReflowEvent(message) ?: return
                // JS interface callbacks arrive on a background thread; hop to main.
                mainHandler.post {
                    if (event is ReflowEvent.Ready && !readyDelivered) {
                        readyDelivered = true
                        onReady(ReflowController(webView))
                    }
                    onEvent(event)
                }
            }
        }

        webView.addJavascriptInterface(bridge, "AndroidReflow")
        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                onCrash()
                return true
            }
        }
        webView.loadUrl("file:///android_asset/reader/reflow/reader.html")

        onDispose {
            webView.removeJavascriptInterface("AndroidReflow")
            webView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
    )
}
