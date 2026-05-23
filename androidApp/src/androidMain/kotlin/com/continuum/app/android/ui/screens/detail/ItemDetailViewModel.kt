package com.continuum.app.android.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.EpisodeListItem
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.catalog.Season
import com.continuum.app.model.catalog.sortedForDisplay
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.PersonalDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the item detail screen.
 */
data class ItemDetailUiState(
    val isLoading: Boolean = true,
    val detail: ItemDetail? = null,
    val seasons: List<Season> = emptyList(),
    val selectedSeasonNumber: Int = 1,
    val episodes: List<EpisodeListItem> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false,
    val error: String? = null,
    val selectedVersionIndex: Int = 0,
    val selectedAudioIndex: Int = 0,
    val selectedSubtitleIndex: Int = -1,
    val hasExplicitVersionSelection: Boolean = false,
    val hasExplicitAudioSelection: Boolean = false,
    val hasExplicitSubtitleSelection: Boolean = false,
)

/**
 * ViewModel for the item detail screen.
 *
 * Fetches item metadata, user state (favorite/watchlist), and for series,
 * also fetches seasons and episodes. Supports toggling favorite and watchlist.
 */
class ItemDetailViewModel(
    private val catalogRepository: CatalogRepository,
    private val personalDataRepository: PersonalDataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val contentId: String = savedStateHandle.get<String>("contentId") ?: ""
    private val initialSeasonNumber: Int? =
        savedStateHandle.get<String>("seasonNumber")?.toIntOrNull()

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    init {
        if (contentId.isNotBlank()) {
            loadDetail()
            loadUserState()
        }
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (val result = catalogRepository.getItemDetail(contentId)) {
                is ApiResult.Success -> {
                    val detail = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            error = null,
                        )
                    }
                    // For series, load seasons
                    if (detail.type == "series") {
                        loadSeasons(detail.contentId)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message.ifBlank { "Failed to load details" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Network error. Check your connection.",
                        )
                    }
                }
            }
        }
    }

    private fun loadUserState() {
        viewModelScope.launch {
            val favResult = personalDataRepository.isFavorite(contentId)
            if (favResult is ApiResult.Success) {
                _uiState.update { it.copy(isFavorite = favResult.data) }
            }
        }
        viewModelScope.launch {
            val wlResult = personalDataRepository.isInWatchlist(contentId)
            if (wlResult is ApiResult.Success) {
                _uiState.update { it.copy(isInWatchlist = wlResult.data) }
            }
        }
    }

    private fun loadSeasons(seriesId: String) {
        viewModelScope.launch {
            when (val result = catalogRepository.getSeasons(seriesId)) {
                is ApiResult.Success -> {
                    val seasons = result.data.seasons.sortedForDisplay()
                    val selectedSeason = seasons.firstOrNull { it.seasonNumber == initialSeasonNumber }
                        ?: seasons.firstOrNull()
                    _uiState.update {
                        it.copy(
                            seasons = seasons,
                            selectedSeasonNumber = selectedSeason?.seasonNumber ?: 1,
                        )
                    }
                    if (selectedSeason != null) {
                        loadEpisodes(seriesId, selectedSeason.seasonNumber)
                    }
                }
                else -> { /* Season load failure is non-critical */ }
            }
        }
    }

    /**
     * Selects a season and loads its episodes.
     */
    fun selectSeason(seasonNumber: Int) {
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber) }
        val seriesId = _uiState.value.detail?.contentId ?: return
        loadEpisodes(seriesId, seasonNumber)
    }

    private fun loadEpisodes(seriesId: String, seasonNumber: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            when (val result = catalogRepository.getEpisodes(seriesId, seasonNumber)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingEpisodes = false,
                            episodes = result.data.episodes,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isLoadingEpisodes = false) }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update { it.copy(isLoadingEpisodes = false) }
                }
            }
        }
    }

    /**
     * Toggles the favorite state for this item.
     */
    fun toggleFavorite() {
        viewModelScope.launch {
            val current = _uiState.value.isFavorite
            val newState = !current
            // Optimistic update
            _uiState.update { it.copy(isFavorite = newState) }
            when (personalDataRepository.toggleFavorite(contentId, newState)) {
                is ApiResult.Success -> { /* already updated */ }
                else -> {
                    // Revert on failure
                    _uiState.update { it.copy(isFavorite = current) }
                }
            }
        }
    }

    fun selectVersion(index: Int) {
        _uiState.update {
            it.copy(
                selectedVersionIndex = index,
                selectedAudioIndex = 0,
                selectedSubtitleIndex = -1,
                hasExplicitVersionSelection = true,
                hasExplicitAudioSelection = false,
                hasExplicitSubtitleSelection = false,
            )
        }
    }

    fun selectAudioTrack(index: Int) {
        _uiState.update {
            it.copy(
                selectedAudioIndex = index,
                hasExplicitAudioSelection = true,
            )
        }
    }

    fun selectSubtitle(index: Int) {
        _uiState.update {
            it.copy(
                selectedSubtitleIndex = index,
                hasExplicitSubtitleSelection = true,
            )
        }
    }

    /**
     * Toggles the watchlist state for this item.
     */
    fun toggleWatchlist() {
        viewModelScope.launch {
            val current = _uiState.value.isInWatchlist
            val newState = !current
            // Optimistic update
            _uiState.update { it.copy(isInWatchlist = newState) }
            when (personalDataRepository.toggleWatchlist(contentId, newState)) {
                is ApiResult.Success -> { /* already updated */ }
                else -> {
                    // Revert on failure
                    _uiState.update { it.copy(isInWatchlist = current) }
                }
            }
        }
    }
}
