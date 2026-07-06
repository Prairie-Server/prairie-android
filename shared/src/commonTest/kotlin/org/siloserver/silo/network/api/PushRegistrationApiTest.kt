package org.siloserver.silo.network.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.siloserver.silo.model.notifications.PushDeviceRegisterRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushRegistrationApiTest {
    @Test
    fun registerAndroidDevicePostsOpaqueTokenPayload() = runTest {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { request ->
            captured = request
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val api = DefaultPushRegistrationApi(client)

        api.register(
            PushDeviceRegisterRequest(
                platform = "android",
                token = "fcm-token",
                deviceId = "device",
                pushMode = "private_push",
            ),
        )

        assertEquals("/api/v1/notifications/push/devices", captured?.url?.encodedPath)
        assertTrue(captured.toString().contains("POST"))
    }
}
