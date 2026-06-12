package com.continuum.app.android.ui.screens.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.continuum.app.common.downloads.OfflineMedia
import com.continuum.app.common.downloads.OfflineMediaResolver
import com.continuum.app.common.downloads.DownloadEnqueuer
import com.continuum.app.common.ebook.EbookLocalStateStore
import com.continuum.app.common.ebook.ReaderCapabilities
import com.continuum.app.common.ebook.ReaderDisplaySettings
import com.continuum.app.common.ebook.ReaderSection
import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.catalog.FileVersion
import com.continuum.app.model.ebook.EbookAnnotation
import com.continuum.app.model.ebook.SaveEbookProgressRequest
import com.continuum.app.model.ebook.bookFormatFromEbookVersion
import com.continuum.app.model.ebook.chooseEbookVersion
import com.continuum.app.model.ebook.ebookPageNumberFromProgressLocation
import com.continuum.app.model.ebook.ebookProgressPercentForPage
import com.continuum.app.model.ebook.isInAppReadableEbookVersion
import com.continuum.app.model.ebook.localBookmarkAnnotation
import com.continuum.app.network.ApiResult
import com.continuum.app.network.ServerRegistry
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.EbookReaderRepository
import com.continuum.app.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared state for the book reader. The [ReaderScreen] reads [format]
 * and dispatches to the right renderer (EPUB, PDF, CBZ/CBR). All
 * renderers share the same VM so position-persistence, font/theme
 * settings, and library back-navigation work the same regardless of
 * format.
 */
data class ReaderUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val author: String? = null,
    val format: BookFormat = BookFormat.Unknown,
    val fileUrl: String? = null,
    val localUri: String? = null,
    val localDisplayName: String? = null,
    val fileId: Int? = null,
    val pageCount: Int? = null,
    /** 0-based current page for fixed-layout (PDF / CBZ / CBR). For
     *  EPUBs the unit is renderer-defined (CFI or chapter index). */
    val currentPage: Int = 0,
    val progressLocation: String? = null,
    val progressPercent: Double = 0.0,
    val bookmarks: List<EbookAnnotation> = emptyList(),
    val capabilities: ReaderCapabilities = ReaderCapabilities.forFormat(BookFormat.Unknown),
    val displaySettings: ReaderDisplaySettings = ReaderDisplaySettings(),
    val sections: List<ReaderSection> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val error: String? = null,
)

private data class InitialReaderState(
    val currentPage: Int? = null,
    val progressLocation: String? = null,
    val progressPercent: Double? = null,
    val bookmarks: List<EbookAnnotation>? = null,
)

