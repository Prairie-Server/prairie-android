package com.continuum.app.domain

import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.model.section.SectionItem
import com.continuum.app.network.ApiResult
import com.continuum.app.network.getOrNull
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.repository.SectionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Aggregated home screen data combining server sections with user-specific lists.
 */
data class HomeData(
    /** All resolved home sections with their items pre-loaded. */
    val sections: List<ResolvedSection>,
    /** Items the user has recently watched (for "continue watching" row). */
    val continueWatching: List<BrowseItem>,
    /** Recently added items (extracted from the matching section, if present). */
    val recentlyAdded: List<SectionItem>,
)

/**
 * Combines section layout data with user personal data to build the home screen.
 *
 * Fetches home sections and watch history concurrently, then assembles
 * a [HomeData] object for the UI.
 */
class GetHomeDataUseCase(
    private val sectionRepo: SectionRepository,
    private val personalDataRepo: PersonalDataRepository,
) {
    /**
     * Fetches all data needed to render the home screen.
     *
     * Returns [HomeData] containing resolved sections (with items), a "continue watching"
     * list derived from the user's watch history, and a "recently added" list from the
     * first section of that type.
     */
    suspend fun getHomeData(): ApiResult<HomeData> = coroutineScope {
        val sectionsDeferred = async { sectionRepo.getHomeSections() }
        val historyDeferred = async { personalDataRepo.listHistory(offset = 0, limit = 20) }

        val sectionsResult = sectionsDeferred.await()

        // If sections fail, the whole operation fails
        when (sectionsResult) {
            is ApiResult.Error -> return@coroutineScope sectionsResult
            is ApiResult.NetworkError -> return@coroutineScope sectionsResult
            is ApiResult.Success -> { /* continue */ }
        }

        val sections = sectionsResult.getOrNull()?.sections.orEmpty()
        val historyResult = historyDeferred.await()

        // Continue watching is sourced from the user's watch history
        val continueWatching = historyResult.getOrNull()?.items.orEmpty()

        // Find the "recently added" section if present
        val recentlyAdded = sections
            .firstOrNull { it.sectionType == "recently_added" }
            ?.items
            .orEmpty()

        ApiResult.Success(
            HomeData(
                sections = sections,
                continueWatching = continueWatching,
                recentlyAdded = recentlyAdded,
            ),
        )
    }
}
