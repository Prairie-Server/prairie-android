package com.continuum.app.tv.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.model.catalog.BrowseItem
import com.continuum.app.model.catalog.EpisodeListItem
import com.continuum.app.model.catalog.ItemDetail
import com.continuum.app.model.catalog.Season
import com.continuum.app.model.catalog.isAudiobookItemType
import com.continuum.app.model.catalog.sortedForDisplay
import com.continuum.app.model.section.SectionItem
import com.continuum.app.network.ApiResult
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.PersonalDataRepository
import com.continuum.app.tv.ui.util.isTvHiddenMediaType
import com.continuum.app.tv.ui.util.visibleOnTv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvItemDetailUiState(
    val isLoading: Boolean = true,
    val detail: ItemDetail? = null,
    val error: String? = null,
    // User state toggles.
    val isFavorite: Boolean = false,
    val inWatchlist: Boolean = false,
    val isTogglingFavorite: Boolean = false,
    val isTogglingWatchlist: Boolean = false,
    val userRating: Int? = null,
    val isTogglingRating: Boolean = false,
    // Series navigation (only relevant when detail.type == "series").
    val seasons: List<Season> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<EpisodeListItem> = emptyList(),
    val seasonsLoading: Boolean = false,
    val episodesLoading: Boolean = false,
    // Version selection for multi-file items.
    val selectedFileId: Int? = null,
    // Catalog-backed related shelf. This is a same-type / same-primary-genre
    // browse query until the server exposes an item-specific related endpoint.
    val moreLikeThis: List<SectionItem> = emptyList(),
    val moreLikeThisLoading: Boolean = false,
)

/**
 * Drives the enhanced TV item detail screen. Loads the full [ItemDetail] plus
 * the current user's favorite/watchlist state in parallel. For series, pulls
 * seasons once the main detail lands and lazily loads episodes whenever the
 * user switches seasons.
 *
 * Receives `contentId` via Koin `parametersOf()` (see
 * [com.continuum.app.tv.di.androidTvModule]).
 */
