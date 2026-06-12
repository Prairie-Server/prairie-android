package com.continuum.app.android.ui.screens.downloads

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.common.downloads.DownloadEnqueuer
import com.continuum.app.common.downloads.DownloadStorage
import com.continuum.app.model.download.DownloadMediaType
import com.continuum.app.model.download.DownloadRecord
import com.continuum.app.model.download.DownloadSidecar
import com.continuum.app.model.download.DownloadStatus
import com.continuum.app.model.download.statusEnum
import com.continuum.app.network.ApiResult
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.errorMessage
import com.continuum.app.repository.DownloadsRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Leaf row in the Downloads tab. Movies / ebooks / audiobooks / single
 * episodes all render as a single [DownloadItem]. Progress is
 * `bytesSent / fileSize` clamped to [0, 1]; `isComplete` derives from
 * server status (so a 100%-bytes-sent record still reports incomplete
 * until the server flips status → completed).
 */
data class DownloadItem(
    val id: String,
    val contentId: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val posterThumbhash: String? = null,
    val fileSizeBytes: Long = 0,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val status: DownloadStatus = DownloadStatus.Unknown,
    /** True when the record finished in a non-success terminal state
     *  (failed / cancelled / unknown). The UI shows a red badge. */
    val isFailed: Boolean = false,
    val mediaType: DownloadMediaType = DownloadMediaType.Unknown,
    val fileId: Int? = null,
    val localUri: String? = null,
    val displayName: String? = null,
    val container: String? = null,
)

/**
 * Recursive hierarchy of downloaded content for the Downloads tab.
 *
 * - [Single] — a movie, ebook, audiobook, or standalone episode.
 * - [Season] — a season of a TV series; children are [Single] episodes.
 * - [Series] — a TV series; children are [Season]s (just one in the
 *   single-season case).
 *
 * Every node aggregates `totalBytesUsed`, `recordIds`, and
 * `progress` from its children so the renderer can show storage +
 * delete-all at every level.
 */
sealed class DownloadEntry {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String?
    abstract val posterUrl: String?
    abstract val posterThumbhash: String?
    abstract val totalBytesUsed: Long
    abstract val progress: Float
    abstract val isComplete: Boolean
    /** Flat list of every server record id under this node — used by
     *  the delete-at-this-level handler. */
    abstract val recordIds: List<String>

    data class Single(val item: DownloadItem) : DownloadEntry() {
        override val id = item.id
        override val title = item.title
        override val subtitle = item.subtitle
        override val posterUrl = item.posterUrl
        override val posterThumbhash = item.posterThumbhash
        override val totalBytesUsed = item.fileSizeBytes
        override val progress = item.progress
        override val isComplete = item.isComplete
        override val recordIds = listOf(item.id)
    }

    data class Season(
        val seriesTitle: String,
        val seasonNumber: Int,
        val episodes: List<Single>,
        override val posterUrl: String?,
        override val posterThumbhash: String?,
    ) : DownloadEntry() {
        override val id = "season:$seriesTitle:$seasonNumber"
        override val title = "Season $seasonNumber"
        val completedCount = episodes.count { it.isComplete }
        override val subtitle = "$completedCount of ${episodes.size} episodes"
        override val totalBytesUsed = episodes.sumOf { it.totalBytesUsed }
        override val progress = if (episodes.isEmpty()) 0f
            else episodes.map { it.progress }.average().toFloat()
        override val isComplete = episodes.isNotEmpty() && episodes.all { it.isComplete }
        override val recordIds = episodes.flatMap { it.recordIds }
    }

    data class Series(
        val seriesTitle: String,
        val seasons: List<Season>,
        override val posterUrl: String?,
        override val posterThumbhash: String?,
    ) : DownloadEntry() {
        override val id = "series:$seriesTitle"
        override val title = seriesTitle
        val totalEpisodes = seasons.sumOf { it.episodes.size }
        val completedEpisodes = seasons.sumOf { it.completedCount }
        override val subtitle = "$completedEpisodes of $totalEpisodes episodes"
        override val totalBytesUsed = seasons.sumOf { it.totalBytesUsed }
        override val progress = if (totalEpisodes == 0) 0f
            else seasons.sumOf { it.episodes.sumOf { ep -> ep.progress.toDouble() } }
                .div(totalEpisodes).toFloat()
        override val isComplete = seasons.isNotEmpty() && seasons.all { it.isComplete }
        override val recordIds = seasons.flatMap { it.recordIds }
    }
}

