package com.continuum.app.network.api

import com.continuum.app.network.ApiResult
import com.continuum.app.network.ContinuumJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CatalogApiTest {

    private class Captured {
        var path: String = ""
        var query: Map<String, String?> = emptyMap()
    }

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = """{"total":0,"has_more":false,"items":[]}""",
        captured: Captured = Captured(),
    ): Pair<CatalogApi, Captured> {
        val client = HttpClient(
            MockEngine { request ->
                captured.path = request.url.encodedPath
                captured.query = request.url.parameters.names()
                    .associateWith { request.url.parameters[it] }
                respond(
                    content = responseBody,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(ContinuumJson) }
        }
        return CatalogApi(client) to captured
    }

    @Test
    fun `getCatalog with snapshotAt sends query param named snapshot not snapshot_at`() = runTest {
        val (api, captured) = api()

        val result = api.getCatalog(snapshotAt = "2026-06-15T10:00:00Z")

        assertEquals("/api/v1/catalog", captured.path)
        assertTrue("snapshot" in captured.query.keys, "Expected 'snapshot' key in query params")
        assertEquals("2026-06-15T10:00:00Z", captured.query["snapshot"])
        assertFalse("snapshot_at" in captured.query.keys, "Must NOT send 'snapshot_at' (server reads 'snapshot')")
        assertIs<ApiResult.Success<*>>(result)
    }

    @Test
    fun `getCatalog without snapshotAt omits snapshot param entirely`() = runTest {
        val (api, captured) = api()

        api.getCatalog(limit = 20)

        assertFalse("snapshot" in captured.query.keys, "snapshot should be absent when not supplied")
        assertFalse("snapshot_at" in captured.query.keys, "snapshot_at should never appear")
        assertEquals("20", captured.query["limit"])
    }
}
