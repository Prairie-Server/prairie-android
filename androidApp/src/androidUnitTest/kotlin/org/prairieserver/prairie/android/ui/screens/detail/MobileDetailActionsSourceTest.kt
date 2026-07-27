package org.prairieserver.prairie.android.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.common.downloads.DownloadEnqueuer
import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.model.catalog.LeafItemUserData
import org.prairieserver.prairie.model.download.DownloadsListResponse
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.CatalogApi
import org.prairieserver.prairie.network.api.DownloadsApi
import org.prairieserver.prairie.network.api.EbookReaderApi
import org.prairieserver.prairie.network.api.PersonalDataApi
import org.prairieserver.prairie.repository.CatalogRepository
import org.prairieserver.prairie.repository.DownloadsRepository
import org.prairieserver.prairie.repository.EbookReaderRepository
import org.prairieserver.prairie.repository.PersonalDataRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MobileDetailActionsSourceTest {
    private val movieDetail = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/MovieDetailContent.kt",
    ).readText()
    private val seriesDetail = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/SeriesDetailContent.kt",
    ).readText()
    private val itemDetail = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/ItemDetailScreen.kt",
    ).readText()
    private val viewModel = File(
        "src/androidMain/kotlin/org/prairieserver/prairie/android/ui/screens/detail/ItemDetailViewModel.kt",
    ).readText()

    @Test
    fun watchedHeroActionCallsRepositoryInsteadOfNoOp() {
        assertFalse(movieDetail.contains("onToggleWatched = { /* no-op"))
        assertFalse(seriesDetail.contains("onToggleWatched = { /* no-op"))
        assertTrue(itemDetail.contains("onToggleWatched = { viewModel.toggleWatched() }"))
        assertTrue(viewModel.contains("fun toggleWatched()"))
        assertTrue(viewModel.contains("personalDataRepository.setWatched(contentId, target)"))
        assertTrue(viewModel.contains("watchedMutationGeneration"))
        assertTrue(viewModel.contains("if (generation == watchedMutationGeneration)"))
    }

    @Test
    fun watchedTogglePersistsOptimisticStateOnSuccess() = runItemDetailTest {
        val repository = RecordingPersonalDataRepository(
            mutableListOf({ ApiResult.Success(Unit) }),
        )
        val viewModel = itemDetailViewModel(repository)
        viewModel.seedDetail(played = false)

        viewModel.toggleWatched()
        advanceUntilIdle()

        assertEquals(listOf(true), repository.watchedCalls)
        assertEquals(true, viewModel.uiState.value.detail?.userData?.played)
    }

    @Test
    fun watchedToggleRevertsOnLatestFailureOnly() = runItemDetailTest {
        val repository = RecordingPersonalDataRepository(
            mutableListOf(
                {
                    delay(100)
                    ApiResult.Error(500, "failed", "first failed")
                },
                { ApiResult.Error(500, "failed", "second failed") },
            ),
        )
        val viewModel = itemDetailViewModel(repository)
        viewModel.seedDetail(played = false)

        viewModel.toggleWatched()
        viewModel.toggleWatched()
        advanceUntilIdle()

        assertEquals(listOf(true, false), repository.watchedCalls)
        assertEquals(
            true,
            viewModel.uiState.value.detail?.userData?.played,
            "The second failed toggle should roll back to the state it observed; the older first failure must not overwrite it.",
        )
    }

    @Test
    fun moviePlayPinsDisplayedVersionWhenTrackOverrideIsSelected() {
        assertTrue(itemDetail.contains("val playbackFileId = explicitFileId ?: detail.versions"))
        assertTrue(itemDetail.contains(".getOrNull(effectiveSelectedVersionIndex)"))
        assertTrue(itemDetail.contains("?.takeIf { state.hasExplicitAudioSelection || state.hasExplicitSubtitleSelection }"))
        assertTrue(itemDetail.contains("playbackFileId,"))
        assertTrue(itemDetail.contains("explicitAudioIndex,"))
        assertTrue(itemDetail.contains("explicitSubtitleIndex,"))
    }

    // Cancel viewModelScope coroutines BEFORE resetting Main: they dispatch
    // on Dispatchers.Main, and one still alive when resetMain/setMain runs
    // throws IllegalStateException from TestMainDispatcher — the CI flake
    // that failed watchedToggleRevertsOnLatestFailureOnly.
    private val createdViewModels = mutableListOf<androidx.lifecycle.ViewModel>()

    private fun runItemDetailTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            createdViewModels.forEach { it.viewModelScope.cancel() }
            createdViewModels.clear()
            Dispatchers.resetMain()
        }
    }

    private fun itemDetailViewModel(
        personalDataRepository: RecordingPersonalDataRepository,
    ): ItemDetailViewModel =
        ItemDetailViewModel(
            catalogRepository = CatalogRepository(CatalogApi(dummyHttpClient())),
            personalDataRepository = personalDataRepository,
            downloadsRepository = DownloadsRepository(EmptyDownloadsApi()),
            downloadEnqueuer = unsafeInstance(),
            ebookReaderRepository = EbookReaderRepository(EbookReaderApi(dummyHttpClient())),
            metadataAiRepository = org.prairieserver.prairie.repository.MetadataAiRepository(
                org.prairieserver.prairie.network.api.DefaultMetadataAiApi(dummyHttpClient()),
            ),
            savedStateHandle = SavedStateHandle(),
        ).also { createdViewModels += it }

    @Suppress("UNCHECKED_CAST")
    private fun ItemDetailViewModel.seedDetail(played: Boolean) {
        val field = ItemDetailViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        val flow = field.get(this) as MutableStateFlow<ItemDetailUiState>
        flow.value = ItemDetailUiState(
            isLoading = false,
            detail = ItemDetail(
                contentId = "movie-1",
                type = "movie",
                title = "Movie",
                userData = LeafItemUserData(played = played),
            ),
        )
    }

    private class RecordingPersonalDataRepository(
        private val responses: MutableList<suspend () -> ApiResult<Unit>>,
    ) : PersonalDataRepository(PersonalDataApi(dummyHttpClient())) {
        val watchedCalls = mutableListOf<Boolean>()

        override suspend fun setWatched(itemId: String, watched: Boolean): ApiResult<Unit> {
            watchedCalls += watched
            return responses.removeFirstOrNull()?.invoke() ?: ApiResult.Success(Unit)
        }
    }

    private class EmptyDownloadsApi : DownloadsApi(dummyHttpClient()) {
        override suspend fun list(): ApiResult<DownloadsListResponse> =
            ApiResult.Success(DownloadsListResponse())
    }

    private inline fun <reified T : Any> unsafeInstance(): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
    }

    companion object {
        private fun dummyHttpClient(): HttpClient =
            HttpClient(MockEngine { respond("{}") })
    }
}
