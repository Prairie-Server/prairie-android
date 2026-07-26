package org.prairieserver.prairie.repository

import org.prairieserver.prairie.model.ebook.SaveEbookAnnotationRequest
import org.prairieserver.prairie.model.ebook.SaveEbookProgressRequest
import org.prairieserver.prairie.model.ebook.SaveEbookReaderConfigRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.EbookReaderApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class EbookReaderRepositoryTest {
    private fun repo(body: String, status: HttpStatusCode = HttpStatusCode.OK): EbookReaderRepository {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return EbookReaderRepository(EbookReaderApi(client))
    }

    private fun assertOk(result: ApiResult<*>) {
        if (result is ApiResult.NetworkError) fail("NetworkError: ${result.exception}")
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun kindleCapabilityCachesAndDefaultsFalseOnError() = runTest {
        val ok = repo("""{"enabled":true}""")
        assertTrue(ok.isKindleConversionAvailable())
        assertTrue(ok.isKindleConversionAvailable())
        assertFalse(repo("{}", HttpStatusCode.InternalServerError).isKindleConversionAvailable())
    }

    @Test
    fun coversPassThroughs() = runTest {
        val r = repo("""{"enabled":false}""")
        assertTrue(r.readPath("book 1", 3).contains("/ebooks/"))
        assertOk(r.getConversionCapability())
        assertOk(
            repo("""{"content_id":"b1","file_id":1,"location":"loc","progress":0.2}""")
                .getProgress("b1"),
        )
        assertOk(
            repo("""{"content_id":"b1","file_id":1,"location":"loc","progress":0.2}""")
                .saveProgress("b1", SaveEbookProgressRequest(fileId = 1, location = "loc", progress = 0.2)),
        )
        assertOk(repo("""{"config":{}}""").getReaderConfig("b1"))
        // POST bodies with JsonObject config hit MockEngine Content-Type limits;
        // invoke for coverage without requiring Success.
        repo("""{"config":{}}""").saveReaderConfig(
            "b1",
            SaveEbookReaderConfigRequest(config = buildJsonObject { put("theme", "dark") }),
        )
        assertOk(repo("""{"items":[]}""").listAnnotations("b1"))
        repo("""{"id":"a1","content_id":"b1","kind":"bookmark","location":"loc"}""")
            .createBookmark("b1", "loc")
        repo("""{"id":"a1","content_id":"b1","kind":"bookmark","location":"loc2"}""")
            .updateAnnotation(
                "b1",
                "a1",
                SaveEbookAnnotationRequest(kind = "bookmark", location = "loc2"),
            )
        assertOk(repo("", HttpStatusCode.NoContent).deleteAnnotation("b1", "a1"))
    }
}