class TvItemDetailViewModel(
    private val catalogRepository: CatalogRepository,
    private val personalDataRepository: PersonalDataRepository,
    private val contentId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvItemDetailUiState())
    val uiState: StateFlow<TvItemDetailUiState> = _uiState.asStateFlow()

    init {
        if (contentId.isNotBlank()) loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Kick off user-state fetches in parallel — they aren't load-blocking;
            // the detail must succeed before we render, but favorite/watchlist
            // state can trickle in afterward.
            loadUserState()
            loadDetail()
        }
    }

    private fun loadDetail() {
        viewModelScope.launch {
            when (val result = catalogRepository.getItemDetail(contentId)) {
                is ApiResult.Success -> {
                    val detail = result.data
                    if (isTvHiddenMediaType(detail.type)) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                detail = null,
                                error = "This title is not available on Android TV.",
                            )
                        }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            userRating = detail.userRating,
                            error = null,
                        )
                    }
                    when (detail.type.lowercase()) {
                        "series" -> loadSeasons(seriesContentId = detail.contentId)
                        "season",
                        "episode",
                        -> detail.seriesId?.takeIf { it.isNotBlank() }?.let { seriesId ->
                            loadSeasons(
                                seriesContentId = seriesId,
                                preferredSeasonNumber = detail.seasonNumber?.takeIf { it > 0 },
                            )
                        }
                    }
                    loadMoreLikeThis(detail)
                }
                is ApiResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = result.message.ifBlank { "Failed to load details" },
                    )
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "Network error. Check your connection.")
                }
            }
        }
    }

    private fun loadUserState() {
        viewModelScope.launch {
            val fav = personalDataRepository.isFavorite(contentId)
            if (fav is ApiResult.Success) {
                _uiState.update { it.copy(isFavorite = fav.data) }
            }
        }
        viewModelScope.launch {
            val watch = personalDataRepository.isInWatchlist(contentId)
            if (watch is ApiResult.Success) {
                _uiState.update { it.copy(inWatchlist = watch.data) }
            }
        }
    }

    fun onToggleFavorite() {
        val current = _uiState.value
        if (current.isTogglingFavorite) return
        val target = !current.isFavorite
        _uiState.update { it.copy(isTogglingFavorite = true, isFavorite = target) }
        viewModelScope.launch {
            val result = personalDataRepository.toggleFavorite(contentId, target)
            if (result !is ApiResult.Success) {
                // Roll back on error.
                _uiState.update {
                    it.copy(isTogglingFavorite = false, isFavorite = !target)
                }
            } else {
                _uiState.update { it.copy(isTogglingFavorite = false) }
            }
        }
    }

    fun onToggleWatchlist() {
        val current = _uiState.value
        if (current.isTogglingWatchlist) return
        val target = !current.inWatchlist
        _uiState.update { it.copy(isTogglingWatchlist = true, inWatchlist = target) }
        viewModelScope.launch {
            val result = personalDataRepository.toggleWatchlist(contentId, target)
            if (result !is ApiResult.Success) {
                _uiState.update {
                    it.copy(isTogglingWatchlist = false, inWatchlist = !target)
                }
            } else {
                _uiState.update { it.copy(isTogglingWatchlist = false) }
            }
        }
    }

    fun onSetRating(stars: Int) {
        val current = _uiState.value
        if (current.isTogglingRating) return
        val target = stars.coerceIn(1, 5)
        val previous = current.userRating
        _uiState.update { it.copy(isTogglingRating = true, userRating = target) }
        viewModelScope.launch {
            val result = personalDataRepository.setRating(contentId, target)
            if (result !is ApiResult.Success) {
                // Roll back on error.
                _uiState.update {
                    it.copy(isTogglingRating = false, userRating = previous)
                }
            } else {
                _uiState.update { it.copy(isTogglingRating = false) }
            }
        }
    }

    fun onClearRating() {
        val current = _uiState.value
        if (current.isTogglingRating) return
        val previous = current.userRating ?: return
        _uiState.update { it.copy(isTogglingRating = true, userRating = null) }
        viewModelScope.launch {
            val result = personalDataRepository.deleteRating(contentId)
            if (result !is ApiResult.Success) {
                // Roll back on error.
                _uiState.update {
                    it.copy(isTogglingRating = false, userRating = previous)
                }
            } else {
                _uiState.update { it.copy(isTogglingRating = false) }
            }
        }
    }

    fun onVersionSelected(fileId: Int?) {
        _uiState.update { it.copy(selectedFileId = fileId) }
    }

    fun onSeasonSelected(seasonNumber: Int) {
        if (_uiState.value.selectedSeason == seasonNumber) return
        _uiState.update { it.copy(selectedSeason = seasonNumber) }
        val detail = _uiState.value.detail ?: return
        val seriesContentId = when (detail.type.lowercase()) {
            "series" -> detail.contentId
            "season" -> detail.seriesId
            "episode" -> detail.seriesId
            else -> null
        } ?: return
        loadEpisodes(seriesContentId, seasonNumber)
    }

    private fun loadSeasons(
        seriesContentId: String,
        preferredSeasonNumber: Int? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(seasonsLoading = true) }
            when (val r = catalogRepository.getSeasons(seriesContentId)) {
                is ApiResult.Success -> {
                    val seasons = r.data.seasons.sortedForDisplay()
                    val selectedSeason = preferredSeasonNumber
                        ?.let { seasonNumber -> seasons.firstOrNull { it.seasonNumber == seasonNumber } }
                    val firstRegular = selectedSeason
                        ?: seasons.firstOrNull { !it.isSpecials }
                        ?: seasons.firstOrNull()
                    _uiState.update {
                        it.copy(
                            seasonsLoading = false,
                            seasons = seasons,
                            selectedSeason = firstRegular?.seasonNumber,
                        )
                    }
                    if (firstRegular != null) loadEpisodes(seriesContentId, firstRegular.seasonNumber)
                }
                else -> _uiState.update { it.copy(seasonsLoading = false) }
            }
        }
    }

    private fun loadEpisodes(seriesContentId: String, seasonNumber: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(episodesLoading = true) }
            when (val r = catalogRepository.getEpisodes(seriesContentId, seasonNumber)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        episodesLoading = false,
                        episodes = r.data.episodes.sortedBy { ep -> ep.episodeNumber },
                    )
                }
                else -> _uiState.update {
                    it.copy(episodesLoading = false, episodes = emptyList())
                }
            }
        }
    }

    private fun loadMoreLikeThis(detail: ItemDetail) {
        val primaryGenre = detail.genres.firstOrNull { it.isNotBlank() }
        val mediaType = detail.type.takeIf { it in setOf("movie", "series", "episode") || isAudiobookItemType(it) }
        if (primaryGenre == null && mediaType == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(moreLikeThisLoading = true) }
            when (val result = catalogRepository.browse(
                mediaType = mediaType,
                genre = primaryGenre,
                sort = "rating_imdb",
                order = "desc",
                limit = 18,
            )) {
                is ApiResult.Success -> {
                    val items = result.data.items
                        .visibleOnTv()
                        .filterNot { it.contentId == detail.contentId }
                        .take(16)
                        .map { it.toSectionItem() }
                    _uiState.update {
                        it.copy(
                            moreLikeThisLoading = false,
                            moreLikeThis = items,
                        )
                    }
                }
                else -> _uiState.update {
                    it.copy(moreLikeThisLoading = false, moreLikeThis = emptyList())
                }
            }
        }
    }
}

private fun BrowseItem.toSectionItem(): SectionItem = SectionItem(
    contentId = contentId,
    type = type,
    title = title,
    year = year,
    genres = genres,
    status = status,
    ratingImdb = ratingImdb,
    overview = overview,
    posterUrl = posterUrl,
    posterThumbhash = posterThumbhash,
    backdropUrl = backdropUrl,
    backdropThumbhash = backdropThumbhash,
    userState = userState,
)