/**
 * A media-type section: one of Movies, TV Shows, Audiobooks, eBooks,
 * or Other. Each section's [entries] is type-shaped — TV has [Series]
 * roots, everything else is flat [Single]s.
 */
data class DownloadTypeSection(
    val mediaType: DownloadMediaType,
    val entries: List<DownloadEntry>,
    val totalBytesUsed: Long,
    val recordIds: List<String>,
) {
    val displayName: String get() = mediaType.displayName
    val itemCount: Int get() = entries.sumOf { it.recordIds.size }
}

data class DownloadsUiState(
    val sections: List<DownloadTypeSection> = emptyList(),
    val totalBytesUsed: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** "Is there anything?" — drives the Downloads-tab visibility in
     *  the bottom nav + the empty-state branch. */
    val isEmpty: Boolean get() = sections.isEmpty()
}

/**
 * Disk-as-truth Downloads orchestrator with hierarchical grouping.
 *
 * Boot sequence:
 *   1. Seed cache from on-disk sidecars so offline launches populate
 *      instantly.
 *   2. Best-effort server refresh — merges, never overwrites.
 *   3. Render UI tree.
 *
 * Render tree:
 *   sections [Movies / TV / Audiobooks / eBooks / Other]
 *     → entries (Single for leaves, Series → Season → Single for TV)
 *
 * Sidecar metadata is re-read on every records emission so an active
 * download's poster + title surface the moment its sidecar lands
 * (previously the row showed bare contentId until the next launch).
 */
