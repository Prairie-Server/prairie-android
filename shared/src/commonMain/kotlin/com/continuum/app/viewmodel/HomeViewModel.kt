package com.continuum.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.domain.MediaActionsCoordinator
import com.continuum.app.model.catalog.MediaItemUserState
import com.continuum.app.model.section.ResolvedSection
import com.continuum.app.model.section.SectionItem
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.SectionRepository
import com.continuum.app.repository.port.HomeCachePort
import com.continuum.app.repository.port.NoOpHomeCachePort
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sections: List<ResolvedSection> = emptyList(),
    val error: String? = null,
)

/**
 * Shared ViewModel for the home screen.
 *
 * Fetches the home section layout, then concurrently resolves each section's
 * items. Used by both Android and Android TV home screens.
 */
class HomeViewModel(
    private val sectionRepository: SectionRepository,
    private val mediaActions: MediaActionsCoordinator,
    // Track B: offline home cache. Defaults to no-op so commonMain/tests stay
    // network-only; the Android platform module binds a Room-backed cache.
    private val homeCache: HomeCachePort = NoOpHomeCachePort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadSections()
    }

    fun loadSections() {
        viewModelScope.launch {
            // Stale-while-revalidate: serve the cached home instantly (offline-
            // capable), then refresh from the network below.
            val cached = homeCache.getCachedHome()
            if (cached != null && cached.sections.isNotEmpty()) {
                _uiState.update { it.copy(isLoading = false, sections = cached.sections, error = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            fetchSections()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            fetchSections()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchSections() {
        // Whether we already have something to show (cached or prior fetch) — if a
        // refresh fails we keep it rather than replacing it with a blocking error.
        val hadSections = _uiState.value.sections.isNotEmpty()
        when (val result = sectionRepository.getHomeSections()) {
            is ApiResult.Success -> {
                val sections = result.data.sections
                val resolvedPairs: List<Pair<ResolvedSection, Boolean>> = sections.map { section ->
                    viewModelScope.async {
                        when (val itemsResult = sectionRepository.getHomeSectionItems(section.id)) {
                            is ApiResult.Success -> (itemsResult.data.section ?: section) to true
                            else -> section to false
                        }
                    }
                }.awaitAll()
                val resolved = resolvedPairs.map { it.first }.filter { it.items.isNotEmpty() }
                // Don't persist a partially-resolved home over a good cached one.
                val fullyResolved = resolvedPairs.all { it.second }

                _uiState.update {
                    // Only replace what's shown when the fetch fully resolved (or there
                    // was nothing yet) — a partial refresh must not clobber a good Home.
                    if (fullyResolved || !hadSections) {
                        it.copy(isLoading = false, sections = resolved, error = null)
                    } else {
                        it.copy(isLoading = false, error = null)
                    }
                }
                if (fullyResolved) {
                    homeCache.cacheHome(resolved)
                }
            }
            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        // Keep cached/prior sections on a failed refresh; only block
                        // with an error when there's nothing to show.
                        error = if (hadSections) null else result.message.ifBlank { "Failed to load home sections" },
                    )
                }
            }
            is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = if (hadSections) null else "Network error. Check your connection.",
                    )
                }
            }
        }
    }

    // -- Card context-menu actions --

    /**
     * Toggle watched state for an item, optimistically updating user state on
     * the matching [SectionItem]s. On failure the optimistic update is rolled
     * back. Continue Watching / In Progress sections are refreshed on success
     * so the server-side resolution (e.g. marking a series clears its CW row)
     * reflects in the UI.
     */
    fun setWatched(itemId: String, watched: Boolean) {
        val previous = _uiState.value.sections
        _uiState.update { state -> state.copy(sections = state.sections.mapItem(itemId) { it.withPlayed(watched) }) }
        viewModelScope.launch {
            when (mediaActions.setWatched(itemId, watched)) {
                is ApiResult.Success -> refresh()
                else -> _uiState.update { it.copy(sections = previous) }
            }
        }
    }

    fun toggleFavorite(itemId: String, favorite: Boolean) {
        val previous = _uiState.value.sections
        _uiState.update { state -> state.copy(sections = state.sections.mapItem(itemId) { it.withFavorite(favorite) }) }
        viewModelScope.launch {
            if (mediaActions.toggleFavorite(itemId, favorite) !is ApiResult.Success) {
                _uiState.update { it.copy(sections = previous) }
            }
        }
    }

    fun toggleWatchlist(itemId: String, inWatchlist: Boolean) {
        val previous = _uiState.value.sections
        _uiState.update { state -> state.copy(sections = state.sections.mapItem(itemId) { it.withWatchlist(inWatchlist) }) }
        viewModelScope.launch {
            if (mediaActions.toggleWatchlist(itemId, inWatchlist) !is ApiResult.Success) {
                _uiState.update { it.copy(sections = previous) }
            }
        }
    }

    /**
     * Removes an item from the home Continue Watching row. Optimistically
     * removes it from any continue-watching / in-progress section and rolls
     * back on failure.
     */
    fun dismissContinueWatching(itemId: String, progressUpdatedAt: String) {
        val previous = _uiState.value.sections
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.sectionType == "continue_watching" || section.sectionType == "in_progress") {
                        section.copy(items = section.items.filterNot { it.contentId == itemId })
                    } else {
                        section
                    }
                }.filter { it.items.isNotEmpty() }
            )
        }
        viewModelScope.launch {
            if (mediaActions.dismissContinueWatching(itemId, progressUpdatedAt) !is ApiResult.Success) {
                _uiState.update { it.copy(sections = previous) }
            }
        }
    }
}

private fun List<ResolvedSection>.mapItem(
    itemId: String,
    transform: (SectionItem) -> SectionItem,
): List<ResolvedSection> = map { section ->
    if (section.items.none { it.contentId == itemId }) section
    else section.copy(items = section.items.map { if (it.contentId == itemId) transform(it) else it })
}

private fun SectionItem.withPlayed(played: Boolean): SectionItem =
    copy(userState = (userState ?: MediaItemUserState()).copy(played = played))

private fun SectionItem.withFavorite(favorite: Boolean): SectionItem =
    copy(userState = (userState ?: MediaItemUserState()).copy(isFavorite = favorite))

private fun SectionItem.withWatchlist(inWatchlist: Boolean): SectionItem =
    copy(userState = (userState ?: MediaItemUserState()).copy(inWatchlist = inWatchlist))
