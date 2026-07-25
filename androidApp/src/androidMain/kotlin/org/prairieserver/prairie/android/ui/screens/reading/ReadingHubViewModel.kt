package org.prairieserver.prairie.android.ui.screens.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.android.ui.screens.browse.CatalogViewDensity
import org.prairieserver.prairie.android.ui.screens.browse.normalizeCatalogNamePrefix
import org.prairieserver.prairie.android.ui.screens.libraries.LibrariesSubtab
import org.prairieserver.prairie.android.ui.screens.libraries.LibraryBrowseSort
import org.prairieserver.prairie.model.catalog.BrowseItem
import org.prairieserver.prairie.model.navigation.ReadingFormatFilter
import org.prairieserver.prairie.model.navigation.availableReadingFormatFilters
import org.prairieserver.prairie.model.navigation.filterByReadingFormat
import org.prairieserver.prairie.model.navigation.matchesReadingFormat
import org.prairieserver.prairie.model.navigation.readingLibraries
import org.prairieserver.prairie.model.personal.UserLibrary
import org.prairieserver.prairie.model.section.LibraryCollection
import org.prairieserver.prairie.model.section.ResolvedSection
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.repository.CatalogRepository
import org.prairieserver.prairie.repository.PersonalDataRepository
import org.prairieserver.prairie.repository.SectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadingHubUiState(
    val isLoadingLibraries: Boolean = true,
    val libraries: List<UserLibrary> = emptyList(),
    val selectedFormat: ReadingFormatFilter = ReadingFormatFilter.All,
    val availableFormats: List<ReadingFormatFilter> = emptyList(),
    val filteredLibraries: List<UserLibrary> = emptyList(),
    val selectedLibraryId: Int? = null,
    val selectedTab: LibrariesSubtab = LibrariesSubtab.Recommended,
    val isLoadingSections: Boolean = false,
    val sections: List<ResolvedSection> = emptyList(),
    val sectionsError: String? = null,
    val isLoadingCatalog: Boolean = false,
    val isLoadingMoreCatalog: Boolean = false,
    val catalogItems: List<BrowseItem> = emptyList(),
    val catalogTotal: Int = 0,
    val catalogHasMore: Boolean = false,
    val browseGenres: List<String> = emptyList(),
    val selectedBrowseGenre: String? = null,
    val selectedNamePrefix: String? = null,
    val catalogDensity: CatalogViewDensity = CatalogViewDensity.Normal,
    val browseSort: LibraryBrowseSort = LibraryBrowseSort.RecentlyAdded,
    val catalogError: String? = null,
    val isLoadingCollections: Boolean = false,
    val collections: List<LibraryCollection> = emptyList(),
    val collectionsError: String? = null,
    val librariesError: String? = null,
)

