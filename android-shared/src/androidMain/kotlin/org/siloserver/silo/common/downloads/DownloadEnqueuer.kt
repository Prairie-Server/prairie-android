package org.siloserver.silo.common.downloads

import android.content.Context
import android.util.Log
import org.siloserver.silo.common.settings.PlayerSettingsStore
import org.siloserver.silo.model.download.DownloadQuality
import org.siloserver.silo.model.download.DownloadMediaType
import org.siloserver.silo.model.download.DownloadRequest
import org.siloserver.silo.model.download.DownloadSidecar
import org.siloserver.silo.model.download.DownloadStatus
import org.siloserver.silo.model.download.statusEnum
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.ServerRegistry
import org.siloserver.silo.model.catalog.FileVersion
import org.siloserver.silo.model.catalog.ItemDetail
import org.siloserver.silo.repository.CatalogRepository
import org.siloserver.silo.repository.DownloadsRepository
import org.siloserver.silo.repository.ProfileRepository
import kotlinx.coroutines.flow.first

/**
 * Thin Android-side helper that combines the cross-platform
 * [DownloadsRepository.create] call with the platform-specific
 * [DownloadWorker.enqueue] handoff. Centralises the (serverId, profileId)
 * resolution so ViewModels stay free of Android `Context` references.
 *
 * Also responsible for stashing the catalog metadata on disk at download
 * start: we resolve the [ItemDetail] here (one network call) and write the
 * initial [DownloadSidecar] before the worker even starts streaming bytes.
 * This is the only place we have both the catalog row and the download
 * record at the same time, so an offline app launch later doesn't need a
 * network round-trip to render the row's title and poster.
 */
