package com.continuum.app.tv.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.section.LibraryCollection
import com.continuum.app.model.section.LibraryCollectionsResponse
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.SectionRepository
import com.continuum.app.tv.ui.util.tvCatalogMediaTypeFor
import com.continuum.app.tv.ui.util.visibleOnTv
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Library content sections, in tvOS order (`TVLibraryPill`):
 * Recommended · Collections · Browse. [Browse] is the full A–Z poster grid
 * (tvOS calls the same surface "Browse"; the on-screen tab label matches).
 */
enum class TvLibraryTab(val label: String) {
    Recommended("Recommended"),
    Collections("Collections"),
    Browse("Browse"),
}

/**
 * One named or anonymous collections section as it should appear on screen,
 * in the order computed from `sort_order`. Mirrors tvOS
 * `LibraryCollectionSection` / the phone client's grouped collections. An
 * empty [name] renders without a group header (the ungrouped / flat bucket).
 */
data class TvCollectionSection(
    val name: String,
    val collections: List<LibraryCollection>,
)

enum class TvLibrarySortOption(val label: String, val wireValue: String) {
    Title("Title", "title"),
    DateAdded("Date Added", "added_at"),
    // Server expects "year" for release-date sort (matches phone); the old
    // "release_date" value was unsupported.
    ReleaseDate("Release Year", "year"),
    Rating("Rating", "rating_imdb");

    companion object {
        fun fromWire(value: String): TvLibrarySortOption =
            entries.firstOrNull { it.wireValue == value } ?: DateAdded
    }
}

data class TvLibraryBrowseFilter(
    val genre: String? = null,
    val namePrefix: String? = null,
    // Default to newest-first (added_at), matching the global browse + the
    // shared CatalogRepository's default; TV previously diverged with Title.
    val sort: String = TvLibrarySortOption.DateAdded.wireValue,
    val yearMin: Int? = null,
    val yearMax: Int? = null,
)

