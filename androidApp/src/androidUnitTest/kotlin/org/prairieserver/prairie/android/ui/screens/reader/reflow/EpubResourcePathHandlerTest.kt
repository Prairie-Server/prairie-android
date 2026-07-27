package org.prairieserver.prairie.android.ui.screens.reader.reflow

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun rejectsAValidNormalizedPathBelongingToAnotherCachedBook() {
        withFixture { fixture ->
            val otherRoot = fixture.root.resolve("epub-${"b".repeat(40)}").apply { mkdirs() }
            val otherChapter = otherRoot.resolve("OEBPS/private.xhtml")
            requireNotNull(otherChapter.parentFile).mkdirs()
            otherChapter.writeText("other book")

            assertEmpty404(
                fixture.handler.handle("${otherRoot.name}/OEBPS/private.xhtml"),
                otherChapter.path,
            )
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

    @Test
    fun returnsEmpty404ForIntermediateAndOutsideSymlinks() {
        withFixture { fixture ->
            val realImages = java.io.File(fixture.bookRoot, "OEBPS/real-images").apply { mkdirs() }
            java.io.File(realImages, "inside.jpg").writeText("inside")
            val intermediateLink = java.io.File(fixture.bookRoot, "OEBPS/images")
            Files.createSymbolicLink(intermediateLink.toPath(), realImages.toPath())

            val outsideDirectory = createTempDirectory("outside-epub-resource").toFile()
            try {
                java.io.File(outsideDirectory, "outside.jpg").writeText("outside")
                val outsideLink = java.io.File(fixture.bookRoot, "OEBPS/outside")
                Files.createSymbolicLink(outsideLink.toPath(), outsideDirectory.toPath())

                listOf(
                    "${fixture.bookRoot.name}/OEBPS/images/inside.jpg",
                    "${fixture.bookRoot.name}/OEBPS/outside/outside.jpg",
                ).forEach { path ->
                    assertEmpty404(fixture.handler.handle(path), path)
                }
            } finally {
                outsideDirectory.deleteRecursively()
            }
        }
    }

    @Test
    fun returnsEmpty404ForEncodedSeparatorsInvalidEscapesAndNul() {
        withFixture { fixture ->
            val cover = java.io.File(fixture.bookRoot, "OEBPS/images/cover.jpg")
            requireNotNull(cover.parentFile).mkdirs()
            cover.writeText("cover")

            listOf(
                "${fixture.bookRoot.name}%2fOEBPS%2fimages%2fcover.jpg",
                "${fixture.bookRoot.name}%252fOEBPS%252fimages%252fcover.jpg",
                "${fixture.bookRoot.name}%5cOEBPS%5cimages%5ccover.jpg",
                "${fixture.bookRoot.name}%255cOEBPS%255cimages%255ccover.jpg",
                "${fixture.bookRoot.name}/OEBPS/images/%",
                "${fixture.bookRoot.name}/OEBPS/images/%2",
                "${fixture.bookRoot.name}/OEBPS/images/%00cover.jpg",
            ).forEach { path ->
                assertEmpty404(fixture.handler.handle(path), path)
            }
        }
    }

    @Test
    fun returnsEmpty404ForDirectoriesAndMissingFiles() {
        withFixture { fixture ->
            java.io.File(fixture.bookRoot, "OEBPS/images").mkdirs()

            listOf(
                fixture.bookRoot.name,
                "${fixture.bookRoot.name}/OEBPS/images",
                "${fixture.bookRoot.name}/OEBPS/images/missing.jpg",
            ).forEach { path ->
                assertEmpty404(fixture.handler.handle(path), path)
            }
        }
    }

    @Test
    fun reportsKnownAndUnknownMimeTypesWithoutGuessing() {
        withFixture { fixture ->
            val styles = java.io.File(fixture.bookRoot, "OEBPS/styles/book.css")
            requireNotNull(styles.parentFile).mkdirs()
            styles.writeText("body{}")
            val unknown = java.io.File(fixture.bookRoot, "OEBPS/resources/blob.bin")
            requireNotNull(unknown.parentFile).mkdirs()
            unknown.writeBytes(byteArrayOf(7))

            val cssResponse = fixture.handler.handle("${fixture.bookRoot.name}/OEBPS/styles/book.css")
            val unknownResponse = fixture.handler.handle("${fixture.bookRoot.name}/OEBPS/resources/blob.bin")

            assertEquals(200, cssResponse.statusCode)
            assertEquals("text/css", cssResponse.mimeType)
            assertEquals("UTF-8", cssResponse.encoding)
            assertEquals(200, unknownResponse.statusCode)
            assertEquals("application/octet-stream", unknownResponse.mimeType)
            assertNull(unknownResponse.encoding)
        }
    }

    private fun assertEmpty404(response: android.webkit.WebResourceResponse, path: String) {
        assertEquals(404, response.statusCode, "accepted $path")
        assertContentEquals(byteArrayOf(), response.data.readBytes(), "non-empty 404 for $path")
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val root = createTempDirectory("epub-resource-root").toFile()
        try {
            val bookRoot = root.resolve("epub-${"a".repeat(40)}").apply { mkdirs() }
            block(Fixture(root, bookRoot, EpubResourcePathHandler(root, bookRoot.name)))
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
