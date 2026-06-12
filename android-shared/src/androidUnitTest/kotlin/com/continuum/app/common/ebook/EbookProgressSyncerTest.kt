package com.continuum.app.common.ebook

import com.continuum.app.model.ebook.EbookReaderProgress
import com.continuum.app.model.ebook.SaveEbookProgressRequest
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.EbookReaderApi
import com.continuum.app.repository.EbookReaderRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EbookProgressSyncerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeApi(private val serverProgress: Map<String, Double>) :
        EbookReaderApi(HttpClient()) {
        val saved = mutableListOf<String>()

        override suspend fun getProgress(contentId: String): ApiResult<EbookReaderProgress> {
            val p = serverProgress[contentId]
                ?: return ApiResult.NetworkError(IllegalStateException("no server progress"))
            return ApiResult.Success(
                EbookReaderProgress(contentId = contentId, fileId = 1, location = "srv", progress = p),
            )
        }

        override suspend fun saveProgress(
            contentId: String,
            request: SaveEbookProgressRequest,
        ): ApiResult<EbookReaderProgress> {
            saved.add(contentId)
            return ApiResult.Success(
                EbookReaderProgress(contentId, request.fileId, request.location, request.progress),
            )
        }
    }

    @Test
    fun `pushes only when local read is further than the server`() = runTest {
        val store = EbookLocalStateStore(tmp.root)
        // further than server -> push
        store.writeProgress("srv", "prof", "book-1", EbookLocalStateStore.ProgressSnapshot(1, "a", 0.5, 1L))
        // behind server -> skip (no regression)
        store.writeProgress("srv", "prof", "book-2", EbookLocalStateStore.ProgressSnapshot(2, "b", 0.2, 1L))
        // server has none -> push
        store.writeProgress("srv", "prof", "book-3", EbookLocalStateStore.ProgressSnapshot(3, "c", 0.1, 1L))

        val api = FakeApi(serverProgress = mapOf("book-1" to 0.3, "book-2" to 0.6))
        EbookProgressSyncer(store, EbookReaderRepository(api), this).flush()

        assertEquals(setOf("book-1", "book-3"), api.saved.toSet())
    }

    @Test
    fun `collapses duplicate scopes to the newest snapshot`() = runTest {
        val store = EbookLocalStateStore(tmp.root)
        store.writeProgress("srv-a", "prof", "book-1", EbookLocalStateStore.ProgressSnapshot(1, "old", 0.3, 1L))
        store.writeProgress("default", "default", "book-1", EbookLocalStateStore.ProgressSnapshot(1, "new", 0.7, 9L))

        val api = FakeApi(serverProgress = emptyMap())
        EbookProgressSyncer(store, EbookReaderRepository(api), this).flush()

        // One push for book-1 (server has none); newest snapshot (0.7) wins.
        assertEquals(listOf("book-1"), api.saved)
    }
}
