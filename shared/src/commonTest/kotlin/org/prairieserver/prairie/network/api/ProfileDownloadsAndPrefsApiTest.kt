package org.prairieserver.prairie.network.api

import org.prairieserver.prairie.model.download.DownloadRequest
import org.prairieserver.prairie.model.profile.CreateProfileRequest
import org.prairieserver.prairie.model.profile.UpdateProfileRequest
import org.prairieserver.prairie.model.settings.LibraryPlaybackPrefRequest
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
import kotlin.test.Test
import kotlin.test.assertIs

class ProfileDownloadsAndPrefsApiTest {
    private fun client(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = HttpClient(
        MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) { install(ContentNegotiation) { json(PrairieJson) } }

    @Test
    fun coversProfileApiRoutes() = runTest {
        val api = ProfileApi(client(body = """{"profiles":[]}"""))
        assertIs<ApiResult.Success<*>>(api.listProfiles())
        assertIs<ApiResult.Success<*>>(
            ProfileApi(client(body = """{"id":"p1","name":"Kids","is_child":true}"""))
                .createProfile(CreateProfileRequest(name = "Kids")),
        )
        assertIs<ApiResult.Success<*>>(
            ProfileApi(client(body = """{"id":"p1","name":"Kids","is_child":true}"""))
                .updateProfile("p1", UpdateProfileRequest(name = "Kids")),
        )
        assertIs<ApiResult.Success<*>>(
            ProfileApi(client(status = HttpStatusCode.NoContent, body = "")).deleteProfile("p1"),
        )
        assertIs<ApiResult.Success<*>>(
            ProfileApi(client(body = """{"valid":true,"profile_token":"tok"}"""))
                .verifyPin("p1", "1234"),
        )
    }

    @Test
    fun coversDownloadsApiRoutes() = runTest {
        val api = DownloadsApi(client(body = """{"downloads":[]}"""))
        assertIs<ApiResult.Success<*>>(api.list())
        assertIs<ApiResult.Success<*>>(
            DownloadsApi(client(body = """{"enabled":true,"download_allowed":true}""")).capability(),
        )
        assertIs<ApiResult.Success<*>>(
            DownloadsApi(
                client(
                    body = """{"id":"d1","content_id":"c1","media_file_id":1,"kind":"queued","status":"queued","created_at":"t"}""",
                ),
            ).create(DownloadRequest(contentId = "c1", fileId = 1)),
        )
        assertIs<ApiResult.Success<*>>(
            DownloadsApi(client(body = """{"downloads":[]}"""))
                .createBatch(DownloadRequest(contentId = "s1", series = true)),
        )
        assertIs<ApiResult.Success<*>>(
            DownloadsApi(client(status = HttpStatusCode.NoContent, body = "")).delete("d1"),
        )
    }

    @Test
    fun coversLibraryPlaybackPrefsApiRoutes() = runTest {
        val api = LibraryPlaybackPrefsApi(client(body = """{"preferences":[]}"""))
        assertIs<ApiResult.Success<*>>(api.list())
        assertIs<ApiResult.Success<*>>(
            LibraryPlaybackPrefsApi(client(status = HttpStatusCode.NoContent, body = ""))
                .set(3, LibraryPlaybackPrefRequest(audioLanguage = "en")),
        )
        assertIs<ApiResult.Success<*>>(
            LibraryPlaybackPrefsApi(client(status = HttpStatusCode.NoContent, body = "")).delete(3),
        )
    }
}
