package org.prairieserver.prairie.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.playback.TranscodeStartRequest
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.DurableLoginAuthority
import org.prairieserver.prairie.network.DurableLoginAuthorityProvider
import org.prairieserver.prairie.network.PrairieJson
import org.prairieserver.prairie.network.TokenManager
import org.prairieserver.prairie.network.TokenManagerImpl
import org.prairieserver.prairie.network.api.PlaybackApi
import org.prairieserver.prairie.network.apiv2.ApiV2Gate
import org.prairieserver.prairie.network.apiv2.PlaybackV2Api
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Covers the Prairie-only transcode / quality-ladder surface of [PlaybackRepository]. */
class PlaybackRepositoryTest {
    private class Identity : TokenManager by TokenManagerImpl(), DurableLoginAuthorityProvider {
        override suspend fun snapshotDurableLoginAuthority(): DurableLoginAuthority? = null
    }

    private class Store : PlaybackJournalStore {
        var rows = emptyList<PlaybackJournalEntry>()
        override suspend fun read() = rows
        override suspend fun write(entries: List<PlaybackJournalEntry>) { rows = entries }
    }

    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
    ) { install(ContentNegotiation) { json(PrairieJson) } }

    private fun sequenced(client: HttpClient): SequencedPlayback {
        val identity = Identity()
        return SequencedPlayback(PlaybackV2Api(client, ApiV2Gate.Unrestricted), identity, identity, Store()) { "stop" }
    }

    private val transcodeRequest = TranscodeStartRequest(
        sessionId = "s1",
        seekSeconds = 10.0,
        targetBitrateKbps = 4000,
        segmentDuration = 4,
        subtitleBurnIn = false,
    )

    @Test
    fun startTranscodeAndQualityLadderUseTheTranscodeApi() = runTest {
        val transcodeClient = client("""{"session_id":"t1","status":"ok","manifest_url":"https://x/t"}""")
        val repo = PlaybackRepository(sequenced(client("{}")), PlaybackApi(transcodeClient))
        assertIs<ApiResult.Success<*>>(repo.startTranscode(transcodeRequest))

        val ladderClient = client("""{"rungs":[],"modes":["auto"],"source_height":1080}""")
        val ladderRepo = PlaybackRepository(sequenced(client("{}")), PlaybackApi(ladderClient))
        assertIs<ApiResult.Success<*>>(ladderRepo.getQualityLadder(1080))
    }

    @Test
    fun transcodeRoutesReportUnavailableWithoutTheTranscodeApi() = runTest {
        val repo = PlaybackRepository(sequenced(client("{}")))
        assertEquals("transcode_unavailable", assertIs<ApiResult.Error>(repo.startTranscode(transcodeRequest)).error)
        assertEquals("transcode_unavailable", assertIs<ApiResult.Error>(repo.getQualityLadder()).error)
    }
}
