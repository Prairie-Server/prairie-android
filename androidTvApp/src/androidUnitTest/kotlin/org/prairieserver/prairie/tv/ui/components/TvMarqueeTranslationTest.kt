package org.prairieserver.prairie.tv.ui.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.prairieserver.prairie.network.api.MetadataAiApi
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.AuthScopeSnapshot
import org.prairieserver.prairie.model.metadata.MetadataAiStatus
import org.prairieserver.prairie.model.metadata.MetadataTranslationJob
import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.repository.MetadataAiRepository
import kotlin.test.assertEquals

import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.model.section.SectionItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvMarqueeTranslationTest {
    private val pendingCard = SectionItem(
        contentId = "movie-1",
        type = "movie",
        title = "Film",
        overview = "Original",
        pendingTranslationLanguage = "nl",
    )
    private val featured = ResolvedSection(
        id = "featured",
        sectionType = "featured",
        title = "Featured",
        featured = true,
        items = listOf(pendingCard),
    )
    private val ordinary = ResolvedSection(
        id = "recently_added",
        sectionType = "recently_added",
        title = "Recently Added",
        items = listOf(pendingCard),
    )
    private val sections = listOf(featured, ordinary)

    @Test
    fun aCardFocusedInAFeaturedSectionTranslatesOnView() {
        val content = TvMarqueeContent.from(pendingCard, featured.title, featured.id)

        assertTrue(isFeaturedMarqueeContent(content, sections))
    }

    @Test
    fun theSameCardFocusedInAnOrdinaryRowDoesNot() {
        val content = TvMarqueeContent.from(pendingCard, ordinary.title, ordinary.id)

        assertFalse(isFeaturedMarqueeContent(content, sections))
    }

    @Test
    fun noContentOrAnUnknownRowDoesNot() {
        assertFalse(isFeaturedMarqueeContent(null, sections))
        val content = TvMarqueeContent.from(pendingCard, "Gone", "gone")
        assertFalse(isFeaturedMarqueeContent(content, sections))
    }
    @Test
    fun retainedCardQueuesOnlyTheCurrentProfilesPendingLanguage() = runTest {
        val requested = mutableListOf<String>()
        var reads = 0
        var refreshed = false
        val api = object : MetadataAiApi {
            override suspend fun status() = ApiResult.Success(
                MetadataAiStatus())
            override suspend fun translateDescription(contentId: String, targetLanguage: String,
                scope: AuthScopeSnapshot?) : ApiResult<MetadataTranslationJob> {
                requested += targetLanguage
                return ApiResult.Success(
                    MetadataTranslationJob("job", "item", contentId, targetLanguage, "pending"))
            }
            override suspend fun refreshDetail(contentId: String, scope: AuthScopeSnapshot?): ApiResult<ItemDetail> {
                reads++
                return ApiResult.Success(ItemDetail(
                    contentId, "movie", title = "Film", overview = if (reads == 1) "Original" else "Localized",
                    pendingTranslationLanguage = if (reads == 1) "de" else null,
                    machineTranslatedFields = if (reads == 1) emptyList() else listOf("overview")))
            }
        }
        val state = TvMarqueeTranslationState(MetadataAiRepository(api), this)
        state.translateOnView(pendingCard) { refreshed = true }
        advanceUntilIdle()
        assertEquals(listOf("de"), requested)
        assertTrue(refreshed)
        val presented = state.presentation(pendingCard, state.phase.value, state.runningContentId.value)
        assertEquals("Localized", presented.synopsis)
        assertTrue(presented.machineTranslated)
        assertFalse(state.working.value)
    }

    @Test
    fun disposingDuringLanguagePreflightCancelsItsQueuedWork() = runTest {
        val response = CompletableDeferred<ItemDetail>()
        var posts = 0
        val api = object : MetadataAiApi {
            override suspend fun status() = ApiResult.Success(
                MetadataAiStatus())
            override suspend fun translateDescription(contentId: String, targetLanguage: String,
                scope: AuthScopeSnapshot?): ApiResult<MetadataTranslationJob> {
                posts++
                return ApiResult.Error(503, "unavailable", "Unavailable")
            }
            override suspend fun refreshDetail(contentId: String, scope: AuthScopeSnapshot?) =
                ApiResult.Success(response.await())
        }
        val state = TvMarqueeTranslationState(MetadataAiRepository(api), this)
        state.translateOnView(pendingCard) {}
        runCurrent()
        assertTrue(state.working.value)
        state.close()
        response.complete(ItemDetail("movie-1", "movie", title = "Film", pendingTranslationLanguage = "de"))
        runCurrent()
        assertEquals(0, posts)
        assertFalse(state.working.value)
    }

    @Test
    fun failedPreflightRetriesOnRefocusWithoutConsumingTheTranslationClaim() = runTest {
        var reads = 0
        var posts = 0
        val api = object : MetadataAiApi {
            override suspend fun status() = ApiResult.Success(MetadataAiStatus())
            override suspend fun translateDescription(contentId: String, targetLanguage: String,
                scope: AuthScopeSnapshot?): ApiResult<MetadataTranslationJob> {
                posts++
                return ApiResult.Success(MetadataTranslationJob("job", "item", contentId, targetLanguage, "pending"))
            }
            override suspend fun refreshDetail(contentId: String, scope: AuthScopeSnapshot?): ApiResult<ItemDetail> {
                if (++reads == 1) return ApiResult.NetworkError(IllegalStateException("Temporary failure"))
                return ApiResult.Success(ItemDetail("movie-1", "movie", title = "Film",
                    overview = if (reads == 2) "Original" else "Localized",
                    pendingTranslationLanguage = if (reads == 2) "nl" else null))
            }
        }
        val state = TvMarqueeTranslationState(MetadataAiRepository(api), this)
        state.onFocusChanged(pendingCard.contentId)
        state.translateOnView(pendingCard) {}
        advanceUntilIdle()
        state.translateOnView(pendingCard) {}
        advanceUntilIdle()
        assertEquals(1, reads, "resting focus must not loop on a failing preflight")
        assertEquals(0, posts)

        state.onFocusChanged("another-card")
        state.onFocusChanged(pendingCard.contentId)
        state.translateOnView(pendingCard) {}
        advanceUntilIdle()
        assertEquals(1, posts)
        assertEquals("Localized", state.presentation(pendingCard, state.phase.value, state.runningContentId.value).synopsis)
    }

}
