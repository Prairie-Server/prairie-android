package org.prairieserver.prairie.repository

import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.livetv.LiveTvChannel
import org.prairieserver.prairie.model.livetv.LiveTvChannelsResponse
import org.prairieserver.prairie.model.livetv.LiveTvGuideResponse
import org.prairieserver.prairie.model.livetv.LiveTvProgram
import org.prairieserver.prairie.model.livetv.LiveTvSession
import org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.LiveTvApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LiveTvRepositoryTest {

    @Test
    fun channelsCachesSuccessfulList() = runTest {
        val api = FakeLiveTvApi(
            channelsResult = ApiResult.Success(
                LiveTvChannelsResponse(
                    listOf(LiveTvChannel(id = "1", number = "4.1", name = "KRON")),
                ),
            ),
        )
        val repo = LiveTvRepository(api)

        val result = repo.channels()
        assertIs<ApiResult.Success<*>>(result)
        assertEquals(listOf("1"), repo.channels.value.map { it.id })
    }

    @Test
    fun channelsErrorDoesNotClobberCache() = runTest {
        val api = FakeLiveTvApi(
            channelsResult = ApiResult.Success(
                LiveTvChannelsResponse(listOf(LiveTvChannel(id = "1", name = "A"))),
            ),
        )
        val repo = LiveTvRepository(api)
        repo.channels()

        api.channelsResult = ApiResult.NetworkError(IllegalStateException("offline"))
        val failed = repo.channels()
        assertIs<ApiResult.NetworkError>(failed)
        assertEquals(listOf("1"), repo.channels.value.map { it.id })
    }

    @Test
    fun startAndReleasePassThrough() = runTest {
        val api = FakeLiveTvApi(
            startResult = ApiResult.Success(
                LiveTvSessionStartResponse(sessionId = "s1", hlsUrl = "/live.m3u8"),
            ),
            releaseResult = ApiResult.Success(LiveTvSession(id = "s1", status = "released")),
        )
        val repo = LiveTvRepository(api)

        val started = assertIs<ApiResult.Success<LiveTvSessionStartResponse>>(repo.startSession("ch"))
        assertEquals("s1", started.data.sessionId)
        assertEquals(1, api.startCalls)

        val released = assertIs<ApiResult.Success<LiveTvSession>>(repo.releaseSession("s1"))
        assertEquals("released", released.data.status)
        assertEquals(listOf("s1"), api.releaseCalls)
    }

    @Test
    fun resetClearsCachedChannels() = runTest {
        val repo = LiveTvRepository(
            FakeLiveTvApi(
                channelsResult = ApiResult.Success(
                    LiveTvChannelsResponse(listOf(LiveTvChannel(id = "1"))),
                ),
            ),
        )
        repo.channels()
        repo.reset()
        assertTrue(repo.channels.value.isEmpty())
    }

    @Test
    fun guideAndProgramPassThrough() = runTest {
        val api = FakeLiveTvApi(
            guideResult = ApiResult.Success(
                LiveTvGuideResponse(programs = listOf(LiveTvProgram(id = "p1", title = "News"))),
            ),
            programResult = ApiResult.Success(LiveTvProgram(id = "p1", title = "News")),
        )
        val repo = LiveTvRepository(api)
        val guide = assertIs<ApiResult.Success<LiveTvGuideResponse>>(repo.guide(listOf("1")))
        assertEquals("p1", guide.data.programs.single().id)
        val program = assertIs<ApiResult.Success<LiveTvProgram>>(repo.program("p1"))
        assertEquals("News", program.data.title)
    }
}

internal class FakeLiveTvApi(
    var channelsResult: ApiResult<LiveTvChannelsResponse> =
        ApiResult.Success(LiveTvChannelsResponse()),
    var guideResult: ApiResult<LiveTvGuideResponse> =
        ApiResult.Success(LiveTvGuideResponse()),
    var programResult: ApiResult<LiveTvProgram> =
        ApiResult.NetworkError(IllegalStateException("unused")),
    var startResult: ApiResult<LiveTvSessionStartResponse> =
        ApiResult.NetworkError(IllegalStateException("unused")),
    var releaseResult: ApiResult<LiveTvSession> =
        ApiResult.NetworkError(IllegalStateException("unused")),
) : LiveTvApi {
    var startCalls = 0
    val releaseCalls = mutableListOf<String>()

    override suspend fun channels(tunerId: String?): ApiResult<LiveTvChannelsResponse> = channelsResult

    override suspend fun guide(
        channelIds: List<String>,
        start: String?,
        end: String?,
    ): ApiResult<LiveTvGuideResponse> = guideResult

    override suspend fun program(programId: String): ApiResult<LiveTvProgram> = programResult

    override suspend fun startSession(channelId: String): ApiResult<LiveTvSessionStartResponse> {
        startCalls += 1
        return startResult
    }

    override suspend fun releaseSession(sessionId: String): ApiResult<LiveTvSession> {
        releaseCalls += sessionId
        return releaseResult
    }

    override suspend fun recordings(status: String?) =
        ApiResult.Success(org.prairieserver.prairie.model.livetv.LiveTvRecordingsResponse())

    override suspend fun scheduleRecording(
        request: org.prairieserver.prairie.model.livetv.LiveTvScheduleRecordingRequest,
    ) = ApiResult.NetworkError(IllegalStateException("unused"))

    override suspend fun cancelRecording(recordingId: String) =
        ApiResult.NetworkError(IllegalStateException("unused"))
}
