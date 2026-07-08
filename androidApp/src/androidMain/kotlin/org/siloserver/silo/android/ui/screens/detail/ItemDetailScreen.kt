package org.siloserver.silo.android.ui.screens.detail

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.siloserver.silo.android.downloads.LEGACY_PUBLIC_DOWNLOAD_PERMISSION
import org.siloserver.silo.android.downloads.hasLegacyPublicDownloadPermission
import org.siloserver.silo.android.ui.components.DetailLoadingSkeleton
import org.siloserver.silo.android.ui.components.ErrorView
import org.siloserver.silo.android.ui.screens.cast.SiloCastTargetPickerSheet
import org.siloserver.silo.android.ui.screens.downloads.openDownloadTargetInExternalApp
import org.siloserver.silo.android.ui.util.playbackResumePosition
import org.siloserver.silo.cast.SiloCastLaunchRequest
import org.siloserver.silo.cast.SiloCastPlaybackRequest
import org.siloserver.silo.common.downloads.DownloadEnqueuer
import org.siloserver.silo.common.downloads.DownloadOpenTarget
import org.siloserver.silo.common.downloads.DownloadStorage
import org.siloserver.silo.model.catalog.FileVersion
import org.siloserver.silo.model.catalog.isAudiobookItemType
import org.siloserver.silo.model.catalog.isBookLikeItemType
import org.siloserver.silo.model.ebook.chooseEbookVersion
import org.siloserver.silo.model.ebook.isInAppReadableEbookVersion
import org.siloserver.silo.model.ebook.isSupportedEbookVersion
import org.siloserver.silo.model.download.DownloadQuality
import org.siloserver.silo.model.feature.CLIENT_WATCH_TOGETHER_SURFACE_ENABLED
import org.siloserver.silo.network.ServerRegistry
import org.koin.compose.koinInject
import org.siloserver.silo.metadata.DescriptionTranslationPhase
import org.siloserver.silo.model.feature.MetadataAiFeatureStore
import org.siloserver.silo.model.metadata.MetadataAiOnView

private const val PLAY_ON_DEVICE_LABEL = "Play on device"

/**
 * Item detail dispatcher. Routes to [MovieDetailContent] or
 * [SeriesDetailContent] based on the item type, with the back button
 * floating over the hero so the artwork extends edge-to-edge.
 *
 * Mirrors `ItemDetailView.swift`'s phone path:
 *   - hero ignores top safe area
 *   - transparent back button overlay tinted onto the artwork
 *   - loading / error / content states
 */
