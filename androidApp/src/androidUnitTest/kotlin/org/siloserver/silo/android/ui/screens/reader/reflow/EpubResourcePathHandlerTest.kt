package org.siloserver.silo.android.ui.screens.reader.reflow

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class EpubResourcePathHandlerTest {
    @Test
    fun servesCanonicalFilesFromHashedEpubDirectory() {
        withFixture { fixture ->
            val image = java.io.File(fixture.bookRoot, "OEBPS/images/cover.jpg")
            requireNotNull(image.parentFile).mkdirs()
            image.writeBytes(byteArrayOf(1, 2, 3))

            val response = fixture.handler.handle("${fixture.bookRoot.name}/OEBPS/images/cover.jpg")

            assertEquals(200, response.statusCode)
            assertEquals("image/jpeg", response.mimeType)
            assertContentEquals(byteArrayOf(1, 2, 3), response.data.readBytes())
        }
    }

    @Test
    fun returnsEmpty404ForTraversalAndOutOfRootPaths() {
        withFixture { fixture ->
            val outside = java.io.File(fixture.root.parentFile, "outside-reader-secret.txt")
            outside.writeText("secret")
            try {
                listOf(
                    "../${outside.name}",
                    "%2e%2e/${outside.name}",
                    "%252e%252e%252f${outside.name}",
                    "/${outside.absolutePath}",
                    "${fixture.bookRoot.name}/../../${outside.name}",
                    "not-an-epub/private.txt",
                ).forEach { path ->
                    val response = fixture.handler.handle(path)
                    assertEquals(404, response.statusCode, "accepted $path")
                    assertContentEquals(byteArrayOf(), response.data.readBytes(), "non-empty 404 for $path")
                }
            } finally {
                outside.delete()
            }
        }
    }

    @Test
    fun returnsEmpty404ForSymlinksEvenWhenTheirTargetIsInsideRoot() {
        withFixture { fixture ->
            val target = java.io.File(fixture.bookRoot, "OEBPS/images/cover.jpg")
            requireNotNull(target.parentFile).mkdirs()
            target.writeText("cover")
            val link = java.io.File(fixture.bookRoot, "OEBPS/images/link.jpg")
            Files.createSymbolicLink(link.toPath(), target.toPath())

            val response = fixture.handler.handle("${fixture.bookRoot.name}/OEBPS/images/link.jpg")

            assertEquals(404, response.statusCode)
            assertContentEquals(byteArrayOf(), response.data.readBytes())
        }
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val root = createTempDirectory("epub-resource-root").toFile()
        try {
            val bookRoot = root.resolve("epub-${"a".repeat(40)}").apply { mkdirs() }
            block(Fixture(root, bookRoot, EpubResourcePathHandler(root)))
        } finally {
            root.deleteRecursively()
        }
    }

    private data class Fixture(
        val root: java.io.File,
        val bookRoot: java.io.File,
        val handler: EpubResourcePathHandler,
    )
}