class DownloadEnqueuer(
    private val context: Context,
    private val repository: DownloadsRepository,
    private val serverRegistry: ServerRegistry,
    private val profileRepository: ProfileRepository,
    private val playerSettingsStore: PlayerSettingsStore,
    private val catalogRepository: CatalogRepository,
    private val storage: DownloadStorage,
    private val metadataStore: DownloadMetadataStore,
) {

    /**
     * Single-file download (movie or individual episode by fileId). Server
     * creates the record, we stash the sidecar, then the [DownloadWorker]
     * picks it up. On server failure, returns the [ApiResult.Error] /
     * [ApiResult.NetworkError] unchanged so the caller can surface a toast.
     */
    suspend fun start(
        contentId: String,
        fileId: Int,
        displayTitle: String,
        downloadQualityOverride: DownloadQuality? = null,
    ): ApiResult<Unit> {
        Log.i(TAG, "start: contentId=$contentId fileId=$fileId title=$displayTitle")
        if (activeDownloadExists(fileId)) {
            Log.i(TAG, "start: fileId=$fileId already queued/downloading — skipping duplicate")
            return ApiResult.Success(Unit)
        }
        val record = when (val r = repository.create(
            downloadRequest(
                contentId = contentId,
                fileId = fileId,
                downloadQualityOverride = downloadQualityOverride,
            ),
        )) {
            is ApiResult.Success -> r.data.also { Log.i(TAG, "start: server record id=${it.id} status=${it.status}") }
            is ApiResult.Error -> { Log.w(TAG, "start: server error ${r.code} ${r.message}"); return ApiResult.Error(r.code, r.error, r.message) }
            is ApiResult.NetworkError -> { Log.w(TAG, "start: network error", r.exception); return ApiResult.NetworkError(r.exception) }
        }
        val sidecar = buildInitialSidecar(record, contentId, displayTitle)
        finalizeAndEnqueue(record, sidecar, displayTitle)
        return ApiResult.Success(Unit)
    }

    /**
     * Single-episode download. Resolves the episode's catalog row for rich
     * sidecar metadata (series title, S/E numbers, still image). The
     * `seriesContentId` is what gets associated on the server side; the
     * `fileId` chosen here is the user-preferred quality.
     */
    suspend fun startEpisode(
        seriesContentId: String,
        episodeContentId: String,
        fileId: Int,
        seriesTitle: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeTitle: String?,
        posterUrl: String? = null,
        downloadQualityOverride: DownloadQuality? = null,
    ): ApiResult<Unit> {
        Log.i(TAG, "startEpisode: series=$seriesContentId ep=$episodeContentId fileId=$fileId S${seasonNumber}E${episodeNumber}")
        if (activeDownloadExists(fileId)) {
            Log.i(TAG, "startEpisode: fileId=$fileId already queued/downloading — skipping duplicate")
            return ApiResult.Success(Unit)
        }
        val displayTitle = "$seriesTitle S${seasonNumber}E${episodeNumber}" +
            (episodeTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        val record = when (val r = repository.create(
            downloadRequest(
                contentId = episodeContentId,
                fileId = fileId,
                downloadQualityOverride = downloadQualityOverride,
            ),
        )) {
            is ApiResult.Success -> r.data
            is ApiResult.Error -> { Log.w(TAG, "startEpisode: server error ${r.code} ${r.message}"); return ApiResult.Error(r.code, r.error, r.message) }
            is ApiResult.NetworkError -> { Log.w(TAG, "startEpisode: network error", r.exception); return ApiResult.NetworkError(r.exception) }
        }
        val episodeDetail = (catalogRepository.getItemDetail(episodeContentId) as? ApiResult.Success)?.data
        val version = episodeDetail?.versionFor(record.mediaFileId)
        val sidecar = DownloadSidecar(
            record = record,
            title = seriesTitle,
            subtitle = "S${seasonNumber}E${episodeNumber}" + (episodeTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            posterUrl = posterUrl,
            seriesTitle = seriesTitle,
            seriesContentId = seriesContentId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            fileName = version?.fileName,
            container = version?.container,
            mediaType = DownloadMediaType.TvShow.wire,
            updatedAtMs = System.currentTimeMillis(),
        )
        finalizeAndEnqueue(record, sidecar, displayTitle)
        return ApiResult.Success(Unit)
    }

    /**
     * Whole-series download. One POST with `series=true` returns N records
     * sharing a `batch_id`. We then look up the series detail + every
     * episode's metadata to enrich each record's sidecar (so the Downloads
     * tab can show "Severance · S01E03 · Defiant Jazz" rather than just a
     * file id), and enqueue one worker per record.
     *
     * Best-effort metadata: if catalog calls fail mid-way through, the
     * remaining sidecars get bare-bones titles instead of failing the
     * whole batch.
     */
    suspend fun startSeries(
        seriesContentId: String,
        downloadQualityOverride: DownloadQuality? = null,
    ): ApiResult<Unit> {
        Log.i(TAG, "startSeries: contentId=$seriesContentId")
        val records = when (val r = repository.createBatch(
            downloadRequest(
                contentId = seriesContentId,
                series = true,
                downloadQualityOverride = downloadQualityOverride,
            ),
        )) {
            is ApiResult.Success -> r.data.also { Log.i(TAG, "startSeries: server returned ${it.size} records") }
            is ApiResult.Error -> { Log.w(TAG, "startSeries: server error ${r.code} ${r.message}"); return ApiResult.Error(r.code, r.error, r.message) }
            is ApiResult.NetworkError -> { Log.w(TAG, "startSeries: network error", r.exception); return ApiResult.NetworkError(r.exception) }
        }
        if (records.isEmpty()) return ApiResult.Success(Unit)

        val seriesDetail = (catalogRepository.getItemDetail(seriesContentId) as? ApiResult.Success)?.data
        val episodeByFileId = buildEpisodeIndexByFileId(seriesContentId)
        val seriesTitle = seriesDetail?.title ?: "Series"
        val posterUrl = seriesDetail?.posterUrl

        for (record in records) {
            // Same duplicate guard as start/startEpisode: a second worker for
            // an already-active fileId would wipe the first one's partial.
            if (activeDownloadExists(record.mediaFileId)) {
                Log.i(TAG, "startSeries: fileId=${record.mediaFileId} already queued/downloading — skipping duplicate")
                continue
            }
            val ep = episodeByFileId[record.mediaFileId]
            val episodeDetail = ep?.let { (catalogRepository.getItemDetail(it.contentId) as? ApiResult.Success)?.data }
            val version = episodeDetail?.versionFor(record.mediaFileId)
            val sidecar = DownloadSidecar(
                record = record,
                title = seriesTitle,
                subtitle = ep?.let {
                    "S${it.seasonNumber}E${it.episodeNumber}" +
                        (it.title?.takeIf { t -> t.isNotBlank() }?.let { t -> " · $t" } ?: "")
                } ?: "Series episode",
                posterUrl = posterUrl,
                posterThumbhash = seriesDetail?.posterThumbhash,
                seriesTitle = seriesTitle,
                seriesContentId = seriesContentId,
                seasonNumber = ep?.seasonNumber,
                episodeNumber = ep?.episodeNumber,
                fileName = version?.fileName,
                container = version?.container ?: ep?.files?.firstOrNull { it.fileId == record.mediaFileId }?.container,
                mediaType = DownloadMediaType.TvShow.wire,
                updatedAtMs = System.currentTimeMillis(),
            )
            val displayTitle = "$seriesTitle ${ep?.let { "S${it.seasonNumber}E${it.episodeNumber}" } ?: ""}".trim()
            finalizeAndEnqueue(record, sidecar, displayTitle)
        }
        return ApiResult.Success(Unit)
    }

    /**
     * Whole-season download. Server doesn't currently expose a season-batch
     * endpoint, so we loop: one POST per episode (chosen file = best
     * available). Sequential rather than parallel to avoid hammering the
     * server with N concurrent record creations, and so a partial failure
     * (mid-season network blip) leaves an obvious "downloaded N of K"
     * state rather than scattered ghost records.
     *
     * Returns success if at least one episode was queued; on total failure
     * returns the first error encountered.
     */
    suspend fun startSeason(
        seriesContentId: String,
        seasonNumber: Int,
        downloadQualityOverride: DownloadQuality? = null,
    ): ApiResult<Unit> {
        Log.i(TAG, "startSeason: series=$seriesContentId season=$seasonNumber")
        val episodes = when (val r = catalogRepository.getEpisodes(seriesContentId, seasonNumber)) {
            is ApiResult.Success -> r.data.episodes
            is ApiResult.Error -> return ApiResult.Error(r.code, r.error, r.message)
            is ApiResult.NetworkError -> return ApiResult.NetworkError(r.exception)
        }
        if (episodes.isEmpty()) return ApiResult.Success(Unit)

        val seriesDetail = (catalogRepository.getItemDetail(seriesContentId) as? ApiResult.Success)?.data
        val seriesTitle = seriesDetail?.title ?: "Series"
        val posterUrl = seriesDetail?.posterUrl

        var firstError: ApiResult<Unit>? = null
        var queued = 0
        for (ep in episodes) {
            // Pick the highest-resolution file (last entry after best-pick
            // would be ideal, but the server already sorts; use first).
            val fileId = ep.files.firstOrNull()?.fileId ?: continue
            val result = startEpisode(
                seriesContentId = seriesContentId,
                episodeContentId = ep.contentId,
                fileId = fileId,
                seriesTitle = seriesTitle,
                seasonNumber = ep.seasonNumber,
                episodeNumber = ep.episodeNumber,
                episodeTitle = ep.title,
                posterUrl = posterUrl,
                downloadQualityOverride = downloadQualityOverride,
            )
            if (result is ApiResult.Success) queued++
            else if (firstError == null) firstError = result
        }
        Log.i(TAG, "startSeason: queued $queued of ${episodes.size}")
        return if (queued > 0) ApiResult.Success(Unit) else (firstError ?: ApiResult.Success(Unit))
    }

    /** Builds a fileId → EpisodeListItem map across every season of [seriesContentId].
     *  Used by [startSeries] to enrich each batched sidecar with episode metadata. */
    private suspend fun buildEpisodeIndexByFileId(
        seriesContentId: String,
    ): Map<Int, org.siloserver.silo.model.catalog.EpisodeListItem> {
        val seasons = when (val r = catalogRepository.getSeasons(seriesContentId)) {
            is ApiResult.Success -> r.data.seasons
            else -> return emptyMap()
        }
        val map = HashMap<Int, org.siloserver.silo.model.catalog.EpisodeListItem>()
        for (season in seasons) {
            val eps = when (val r = catalogRepository.getEpisodes(seriesContentId, season.seasonNumber)) {
                is ApiResult.Success -> r.data.episodes
                else -> continue
            }
            for (ep in eps) {
                for (f in ep.files) {
                    map[f.fileId] = ep
                }
            }
        }
        return map
    }

    /**
     * True when the active scope already has a queued/downloading record for
     * [fileId]. Two workers for one fileId share the same on-disk target, and
     * [DownloadStorage.prepareWrite] recreates that directory — so a duplicate
     * enqueue lets the second worker wipe the first one's partial mid-stream.
     * The sidecar store is the local source of truth for per-scope status.
     */
    private suspend fun activeDownloadExists(fileId: Int): Boolean {
        val serverId = serverRegistry.activeServerId.value ?: DEFAULT_SERVER_ID
        val profileId = profileRepository.getActiveProfileId() ?: DEFAULT_PROFILE_ID
        val existing = runCatching { metadataStore.readSidecar(serverId, profileId, fileId) }.getOrNull()
            ?: return false
        return when (existing.record.statusEnum()) {
            DownloadStatus.Queued, DownloadStatus.Downloading -> true
            else -> false
        }
    }

    private suspend fun downloadRequest(
        contentId: String,
        fileId: Int? = null,
        episodeId: String? = null,
        series: Boolean = false,
        downloadQualityOverride: DownloadQuality? = null,
    ): DownloadRequest {
        val quality = downloadQualityOverride ?: DownloadQuality.fromWire(playerSettingsStore.defaultDownloadQualityFlow.first())
        return DownloadRequest(
            contentId = contentId,
            episodeId = episodeId,
            fileId = fileId,
            series = series,
            quality = quality.wire,
            targetBitrateKbps = quality.targetBitrateKbps,
        )
    }

    /**
     * Common tail: persists the sidecar and kicks off the
     * [DownloadWorker]. Pulls (serverId, profileId, wifiOnly) here once so
     * each entry point doesn't repeat the lookup.
     */
    private suspend fun finalizeAndEnqueue(
        record: org.siloserver.silo.model.download.DownloadRecord,
        sidecar: DownloadSidecar,
        displayTitle: String,
    ) {
        val serverId = serverRegistry.activeServerId.value ?: DEFAULT_SERVER_ID
        val profileId = profileRepository.getActiveProfileId() ?: DEFAULT_PROFILE_ID
        val wifiOnly = playerSettingsStore.downloadsWifiOnlyFlow.first()
        // The Room metadata row is what makes the download visible in the
        // Downloads tab (it's the source of truth there). A write failure would
        // create an invisible download — log loudly. (Recovery: the worker
        // re-asserts the row on status transitions; full create-if-missing is a
        // tracked follow-up.)
        runCatching {
            metadataStore.writeSidecar(serverId, profileId, sidecar)
        }.onFailure { Log.e(TAG, "finalize: writeSidecar FAILED for id=${record.id} — download will not appear in Downloads", it) }
        DownloadWorker.enqueue(
            context = context,
            downloadId = record.id,
            fileId = record.mediaFileId,
            serverId = serverId,
            profileId = profileId,
            fileName = sidecar.fileName,
            container = sidecar.container,
            mediaType = sidecar.mediaType,
            displayTitle = displayTitle,
            wifiOnly = wifiOnly,
        )
    }

    private suspend fun buildInitialSidecar(
        record: org.siloserver.silo.model.download.DownloadRecord,
        contentId: String,
        fallbackTitle: String,
    ): DownloadSidecar {
        val detail = when (val r = catalogRepository.getItemDetail(contentId)) {
            is ApiResult.Success -> r.data
            else -> null
        }
        val version = detail?.versionFor(record.mediaFileId)
        return DownloadSidecar(
            record = record,
            title = detail?.title ?: fallbackTitle,
            posterUrl = detail?.posterUrl,
            posterThumbhash = detail?.posterThumbhash,
            year = detail?.year?.takeIf { it > 0 },
            seriesTitle = detail?.seriesTitle,
            // Stable TV grouping key — present for episode detail downloads via the
            // main detail button (this path), so they group like startEpisode's.
            seriesContentId = detail?.seriesId,
            seasonNumber = detail?.seasonNumber,
            episodeNumber = detail?.episodeNumber,
            subtitle = detail?.let { d ->
                when {
                    d.seriesTitle != null && d.seasonNumber != null && d.episodeNumber != null ->
                        "${d.seriesTitle} · S${d.seasonNumber}E${d.episodeNumber}"
                    d.year > 0 -> "${d.year}"
                    else -> null
                }
            },
            fileName = version?.fileName,
            container = version?.container,
            mediaType = DownloadMediaType.fromCatalogType(detail?.type).wire,
            overview = detail?.overview,
            // Author drives the Downloads-tab books tiering (author → book) — capture
            // it for ebooks too, not just audiobooks.
            author = detail?.audiobook?.authorNames ?: detail?.ebook?.authorNames,
            narrator = detail?.audiobook?.narratorNames,
            // Truthful PER-FILE duration: a download is one file, and for a
            // multi-part audiobook the offline player treats this as the
            // stream's length — the whole-book total would run the slider and
            // resume in whole-book space against a single part's bytes. Fall
            // back to the audiobook total only when the file has no probed
            // duration.
            durationSeconds = version?.duration?.takeIf { it > 0.0 }
                ?: detail?.audiobook?.totalDurationSeconds?.toDouble(),
            chapters = version?.chapters,
            updatedAtMs = System.currentTimeMillis(),
        )
    }

    private fun ItemDetail.versionFor(fileId: Int): FileVersion? =
        versions.firstOrNull { it.fileId == fileId }

    /** Cancel a queued / in-flight download by record id (worker-tag). */
    fun cancel(downloadId: String) {
        Log.i(TAG, "cancel: downloadId=$downloadId")
        DownloadWorker.cancel(context, downloadId)
    }

    companion object {
        private const val TAG = "DownloadEnqueuer"
        const val DEFAULT_SERVER_ID = "default"
        const val DEFAULT_PROFILE_ID = "default"
    }
}
