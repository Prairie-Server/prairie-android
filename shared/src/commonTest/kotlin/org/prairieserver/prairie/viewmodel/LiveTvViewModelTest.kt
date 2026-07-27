package org.prairieserver.prairie.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.prairieserver.prairie.model.livetv.LiveTvChannel
import org.prairieserver.prairie.model.livetv.LiveTvChannelsResponse
import org.prairieserver.prairie.model.livetv.LiveTvGuideResponse
import org.prairieserver.prairie.model.livetv.LiveTvProgram
import org.prairieserver.prairie.model.livetv.LiveTvSession
import org.prairieserver.prairie.model.livetv.LiveTvSessionStartResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.FakeLiveTvApi
import org.prairieserver.prairie.repository.LiveTvRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    // Cancel viewModelScope coroutines BEFORE resetting Main: a coroutine
    // still parked on Dispatchers.Main when a later test calls setMain/resetMain
    // throws IllegalStateException from TestMainDispatcher.
    private val createdViewModels = mutableListOf<androidx.lifecycle.ViewModel>()

    private fun <T : androidx.lifecycle.ViewModel> track(viewModel: T): T {
        createdViewModels += viewModel
        return viewModel
    }


    @Test
    fun loadsEnabledChannelsAndNowPlaying() = runTest {
        val api = FakeLiveTvApi(
            channelsResult = ApiResult.Success(
                LiveTvChannelsResponse(
                    listOf(
                        LiveTvChannel(id = "1", number = "4.1", name = "KRON", enabled = true),
                        LiveTvChannel(id = "2", number = "5.1", name = "KPIX", enabled = false),
                    ),
                ),
            ),
            guideResult = ApiResult.Success(
                LiveTvGuideResponse(
                    programs = listOf(
                        LiveTvProgram(
                            id = "p1",
                            channelId = "1",
                            start = "2026-07-25T17:00:00Z",
                            stop = "2026-07-25T19:00:00Z",
                            title = "Evening News",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = track(LiveTvViewModel(
            repository = LiveTvRepository(api),
            nowMillisProvider = { parseFixedNow() },
        ))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("1"), viewModel.uiState.value.channels.map { it.channel.id })
        assertEquals("Evening News", viewModel.uiState.value.channels.single().nowPlaying?.title)
    }

    @Test
    fun loadErrorSurfacesMessage() = runTest {
        val viewModel = track(LiveTvViewModel(
            LiveTvRepository(
                FakeLiveTvApi(
                    channelsResult = ApiResult.Error(500, "internal", "boom"),
                ),
            ),
        ))
        advanceUntilIdle()
        assertEquals("boom", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.channels.isEmpty())
    }

    @Test
    fun playerStartsSessionAndStopReleasesIt() = runTest {
        val api = FakeLiveTvApi(
            startResult = ApiResult.Success(
                LiveTvSessionStartResponse(
                    sessionId = "s1",
                    hlsUrl = "/api/v1/livetv/live-hls/s1/index.m3u8",
                    transport = "hls",
                ),
            ),
            releaseResult = ApiResult.Success(LiveTvSession(id = "s1", status = "released")),
        )
        val viewModel = track(LiveTvPlayerViewModel(LiveTvRepository(api)))

        viewModel.start("ch-1", "KRON")
        advanceUntilIdle()

        assertEquals("s1", viewModel.uiState.value.session?.sessionId)
        assertEquals("/api/v1/livetv/live-hls/s1/index.m3u8", viewModel.uiState.value.session?.playableUrl)
        assertTrue(viewModel.uiState.value.session?.isHls == true)
        assertNull(viewModel.uiState.value.error)

        viewModel.stop()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.session)
        assertEquals(listOf("s1"), api.releaseCalls)
    }

    @Test
    fun playerRejectsBlankStreamUrlAndReleasesSession() = runTest {
        val api = FakeLiveTvApi(
            startResult = ApiResult.Success(
                LiveTvSessionStartResponse(sessionId = "s1", hlsUrl = "", streamUrl = ""),
            ),
            releaseResult = ApiResult.Success(LiveTvSession(id = "s1", status = "released")),
        )
        val viewModel = track(LiveTvPlayerViewModel(LiveTvRepository(api)))
        viewModel.start("ch-1")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.session)
        assertNotNull(viewModel.uiState.value.error)
        assertEquals(listOf("s1"), api.releaseCalls)
    }

    @Test
    fun playerStartFailureSurfacesError() = runTest {
        val viewModel = track(LiveTvPlayerViewModel(
            LiveTvRepository(
                FakeLiveTvApi(
                    startResult = ApiResult.Error(409, "no_tuner", "No tuner available"),
                ),
            ),
        ))
        viewModel.start("ch-1")
        advanceUntilIdle()
        assertEquals("No tuner available", viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.session)
    }

    @Test
    fun playerIgnoresBlankChannelId() = runTest {
        val api = FakeLiveTvApi()
        val viewModel = track(LiveTvPlayerViewModel(LiveTvRepository(api)))
        viewModel.start("")
        advanceUntilIdle()
        assertEquals(0, api.startCalls)
        assertNotNull(viewModel.uiState.value.error)
    }

    private fun parseFixedNow(): Long {
        // Midway through the Evening News window above.
        return org.prairieserver.prairie.util.parseRfc3339ToEpochMillis("2026-07-25T18:00:00Z")!!
    }
}
