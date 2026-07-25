package org.prairieserver.prairie.domain

import org.prairieserver.prairie.model.playback.ClientCodecCapabilities
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.api.CatalogApi
import org.prairieserver.prairie.network.api.PlaybackApi
import org.prairieserver.prairie.repository.CatalogRepository
import org.prairieserver.prairie.repository.PlaybackRepository
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

class ManagePlaybackUseCaseTest {
    private fun useCase(): ManagePlaybackUseCase {
        val client = HttpClient(MockEngine { req ->
            val body = when {
                req.url.encodedPath.contains("watch") ->
                    """{"content_id":"m1","type":"movie","title":"M"}"""
                req.url.encodedPath.contains("start") ->
                    """{"session_id":"s1","user_id":1,"media_file_id":9,"play_method":"direct","stream_url":"https://x"}"""
                else -> ""
            }
            val status = if (body.isEmpty()) HttpStatusCode.NoContent else HttpStatusCode.OK
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json(PrairieJson) } }
        return ManagePlaybackUseCase(
            PlaybackRepository(PlaybackApi(client)),
            CatalogRepository(CatalogApi(client)),
        )
    }

    @Test
    fun coversLifecycle() = runTest {
        val uc = useCase()
        assertIs<ApiResult.Success<*>>(
            uc.startPlayback("m1", fileId = 9, profileId = "p", capabilities = ClientCodecCapabilities(), startPosition = 1.0),
        )
        assertIs<ApiResult.Success<*>>(uc.reportProgress("s1", 2.0, false))
        assertIs<ApiResult.Success<*>>(uc.stopPlayback("s1"))
        assertIs<ApiResult.Success<*>>(uc.getWatchDetail("m1"))
    }
}
