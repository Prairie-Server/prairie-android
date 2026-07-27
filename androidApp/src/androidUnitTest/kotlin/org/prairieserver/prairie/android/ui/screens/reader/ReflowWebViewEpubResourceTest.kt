package org.prairieserver.prairie.android.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReflowWebViewEpubResourceTest {
    private val webView = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/reader/reflow/ReflowWebView.kt",
    ).readText()
    private val paginator = File("src/androidMain/assets/reader/reflow/paginator.js").readText()
    private val readerHtml = File("src/androidMain/assets/reader/reflow/reader.html").readText()
    private val epubBook = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/reader/EpubBook.kt",
    ).readText()

    @Test
    fun reflowWebViewUsesOnlyThePrivateAppAssetsOrigin() {
        assertTrue(
            webView.contains("settings.allowFileAccess = false") &&
                webView.contains("settings.allowContentAccess = false") &&
                webView.contains("settings.allowFileAccessFromFileURLs = false") &&
                webView.contains("settings.allowUniversalAccessFromFileURLs = false"),
            "The reader WebView must disable all file/content-origin access.",
        )
        assertTrue(
            webView.contains(
                """loadUrl("https://appassets.androidplatform.net/assets/reader/reflow/reader.html")""",
            ),
            "The reader shell must load from the private HTTPS app-assets origin.",
        )
        assertTrue(
            webView.contains("WebViewAssetLoader") &&
                webView.contains("shouldInterceptRequest") &&
                webView.contains("assetLoader.shouldInterceptRequest"),
            "Every private-origin request must be intercepted by WebViewAssetLoader.",
        )
        assertFalse(webView.contains("""loadUrl("file:"""))
    }

    @Test
    fun paginatorRewritesRelativeResourcesWithoutCreatingABaseElement() {
        assertFalse(paginator.contains("createElement('base')"))
        assertFalse(paginator.contains("createElement(\"base\")"))
        assertTrue(paginator.contains("rewriteResourceUrls"))
        assertTrue(paginator.contains("appassets.androidplatform.net"))
        assertFalse(epubBook.contains("""URI("file""""))
        assertTrue(epubBook.contains("https://appassets.androidplatform.net/epub/"))
    }

    @Test
    fun readerShellHasStrictCspCompatibleWithPaginator() {
        assertTrue(readerHtml.contains("Content-Security-Policy"))
        listOf(
            "default-src 'none'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self'",
            "font-src 'self'",
            "connect-src 'none'",
            "frame-src 'none'",
            "object-src 'none'",
            "form-action 'none'",
            "base-uri 'none'",
        ).forEach { directive ->
            assertTrue(readerHtml.contains(directive), "Missing CSP directive: $directive")
        }
    }

    @Test
    fun readerBlocksNavigationAwayFromItsShell() {
        assertTrue(
            webView.contains("shouldOverrideUrlLoading") &&
                webView.contains("READER_SHELL_URL"),
            "The reader must explicitly reject navigation away from its private shell.",
        )
    }
}