class DownloadsViewModel(
    private val repository: DownloadsRepository,
    private val storage: DownloadStorage,
    private val serverRegistry: ServerRegistry,
    private val profileRepository: ProfileRepository,
    private val downloadEnqueuer: DownloadEnqueuer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState(isLoading = true))
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    /** Sidecar map keyed by record id. Refreshed on every records
     *  emission via [reloadSidecarMetadata]. */
    @Volatile private var metadataByRecordId: Map<String, DownloadSidecar> = emptyMap()

    /** `(serverId, profileId)` scope each fileId's sidecar lives under,
     *  captured during the same walk that loads [metadataByRecordId] so
     *  [toItem] doesn't re-walk the filesystem per record per emission. */
    @Volatile private var scopeByFileId: Map<Int, Pair<String, String>> = emptyMap()

    init {
        viewModelScope.launch {
            // Bootstrap: backfill + initial sidecar read.
            withContext(Dispatchers.IO) { reloadSidecarMetadata() }
            val seeded = metadataByRecordId.values.toList()
            repository.seedFromSidecars(seeded.map { it.record })

            val keep = seeded.map { it.record.id }.toSet()
            launch { repository.refresh(keepIdsAbsentFromServer = keep) }

            repository.records.collect { records ->
                // Section building reads sidecars + walks file sizes — keep
                // it off the main dispatcher; the worker emits every ~200ms
                // during an active download.
                val (sections, bytesUsed) = withContext(Dispatchers.IO) {
                    // Refresh sidecars so newly-enqueued records get their
                    // metadata into the UI without waiting for a restart.
                    if (records.any { it.id !in metadataByRecordId }) {
                        reloadSidecarMetadata()
                    }
                    records.toSections() to storage.totalBytesUsed()
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        // Preserve any delete/refresh failure — refresh() and
                        // clearError() manage the error lifecycle explicitly;
                        // a progress tick must not wipe it before it's shown.
                        error = it.error,
                        sections = sections,
                        totalBytesUsed = bytesUsed,
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { reloadSidecarMetadata() }
            val keep = metadataByRecordId.keys
            when (val r = repository.refresh(keepIdsAbsentFromServer = keep)) {
                is ApiResult.Success -> _uiState.update { it.copy(error = null) }
                is ApiResult.Error, is ApiResult.NetworkError ->
                    _uiState.update { it.copy(error = r.errorMessage("Failed to refresh downloads")) }
            }
        }
    }

    /** Clear a surfaced error once the UI has shown it (e.g. after the
     *  snackbar is dismissed). */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Remove a single record by id (leaf row). */
    fun removeDownload(id: String) {
        viewModelScope.launch { removeRecords(listOf(id)) }
    }

    /** Remove every record under an entry (Series, Season, or single
     *  leaf). [DownloadEntry.recordIds] aggregates across all
     *  descendants, so this works at every level. */
    fun removeEntry(entry: DownloadEntry) {
        viewModelScope.launch { removeRecords(entry.recordIds) }
    }

    /** Remove every record in a media-type section (e.g. "Delete all
     *  Movies", "Delete all TV"). */
    fun removeSection(section: DownloadTypeSection) {
        viewModelScope.launch { removeRecords(section.recordIds) }
    }

    private suspend fun removeRecords(ids: List<String>) {
        if (ids.isEmpty()) return
        val serverId = serverRegistry.activeServerId.value ?: DownloadEnqueuer.DEFAULT_SERVER_ID
        val profileId = withContext(Dispatchers.IO) {
            profileRepository.getActiveProfileId()
        } ?: DownloadEnqueuer.DEFAULT_PROFILE_ID

        var firstError: String? = null
        for (id in ids) {
            val record = repository.records.value.firstOrNull { it.id == id }
            val sidecar = metadataByRecordId[id]
            val fileId = record?.mediaFileId ?: sidecar?.record?.mediaFileId
            Log.i(TAG, "remove($id): record=${record?.status ?: "(missing)"} fileId=$fileId")

            // Server delete FIRST. Only a confirmed removal (success, or a
            // 404 = the record exists only locally — repository.delete maps
            // that to Success) may drop bytes from disk. pendingDelete is
            // in-memory only: deleting bytes before the server confirms
            // means an app restart merges the server record back pointing
            // at files that no longer exist.
            when (val result = repository.delete(id)) {
                is ApiResult.Success -> Log.i(TAG, "remove($id): server delete OK")
                is ApiResult.Error -> {
                    Log.w(TAG, "remove($id): server returned ${result.code} ${result.message}")
                    if (firstError == null) firstError = result.errorMessage("Delete failed (${result.code})")
                    continue
                }
                is ApiResult.NetworkError -> {
                    // One network failure means the rest of the batch will
                    // fail the same way — bail out instead of grinding on.
                    Log.w(TAG, "remove($id): network error", result.exception)
                    if (firstError == null) firstError = result.errorMessage("Network error. Check your connection.")
                    break
                }
            }

            // An active record still has a DownloadWorker streaming bytes.
            // Cancel it only after the server confirmed the delete, so a
            // failed server delete leaves the active download untouched
            // (pendingDelete suppression covers the worker race in between).
            val status = record?.statusEnum() ?: sidecar?.record?.statusEnum()
            if (status == DownloadStatus.Queued || status == DownloadStatus.Downloading) {
                downloadEnqueuer.cancel(id)
            }

            if (fileId != null) {
                withContext(Dispatchers.IO) {
                    storage.delete(serverId, profileId, fileId)
                    storage.deleteSidecar(serverId, profileId, fileId)
                }
                scopeByFileId = scopeByFileId - fileId
            }
            metadataByRecordId = metadataByRecordId - id
        }
        val bytesUsed = withContext(Dispatchers.IO) { storage.totalBytesUsed() }
        _uiState.update {
            it.copy(
                error = firstError,
                totalBytesUsed = bytesUsed,
            )
        }
    }

    /** One filesystem walk loads both lookup maps. Call on Dispatchers.IO. */
    private fun reloadSidecarMetadata() {
        val scoped = runCatching { storage.listAllSidecarsWithScope() }.getOrElse { emptyList() }
        metadataByRecordId = scoped.associate { (_, _, sidecar) -> sidecar.record.id to sidecar }
        scopeByFileId = scoped.associate { (serverId, profileId, sidecar) ->
            sidecar.record.mediaFileId to (serverId to profileId)
        }
    }

    companion object {
        private const val TAG = "DownloadsViewModel"
    }

    // ── Sectioning + grouping ─────────────────────────────────────────────

    private fun DownloadRecord.toItem(): DownloadItem {
        val meta = metadataByRecordId[id]
        val mediaType = resolveMediaType()
        val located = scopeByFileId[mediaFileId]?.let { (serverId, profileId) ->
            storage.locateLocalMedia(serverId, profileId, mediaFileId)
        }
        val progress = if (fileSize > 0) {
            (bytesSent.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
        } else 0f
        val status = statusEnum()
        return DownloadItem(
            id = id,
            contentId = contentId,
            title = meta?.title ?: contentId.ifEmpty { "Download #${mediaFileId}" },
            subtitle = meta?.subtitle,
            posterUrl = meta?.posterUrl,
            posterThumbhash = meta?.posterThumbhash,
            fileSizeBytes = fileSize,
            progress = progress,
            isComplete = status == DownloadStatus.Completed,
            status = status,
            isFailed = status in setOf(DownloadStatus.Failed, DownloadStatus.Cancelled, DownloadStatus.Unknown),
            mediaType = mediaType,
            fileId = mediaFileId,
            localUri = located?.uriString,
            displayName = located?.displayName ?: meta?.fileName,
            container = meta?.container,
        )
    }

    /**
     * Resolves the media type for a record. Trusts the sidecar's
     * [DownloadSidecar.mediaType] field first; if unset (older sidecar
     * predating the field), falls back to the seriesTitle heuristic
     * (presence implies TV).
     */
    private fun DownloadRecord.resolveMediaType(): DownloadMediaType {
        val sidecar = metadataByRecordId[id]
        val explicit = DownloadMediaType.fromWire(sidecar?.mediaType)
        if (explicit != DownloadMediaType.Unknown) return explicit
        return if (!sidecar?.seriesTitle.isNullOrBlank()) DownloadMediaType.TvShow
        else DownloadMediaType.Movie
    }

    /** Top-level: group records into per-mediaType sections in stable
     *  display order. Empty sections are skipped. */
    private fun List<DownloadRecord>.toSections(): List<DownloadTypeSection> {
        if (isEmpty()) return emptyList()
        val byType = groupBy { it.resolveMediaType() }
        val sectionOrder = listOf(
            DownloadMediaType.Movie,
            DownloadMediaType.TvShow,
            DownloadMediaType.Audiobook,
            DownloadMediaType.Ebook,
            DownloadMediaType.Unknown,
        )
        return sectionOrder.mapNotNull { type ->
            val recs = byType[type].orEmpty()
            if (recs.isEmpty()) return@mapNotNull null
            val entries = when (type) {
                DownloadMediaType.TvShow -> recs.toTvEntries()
                else -> recs.map { DownloadEntry.Single(it.toItem()) }
            }
            DownloadTypeSection(
                mediaType = type,
                entries = entries,
                totalBytesUsed = entries.sumOf { it.totalBytesUsed },
                recordIds = entries.flatMap { it.recordIds },
            )
        }
    }

    /**
     * TV branch: records → [Series] → [Season] → [Single]. A record with
     * no resolvable seriesTitle (rare — TV-typed but missing metadata)
     * still surfaces as a Single under the TV section so the user can
     * delete it.
     */
    private fun List<DownloadRecord>.toTvEntries(): List<DownloadEntry> {
        val bySeries = LinkedHashMap<String, MutableList<DownloadRecord>>()
        val orphans = mutableListOf<DownloadRecord>()
        for (rec in this) {
            val series = metadataByRecordId[rec.id]?.seriesTitle?.takeIf { it.isNotBlank() }
            if (series == null) orphans += rec
            else bySeries.getOrPut(series) { mutableListOf() } += rec
        }

        val seriesEntries = bySeries.map { (seriesTitle, recs) ->
            // Group by season number; null seasonNumber becomes -1 so
            // it sorts first (won't happen in practice for properly-
            // tagged downloads).
            val bySeason = LinkedHashMap<Int, MutableList<DownloadRecord>>()
            for (rec in recs) {
                val season = metadataByRecordId[rec.id]?.seasonNumber ?: -1
                bySeason.getOrPut(season) { mutableListOf() } += rec
            }
            val firstMeta = recs.asSequence()
                .mapNotNull { metadataByRecordId[it.id] }
                .firstOrNull()
            val seasons = bySeason.toSortedMap().map { (season, seasonRecs) ->
                val episodes = seasonRecs
                    .map { DownloadEntry.Single(it.toItem()) }
                    .sortedBy { metadataByRecordId[it.item.id]?.episodeNumber ?: Int.MAX_VALUE }
                DownloadEntry.Season(
                    seriesTitle = seriesTitle,
                    seasonNumber = season,
                    episodes = episodes,
                    posterUrl = firstMeta?.posterUrl,
                    posterThumbhash = firstMeta?.posterThumbhash,
                )
            }
            DownloadEntry.Series(
                seriesTitle = seriesTitle,
                seasons = seasons,
                posterUrl = firstMeta?.posterUrl,
                posterThumbhash = firstMeta?.posterThumbhash,
            )
        }

        return seriesEntries + orphans.map { DownloadEntry.Single(it.toItem()) }
    }
}