@Composable
fun ItemDetailScreen(
    onBackClick: () -> Unit,
    onPlayClick: (String, Int?, Int?, Int?, Double?) -> Unit,
    onItemDetailClick: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onSeasonClick: (String, Int) -> Unit,
    onAudiobookPlayClick: (contentId: String, fileId: Int?, fromStart: Boolean) -> Unit = { _, _, _ -> },
    onBookReadClick: (String, Int?) -> Unit = { _, _ -> },
    onWatchTogether: (String, Int?) -> Unit = { _, _ -> },
    viewModel: ItemDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Refresh on return (e.g. backing out of the player): the ViewModel loads
    // once in init, so without this the Play button keeps the resume label
    // computed before playback. Fires on every ON_RESUME; no first-entry
    // guard — the composable is recreated on back-stack pop, so any
    // effect-local "skip the first" flag would reset and swallow exactly the
    // resume we care about. refreshOnReturn() no-ops while detail is still
    // null, which covers the initial load.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOnReturn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val downloadStorage: DownloadStorage = koinInject()
    val serverRegistry: ServerRegistry = koinInject()
    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingDownloadQualityAction by remember { mutableStateOf<((DownloadQuality) -> Unit)?>(null) }
    var pendingDownloadEstimate by remember {
        mutableStateOf<org.siloserver.silo.model.download.DownloadSizeEstimate?>(null)
    }
    var showDownloadQualityPicker by remember { mutableStateOf(false) }
    var pendingSiloCastLaunchRequest by remember { mutableStateOf<SiloCastLaunchRequest?>(null) }
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingDownloadAction
        pendingDownloadAction = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(
                context,
                "Storage permission is required to save public downloads on this Android version.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun runDownloadAction(requirePermission: Boolean = true, action: () -> Unit) {
        if (!requirePermission || hasLegacyPublicDownloadPermission(context)) {
            action()
            return
        }
        pendingDownloadAction = action
        legacyStoragePermissionLauncher.launch(LEGACY_PUBLIC_DOWNLOAD_PERMISSION)
    }

    fun runDownloadQualityAction(
        requirePermission: Boolean = true,
        estimate: org.siloserver.silo.model.download.DownloadSizeEstimate? = null,
        action: (DownloadQuality) -> Unit,
    ) {
        runDownloadAction(requirePermission = requirePermission) {
            pendingDownloadQualityAction = action
            pendingDownloadEstimate = estimate
            showDownloadQualityPicker = true
        }
    }

    fun runDownloadTap(
        downloadState: DetailDownloadState,
        directAction: () -> Unit,
        qualityAction: (DownloadQuality) -> Unit,
        estimate: org.siloserver.silo.model.download.DownloadSizeEstimate? = null,
    ) {
        if (!downloadState.isDownloaded && downloadState.progress == null) {
            runDownloadQualityAction(requirePermission = true, estimate = estimate, action = qualityAction)
        } else {
            runDownloadAction(requirePermission = false, action = directAction)
        }
    }

    fun localDownloadFor(fileId: Int) =
        downloadStorage.locateLocalMedia(
            serverId = serverRegistry.activeServerId.value ?: DownloadEnqueuer.DEFAULT_SERVER_ID,
            profileId = serverRegistry.activeEntry.value?.profileId ?: DownloadEnqueuer.DEFAULT_PROFILE_ID,
            fileId = fileId,
        )

    fun openExternalDownload(version: FileVersion, displayTitle: String) {
        val local = localDownloadFor(version.fileId)
        val target = DownloadOpenTarget.from(
            isComplete = local != null,
            localUri = local?.uriString,
            displayName = local?.displayName ?: version.fileName ?: displayTitle,
            container = version.container,
        )
        if (target == null || !openDownloadTargetInExternalApp(context, target)) {
            Toast.makeText(context, "No app found to open this file.", Toast.LENGTH_LONG).show()
        }
    }

    // Launch shape mirrors Apple's SiloControlLaunchRequest: serverId +
    // nested playback request. A missing active server produces no request —
    // the receiver would reject it as a server mismatch anyway.
    fun videoCastRequest(
        contentId: String,
        title: String,
        subtitle: String? = null,
        fileId: Int? = null,
        audioTrackIndex: Int? = null,
        subtitleTrackIndex: Int? = null,
        resumePositionSeconds: Double? = null,
    ): SiloCastLaunchRequest? {
        val serverId = serverRegistry.activeServerId.value ?: return null
        return SiloCastLaunchRequest(
            serverId = serverId,
            playback = SiloCastPlaybackRequest(
                contentId = contentId,
                fileId = fileId,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                startFromBeginning = resumePositionSeconds == null,
                resumePosition = resumePositionSeconds,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.isLoading && state.detail == null -> {
                DetailLoadingSkeleton()
            }

            state.error != null && state.detail == null -> {
                ErrorView(
                    message = state.error ?: "Something went wrong",
                    onRetry = { viewModel.loadDetail() },
                )
            }

            state.detail != null -> {
                val detail = state.detail!!
                val metadataAiStore: MetadataAiFeatureStore = koinInject()
                val metadataAiStatus by metadataAiStore.status.collectAsState()
                val translationPhase by viewModel.translationPhase.collectAsState()
                val translationEligible = metadataAiStatus.onView != MetadataAiOnView.Off &&
                    detail.pendingTranslationLanguage != null
                // `auto` on-view mode fires once per (content, language); the
                // controller latches so recompositions can't re-queue jobs.
                LaunchedEffect(detail.contentId, detail.pendingTranslationLanguage, metadataAiStatus.onView) {
                    if (metadataAiStatus.onView == MetadataAiOnView.Auto &&
                        detail.pendingTranslationLanguage != null
                    ) {
                        viewModel.translateDescription(auto = true)
                    }
                }
                val translationSlot: (@Composable () -> Unit)? = if (translationEligible &&
                    (metadataAiStatus.onView == MetadataAiOnView.Button ||
                        translationPhase != DescriptionTranslationPhase.Idle)
                ) {
                    {
                        DescriptionTranslationSection(
                            phase = translationPhase,
                            onTranslate = { viewModel.translateDescription() },
                        )
                    }
                } else {
                    null
                }
                val effectiveSelectedVersionIndex = if (state.hasExplicitVersionSelection) {
                    state.selectedVersionIndex
                } else {
                    detail.userData?.lastFileId
                        ?.let { lastFileId ->
                            detail.versions.indexOfFirst { it.fileId == lastFileId }
                                .takeIf { it >= 0 }
                        }
                        ?: state.selectedVersionIndex
                }
                val explicitFileId = detail.versions
                    .getOrNull(effectiveSelectedVersionIndex)
                    ?.fileId
                    ?.takeIf { state.hasExplicitVersionSelection }
                val explicitAudioIndex = state.selectedAudioIndex
                    .takeIf { state.hasExplicitAudioSelection }
                val explicitSubtitleIndex = state.selectedSubtitleIndex
                    .takeIf { state.hasExplicitSubtitleSelection }
                val playbackFileId = explicitFileId ?: detail.versions
                    .getOrNull(effectiveSelectedVersionIndex)
                    ?.fileId
                    ?.takeIf { state.hasExplicitAudioSelection || state.hasExplicitSubtitleSelection }
                val effectiveAudiobookFileId = detail.versions
                    .getOrNull(effectiveSelectedVersionIndex)
                    ?.fileId

                when {
                    isAudiobookItemType(detail.type) -> {
                        val downloadRecords by viewModel.downloads.collectAsState()
                        val audiobookVersion = effectiveAudiobookFileId
                            ?.let { fileId -> detail.versions.firstOrNull { it.fileId == fileId } }
                            ?: detail.versions.firstOrNull()
                        val audiobookLocalDownload = audiobookVersion?.let { version ->
                            localDownloadFor(version.fileId)
                        }
                        val downloadState = detailDownloadStateFor(
                            version = audiobookVersion,
                            records = downloadRecords,
                            hasLocalMedia = audiobookVersion?.let { audiobookLocalDownload != null },
                        )

                        org.siloserver.silo.android.ui.screens.audiobook.AudiobookDetailContent(
                            detail = detail,
                            isFavorite = state.isFavorite,
                            isInWatchlist = state.isInWatchlist,
                            selectedFileId = effectiveAudiobookFileId,
                            isDownloaded = downloadState.isDownloaded,
                            downloadProgress = downloadState.progress,
                            onPlayClick = { fileId ->
                                onAudiobookPlayClick(detail.contentId, fileId, false)
                            },
                            onPlayFromStartClick = { fileId ->
                                onAudiobookPlayClick(detail.contentId, fileId, true)
                            },
                            onChapterClick = { _ ->
                                onAudiobookPlayClick(detail.contentId, effectiveAudiobookFileId, false)
                            },
                            onFavoriteClick = { viewModel.toggleFavorite() },
                            onWatchlistClick = { viewModel.toggleWatchlist() },
                            onDownloadClick = audiobookVersion?.let { version ->
                                {
                                    runDownloadTap(
                                        downloadState = downloadState,
                                        directAction = {
                                            viewModel.onDownloadTapped(
                                                version,
                                                detail.title,
                                                forceRedownloadMissingLocal = downloadState.needsLocalRecovery,
                                            )
                                        },
                                        qualityAction = { quality ->
                                            viewModel.onDownloadTapped(
                                                version,
                                                detail.title,
                                                forceRedownloadMissingLocal = downloadState.needsLocalRecovery,
                                                downloadQuality = quality,
                                            )
                                        },
                                        estimate = org.siloserver.silo.model.download.DownloadSizeEstimate
                                            .estimate(versions = listOf(version), fileId = version.fileId),
                                    )
                                }
                            },
                        )
                    }

                    isBookLikeItemType(detail.type) -> {
                        val selectedBookVersion = if (state.hasExplicitVersionSelection) {
                            detail.versions.getOrNull(state.selectedVersionIndex)
                        } else {
                            chooseEbookVersion(detail.versions, requestedFileId = detail.userData?.lastFileId)
                                ?: detail.versions.firstOrNull { it.isSupportedEbookVersion() }
                                ?: detail.versions.firstOrNull()
                        }
                        val selectedBookVersionIndex = selectedBookVersion
                            ?.let { version -> detail.versions.indexOfFirst { it.fileId == version.fileId } }
                            ?.takeIf { it >= 0 }
                            ?: 0
                        val selectedBookLocalDownload = selectedBookVersion?.let { version ->
                            localDownloadFor(version.fileId)
                        }
                        val downloadRecords by viewModel.downloads.collectAsState()
                        val downloadState = detailDownloadStateFor(
                            version = selectedBookVersion,
                            records = downloadRecords,
                            hasLocalMedia = selectedBookVersion?.let { selectedBookLocalDownload != null },
                        )

                        org.siloserver.silo.android.ui.screens.book.BookDetailContent(
                            detail = detail,
                            isFavorite = state.isFavorite,
                            isInWatchlist = state.isInWatchlist,
                            selectedVersionIndex = selectedBookVersionIndex,
                            onVersionSelected = { viewModel.selectVersion(it) },
                            canReadSelectedVersion = selectedBookVersion
                                ?.isInAppReadableEbookVersion(state.kindleConversionAvailable) == true,
                            isDownloaded = downloadState.isDownloaded,
                            downloadProgress = downloadState.progress,
                            onReadClick = { fileId -> onBookReadClick(detail.contentId, fileId) },
                            onFavoriteClick = { viewModel.toggleFavorite() },
                            onWatchlistClick = { viewModel.toggleWatchlist() },
                            onDownloadClick = selectedBookVersion?.takeIf { it.isSupportedEbookVersion() }?.let { version ->
                                {
                                    runDownloadTap(
                                        downloadState = downloadState,
                                        directAction = {
                                            viewModel.onDownloadTapped(
                                                version,
                                                detail.title,
                                                forceRedownloadMissingLocal = downloadState.needsLocalRecovery,
                                            )
                                        },
                                        qualityAction = { quality ->
                                            viewModel.onDownloadTapped(
                                                version,
                                                detail.title,
                                                forceRedownloadMissingLocal = downloadState.needsLocalRecovery,
                                                downloadQuality = quality,
                                            )
                                        },
                                        estimate = org.siloserver.silo.model.download.DownloadSizeEstimate
                                            .estimate(versions = listOf(version), fileId = version.fileId),
                                    )
                                }
                            },
                            onOpenExternalClick = selectedBookVersion
                                ?.takeIf {
                                    !it.isInAppReadableEbookVersion(state.kindleConversionAvailable) &&
                                        downloadState.isDownloaded &&
                                        selectedBookLocalDownload != null
                                }
                                ?.let { version ->
                                    { openExternalDownload(version, detail.title) }
                                },
                        )
                    }

                    detail.type == "series" -> {
                        val nextEpisode = state.episodes.firstOrNull { ep ->
                            ep.userData?.played != true
                        } ?: state.episodes.firstOrNull()
                        val nextEpisodeLabel = nextEpisode?.let { ep ->
                            "S${ep.seasonNumber}·E${ep.episodeNumber}"
                        }
                        val episodeDownloadRecords by viewModel.downloads.collectAsState()
                        // Series-level roll-up across ALL seasons (state.allEpisodeFileIds
                        // is loaded in the background): ✓ only when every episode is
                        // downloaded; a partial fraction otherwise.
                        val seriesDownloadState = remember(
                            episodeDownloadRecords,
                            state.allEpisodeFileIds,
                            state.allEpisodeIdsComplete,
                        ) {
                            val ids = state.allEpisodeFileIds
                            if (ids.isEmpty()) {
                                DetailDownloadState()
                            } else {
                                val downloaded = ids.count {
                                    detailDownloadStateForFile(it, episodeDownloadRecords).isDownloaded
                                }
                                // ✓ only when every season loaded AND every episode is
                                // downloaded; otherwise show a partial fraction.
                                val allDone = state.allEpisodeIdsComplete && downloaded == ids.size
                                DetailDownloadState(
                                    isDownloaded = allDone,
                                    progress = if (!allDone && downloaded > 0) {
                                        downloaded.toFloat() / ids.size
                                    } else {
                                        null
                                    },
                                )
                            }
                        }

                        SeriesDetailContent(
                            translation = translationSlot,
                            detail = detail,
                            seasons = state.seasons,
                            selectedSeasonNumber = state.selectedSeasonNumber,
                            episodes = state.episodes,
                            isLoadingEpisodes = state.isLoadingEpisodes,
                            isFavorite = state.isFavorite,
                            isInWatchlist = state.isInWatchlist,
                            nextEpisodeLabel = nextEpisodeLabel,
                            onPlayClick = {
                                nextEpisode?.let {
                                    onPlayClick(it.contentId, null, null, null, playbackResumePosition(it))
                                } ?: onPlayClick(
                                    detail.contentId,
                                    null,
                                    null,
                                    null,
                                    playbackResumePosition(detail.userData),
                                )
                            },
                            onEpisodePlayClick = { contentId, resumePositionSeconds ->
                                onPlayClick(contentId, null, null, null, resumePositionSeconds)
                            },
                            onEpisodeDetailClick = onItemDetailClick,
                            onSeasonSelected = { viewModel.selectSeason(it) },
                            onFavoriteClick = { viewModel.toggleFavorite() },
                            onWatchlistClick = { viewModel.toggleWatchlist() },
                            onToggleWatched = { viewModel.toggleWatched() },
                            userRating = state.userRating,
                            onSetRating = { viewModel.setRating(it) },
                            onClearRating = { viewModel.clearRating() },
                            onPersonClick = onPersonClick,
                            onItemDetailClick = onItemDetailClick,
                            onSeriesDownloadClick = {
                                if (seriesDownloadState.isDownloaded) {
                                    runDownloadAction(requirePermission = false) {
                                        viewModel.onSeriesDownloadTapped()
                                    }
                                } else {
                                    runDownloadQualityAction { quality ->
                                        viewModel.onSeriesDownloadTapped(downloadQuality = quality)
                                    }
                                }
                            },
                            onSeasonDownloadClick = { season ->
                                runDownloadQualityAction { quality ->
                                    viewModel.onSeasonDownloadTapped(season, downloadQuality = quality)
                                }
                            },
                            onEpisodeDownloadClick = { ep ->
                                val episodeState = detailDownloadStateForFile(
                                    fileId = ep.files.firstOrNull()?.fileId,
                                    records = episodeDownloadRecords,
                                )
                                runDownloadTap(
                                    downloadState = episodeState,
                                    directAction = { viewModel.onEpisodeDownloadTapped(ep) },
                                    qualityAction = { quality ->
                                        viewModel.onEpisodeDownloadTapped(ep, downloadQuality = quality)
                                    },
                                    estimate = org.siloserver.silo.model.download.DownloadSizeEstimate
                                        .estimate(fileSizes = ep.files.map { it.fileSize }),
                                )
                            },
                            episodeDownloadState = { ep ->
                                detailDownloadStateForFile(
                                    fileId = ep.files.firstOrNull()?.fileId,
                                    records = episodeDownloadRecords,
                                )
                            },
                            seriesDownloadState = seriesDownloadState,
                            playOnDeviceLabel = PLAY_ON_DEVICE_LABEL,
                            onPlayOnDevice = {
                                val castContentId = nextEpisode?.contentId ?: detail.contentId
                                pendingSiloCastLaunchRequest = videoCastRequest(
                                    contentId = castContentId,
                                    title = nextEpisode?.title ?: detail.title,
                                    subtitle = nextEpisodeLabel,
                                    resumePositionSeconds = nextEpisode
                                        ?.let { playbackResumePosition(it) }
                                        ?: playbackResumePosition(detail.userData),
                                )
                            },
                            onWatchTogether = if (CLIENT_WATCH_TOGETHER_SURFACE_ENABLED) {
                                { onWatchTogether(nextEpisode?.contentId ?: detail.contentId, null) }
                            } else {
                                null
                            },
                        )
                    }

                    else -> {
                        val seriesId = detail.seriesId
                        val seasonNumber = detail.seasonNumber
                        // Derive download state for the currently-selected
                        // version. Re-reads on every UI emission so the
                        // worker's upsertLocal progress + status transitions
                        // flow through to the DownloadButton.
                        val downloadRecords by viewModel.downloads.collectAsState()
                        val selectedVersion = detail.versions.getOrNull(effectiveSelectedVersionIndex)
                        val selectedLocalDownload = selectedVersion?.let { version ->
                            localDownloadFor(version.fileId)
                        }
                        val downloadState = detailDownloadStateFor(
                            version = selectedVersion,
                            records = downloadRecords,
                            hasLocalMedia = selectedVersion?.let { selectedLocalDownload != null },
                        )

                        MovieDetailContent(
                            translation = translationSlot,
                            detail = detail,
                            isFavorite = state.isFavorite,
                            isInWatchlist = state.isInWatchlist,
                            selectedVersionIndex = effectiveSelectedVersionIndex,
                            selectedAudioIndex = state.selectedAudioIndex,
                            selectedSubtitleIndex = state.selectedSubtitleIndex,
                            onPlayClick = {
                                onPlayClick(
                                    detail.contentId,
                                    playbackFileId,
                                    explicitAudioIndex,
                                    explicitSubtitleIndex,
                                    playbackResumePosition(detail.userData),
                                )
                            },
                            onFavoriteClick = { viewModel.toggleFavorite() },
                            onWatchlistClick = { viewModel.toggleWatchlist() },
                            onToggleWatched = { viewModel.toggleWatched() },
                            userRating = state.userRating,
                            onSetRating = { viewModel.setRating(it) },
                            onClearRating = { viewModel.clearRating() },
                            onVersionSelected = { viewModel.selectVersion(it) },
                            onAudioSelected = { viewModel.selectAudioTrack(it) },
                            onSubtitleSelected = { viewModel.selectSubtitle(it) },
                            onPersonClick = onPersonClick,
                            onItemDetailClick = onItemDetailClick,
                            onSeriesClick = seriesId?.let { resolvedSeriesId ->
                                { onSeriesClick(resolvedSeriesId) }
                            },
                            onSeasonClick = if (seriesId != null && seasonNumber != null) {
                                { onSeasonClick(seriesId, seasonNumber) }
                            } else {
                                null
                            },
                            isDownloaded = downloadState.isDownloaded,
                            downloadProgress = downloadState.progress,
                            playOnDeviceLabel = PLAY_ON_DEVICE_LABEL,
                            onDownloadTapped = selectedVersion?.let { v ->
                                {
                                    runDownloadTap(
                                        downloadState = downloadState,
                                        directAction = {
                                            viewModel.onDownloadTapped(
                                                v,
                                                detail.title,
                                                forceRedownloadMissingLocal = downloadState.needsLocalRecovery,
                                            )
                                        },
                                        qualityAction = { quality ->
                                            viewModel.onDownloadTapped(
                                                v,
                                                detail.title,
                                                forceRedownloadMissingLocal = downloadState.needsLocalRecovery,
                                                downloadQuality = quality,
                                            )
                                        },
                                        estimate = org.siloserver.silo.model.download.DownloadSizeEstimate
                                            .estimate(versions = listOf(v), fileId = v.fileId),
                                    )
                                }
                            },
                            onPlayOnDevice = {
                                pendingSiloCastLaunchRequest = videoCastRequest(
                                    contentId = detail.contentId,
                                    title = detail.title,
                                    fileId = playbackFileId,
                                    audioTrackIndex = explicitAudioIndex,
                                    subtitleTrackIndex = explicitSubtitleIndex,
                                    resumePositionSeconds = playbackResumePosition(detail.userData),
                                )
                            },
                            onWatchTogether = if (CLIENT_WATCH_TOGETHER_SURFACE_ENABLED) {
                                { onWatchTogether(detail.contentId, explicitFileId) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        if (showDownloadQualityPicker) {
            DownloadQualityPickerSheet(
                onQualitySelected = { quality ->
                    pendingDownloadQualityAction?.let { action ->
                        val runSelectedQuality: (DownloadQuality) -> Unit = { quality -> action(quality) }
                        runSelectedQuality(quality)
                    }
                    pendingDownloadQualityAction = null
                    pendingDownloadEstimate = null
                    showDownloadQualityPicker = false
                },
                onDismiss = {
                    pendingDownloadQualityAction = null
                    pendingDownloadEstimate = null
                    showDownloadQualityPicker = false
                },
                estimate = pendingDownloadEstimate,
                availableBytes = remember { downloadStorage.usableSpaceBytes() },
            )
        }

        pendingSiloCastLaunchRequest?.let { request ->
            SiloCastTargetPickerSheet(
                launchRequest = request,
                onDismiss = { pendingSiloCastLaunchRequest = null },
            )
        }

        // Floating back button — sits on the hero artwork without
        // pushing content down, mirroring iOS's transparent nav bar.
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
    }
}