class ReaderViewModel(
    private val catalogRepository: CatalogRepository,
    private val ebookReaderRepository: EbookReaderRepository,
    private val offlineMediaResolver: OfflineMediaResolver,
    private val localStateStore: EbookLocalStateStore,
    private val serverRegistry: ServerRegistry,
    private val profileRepository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val contentId: String = savedStateHandle.get<String>("contentId") ?: ""
    private val requestedFileId: Int? = savedStateHandle.get<String>("fileId")?.toIntOrNull()
    private var shouldSuppressInitialPageChange = false
    private var progressSaveJob: Job? = null

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        if (contentId.isNotBlank()) loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            when (val r = catalogRepository.getItemDetail(contentId)) {
                is ApiResult.Success -> {
                    val d = r.data
                    val version = chooseEbookVersion(d.versions, requestedFileId)
                    if (version == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                title = d.title,
                                author = d.ebook?.authorNames ?: d.book?.author,
                                error = "No supported ebook file is available.",
                            )
                        }
                        return@launch
                    }
                    val offlineMedia = offlineMediaResolver.findLocalMedia(
                        contentId = d.contentId,
                        requestedFileId = version.fileId,
                        allowFallback = requestedFileId == null,
                    )
                    val readerState = loadReaderState(version.fileId)
                    val displaySettings = loadDisplaySettings()
                    shouldSuppressInitialPageChange = readerState.progressLocation != null
                    val format = version.bookFormatFromEbookVersion()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = d.title,
                            author = d.ebook?.authorNames ?: d.book?.author,
                            format = format,
                            fileUrl = offlineMedia?.fileUrl ?: ebookReaderRepository.readPath(d.contentId, version.fileId),
                            localUri = offlineMedia?.uriString,
                            localDisplayName = offlineMedia?.displayName,
                            fileId = version.fileId,
                            pageCount = d.book?.pageCount,
                            capabilities = ReaderCapabilities.forFormat(format),
                            currentPage = readerState.currentPage ?: it.currentPage,
                            progressLocation = readerState.progressLocation,
                            progressPercent = readerState.progressPercent ?: it.progressPercent,
                            bookmarks = readerState.bookmarks ?: it.bookmarks,
                            displaySettings = displaySettings,
                        )
                    }
                }
                is ApiResult.Error -> loadOfflineOnly(error = r.message)
                is ApiResult.NetworkError -> loadOfflineOnly(error = r.exception.message)
            }
        }
    }

    private suspend fun loadOfflineOnly(error: String?) {
        val media = offlineMediaResolver.findLocalMedia(
            contentId = contentId,
            requestedFileId = requestedFileId,
            allowFallback = requestedFileId == null,
        )
        if (media == null) {
            _uiState.update { it.copy(isLoading = false, error = error) }
            return
        }
        val version = media.toFileVersion()
        val format = version.bookFormatFromEbookVersion()
        if (!version.isInAppReadableEbookVersion() || format == BookFormat.Unknown) {
            _uiState.update { it.copy(isLoading = false, error = "Downloaded ebook format is not supported.") }
            return
        }
        val readerState = loadLocalReaderState(media.fileId)
        val displaySettings = loadDisplaySettings()
        shouldSuppressInitialPageChange = readerState.progressLocation != null
        _uiState.update {
            it.copy(
                isLoading = false,
                title = media.sidecar.title,
                author = null,
                format = format,
                fileUrl = media.fileUrl,
                localUri = media.uriString,
                localDisplayName = media.displayName,
                fileId = media.fileId,
                pageCount = null,
                capabilities = ReaderCapabilities.forFormat(format),
                currentPage = readerState.currentPage ?: it.currentPage,
                progressLocation = readerState.progressLocation,
                progressPercent = readerState.progressPercent ?: it.progressPercent,
                bookmarks = readerState.bookmarks ?: it.bookmarks,
                displaySettings = displaySettings,
                error = null,
            )
        }
    }

    private suspend fun loadReaderState(fileId: Int): InitialReaderState {
        var currentPage: Int? = null
        var progressLocation: String? = null
        var progressPercent: Double? = null
        var bookmarks: List<EbookAnnotation>? = null

        val local = loadLocalReaderState(fileId)
        currentPage = local.currentPage
        progressLocation = local.progressLocation
        progressPercent = local.progressPercent
        bookmarks = local.bookmarks

        when (val progress = ebookReaderRepository.getProgress(contentId)) {
            is ApiResult.Success -> {
                if (progress.data.fileId == fileId && local.progressLocation == null) {
                    progressLocation = progress.data.location
                    progressPercent = progress.data.progress.coerceIn(0.0, 1.0)
                    currentPage = ebookPageNumberFromProgressLocation(progress.data.location)
                }
            }
            else -> Unit
        }

        when (val annotations = ebookReaderRepository.listAnnotations(contentId)) {
            is ApiResult.Success -> {
                val serverBookmarks = annotations.data.items.filter { annotation -> annotation.kind == "bookmark" }
                val serverLocations = serverBookmarks.mapNotNull { annotation -> annotation.location }.toSet()
                val staleLocalBookmarks = bookmarks.orEmpty().filter { annotation ->
                    annotation.id.startsWith("local-") &&
                        annotation.location != null &&
                        annotation.location in serverLocations
                }
                if (staleLocalBookmarks.isNotEmpty()) {
                    val (serverId, profileId) = resolveScope()
                    withContext(Dispatchers.IO) {
                        staleLocalBookmarks.forEach { annotation ->
                            localStateStore.removeBookmark(serverId, profileId, contentId, annotation.id)
                        }
                    }
                }
                val currentLocalBookmarks = bookmarks.orEmpty()
                    .filterNot { annotation -> staleLocalBookmarks.any { stale -> stale.id == annotation.id } }
                bookmarks = (currentLocalBookmarks + serverBookmarks)
                    .distinctBy { annotation -> annotation.id }
                    .takeIf { merged -> merged.isNotEmpty() }
            }
            else -> Unit
        }

        return InitialReaderState(
            currentPage = currentPage,
            progressLocation = progressLocation,
            progressPercent = progressPercent,
            bookmarks = bookmarks,
        )
    }

    private suspend fun loadLocalReaderState(fileId: Int): InitialReaderState {
        val (serverId, profileId) = resolveScope()
        val progress = withContext(Dispatchers.IO) {
            localStateStore.readProgress(serverId, profileId, contentId)
        }?.takeIf { it.fileId == fileId }
        val bookmarks = withContext(Dispatchers.IO) {
            localStateStore.listBookmarks(serverId, profileId, contentId)
        }.map { it.toAnnotation() }
        return InitialReaderState(
            currentPage = ebookPageNumberFromProgressLocation(progress?.location),
            progressLocation = progress?.location,
            progressPercent = progress?.progress,
            bookmarks = bookmarks.takeIf { it.isNotEmpty() },
        )
    }

    private suspend fun loadDisplaySettings(): ReaderDisplaySettings {
        val (serverId, profileId) = resolveScope()
        return withContext(Dispatchers.IO) {
            localStateStore.readDisplaySettings(serverId, profileId)
        } ?: ReaderDisplaySettings()
    }

    fun onPageChanged(page: Int) {
        val state = _uiState.value
        val fileId = state.fileId ?: return
        val normalizedPage = page.coerceAtLeast(0)
        if (shouldSuppressInitialPageChange && normalizedPage == state.currentPage) {
            shouldSuppressInitialPageChange = false
            return
        }
        shouldSuppressInitialPageChange = false
        val location = "page:$normalizedPage"
        val progressPercent = ebookProgressPercentForPage(
            page = normalizedPage,
            pageCount = state.pageCount,
            currentProgress = state.progressPercent,
        )
        _uiState.update {
            it.copy(
                currentPage = normalizedPage,
                progressLocation = location,
                progressPercent = progressPercent,
            )
        }
        persistProgress(fileId = fileId, location = location, progressPercent = progressPercent)
    }

    fun onPageCountKnown(count: Int) {
        if (count <= 0) return
        _uiState.update { it.copy(pageCount = count) }
    }

    /**
     * Persist a reflowable-reader locator. The reflowable engine reports an
     * opaque [locationJson] string and a 0..1 book-level [progress]; we store
     * both through the same local-then-server path as page-based progress so
     * the shipped [EbookProgressSyncer] carries it offline->online.
     */
    fun onLocatorChanged(locationJson: String, progress: Double) {
        val state = _uiState.value
        val fileId = state.fileId ?: return
        val clampedProgress = progress.coerceIn(0.0, 1.0)
        shouldSuppressInitialPageChange = false
        _uiState.update {
            it.copy(
                progressLocation = locationJson,
                progressPercent = clampedProgress,
            )
        }
        persistProgress(fileId = fileId, location = locationJson, progressPercent = clampedProgress)
    }

    /**
     * Persist the given reading position locally and (lazily) to the server,
     * cancelling any in-flight save. Shared by page-based ([onPageChanged]) and
     * reflowable-locator ([onLocatorChanged]) progress reporting.
     */
    private fun persistProgress(fileId: Int, location: String, progressPercent: Double) {
        progressSaveJob?.cancel()
        _uiState.update { it.copy(isSyncing = true, syncError = null) }
        viewModelScope.launch {
            val (serverId, profileId) = resolveScope()
            withContext(Dispatchers.IO) {
                localStateStore.writeProgress(
                    serverId,
                    profileId,
                    contentId,
                    EbookLocalStateStore.ProgressSnapshot(
                        fileId = fileId,
                        location = location,
                        progress = progressPercent,
                        updatedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
        }
        val saveJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            when (ebookReaderRepository.saveProgress(
                contentId,
                SaveEbookProgressRequest(
                    fileId = fileId,
                    location = location,
                    progress = progressPercent,
                ),
            )) {
                is ApiResult.Success -> {
                    if (progressSaveJob === coroutineContext[Job]) {
                        _uiState.update { it.copy(isSyncing = false) }
                    }
                }
                else -> {
                    if (progressSaveJob === coroutineContext[Job]) {
                        _uiState.update { it.copy(isSyncing = false, syncError = "Reading progress could not sync.") }
                    }
                }
            }
        }
        progressSaveJob = saveJob
        saveJob.start()
    }

    /**
     * Apply a pinch-zoom text-scale nudge from the reflowable reader, reusing
     * the existing display-settings persistence path.
     */
    fun nudgeTextScale(zoom: Float) {
        val cur = _uiState.value.displaySettings
        setDisplaySettings(cur.copy(textScale = (cur.textScale * zoom).coerceIn(0.6f, 3.0f)))
    }

    fun jumpToPage(page: Int) {
        onPageChanged(page.coerceAtLeast(0))
    }

    fun setDisplaySettings(settings: ReaderDisplaySettings) {
        val normalized = settings.normalized()
        _uiState.update { it.copy(displaySettings = normalized) }
        viewModelScope.launch {
            val (serverId, profileId) = resolveScope()
            withContext(Dispatchers.IO) {
                localStateStore.writeDisplaySettings(serverId, profileId, normalized)
            }
        }
    }

    fun setSections(sections: List<ReaderSection>) {
        _uiState.update { it.copy(sections = sections) }
    }

    fun addBookmark() {
        val location = _uiState.value.progressLocation ?: "page:${_uiState.value.currentPage}"
        viewModelScope.launch {
            val (serverId, profileId) = resolveScope()
            val local = withContext(Dispatchers.IO) {
                localStateStore.addBookmark(serverId, profileId, contentId, location)
            }
            _uiState.update { it.copy(bookmarks = it.bookmarks + local.toAnnotation(), syncError = null) }
            when (val result = ebookReaderRepository.createBookmark(contentId, location)) {
                is ApiResult.Success -> {
                    withContext(Dispatchers.IO) {
                        localStateStore.removeBookmark(serverId, profileId, contentId, local.id)
                    }
                    _uiState.update {
                        it.copy(
                            bookmarks = (it.bookmarks.filterNot { bookmark -> bookmark.id == local.id } + result.data),
                            syncError = null,
                        )
                    }
                }
                else -> _uiState.update { it.copy(syncError = "Bookmark could not sync.") }
            }
        }
    }

    fun deleteBookmark(bookmark: EbookAnnotation) {
        viewModelScope.launch {
            val (serverId, profileId) = resolveScope()
            withContext(Dispatchers.IO) {
                localStateStore.removeBookmark(serverId, profileId, contentId, bookmark.id)
            }
            _uiState.update { state ->
                state.copy(bookmarks = state.bookmarks.filterNot { it.id == bookmark.id }, syncError = null)
            }
            if (!bookmark.id.startsWith("local-")) {
                when (ebookReaderRepository.deleteAnnotation(contentId, bookmark.id)) {
                    is ApiResult.Success -> Unit
                    else -> _uiState.update { it.copy(syncError = "Bookmark delete could not sync.") }
                }
            }
        }
    }

    private suspend fun resolveScope(): Pair<String, String> {
        val serverId = serverRegistry.activeServerId.value ?: DownloadEnqueuer.DEFAULT_SERVER_ID
        val profileId = profileRepository.getActiveProfileId() ?: DownloadEnqueuer.DEFAULT_PROFILE_ID
        return serverId to profileId
    }

    private fun OfflineMedia.toFileVersion(): FileVersion =
        FileVersion(
            fileId = fileId,
            fileName = sidecar.fileName ?: displayName,
            container = sidecar.container ?: displayName.substringAfterLast('.', missingDelimiterValue = ""),
        )

    private fun EbookLocalStateStore.BookmarkSnapshot.toAnnotation(): EbookAnnotation =
        localBookmarkAnnotation(
            id = id,
            contentId = contentId,
            location = location,
            createdAt = createdAtMs.toString(),
        )
}
