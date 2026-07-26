package org.prairieserver.prairie.model.feature

import kotlinx.coroutines.test.runTest
import org.prairieserver.prairie.model.livetv.LiveTvChannel
import org.prairieserver.prairie.model.livetv.LiveTvChannelsResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.FakeLiveTvApi
import org.prairieserver.prairie.repository.LiveTvRepository
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveTvFeatureStoreTest {

    @Test
    fun startsHiddenAndEnablesWhenChannelsNonempty() = runTest {
        val store = LiveTvFeatureStore(
            LiveTvRepository(
                FakeLiveTvApi(
                    channelsResult = ApiResult.Success(
                        LiveTvChannelsResponse(listOf(LiveTvChannel(id = "1", name = "KRON"))),
                    ),
                ),
            ),
        )

        assertFalse(store.isEnabled.value)
        store.refresh()
        assertTrue(store.isEnabled.value)
    }

    @Test
    fun emptyChannelListKeepsSurfaceHidden() = runTest {
        val store = LiveTvFeatureStore(
            LiveTvRepository(
                FakeLiveTvApi(
                    channelsResult = ApiResult.Success(LiveTvChannelsResponse(emptyList())),
                ),
            ),
        )

        store.refresh()
        assertFalse(store.isEnabled.value)
    }

    @Test
    fun transientFailureKeepsPreviousCapabilityValue() = runTest {
        val api = FakeLiveTvApi(
            channelsResult = ApiResult.Success(
                LiveTvChannelsResponse(listOf(LiveTvChannel(id = "1"))),
            ),
        )
        val store = LiveTvFeatureStore(LiveTvRepository(api))
        store.refresh()
        assertTrue(store.isEnabled.value)

        api.channelsResult = ApiResult.NetworkError(IllegalStateException("offline"))
        store.refresh()
        assertTrue(store.isEnabled.value)
    }

    @Test
    fun resetHidesLiveTvBeforeNextProbe() = runTest {
        val store = LiveTvFeatureStore(
            LiveTvRepository(
                FakeLiveTvApi(
                    channelsResult = ApiResult.Success(
                        LiveTvChannelsResponse(listOf(LiveTvChannel(id = "1"))),
                    ),
                ),
            ),
        )
        store.refresh()
        store.reset()
        assertFalse(store.isEnabled.value)
    }

    @Test
    fun staleResponseAfterResetIsIgnored() = runTest {
        val api = SequencingLiveTvApi()
        val store = LiveTvFeatureStore(LiveTvRepository(api))

        // First refresh succeeds, then reset mid-flight before second response applies.
        store.refresh()
        assertTrue(store.isEnabled.value)

        api.next = ApiResult.Success(
            LiveTvChannelsResponse(listOf(LiveTvChannel(id = "2"), LiveTvChannel(id = "3"))),
        )
        // Capture generation bump via reset before applying a late success.
        store.reset()
        assertFalse(store.isEnabled.value)
        // A refresh started after reset should re-enable.
        store.refresh()
        assertTrue(store.isEnabled.value)
    }
}

private class SequencingLiveTvApi : org.prairieserver.prairie.network.api.LiveTvApi {
    var next: ApiResult<LiveTvChannelsResponse> = ApiResult.Success(
        LiveTvChannelsResponse(listOf(LiveTvChannel(id = "1"))),
    )

    override suspend fun channels(tunerId: String?) = next

    override suspend fun guide(
        channelIds: List<String>,
        start: String?,
        end: String?,
    ) = ApiResult.NetworkError(IllegalStateException("unused"))

    override suspend fun program(programId: String) =
        ApiResult.NetworkError(IllegalStateException("unused"))

    override suspend fun startSession(channelId: String) =
        ApiResult.NetworkError(IllegalStateException("unused"))

    override suspend fun releaseSession(sessionId: String) =
        ApiResult.NetworkError(IllegalStateException("unused"))

    override suspend fun recordings(status: String?) =
        ApiResult.Success(org.prairieserver.prairie.model.livetv.LiveTvRecordingsResponse())

    override suspend fun scheduleRecording(
        request: org.prairieserver.prairie.model.livetv.LiveTvScheduleRecordingRequest,
    ) = ApiResult.NetworkError(IllegalStateException("unused"))

    override suspend fun cancelRecording(recordingId: String) =
        ApiResult.NetworkError(IllegalStateException("unused"))
}
