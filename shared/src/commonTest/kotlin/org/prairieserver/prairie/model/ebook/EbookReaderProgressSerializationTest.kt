package org.prairieserver.prairie.model.ebook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class EbookReaderProgressSerializationTest {

    // Mirrors PrairieJson (PrairieHttpClientImpl.kt).
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * GET /api/v1/ebooks/{id}/progress returns `{}` when no progress has been
     * saved (book never opened). Before the fix this threw MissingFieldException
     * because contentId/fileId/location were non-nullable. After the fix, all
     * three must default to null so an unread book starts from the beginning.
     */
    @Test
    fun decodesEmptyProgressObjectWithoutThrowingMissingFieldException() {
        val progress = json.decodeFromString<EbookReaderProgress>("{}")

        assertNull(progress.contentId, "contentId should be null when missing from response")
        assertNull(progress.fileId, "fileId should be null when missing from response")
        assertNull(progress.location, "location should be null when missing from response")
        assertEquals(0.0, progress.progress, "progress should default to 0.0")
        assertNull(progress.updatedAt, "updatedAt should be null when missing from response")
    }

    /**
     * A fully-populated progress response must still round-trip correctly so
     * existing resume-from-position behaviour is not broken.
     */
    @Test
    fun decodesFullProgressObject() {
        val raw = """
            {
              "content_id": "ebook-123",
              "file_id": 42,
              "location": "epubcfi(/6/4[chap01ref]!/4[body01]/10[para05]/2/1:3)",
              "progress": 0.37,
              "updated_at": "2024-01-15T10:30:00Z"
            }
        """.trimIndent()

        val progress = json.decodeFromString<EbookReaderProgress>(raw)

        assertEquals("ebook-123", progress.contentId)
        assertEquals(42, progress.fileId)
        assertEquals("epubcfi(/6/4[chap01ref]!/4[body01]/10[para05]/2/1:3)", progress.location)
        assertEquals(0.37, progress.progress)
        assertEquals("2024-01-15T10:30:00Z", progress.updatedAt)
    }

    @Test
    fun readerConfigAnnotationsAndSaveRequestsRoundTrip() {
        val config = EbookReaderConfig(
            contentId = "ebook-123",
            config = JsonObject(mapOf("theme" to JsonPrimitive("sepia"))),
            updatedAt = "2024-01-16T10:30:00Z",
        )
        val configRaw = json.encodeToString(EbookReaderConfig.serializer(), config)
        assertEquals("sepia", json.decodeFromString<EbookReaderConfig>(configRaw).config["theme"]?.toString()?.trim('"'))

        val saveConfig = SaveEbookReaderConfigRequest(config.config)
        assertEquals(config.config, saveConfig.config)

        val saveProgress = SaveEbookProgressRequest(
            fileId = 42,
            location = "epubcfi(/6/4)",
            progress = 0.5,
        )
        assertEquals(0.5, saveProgress.progress)

        val annotation = EbookAnnotation(
            id = "ann-1",
            contentId = "ebook-123",
            kind = "highlight",
            cfiRange = "epubcfi(/6/4,/1:0,/1:5)",
            location = "chapter-1",
            selectedText = "Selected",
            note = "Note",
            style = "underline",
            color = "yellow",
            metadata = JsonObject(mapOf("page" to JsonPrimitive(12))),
            createdAt = "2024-01-16T10:30:00Z",
            updatedAt = "2024-01-17T10:30:00Z",
        )
        val response = EbookAnnotationListResponse(items = listOf(annotation))
        assertEquals("ann-1", response.items.single().id)

        val saveAnnotation = SaveEbookAnnotationRequest(
            kind = "highlight",
            cfiRange = annotation.cfiRange,
            location = annotation.location,
            selectedText = annotation.selectedText,
            note = annotation.note,
            style = annotation.style,
            color = annotation.color,
            metadata = annotation.metadata,
        )
        assertEquals("Selected", saveAnnotation.selectedText)

        val bookmark = localBookmarkAnnotation(
            id = "bookmark-1",
            contentId = "ebook-123",
            location = "epubcfi(/6/8)",
            createdAt = "2024-01-18T10:30:00Z",
        )
        assertEquals("bookmark", bookmark.kind)
        assertEquals("epubcfi(/6/8)", bookmark.location)
    }
}
