package org.prairieserver.prairie.android.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.prairieserver.prairie.common.downloads.DownloadEnqueuer
import org.prairieserver.prairie.model.catalog.EpisodeListItem
import org.prairieserver.prairie.model.catalog.FileVersion
import org.prairieserver.prairie.model.catalog.ItemDetail
import org.prairieserver.prairie.model.catalog.LeafItemUserData
import org.prairieserver.prairie.model.catalog.Season
import org.prairieserver.prairie.model.catalog.sortedForDisplay
import org.prairieserver.prairie.model.download.DownloadCapability
import org.prairieserver.prairie.model.download.DownloadRecord
import org.prairieserver.prairie.model.download.statusEnum
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.model.catalog.isBookLikeItemType
import org.prairieserver.prairie.metadata.DescriptionTranslationController
import org.prairieserver.prairie.metadata.DescriptionTranslationPhase
import org.prairieserver.prairie.repository.CatalogRepository
import org.prairieserver.prairie.repository.MetadataAiRepository
import org.prairieserver.prairie.repository.DownloadsRepository
import org.prairieserver.prairie.repository.EbookReaderRepository
import org.prairieserver.prairie.repository.PersonalDataRepository
import org.prairieserver.prairie.viewmodel.applyLocalPlaybackProgress
import org.prairieserver.prairie.model.download.DownloadQuality
import org.prairieserver.prairie.playback.SUBTITLE_OFF_FINGERPRINT
import org.prairieserver.prairie.playback.audioTrackFingerprint
import org.prairieserver.prairie.playback.resolveAudioTrackOrdinal
import org.prairieserver.prairie.playback.resolveSubtitleTrackOrdinal
import org.prairieserver.prairie.playback.subtitleTrackFingerprint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** First-file ids of EVERY episode across ALL seasons (loaded once for the
     *  series-level downloaded roll-up — the per-season `episodes` only covers
     *  the selected season). Empty until the background load completes. */
    val allEpisodeFileIds: List<Int> = emptyList(),
    /** True only when EVERY season's episodes loaded successfully — the series
     *  hero may show ✓ only then (a failed season would shrink the denominator
     *  and falsely complete the roll-up). */
    val allEpisodeIdsComplete: Boolean = false,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false,
    val userRating: Int? = null,
    val error: String? = null,
    val selectedVersionIndex: Int = 0,
    val selectedAudioIndex: Int = 0,
    val selectedSubtitleIndex: Int = -1,
    val hasExplicitVersionSelection: Boolean = false,
    val hasExplicitAudioSelection: Boolean = false,
    val hasExplicitSubtitleSelection: Boolean = false,
    /** Server converts Kindle (mobi/azw/azw3) to EPUB, so they read in-app. */
    val kindleConversionAvailable: Boolean = false,
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
    private val downloadsRepository: DownloadsRepository,
    private val downloadEnqueuer: DownloadEnqueuer,
    private val ebookReaderRepository: EbookReaderRepository,
    metadataAiRepository: MetadataAiRepository,
    savedStateHandle: SavedStateHandle,
    private val userItemState: org.prairieserver.prairie.repository.port.UserItemStatePort =
        org.prairieserver.prairie.repository.port.NoOpUserItemStatePort,
) : ViewModel() {

    private val contentId: String = savedStateHandle.get<String>("contentId") ?: ""
    private val initialSeasonNumber: Int? =
        savedStateHandle.get<String>("seasonNumber")?.toIntOrNull()

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()
    private var episodeLoadJob: Job? = null
    private var allEpisodeFileIdsJob: Job? = null
    // The season number the currently-shown episodes actually belong to. A
    // failed season switch reverts the optimistic selection to THIS season —
    // not merely the previously-selected one, which may itself have failed —
    // so the chips and the episode list stay in agreement (TV parity: T15).
    private var loadedSeasonNumber: Int? = null

    /** Live mirror of the shared records flow; the screen reads this to
     *  derive per-version download state (isDownloaded / progress). */
    val downloads: StateFlow<List<DownloadRecord>> = downloadsRepository.records
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Server download capability (issue #20 §3). The screen reads this to gate
     *  which quality presets the picker offers. Null until the first fetch. */
    val downloadCapability: StateFlow<DownloadCapability?> = downloadsRepository.capability

    private var watchedMutationGeneration = 0

    private val descriptionTranslation = DescriptionTranslationController(
        repository = metadataAiRepository,
        delayMs = { kotlinx.coroutines.delay(it) },
    )
    val translationPhase: StateFlow<DescriptionTranslationPhase> = descriptionTranslation.phase

    init {
        // Refresh once so server-side records are visible when the user
        // lands on the detail screen (e.g., to show 'Downloaded' on a file
        // that was downloaded in a previous app session).
        viewModelScope.launch { downloadsRepository.refresh() }
        // Fetch download capability so the quality picker only offers presets
        // this account may request (issue #20 GAP 4). Best-effort: on failure
        // the picker falls back to an optimistic list; the server still rejects
        // a disallowed quality. Cached in the repo, so this is cheap per detail.
        viewModelScope.launch { downloadsRepository.refreshCapability() }
    }

    /**
     * Returns the download record for the given [version]'s fileId, or
     * null when nothing has been requested for that version yet.
     */
    fun downloadRecordFor(version: FileVersion): DownloadRecord? =
        downloads.value.firstOrNull { it.mediaFileId == version.fileId }

    /**
     * Tap action for the download button. Branches on current record state:
     *  - None / failed / cancelled → start a new download
     *  - Queued / downloading → cancel via WorkManager + delete the record
     *  - Completed → no-op (user deletes from the Downloads tab)
     *  - Completed but local file missing → delete stale server row, then start again
     */
    fun onDownloadTapped(
        version: FileVersion,
        displayTitle: String,
        forceRedownloadMissingLocal: Boolean = false,
        downloadQuality: DownloadQuality? = null,
    ) {
        val existing = downloadRecordFor(version)
        when (
            detailDownloadTapAction(
                status = existing?.statusEnum(),
                forceRedownloadMissingLocal = forceRedownloadMissingLocal,
            )
        ) {
            DetailDownloadTapAction.Cancel -> {
                existing?.let { record ->
                    downloadEnqueuer.cancel(record.id)
                    viewModelScope.launch { downloadsRepository.delete(record.id) }
                }
            }
            DetailDownloadTapAction.Ignore -> Unit  // Manage via Downloads tab.
            DetailDownloadTapAction.ReplaceAndStart -> viewModelScope.launch {
                val staleRecord = existing
                if (staleRecord == null || downloadsRepository.delete(staleRecord.id) is ApiResult.Success) {
                    startDownload(version, displayTitle, downloadQuality)
                }
            }
            DetailDownloadTapAction.Start -> viewModelScope.launch {
                startDownload(version, displayTitle, downloadQuality)
            }
        }
    }

    /** True = download started, false = registration failed. Drives the
     *  download-start haptic (the only haptic iOS mobile has). */
    private val _downloadStartEvents = kotlinx.coroutines.flow.MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val downloadStartEvents: kotlinx.coroutines.flow.SharedFlow<Boolean> = _downloadStartEvents

    private suspend fun startDownload(
        version: FileVersion,
        displayTitle: String,
        downloadQuality: DownloadQuality? = null,
    ) {
        // wifiOnly read from per-profile PlayerSettingsStore inside
        // DownloadEnqueuer.start; default true.
        val result = downloadEnqueuer.start(
            contentId = contentId,
            fileId = version.fileId,
            displayTitle = displayTitle,
            downloadQualityOverride = downloadQuality,
        )
        _downloadStartEvents.emit(result is ApiResult.Success)
    }

    /**
     * Per-episode download tap. Picks the best file for the episode (first
     * entry in the server-sorted files list) and queues it. If the episode
     * has no files (rare — orphaned record), no-ops.
     */
    fun onEpisodeDownloadTapped(
        episode: EpisodeListItem,
        downloadQuality: DownloadQuality? = null,
    ) {
        val fileId = episode.files.firstOrNull()?.fileId ?: return
        val detail = _uiState.value.detail ?: return
        // Branch on current state like the movie/audiobook path: a downloaded
        // episode is a no-op (manage via the Downloads tab); an in-flight one
        // cancels; otherwise start. Previously it always re-enqueued.
        val existing = downloads.value.firstOrNull { it.mediaFileId == fileId }
        when (detailDownloadTapAction(existing?.statusEnum(), forceRedownloadMissingLocal = false)) {
            DetailDownloadTapAction.Ignore -> Unit
            DetailDownloadTapAction.Cancel -> existing?.let { record ->
                downloadEnqueuer.cancel(record.id)
                viewModelScope.launch { downloadsRepository.delete(record.id) }
            }
            DetailDownloadTapAction.Start, DetailDownloadTapAction.ReplaceAndStart -> viewModelScope.launch {
                // Episode pages load the episode itself as `detail`, so the
                // parent reference supplies the series id/title for grouping.
                downloadEnqueuer.startEpisode(
                    seriesContentId = if (detail.type == "series") {
                        detail.contentId
                    } else {
                        detail.seriesId ?: detail.contentId
                    },
                    episodeContentId = episode.contentId,
                    fileId = fileId,
                    seriesTitle = if (detail.type == "series") {
                        detail.title
                    } else {
                        detail.seriesTitle ?: detail.title
                    },
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    episodeTitle = episode.title,
                    posterUrl = detail.posterUrl,
                    downloadQualityOverride = downloadQuality,
                )
            }
        }
    }

    /** Series-level "Download series" — uses the server's batch endpoint
     *  (one POST → N records sharing a batchId). */
    fun onSeriesDownloadTapped(downloadQuality: DownloadQuality? = null) {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            downloadEnqueuer.startSeries(
                seriesContentId = detail.contentId,
                downloadQualityOverride = downloadQuality,
            )
        }
    }

    /** Per-season "Download season" — server has no season-batch endpoint
     *  so this loops POST-per-episode locally inside the enqueuer. */
    fun onSeasonDownloadTapped(
        seasonNumber: Int,
        downloadQuality: DownloadQuality? = null,
    ) {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            downloadEnqueuer.startSeason(
                seriesContentId = detail.contentId,
                seasonNumber = seasonNumber,
                downloadQualityOverride = downloadQuality,
            )
        }
    }

    init {
        if (contentId.isNotBlank()) {
            loadDetail()
            loadUserState()
        }
    }

    fun loadDetail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            seedCachedDetail()

            when (val result = catalogRepository.getItemDetail(contentId)) {
                is ApiResult.Success -> {
                    val detail = withLocalProgress(result.data)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            userRating = detail.userRating,
                            error = null,
                        )
                    }
                    // Restore a persisted audio/subtitle override for this item.
                    seedPersistedTrackSelection()
                    // For series, load seasons
                    if (detail.type == "series") {
                        loadSeasons(detail.contentId)
                    }
                    // For episodes, load the parent series' seasons + this
                    // season's siblings so the page can offer the selector.
                    if (detail.type == "episode") {
                        val seriesId = detail.seriesId
                        val seasonNumber = detail.seasonNumber
                        if (seriesId != null && seasonNumber != null) {
                            loadEpisodeSiblings(seriesId, seasonNumber)
                        }
                    }
                    // For books, learn whether the server converts Kindle formats to
                    // EPUB, so the "Read" affordance can offer mobi/azw/azw3 in-app.
                    if (isBookLikeItemType(detail.type)) {
                        viewModelScope.launch {
                            if (ebookReaderRepository.isKindleConversionAvailable()) {
                                _uiState.update { it.copy(kindleConversionAvailable = true) }
                            }
                        }
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

    /**
     * Quiet refresh for returning to an already-loaded detail screen (e.g.
     * backing out of the player): re-reads userData so the Play button's
     * resume label and the episode list reflect the session that just ended.
     * Deliberately NOT [loadDetail] — no loading flashes, and the user's
     * season selection is preserved.
     */
    fun refreshOnReturn() {
        val current = _uiState.value.detail ?: return
        viewModelScope.launch {
            // Local overlay first: the player's final position write is already
            // on disk, so the label corrects before the server round-trip.
            val overlaid = withLocalProgress(current)
            if (overlaid != current) {
                _uiState.update { it.copy(detail = overlaid) }
            }
            when (val result = catalogRepository.getItemDetail(contentId)) {
                is ApiResult.Success -> {
                    val detail = withLocalProgress(result.data)
                    _uiState.update {
                        it.copy(
                            detail = detail,
                            userRating = detail.userRating,
                        )
                    }
                }
                // Quiet refresh: on failure keep showing what we have.
                is ApiResult.Error,
                is ApiResult.NetworkError -> Unit
            }
        }
        if (current.type == "series") {
            loadEpisodes(current.contentId, _uiState.value.selectedSeasonNumber)
        } else if (current.type == "episode") {
            current.seriesId?.let { loadEpisodes(it, _uiState.value.selectedSeasonNumber) }
        }
    }

    private suspend fun seedCachedDetail() {
        val cached = catalogRepository.getCachedItemDetail(contentId)?.let { withLocalProgress(it) } ?: return
        _uiState.update {
            it.copy(
                isLoading = true,
                detail = cached,
                userRating = cached.userRating,
                error = null,
            )
        }
    }

    private fun loadUserState() {
        viewModelScope.launch {
            // Local optimistic favorite wins and is applied IMMEDIATELY (isFavorite
            // is a network-only read — stale/unavailable offline or right after an
            // offline toggle; don't let a slow/failed network call delay or clobber it).
            val localFavorite = runCatching {
                userItemState.localContentStates(listOf(contentId))[contentId]?.favorite
            }.getOrNull()
            if (localFavorite != null) {
                _uiState.update { it.copy(isFavorite = localFavorite) }
            } else {
                val favResult = personalDataRepository.isFavorite(contentId)
                if (favResult is ApiResult.Success) {
                    _uiState.update { it.copy(isFavorite = favResult.data) }
                }
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
                        loadEpisodes(
                            seriesId = seriesId,
                            seasonNumber = selectedSeason.seasonNumber,
                            seasonsForDownloadRollup = seasons,
                        )
                    } else {
                        loadAllEpisodeFileIds(seriesId, seasons)
                    }
                }
                else -> { /* Season load failure is non-critical */ }
            }
        }
    }

    /** Loads every season's episodes once to compute the series-level downloaded
     *  roll-up (✓ only when ALL episodes are downloaded). Best-effort: a season
     *  that fails to load just contributes no ids. Episode reads are cache-backed. */
    private fun loadAllEpisodeFileIds(
        seriesId: String,
        seasons: List<Season>,
        seedEpisodes: List<EpisodeListItem> = emptyList(),
        skipSeasonNumber: Int? = null,
    ) {
        allEpisodeFileIdsJob?.cancel()
        allEpisodeFileIdsJob = viewModelScope.launch {
            val fileIds = mutableListOf<Int>()
            seedEpisodes.forEach { ep -> ep.files.firstOrNull()?.fileId?.let { fileIds += it } }
            val canSkipSeedSeason = skipSeasonNumber != null && seedEpisodes.isNotEmpty()
            if (fileIds.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        allEpisodeFileIds = fileIds.distinct(),
                        allEpisodeIdsComplete = canSkipSeedSeason && seasons.size <= 1,
                    )
                }
            }

            // This roll-up is only for the detail download badge. Let the selected
            // season render and become interactive before crawling the rest.
            delay(350)

            var complete = true
            for (season in seasons) {
                if (canSkipSeedSeason && season.seasonNumber == skipSeasonNumber) continue
                when (val r = catalogRepository.getEpisodes(seriesId, season.seasonNumber)) {
                    is ApiResult.Success -> r.data.episodes.forEach { ep ->
                        ep.files.firstOrNull()?.fileId?.let { fileIds += it }
                    }
                    // A season we couldn't load means we can't prove series-completeness.
                    else -> complete = false
                }
            }
            _uiState.update {
                it.copy(
                    allEpisodeFileIds = fileIds.distinct(),
                    allEpisodeIdsComplete = complete,
                )
            }
        }
    }

    /** Episode pages: seasons + this season's siblings, mirroring iOS's
     *  episode detail (the episode's own season stays selected; no series
     *  download roll-up — that's a series-page concern). */
    private fun loadEpisodeSiblings(seriesId: String, seasonNumber: Int) {
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber) }
        loadEpisodes(seriesId, seasonNumber)
        viewModelScope.launch {
            when (val result = catalogRepository.getSeasons(seriesId)) {
                is ApiResult.Success -> {
                    val seasons = result.data.seasons.sortedForDisplay()
                    _uiState.update { it.copy(seasons = seasons) }
                }
                else -> { /* Season load failure is non-critical */ }
            }
        }
    }

    /**
     * Selects a season and loads its episodes. Works from both the series
     * page and an episode page (where the loaded detail is the episode and
     * the series id comes from its parent reference).
     */
    fun selectSeason(seasonNumber: Int) {
        // Optimistic write; a failed load reverts to [loadedSeasonNumber] so
        // the new season header can't sit above the old season's still-loaded
        // episodes (see loadEpisodes' error branches).
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber) }
        val detail = _uiState.value.detail ?: return
        val seriesId = if (detail.type == "series") detail.contentId else detail.seriesId ?: return
        loadEpisodes(seriesId, seasonNumber)
    }

    private fun loadEpisodes(
        seriesId: String,
        seasonNumber: Int,
        seasonsForDownloadRollup: List<Season>? = null,
    ) {
        episodeLoadJob?.cancel()
        episodeLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true) }
            when (val result = catalogRepository.getEpisodes(seriesId, seasonNumber)) {
                is ApiResult.Success -> {
                    val episodes = withLocalProgress(result.data.episodes)
                    loadedSeasonNumber = seasonNumber
                    _uiState.update {
                        it.copy(
                            isLoadingEpisodes = false,
                            episodes = episodes,
                        )
                    }
                    seasonsForDownloadRollup?.let { seasons ->
                        loadAllEpisodeFileIds(
                            seriesId = seriesId,
                            seasons = seasons,
                            seedEpisodes = episodes,
                            skipSeasonNumber = seasonNumber,
                        )
                    }
                }
                // Failed season switch: revert the optimistic selection to the
                // season whose episodes are actually on screen. A
                // successful-but-empty season keeps the new selection (empty state).
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingEpisodes = false,
                            selectedSeasonNumber = loadedSeasonNumber ?: it.selectedSeasonNumber,
                        )
                    }
                    seasonsForDownloadRollup?.let { loadAllEpisodeFileIds(seriesId, it) }
                }
                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingEpisodes = false,
                            selectedSeasonNumber = loadedSeasonNumber ?: it.selectedSeasonNumber,
                        )
                    }
                    seasonsForDownloadRollup?.let { loadAllEpisodeFileIds(seriesId, it) }
                }
            }
        }
    }

    private suspend fun withLocalProgress(detail: ItemDetail): ItemDetail =
        applyLocalPlaybackProgress(detail, userItemState.localPlaybackProgress(detail.contentId))

    private suspend fun withLocalProgress(episodes: List<EpisodeListItem>): List<EpisodeListItem> {
        if (episodes.isEmpty()) return episodes
        val progress = userItemState.localPlaybackProgressForContent(episodes.map { it.contentId })
        if (progress.isEmpty()) return episodes
        return episodes.map { episode -> applyLocalPlaybackProgress(episode, progress[episode.contentId]) }
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

    /**
     * Sets the user's star rating, clamped to 1..5. Mirrors the
     * [toggleFavorite] optimistic-update pattern: update state, call the
     * repository, revert on any non-Success result.
     */
    fun setRating(stars: Int) {
        val target = stars.coerceIn(1, 5)
        viewModelScope.launch {
            val previous = _uiState.value.userRating
            // Optimistic update
            _uiState.update { it.copy(userRating = target) }
            when (personalDataRepository.setRating(contentId, target)) {
                is ApiResult.Success -> { /* already updated */ }
                else -> {
                    // Revert on failure
                    _uiState.update { it.copy(userRating = previous) }
                }
            }
        }
    }

    /** Removes the user's rating with optimistic update + revert on failure. */
    fun clearRating() {
        viewModelScope.launch {
            val previous = _uiState.value.userRating ?: return@launch
            // Optimistic update
            _uiState.update { it.copy(userRating = null) }
            when (personalDataRepository.deleteRating(contentId)) {
                is ApiResult.Success -> { /* already updated */ }
                else -> {
                    // Revert on failure
                    _uiState.update { it.copy(userRating = previous) }
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
        // Restore any saved audio/subtitle override for the newly-selected file.
        seedPersistedTrackSelection()
    }

    /** Back to Auto — clears the version override and, like [selectVersion],
     *  the file-specific audio/subtitle overrides with it. */
    fun selectAutoVersion() {
        _uiState.update {
            it.copy(
                selectedVersionIndex = 0,
                selectedAudioIndex = 0,
                selectedSubtitleIndex = -1,
                hasExplicitVersionSelection = false,
                hasExplicitAudioSelection = false,
                hasExplicitSubtitleSelection = false,
            )
        }
        seedPersistedTrackSelection()
    }

    fun selectAudioTrack(index: Int) {
        _uiState.update {
            it.copy(
                selectedAudioIndex = index,
                hasExplicitAudioSelection = true,
            )
        }
        persistTrackSelection()
    }

    /** Back to Auto — playback falls through to the file's default track. */
    fun selectAutoAudioTrack() {
        _uiState.update {
            it.copy(
                selectedAudioIndex = 0,
                hasExplicitAudioSelection = false,
            )
        }
        persistTrackSelection()
    }

    fun selectSubtitle(index: Int) {
        _uiState.update {
            it.copy(
                selectedSubtitleIndex = index,
                hasExplicitSubtitleSelection = true,
            )
        }
        persistTrackSelection()
    }

    /** Back to Auto — distinct from an explicit -1 ("Off") selection. */
    fun selectAutoSubtitle() {
        _uiState.update {
            it.copy(
                selectedSubtitleIndex = -1,
                hasExplicitSubtitleSelection = false,
            )
        }
        persistTrackSelection()
    }

    /**
     * Persist the current audio/subtitle override for the selected version's
     * file, so it survives leaving and re-opening the detail page (and lines up
     * with the player, which records the same fingerprints against the same
     * port). Auto (no explicit pick) writes null to clear any prior override;
     * an explicit "Off" writes the shared off sentinel; a concrete pick writes
     * the catalog track's fingerprint. Keyed on (contentId, fileId) — different
     * versions carry independent selections, matching [selectVersion]'s reset.
     */
    private fun persistTrackSelection() {
        val state = _uiState.value
        val detail = state.detail ?: return
        val version = detail.versions.getOrNull(state.selectedVersionIndex) ?: return
        val subtitleFingerprint = when {
            !state.hasExplicitSubtitleSelection -> null
            state.selectedSubtitleIndex == -1 -> SUBTITLE_OFF_FINGERPRINT
            else -> version.subtitleTracks.orEmpty()
                .getOrNull(state.selectedSubtitleIndex)
                ?.let(::subtitleTrackFingerprint)
        }
        val audioFingerprint = when {
            !state.hasExplicitAudioSelection -> null
            else -> version.audioTracks.orEmpty()
                .getOrNull(state.selectedAudioIndex)
                ?.let(::audioTrackFingerprint)
        }
        viewModelScope.launch {
            userItemState.recordSubtitleTrackSelection(detail.contentId, version.fileId, subtitleFingerprint)
            userItemState.recordAudioTrackSelection(detail.contentId, version.fileId, audioFingerprint)
        }
    }

    /**
     * Seed the audio/subtitle selectors from a previously persisted override
     * for the selected version's file, so re-opening the detail page restores
     * what the user last chose. Fingerprints map back to the catalog list via
     * the shared resolvers (Off -> -1). Only applies a dimension when a saved
     * fingerprint actually matches a current track, leaving Auto otherwise.
     */
    private fun seedPersistedTrackSelection() {
        val state = _uiState.value
        val detail = state.detail ?: return
        val version = detail.versions.getOrNull(state.selectedVersionIndex) ?: return
        viewModelScope.launch {
            val saved = userItemState.localTrackSelection(detail.contentId, version.fileId) ?: return@launch
            val subtitleOrdinal = resolveSubtitleTrackOrdinal(
                version.subtitleTracks.orEmpty(),
                saved.subtitleFingerprint,
            )
            val audioOrdinal = resolveAudioTrackOrdinal(
                version.audioTracks.orEmpty(),
                saved.audioFingerprint,
            )
            if (subtitleOrdinal == null && audioOrdinal == null) return@launch
            _uiState.update {
                // The ordinals were resolved against `version`; if the user
                // switched versions while this suspended, they'd index into the
                // wrong track list — leave the new version untouched.
                if (it.detail?.versions?.getOrNull(it.selectedVersionIndex)?.fileId != version.fileId) {
                    return@update it
                }
                // Don't clobber a pick the user already made this session.
                it.copy(
                    selectedSubtitleIndex = if (it.hasExplicitSubtitleSelection) it.selectedSubtitleIndex
                    else subtitleOrdinal ?: it.selectedSubtitleIndex,
                    hasExplicitSubtitleSelection = it.hasExplicitSubtitleSelection || subtitleOrdinal != null,
                    selectedAudioIndex = if (it.hasExplicitAudioSelection) it.selectedAudioIndex
                    else audioOrdinal ?: it.selectedAudioIndex,
                    hasExplicitAudioSelection = it.hasExplicitAudioSelection || audioOrdinal != null,
                )
            }
        }
    }


    /**
     * Fire (or auto-fire once, when [auto] is set) the description
     * translation for the loaded item, then poll detail until the server
     * clears `pending_translation_language` — the refetch delivers the
     * translated overview into uiState.
     */
    fun translateDescription(auto: Boolean = false) {
        val detail = _uiState.value.detail ?: return
        val target = detail.pendingTranslationLanguage ?: return
        if (auto) {
            if (!descriptionTranslation.shouldAutoFire(detail.contentId, target)) return
            descriptionTranslation.markAutoFired(detail.contentId, target)
        }
        descriptionTranslation.resetFailure()
        viewModelScope.launch {
            descriptionTranslation.translate(
                contentId = detail.contentId,
                targetLanguage = target,
                refetchPendingLanguage = {
                    when (val result = catalogRepository.getItemDetail(contentId)) {
                        is ApiResult.Success -> {
                            val refreshed = withLocalProgress(result.data)
                            _uiState.update { it.copy(detail = refreshed) }
                            refreshed.pendingTranslationLanguage
                        }
                        else -> target // transient refetch failure: keep polling
                    }
                },
                onTranslated = { },
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

    fun toggleWatched() {
        val currentDetail = _uiState.value.detail ?: return
        val current = currentDetail.userData?.played == true
        val target = !current
        val generation = ++watchedMutationGeneration
        updatePlayedState(target)
        viewModelScope.launch {
            when (personalDataRepository.setWatched(contentId, target)) {
                is ApiResult.Success -> { /* already updated */ }
                else -> if (generation == watchedMutationGeneration) updatePlayedState(current)
            }
        }
    }

    private fun updatePlayedState(played: Boolean) {
        _uiState.update { state ->
            val detail = state.detail ?: return@update state
            val userData = detail.userData ?: LeafItemUserData()
            state.copy(detail = detail.copy(userData = userData.copy(played = played)))
        }
    }
}