class ReadingHubViewModel(
    private val personalDataRepository: PersonalDataRepository,
    private val sectionRepository: SectionRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReadingHubUiState())
    val uiState: StateFlow<ReadingHubUiState> = _uiState.asStateFlow()

    private var recommendedLoadedLibraryId: Int? = null
    private var browseLoadedLibraryId: Int? = null
    private var collectionsLoadedLibraryId: Int? = null
    private val pageSize = 42

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingLibraries = true,
                    librariesError = null,
                )
            }

            when (val result = personalDataRepository.listUserLibraries()) {
                is ApiResult.Success -> {
                    val libraries = result.data
                        .readingLibraries()
                        .sortedBy { library -> library.sortOrder }
                    val availableFormats = libraries.availableReadingFormatFilters()
                    val selectedFormat = _uiState.value.selectedFormat
                        .takeIf { it in availableFormats }
                        ?: availableFormats.firstOrNull()
                        ?: ReadingFormatFilter.All
                    val filteredLibraries = libraries.filterByReadingFormat(selectedFormat)
                    val selectedLibraryId = _uiState.value.selectedLibraryId
                        ?.takeIf { currentId -> filteredLibraries.any { it.id == currentId } }
                        ?: filteredLibraries.firstOrNull()?.id

                    _uiState.update {
                        val base = if (selectedLibraryId == null) it.clearingLibraryContent() else it
                        base.copy(
                            isLoadingLibraries = false,
                            libraries = libraries,
                            selectedFormat = selectedFormat,
                            availableFormats = availableFormats,
                            filteredLibraries = filteredLibraries,
                            selectedLibraryId = selectedLibraryId,
                            librariesError = null,
                        )
                    }

                    if (selectedLibraryId != null) {
                        loadCurrentTab(selectedLibraryId, force = true)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.clearingLibraryContent().copy(
                            isLoadingLibraries = false,
                            libraries = emptyList(),
                            availableFormats = emptyList(),
                            filteredLibraries = emptyList(),
                            selectedLibraryId = null,
                            librariesError = result.message.ifBlank { "Failed to load reading libraries" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.clearingLibraryContent().copy(
                            isLoadingLibraries = false,
                            libraries = emptyList(),
                            availableFormats = emptyList(),
                            filteredLibraries = emptyList(),
                            selectedLibraryId = null,
                            librariesError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    fun selectFormat(format: ReadingFormatFilter) {
        val state = _uiState.value
        if (format == state.selectedFormat || format !in state.availableFormats) return

        val filteredLibraries = state.libraries.filterByReadingFormat(format)
        val selectedLibraryId = state.selectedLibraryId
            ?.takeIf { currentId -> filteredLibraries.any { it.id == currentId } }
            ?: filteredLibraries.firstOrNull()?.id
        val libraryChanged = selectedLibraryId != state.selectedLibraryId

        if (libraryChanged) {
            recommendedLoadedLibraryId = null
            browseLoadedLibraryId = null
            collectionsLoadedLibraryId = null
        }

        _uiState.update {
            val base = if (libraryChanged) it.clearingLibraryContent() else it
            base.copy(
                selectedFormat = format,
                filteredLibraries = filteredLibraries,
                selectedLibraryId = selectedLibraryId,
            )
        }

        selectedLibraryId?.let { loadCurrentTab(it, force = libraryChanged) }
    }

    fun selectLibrary(libraryId: Int) {
        if (_uiState.value.filteredLibraries.none { it.id == libraryId }) return
        if (_uiState.value.selectedLibraryId == libraryId) return
        recommendedLoadedLibraryId = null
        browseLoadedLibraryId = null
        collectionsLoadedLibraryId = null
        _uiState.update {
            it.clearingLibraryContent().copy(selectedLibraryId = libraryId)
        }
        loadCurrentTab(libraryId, force = true)
    }

    fun selectTab(tab: LibrariesSubtab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        _uiState.value.selectedLibraryId?.let { loadCurrentTab(it, force = false) }
    }

    fun selectBrowseGenre(genre: String?) {
        _uiState.update {
            it.copy(
                selectedBrowseGenre = genre,
                catalogItems = emptyList(),
                catalogTotal = 0,
                catalogHasMore = false,
            )
        }
        _uiState.value.selectedLibraryId?.let { loadCatalog(it, reset = true, force = true) }
    }

    fun selectBrowseSort(sort: LibraryBrowseSort) {
        _uiState.update {
            it.copy(
                browseSort = sort,
                catalogItems = emptyList(),
                catalogTotal = 0,
                catalogHasMore = false,
            )
        }
        _uiState.value.selectedLibraryId?.let { loadCatalog(it, reset = true, force = true) }
    }

    fun selectNamePrefix(prefix: String?) {
        val normalizedPrefix = normalizeCatalogNamePrefix(prefix)
        if (_uiState.value.selectedNamePrefix == normalizedPrefix) return
        _uiState.update {
            it.copy(
                selectedNamePrefix = normalizedPrefix,
                catalogItems = emptyList(),
                catalogTotal = 0,
                catalogHasMore = false,
            )
        }
        _uiState.value.selectedLibraryId?.let { loadCatalog(it, reset = true, force = true) }
    }

    fun selectViewDensity(density: CatalogViewDensity) {
        if (_uiState.value.catalogDensity == density) return
        _uiState.update { it.copy(catalogDensity = density) }
    }

    fun loadMoreCatalog() {
        val state = _uiState.value
        val libraryId = state.selectedLibraryId ?: return
        if (state.isLoadingCatalog || state.isLoadingMoreCatalog || !state.catalogHasMore) return
        loadCatalog(libraryId, reset = false, force = true)
    }

    fun showBrowseFromRecommended() {
        _uiState.update { it.copy(selectedTab = LibrariesSubtab.Browse) }
        _uiState.value.selectedLibraryId?.let { loadCatalog(it, reset = true, force = false) }
    }

    fun retryCurrentTab() {
        val libraryId = _uiState.value.selectedLibraryId
        if (libraryId == null) {
            refresh()
        } else {
            loadCurrentTab(libraryId, force = true)
        }
    }

    private fun loadCurrentTab(libraryId: Int, force: Boolean) {
        when (_uiState.value.selectedTab) {
            LibrariesSubtab.Recommended -> loadRecommended(libraryId, force)
            LibrariesSubtab.Browse -> loadCatalog(libraryId, reset = true, force = force)
            LibrariesSubtab.Collections -> loadCollections(libraryId, force)
        }
    }

    private fun loadRecommended(libraryId: Int, force: Boolean) {
        if (!force && recommendedLoadedLibraryId == libraryId) return
        recommendedLoadedLibraryId = libraryId
        viewModelScope.launch {
            if (!isSelectedLibrary(libraryId)) return@launch
            _uiState.update {
                it.copy(
                    isLoadingSections = true,
                    sectionsError = null,
                    sections = emptyList(),
                )
            }

            when (val result = sectionRepository.getLibrarySections(libraryId)) {
                is ApiResult.Success -> {
                    if (!isSelectedLibrary(libraryId)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingSections = false,
                            sections = result.data.sections.filter { section -> section.items.isNotEmpty() },
                            sectionsError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    if (!isSelectedLibrary(libraryId)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingSections = false,
                            sections = emptyList(),
                            sectionsError = result.message.ifBlank { "Failed to load recommendations" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    if (!isSelectedLibrary(libraryId)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingSections = false,
                            sections = emptyList(),
                            sectionsError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    private fun loadCatalog(libraryId: Int, reset: Boolean, force: Boolean) {
        if (reset && !force && browseLoadedLibraryId == libraryId && _uiState.value.catalogItems.isNotEmpty()) {
            return
        }
        browseLoadedLibraryId = libraryId
        viewModelScope.launch {
            if (!isSelectedLibrary(libraryId)) return@launch
            val state = _uiState.value
            val offset = if (reset) 0 else state.catalogItems.size
            val requestedFormat = state.selectedFormat
            val requestedGenre = state.selectedBrowseGenre
            val requestedNamePrefix = state.selectedNamePrefix
            val requestedSort = state.browseSort

            if (reset && state.browseGenres.isEmpty()) {
                launch {
                    when (val filters = catalogRepository.getFilters(libraryId)) {
                        is ApiResult.Success -> {
                            if (
                                !isSelectedLibrary(libraryId) ||
                                _uiState.value.selectedFormat != requestedFormat
                            ) {
                                return@launch
                            }
                            _uiState.update { it.copy(browseGenres = filters.data.genres) }
                        }
                        else -> Unit
                    }
                }
            }

            _uiState.update {
                if (reset) {
                    it.copy(
                        isLoadingCatalog = true,
                        isLoadingMoreCatalog = false,
                        catalogError = null,
                    )
                } else {
                    it.copy(
                        isLoadingMoreCatalog = true,
                        catalogError = null,
                    )
                }
            }

            when (
                val result = catalogRepository.browse(
                    libraryId = libraryId,
                    genre = requestedGenre,
                    sort = requestedSort.sortField,
                    order = requestedSort.sortOrder,
                    offset = offset,
                    limit = pageSize,
                    namePrefix = requestedNamePrefix,
                )
            ) {
                is ApiResult.Success -> {
                    if (!isCurrentCatalogRequest(libraryId, requestedFormat, requestedGenre, requestedNamePrefix, requestedSort)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            isLoadingMoreCatalog = false,
                            catalogItems = if (reset) result.data.items else it.catalogItems + result.data.items,
                            catalogTotal = result.data.total,
                            catalogHasMore = result.data.hasMore,
                            catalogError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    if (!isCurrentCatalogRequest(libraryId, requestedFormat, requestedGenre, requestedNamePrefix, requestedSort)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            isLoadingMoreCatalog = false,
                            catalogError = result.message.ifBlank { "Failed to load catalog" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    if (!isCurrentCatalogRequest(libraryId, requestedFormat, requestedGenre, requestedNamePrefix, requestedSort)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingCatalog = false,
                            isLoadingMoreCatalog = false,
                            catalogError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    private fun loadCollections(libraryId: Int, force: Boolean) {
        if (!force && collectionsLoadedLibraryId == libraryId && _uiState.value.collections.isNotEmpty()) {
            return
        }
        collectionsLoadedLibraryId = libraryId
        viewModelScope.launch {
            if (!isSelectedLibrary(libraryId)) return@launch
            _uiState.update {
                it.copy(
                    isLoadingCollections = true,
                    collectionsError = null,
                    collections = emptyList(),
                )
            }
            when (val result = sectionRepository.getLibraryCollections(libraryId)) {
                is ApiResult.Success -> {
                    if (!isSelectedLibrary(libraryId)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingCollections = false,
                            collections = result.data,
                            collectionsError = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    if (!isSelectedLibrary(libraryId)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingCollections = false,
                            collectionsError = result.message.ifBlank { "Failed to load collections" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    if (!isSelectedLibrary(libraryId)) return@launch
                    _uiState.update {
                        it.copy(
                            isLoadingCollections = false,
                            collectionsError = "Network error: ${result.exception.message ?: "unknown"}",
                        )
                    }
                }
            }
        }
    }

    private fun isSelectedLibrary(libraryId: Int): Boolean {
        val state = _uiState.value
        return state.selectedLibraryId == libraryId &&
            state.filteredLibraries.any { it.id == libraryId && it.matchesReadingFormat(state.selectedFormat) }
    }

    private fun isCurrentCatalogRequest(
        libraryId: Int,
        format: ReadingFormatFilter,
        genre: String?,
        namePrefix: String?,
        sort: LibraryBrowseSort,
    ): Boolean {
        val state = _uiState.value
        return state.selectedLibraryId == libraryId &&
            state.selectedFormat == format &&
            state.selectedBrowseGenre == genre &&
            state.selectedNamePrefix == namePrefix &&
            state.browseSort == sort
    }
}

/**
 * Clears every piece of per-library content — Recommended sections,
 * Browse catalog + filters, Collections — along with their loading and
 * error flags. Used whenever the effective library selection changes
 * or goes away. Library-list fields (libraries, formats, selection,
 * librariesError) are left for the caller to set.
 */
private fun ReadingHubUiState.clearingLibraryContent(): ReadingHubUiState = copy(
    sections = emptyList(),
    sectionsError = null,
    isLoadingSections = false,
    catalogItems = emptyList(),
    catalogTotal = 0,
    catalogHasMore = false,
    catalogError = null,
    isLoadingCatalog = false,
    isLoadingMoreCatalog = false,
    browseGenres = emptyList(),
    selectedBrowseGenre = null,
    selectedNamePrefix = null,
    collections = emptyList(),
    collectionsError = null,
    isLoadingCollections = false,
)