class TvLibraryDetailViewModel(
    private val sectionRepository: SectionRepository,
    private val catalogRepository: CatalogRepository,
    private val libraryId: Int,
    private val libraryTitle: String,
    private val libraryType: String,
) : ViewModel() {

    data class UiState(
        val title: String,
        val libraryType: String,
        val selectedTab: TvLibraryTab = TvLibraryTab.Recommended,
        val sections: List<ResolvedSection> = emptyList(),
        val recommendedLoading: Boolean = true,
        val recommendedError: String? = null,
        val genres: List<String> = emptyList(),
        val filtersLoading: Boolean = true,
        val browseItems: List<BrowseItem> = emptyList(),
        val browseHasMore: Boolean = false,
        val browseLoading: Boolean = false,
        val browseLoadingMore: Boolean = false,
        val browseError: String? = null,
        val browseFilter: TvLibraryBrowseFilter = TvLibraryBrowseFilter(),
        val collections: List<LibraryCollection> = emptyList(),
        val collectionSections: List<TvCollectionSection> = emptyList(),
        val collectionsLoading: Boolean = false,
        val collectionsError: String? = null,
    )

    private val _uiState = MutableStateFlow(
        UiState(
            title = libraryTitle,
            libraryType = libraryType,
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val pageSize = 100
    private var loadedRecommended = false
    private var loadedBrowse = false
    private var loadedCollections = false
    private var browseGeneration = 0
    private var browseSnapshot: String? = null

    init {
        loadRecommended()
        loadFilters()
    }

    fun onTabSelected(tab: TvLibraryTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        when (tab) {
            TvLibraryTab.Recommended -> if (!loadedRecommended) loadRecommended()
            TvLibraryTab.Browse -> if (!loadedBrowse) loadBrowse(reset = true)
            TvLibraryTab.Collections -> if (!loadedCollections) loadCollections()
        }
    }

    fun onGenreChanged(genre: String?) {
        updateBrowseFilter(
            _uiState.value.browseFilter.copy(
                genre = genre,
                namePrefix = null,
            ),
        )
    }

    fun onSortChanged(sort: TvLibrarySortOption) {
        updateBrowseFilter(
            _uiState.value.browseFilter.copy(
                sort = sort.wireValue,
                namePrefix = null,
            ),
        )
    }

    fun onNamePrefixChanged(prefix: String?) {
        updateBrowseFilter(
            _uiState.value.browseFilter.copy(namePrefix = prefix),
        )
    }

    fun onYearRangeChanged(yearMin: Int?, yearMax: Int?) {
        updateBrowseFilter(
            _uiState.value.browseFilter.copy(
                yearMin = yearMin,
                yearMax = yearMax,
                // Match the existing pattern in onGenreChanged/onSortChanged:
                // changing a high-level filter dimension resets the alphabet jump.
                namePrefix = null,
            ),
        )
    }

    fun loadMoreBrowse() {
        val state = _uiState.value
        if (state.browseLoading || state.browseLoadingMore || !state.browseHasMore) return
        loadBrowse(reset = false)
    }

    fun retryRecommended() {
        loadRecommended()
    }

    fun retryBrowse() {
        loadBrowse(reset = true)
    }

    fun retryCollections() {
        loadCollections()
    }

    private fun updateBrowseFilter(filter: TvLibraryBrowseFilter) {
        if (_uiState.value.browseFilter == filter) return
        _uiState.update { it.copy(browseFilter = filter) }
        if (_uiState.value.selectedTab == TvLibraryTab.Browse || loadedBrowse) {
            loadBrowse(reset = true)
        }
    }

    private fun loadRecommended() {
        loadedRecommended = true
        viewModelScope.launch {
            _uiState.update { it.copy(recommendedLoading = true, recommendedError = null) }

            val layout = when (val layoutResult = sectionRepository.getLibrarySections(libraryId)) {
                is ApiResult.Success -> layoutResult.data
                is ApiResult.Error -> {
                    loadedRecommended = false
                    _uiState.update {
                        it.copy(
                            recommendedLoading = false,
                            recommendedError = layoutResult.message.ifBlank { "Failed to load sections" },
                        )
                    }
                    return@launch
                }
                is ApiResult.NetworkError -> {
                    loadedRecommended = false
                    _uiState.update {
                        it.copy(
                            recommendedLoading = false,
                            recommendedError = "Network error: ${layoutResult.exception.message ?: "unknown"}",
                        )
                    }
                    return@launch
                }
            }

            val resolved = layout.sections.map { section ->
                async {
                    when (val result = sectionRepository.getLibrarySectionItems(libraryId, section.id)) {
                        is ApiResult.Success -> result.data.section ?: section
                        else -> section
                    }
                }
            }.awaitAll()

            _uiState.update {
                it.copy(
                    sections = resolved.visibleOnTv(),
                    recommendedLoading = false,
                    recommendedError = null,
                )
            }
        }
    }

    private fun loadFilters() {
        viewModelScope.launch {
            _uiState.update { it.copy(filtersLoading = true) }
            when (val filters = catalogRepository.getFilters(libraryId)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        genres = filters.data.genres.sorted(),
                        filtersLoading = false,
                    )
                }
                else -> _uiState.update { it.copy(filtersLoading = false) }
            }
        }
    }

    private fun loadBrowse(reset: Boolean) {
        loadedBrowse = true

        val generation = if (reset) {
            browseGeneration += 1
            browseGeneration
        } else {
            browseGeneration
        }

        if (reset) {
            browseSnapshot = null
        }

        viewModelScope.launch {
            val state = _uiState.value
            val offset = if (reset) 0 else state.browseItems.size
            val filter = state.browseFilter

            _uiState.update {
                if (reset) {
                    it.copy(
                        browseItems = emptyList(),
                        browseHasMore = false,
                        browseLoading = true,
                        browseLoadingMore = false,
                        browseError = null,
                    )
                } else {
                    it.copy(browseLoadingMore = true)
                }
            }

            val result = catalogRepository.browse(
                source = "query",
                mediaType = mediaTypeFor(libraryType),
                libraryId = libraryId,
                genre = filter.genre,
                sort = filter.sort,
                offset = offset,
                limit = pageSize,
                namePrefix = filter.namePrefix,
                yearMin = filter.yearMin,
                yearMax = filter.yearMax,
                snapshotAt = browseSnapshot,
            )

            if (generation != browseGeneration) return@launch

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    if (browseSnapshot == null) {
                        browseSnapshot = response.snapshot
                    }
                    _uiState.update {
                        val visibleItems = response.items.visibleOnTv()
                        it.copy(
                            browseItems = if (reset) visibleItems else it.browseItems + visibleItems,
                            browseHasMore = response.hasMore,
                            browseLoading = false,
                            browseLoadingMore = false,
                            browseError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    loadedBrowse = false
                    _uiState.update {
                        it.copy(
                            browseLoading = false,
                            browseLoadingMore = false,
                            browseError = result.message.ifBlank { "Failed to load library" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    loadedBrowse = false
                    _uiState.update {
                        it.copy(
                            browseLoading = false,
                            browseLoadingMore = false,
                            browseError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    private fun loadCollections() {
        loadedCollections = true
        viewModelScope.launch {
            _uiState.update { it.copy(collectionsLoading = true, collectionsError = null) }
            when (val result = sectionRepository.getLibraryCollectionsGrouped(libraryId)) {
                is ApiResult.Success -> {
                    val sections = buildCollectionSections(result.data)
                    _uiState.update {
                        it.copy(
                            collections = sections.flatMap { section -> section.collections },
                            collectionSections = sections,
                            collectionsLoading = false,
                            collectionsError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    loadedCollections = false
                    _uiState.update {
                        it.copy(
                            collectionsLoading = false,
                            collectionsError = result.message.ifBlank { "Failed to load collections" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    loadedCollections = false
                    _uiState.update {
                        it.copy(
                            collectionsLoading = false,
                            collectionsError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    /**
     * Builds the ordered render list: each non-empty group becomes a section in
     * `sort_order` order, and the ungrouped bucket is woven in at its own
     * `sort_order` slot. When the response is flat (no groups), a single
     * anonymous section is produced. Mirrors the phone client's `buildSections`.
     */
    private fun buildCollectionSections(
        response: LibraryCollectionsResponse,
    ): List<TvCollectionSection> {
        val groups = response.groups
        val ungrouped = response.ungrouped

        if (groups.isEmpty() && ungrouped == null) {
            return if (response.collections.isEmpty()) {
                emptyList()
            } else {
                listOf(TvCollectionSection(name = "", collections = response.collections))
            }
        }

        data class Slot(val order: Int, val section: TvCollectionSection)
        val slots = mutableListOf<Slot>()
        for (group in groups) {
            if (group.collections.isEmpty()) continue
            slots += Slot(
                order = group.sortOrder,
                section = TvCollectionSection(name = group.name, collections = group.collections),
            )
        }
        if (ungrouped != null && ungrouped.collections.isNotEmpty()) {
            slots += Slot(
                order = ungrouped.sortOrder,
                section = TvCollectionSection(name = "", collections = ungrouped.collections),
            )
        }
        return slots.sortedBy { it.order }.map { it.section }
    }

    private fun mediaTypeFor(type: String): String? = tvCatalogMediaTypeFor(type)
}
