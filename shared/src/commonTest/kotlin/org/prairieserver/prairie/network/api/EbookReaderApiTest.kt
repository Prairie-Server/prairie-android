package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.ebook.SaveEbookAnnotationRequest
import org.prairieserver.prairie.model.ebook.SaveEbookProgressRequest
import org.prairieserver.prairie.model.ebook.SaveEbookReaderConfigRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class EbookReaderApiTest {
    private class Captured {
        var method: HttpMethod? = null
        var path: String = ""
        var contentType: ContentType? = null
        var body: String = ""
    }

    private fun newApi(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
        captured: Captured? = null,
    ): EbookReaderApi {
        val client = HttpClient(
            MockEngine { request ->
                captured?.let {
                    it.method = request.method
                    it.path = request.url.encodedPath
                    it.contentType = request.body.contentType
                    it.body = request.body.toByteArray().decodeToString()
                }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
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
        assertOk(newApi("""{"items":[]}""").listAnnotations("b1"))
        assertOk(newApi(status = HttpStatusCode.NoContent, body = "").deleteAnnotation("b1", "a1"))
    }

    @Test
    fun writeRoutesSendJsonContract() = runTest {
        val saveConfig = Captured()
        assertOk(
            newApi("""{"config":{}}""", captured = saveConfig).saveReaderConfig(
                "b1",
                SaveEbookReaderConfigRequest(config = buildJsonObject { put("a", "1") }),
            ),
        )
        assertEquals(HttpMethod.Put, saveConfig.method)
        assertEquals("/api/v1/ebooks/b1/reader-config", saveConfig.path)
        assertEquals(ContentType.Application.Json, saveConfig.contentType?.withoutParameters())
        assertEquals("1", PrairieJson.parseToJsonElement(saveConfig.body).jsonObject["config"]!!.jsonObject["a"]!!.jsonPrimitive.content)

        val list = Captured()
        assertOk(newApi("""{"items":[]}""", captured = list).listAnnotations("b1"))
        assertEquals(HttpMethod.Get, list.method)
        assertEquals("/api/v1/ebooks/b1/annotations", list.path)

        val create = Captured()
        assertOk(
            newApi("""{"id":"a1","content_id":"b1","kind":"note"}""", captured = create).createAnnotation(
                "b1",
                SaveEbookAnnotationRequest(kind = "note", note = "hi"),
            ),
        )
        assertEquals(HttpMethod.Post, create.method)
        assertEquals("/api/v1/ebooks/b1/annotations", create.path)
        assertEquals(ContentType.Application.Json, create.contentType?.withoutParameters())
        val createBody = PrairieJson.parseToJsonElement(create.body).jsonObject
        assertEquals("note", createBody["kind"]!!.jsonPrimitive.content)
        assertEquals("hi", createBody["note"]!!.jsonPrimitive.content)

        val update = Captured()
        assertOk(
            newApi("""{"id":"a1","content_id":"b1","kind":"note"}""", captured = update).updateAnnotation(
                "b1",
                "a1",
                SaveEbookAnnotationRequest(kind = "note", note = "yo"),
            ),
        )
        assertEquals(HttpMethod.Patch, update.method)
        assertEquals("/api/v1/ebooks/b1/annotations/a1", update.path)
        assertEquals(ContentType.Application.Json, update.contentType?.withoutParameters())
        assertEquals("yo", PrairieJson.parseToJsonElement(update.body).jsonObject["note"]!!.jsonPrimitive.content)
    }
}
