package com.continuum.app.android.ui.screens.reader

import androidx.lifecycle.SavedStateHandle
import com.continuum.app.common.downloads.DownloadStorage
import com.continuum.app.common.downloads.DownloadTarget
import com.continuum.app.common.downloads.OfflineMediaResolver
import com.continuum.app.common.ebook.EbookLocalStateStore
import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.download.DownloadRecord
import com.continuum.app.model.download.DownloadSidecar
import com.continuum.app.model.ebook.EbookReadMode
import com.continuum.app.model.server.ServerEntry
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import com.continuum.app.network.api.CatalogApi
import com.continuum.app.network.api.EbookReaderApi
import com.continuum.app.network.api.ProfileApi
import com.continuum.app.network.ContinuumJson
import com.continuum.app.repository.CatalogRepository
import com.continuum.app.repository.EbookReaderRepository
import com.continuum.app.repository.ProfileRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelReaderTargetSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun viewModelUsesReaderTargetSelectionInsteadOfInAppOnlySelection() {
        val source = java.io.File(
            "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt",
        ).readText()

        assertTrue(source.contains("chooseReaderVersion("))
        assertFalse(source.contains("chooseEbookVersion("))
    }

    @Test
    fun externalOnlyTargetsRemainVisibleInReaderState() {
        val source = java.io.File(
            "src/androidMain/kotlin/com/continuum/app/android/ui/screens/reader/ReaderViewModel.kt",
        ).readText()

        assertTrue(source.contains("EbookReadMode.ExternalOnly"))
        assertTrue(source.contains("readMode = target.support.readMode"))
        assertTrue(source.contains("Download this original to open it with another reader."))
    }

    @Test
    fun externalOnlyOnlineTargetWithoutLocalDownloadPopulatesExternalState() = runTest(dispatcher) {
        val vm = viewModel(
            catalogRepository = catalogRepository(
                responseBody = itemDetailJson(fileName = "book.mobi", container = "mobi"),
            ),
            downloadStorage = DownloadStorage(tmp.newFolder("downloads")),
        )

        advanceUntilIdle()
        val state = vm.awaitLoaded()

        assertEquals(EbookReadMode.ExternalOnly, state.readMode)
        assertEquals(BookFormat.Mobi, state.format)
        assertEquals("MOBI", state.formatDisplayName)
        assertNull(state.fileUrl)
        assertNull(state.localUri)
        assertEquals("book.mobi", state.localDisplayName)
        assertEquals("Download this original to open it with another reader.", state.error)
    }

    @Test
    fun offlineOnlyExternalOnlyLocalMediaPopulatesDownloadedOriginalState() = runTest(dispatcher) {
        val storage = DownloadStorage(tmp.newFolder("downloads"))
        storage.writeCompletedDownload(fileName = "book.mobi")

        val vm = viewModel(
            catalogRepository = catalogRepository(status = HttpStatusCode.InternalServerError),
            downloadStorage = storage,
        )

        advanceUntilIdle()
        val state = vm.awaitLoaded()

        assertEquals(EbookReadMode.ExternalOnly, state.readMode)
        assertEquals(BookFormat.Mobi, state.format)
        assertEquals("MOBI", state.formatDisplayName)
        assertTrue(state.fileUrl?.startsWith("file://") == true)
        assertTrue(state.fileUrl?.contains("book.mobi") == true)
        assertEquals(state.fileUrl, state.localUri)
        assertEquals("book.mobi", state.localDisplayName)
        assertNull(state.error)
    }

    @Test
    fun onlineMissingRequestedFileIdFallsThroughToReadableTarget() = runTest(dispatcher) {
        // chooseReaderVersion (Task 3): when the requested fileId is absent the
        // selection falls through to the best available readable version, consistent
        // with chooseEbookVersion. The catalog here only has fileId=8 (epub); the
        // requested fileId=7 is not present, so the reader should open fileId=8.
        val vm = viewModel(
            catalogRepository = catalogRepository(
                responseBody = itemDetailJson(fileId = 8, fileName = "other.epub", container = "epub"),
            ),
            downloadStorage = DownloadStorage(tmp.newFolder("downloads")),
            requestedFileId = FILE_ID,
        )

        advanceUntilIdle()
        val state = vm.awaitLoaded()

        assertEquals("Book", state.title)
        assertEquals("Ada Author", state.author)
        assertEquals(8, state.fileId)
        assertEquals(BookFormat.Epub, state.format)
        assertEquals(EbookReadMode.InApp, state.readMode)
        assertNull(state.error)
    }

    @Test
    fun onlineNoRequestUsesSelectedCatalogTargetNotDifferentLocalFallback() = runTest(dispatcher) {
        val storage = DownloadStorage(tmp.newFolder("downloads"))
        storage.writeCompletedDownload(fileId = 7, fileName = "book.mobi")

        val vm = viewModel(
            catalogRepository = catalogRepository(
                responseBody = itemDetailJson(fileId = 8, fileName = "book.epub", container = "epub"),
            ),
            downloadStorage = storage,
            requestedFileId = null,
        )

        advanceUntilIdle()
        val state = vm.awaitLoaded()

        assertEquals(8, state.fileId)
        assertEquals(BookFormat.Epub, state.format)
        assertEquals(EbookReadMode.InApp, state.readMode)
        assertEquals("/api/v1/ebooks/$CONTENT_ID/files/8/read", state.fileUrl)
        assertNull(state.localUri)
        assertEquals("book.epub", state.localDisplayName)
        assertNull(state.error)
    }

    @Test
    fun offlineOnlyNoRequestPrefersDownloadedInAppTargetOverEarlierExternalOriginal() = runTest(dispatcher) {
        val storage = DownloadStorage(tmp.newFolder("downloads"))
        storage.writeCompletedDownload(fileId = 7, fileName = "book.mobi")
        storage.writeCompletedDownload(fileId = 8, fileName = "book.epub")

        val vm = viewModel(
            catalogRepository = catalogRepository(status = HttpStatusCode.InternalServerError),
            downloadStorage = storage,
            requestedFileId = null,
        )

        advanceUntilIdle()
        val state = vm.awaitLoaded()

        assertEquals(8, state.fileId)
        assertEquals(BookFormat.Epub, state.format)
        assertEquals(EbookReadMode.InApp, state.readMode)
        assertEquals("EPUB", state.formatDisplayName)
        assertTrue(state.fileUrl?.startsWith("file://") == true)
        assertTrue(state.fileUrl?.contains("book.epub") == true)
        assertEquals(state.fileUrl, state.localUri)
        assertEquals("book.epub", state.localDisplayName)
        assertNull(state.error)
    }

    @Test
    fun pageJumpClampsToKnownPageCountBeforePersistingProgress() = runTest(dispatcher) {
        val vm = viewModel(
            catalogRepository = catalogRepository(
                responseBody = itemDetailJson(fileId = FILE_ID, fileName = "book.pdf", container = "pdf"),
            ),
            downloadStorage = DownloadStorage(tmp.newFolder("downloads")),
        )

        advanceUntilIdle()
        vm.awaitLoaded()
        vm.onPageCountKnown(10)
        vm.jumpToPage(999)
        advanceUntilIdle()
        vm.awaitSyncIdle()

        val state = vm.uiState.value
        assertEquals(9, state.currentPage)
        assertEquals("page:9", state.progressLocation)
        assertEquals(1.0, state.progressPercent)
    }

    @Test
    fun latePageCountClampsPreviouslyUnknownPageProgress() = runTest(dispatcher) {
        val vm = viewModel(
            catalogRepository = catalogRepository(
                responseBody = itemDetailJson(fileId = FILE_ID, fileName = "book.pdf", container = "pdf"),
            ),
            downloadStorage = DownloadStorage(tmp.newFolder("downloads")),
        )

        advanceUntilIdle()
        vm.awaitLoaded()
        vm.onPageChanged(999)
        vm.onPageCountKnown(10)
        advanceUntilIdle()
        vm.awaitSyncIdle()

        val state = vm.uiState.value
        assertEquals(9, state.currentPage)
        assertEquals("page:9", state.progressLocation)
        assertEquals(1.0, state.progressPercent)
    }

    private fun viewModel(
        catalogRepository: CatalogRepository,
        downloadStorage: DownloadStorage,
        requestedFileId: Int? = FILE_ID,
    ): ReaderViewModel =
        ReaderViewModel(
            catalogRepository = catalogRepository,
            ebookReaderRepository = ebookReaderRepository(),
            offlineMediaResolver = OfflineMediaResolver(downloadStorage),
            localStateStore = EbookLocalStateStore(tmp.newFolder("reader-state")),
            serverRegistry = FakeServerRegistry(),
            profileRepository = FakeProfileRepository(),
            savedStateHandle = SavedStateHandle(
                buildMap {
                    put("contentId", CONTENT_ID)
                    requestedFileId?.let { put("fileId", it.toString()) }
                },
            ),
        )

    private suspend fun ReaderViewModel.awaitLoaded(): ReaderUiState {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (uiState.value.isLoading) {
                    delay(10)
                }
            }
        }
        return uiState.value
    }

    private suspend fun ReaderViewModel.awaitSyncIdle(): ReaderUiState {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (uiState.value.isSyncing) {
                    delay(10)
                }
            }
        }
        return uiState.value
    }

    private fun catalogRepository(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = """{"error":"server_error","message":"offline"}""",
    ): CatalogRepository =
        CatalogRepository(
            CatalogApi(
                HttpClient(
                    MockEngine { request ->
                        val body = if (request.url.encodedPath == "/api/v1/catalog/items/$CONTENT_ID") {
                            responseBody
                        } else {
                            """{"error":"not_found","message":"not found"}"""
                        }
                        respond(
                            content = body,
                            status = if (request.url.encodedPath == "/api/v1/catalog/items/$CONTENT_ID") {
                                status
                            } else {
                                HttpStatusCode.NotFound
                            },
                            headers = jsonHeaders,
                        )
                    },
                ) {
                    install(ContentNegotiation) { json(ContinuumJson) }
                },
            ),
        )

    private fun ebookReaderRepository(): EbookReaderRepository =
        EbookReaderRepository(
            EbookReaderApi(
                HttpClient(
                    MockEngine {
                        respond(
                            content = """{"error":"not_found","message":"not found"}""",
                            status = HttpStatusCode.NotFound,
                            headers = jsonHeaders,
                        )
                    },
                ) {
                    install(ContentNegotiation) { json(ContinuumJson) }
                },
            ),
        )

    private fun itemDetailJson(fileId: Int = FILE_ID, fileName: String, container: String): String =
        """
        {
          "content_id": "$CONTENT_ID",
          "type": "ebook",
          "title": "Book",
          "ebook": {
            "authors": [
              {
                "name": "Ada Author"
              }
            ]
          },
          "versions": [
            {
              "file_id": $fileId,
              "file_name": "$fileName",
              "container": "$container"
            }
          ]
        }
        """.trimIndent()

    private fun DownloadStorage.writeCompletedDownload(fileId: Int = FILE_ID, fileName: String) {
        val container = fileName.substringAfterLast('.', missingDelimiterValue = "")
        prepareWrite(SERVER_ID, PROFILE_ID, fileId, fileName = fileName, container = container).writeTargetBytes(
            container.encodeToByteArray(),
        )
        writeSidecar(
            SERVER_ID,
            PROFILE_ID,
            DownloadSidecar(
                record = DownloadRecord(
                    id = "dl-$fileId",
                    contentId = CONTENT_ID,
                    mediaFileId = fileId,
                    fileSize = container.length.toLong(),
                    bytesSent = container.length.toLong(),
                    kind = "queued",
                    status = "completed",
                    createdAt = "2026-06-14T00:00:00Z",
                ),
                title = "Book",
                fileName = fileName,
                container = container,
                mediaType = "ebook",
                updatedAtMs = 1L,
            ),
        )
    }

    private fun DownloadTarget.writeTargetBytes(bytes: ByteArray) {
        openOutputStream().use { it.write(bytes) }
    }

    private class FakeServerRegistry : ServerRegistry {
        override val entries: StateFlow<List<ServerEntry>> = MutableStateFlow(emptyList())
        override val activeServerId: StateFlow<String?> = MutableStateFlow(SERVER_ID)
        override val activeEntry: StateFlow<ServerEntry?> = MutableStateFlow(null)
        override suspend fun addOrUpdate(url: String, fetchedName: String?): String = SERVER_ID
        override suspend fun rename(serverId: String, userOverrideName: String?) = Unit
        override suspend fun setFetchedName(serverId: String, fetchedName: String?) = Unit
        override suspend fun setProfileId(serverId: String, profileId: String?) = Unit
        override suspend fun remove(serverId: String) = Unit
        override suspend fun signOut(serverId: String) = Unit
        override suspend fun switchTo(serverId: String) = Unit
        override suspend fun touchActive() = Unit
    }

    private class FakeProfileRepository : ProfileRepository(
        profileApi = ProfileApi(noOpClient()),
        tokenManager = FakeTokenManager(),
    ) {
        override suspend fun getActiveProfileId(): String = PROFILE_ID
    }

    private class FakeTokenManager : TokenManager {
        override val sessionExpired = MutableSharedFlow<Unit>()
        override suspend fun getAccessToken(): String? = null
        override suspend fun getRefreshToken(): String? = null
        override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) = Unit
        override suspend fun clearTokens() = Unit
        override suspend fun invalidateSession() = Unit
        override suspend fun getProfileId(): String? = PROFILE_ID
        override suspend fun setProfileId(profileId: String?) = Unit
        override suspend fun getProfileToken(): String? = null
        override suspend fun setProfileToken(token: String?) = Unit
        override suspend fun getServerUrl(): String = "http://localhost"
        override suspend fun setServerUrl(url: String) = Unit
        override suspend fun getCurrentServerId(): String? = SERVER_ID
        override suspend fun switchActiveServer(serverId: String?) = Unit
        override suspend fun signOutCurrentServer() = Unit
    }

    private companion object {
        const val CONTENT_ID = "book-1"
        const val FILE_ID = 7
        const val SERVER_ID = "srv1"
        const val PROFILE_ID = "profA"

        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        fun noOpClient(): HttpClient =
            HttpClient(MockEngine { respond("{}", headers = jsonHeaders) }) {
                install(ContentNegotiation) { json(ContinuumJson) }
            }
    }
}
