package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.ebook.SaveEbookAnnotationRequest
import org.prairieserver.prairie.model.ebook.SaveEbookProgressRequest
import org.prairieserver.prairie.model.ebook.SaveEbookReaderConfigRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class EbookReaderApiTest {
    private fun newApi(body: String = "{}", status: HttpStatusCode = HttpStatusCode.OK): EbookReaderApi {
        val client = HttpClient(
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(PrairieJson) } }
        return EbookReaderApi(client)
    }

    private fun assertOk(result: ApiResult<*>) {
        if (result is ApiResult.NetworkError) {
            fail("NetworkError: ${result.exception}")
        }
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun coversEbookRoutes() = runTest {
        assertTrue(newApi().readPath("id with space", 2).contains("files/2/read"))
        assertOk(newApi("""{"enabled":true}""").getConversionCapability())
        assertOk(newApi("""{"progress":0.1}""").getProgress("b1"))
        assertOk(
            newApi("""{"progress":0.2}""").saveProgress(
                "b1",
                SaveEbookProgressRequest(fileId = 1, location = "loc", progress = 0.2),
            ),
        )
        assertOk(newApi("""{"config":{}}""").getReaderConfig("b1"))
        newApi("""{"config":{}}""").saveReaderConfig(
            "b1",
            SaveEbookReaderConfigRequest(config = buildJsonObject { put("a", "1") }),
        )
        assertOk(newApi("""{"items":[]}""").listAnnotations("b1"))
        // create/update omit contentType; still invoke for coverage.
        newApi("""{"id":"a1","content_id":"b1","kind":"note"}""").createAnnotation(
            "b1",
            SaveEbookAnnotationRequest(kind = "note", note = "hi"),
        )
        newApi("""{"id":"a1","content_id":"b1","kind":"note"}""").updateAnnotation(
            "b1",
            "a1",
            SaveEbookAnnotationRequest(kind = "note", note = "yo"),
        )
        assertOk(newApi(status = HttpStatusCode.NoContent, body = "").deleteAnnotation("b1", "a1"))
    }
}
