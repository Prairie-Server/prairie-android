package org.prairieserver.prairie.android.ui.screens.reader

import android.net.Uri
import org.junit.runner.RunWith
import org.prairieserver.prairie.android.ui.screens.reader.reflow.blockedFileResponse
import org.prairieserver.prairie.android.ui.screens.reader.reflow.interceptEpubCacheRequest
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReflowWebViewEpubResourceTest {
    private val webView = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/reader/reflow/ReflowWebView.kt",
    ).readText()
    private val paginator = File("src/androidMain/assets/reader/reflow/paginator.js").readText()

    @Test
    fun reflowWebViewAllowsInjectedEpubHtmlToLoadExtractedResources() {
        assertTrue(
            paginator.contains("document.querySelector('base')") &&
                paginator.contains("b.href = baseUrl"),
            "The reflow page must install the EPUB directory as the document base URL.",
        )
        assertTrue(
            webView.contains("settings.allowFileAccess = false") &&
                webView.contains("settings.allowFileAccessFromFileURLs = false") &&
                webView.contains("settings.allowUniversalAccessFromFileURLs = false"),
            "The asset-backed reflow WebView must not grant filesystem or file-origin access.",
        )
        assertTrue(
            webView.contains("shouldInterceptRequest") &&
                webView.contains("interceptEpubCacheRequest"),
            "EPUB CSS/images under the readers cache must be served via shouldInterceptRequest.",
        )
    }

    @Test
    fun interceptServesOnlyFilesUnderReadersRoot() {
        val root = createTempDirectory("readers-root-").toFile()
        try {
            val allowed = File(root, "epub-abc/OEBPS/images/cover.jpg").apply {
                parentFile!!.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val outside = File(root.parentFile!!, "outside.jpg").apply {
                writeBytes(byteArrayOf(9))
            }

            val served = interceptEpubCacheRequest(Uri.fromFile(allowed), root)
            assertNotNull(served)
            assertEquals("image/jpeg", served.mimeType)
            assertEquals(listOf(1.toByte(), 2.toByte(), 3.toByte()), served.data.readBytes().toList())
            served.data.close()

            val blocked = interceptEpubCacheRequest(Uri.fromFile(outside), root)
            assertNotNull(blocked)
            assertEquals(403, blocked.statusCode)
            assertEquals(0, blocked.data.readBytes().size)
            blocked.data.close()

            assertNull(
                interceptEpubCacheRequest(
                    Uri.parse("file:///android_asset/reader/reflow/reader.html"),
                    root,
                ),
            )
            assertNull(interceptEpubCacheRequest(Uri.parse("https://example.test/x"), root))

            // Sanity: helper matches the shape returned for rejected paths.
            val empty = blockedFileResponse()
            assertEquals(403, empty.statusCode)
            empty.data.close()
        } finally {
            root.deleteRecursively()
            File(root.parentFile, "outside.jpg").delete()
        }
    }
}
