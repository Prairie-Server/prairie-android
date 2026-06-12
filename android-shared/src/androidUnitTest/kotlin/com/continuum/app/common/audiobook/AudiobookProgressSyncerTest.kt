package com.continuum.app.common.audiobook

import com.continuum.app.model.personal.SyncProgressItem
import com.continuum.app.network.ApiResult
import com.continuum.app.network.api.PersonalDataApi
import com.continuum.app.repository.PersonalDataRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookProgressSyncerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class CapturingRepo : PersonalDataRepository(PersonalDataApi(HttpClient())) {
        val calls = mutableListOf<List<SyncProgressItem>>()
        override suspend fun syncProgress(items: List<SyncProgressItem>): ApiResult<Unit> {
            calls.add(items)
            return ApiResult.Success(Unit)
        }
    }

    @Test
    fun `flush pushes the latest snapshot per content id and skips zero progress`() = runTest {
        val store = AudiobookPositionStore(tmp.root)
        store.write("server-a", "prof", "book-1", AudiobookPositionStore.Snapshot(100.0, 1000.0, 1L))
        // Same book under a different scope, but newer — should win.
        store.write("default", "default", "book-1", AudiobookPositionStore.Snapshot(250.0, 1000.0, 9L))
        // Zero position — should be skipped.
        store.write("server-a", "prof", "book-2", AudiobookPositionStore.Snapshot(0.0, 1000.0, 5L))

        val repo = CapturingRepo()
        AudiobookProgressSyncer(store, repo, this).flush()

        assertEquals(1, repo.calls.size)
        val items = repo.calls.first()
        assertEquals(1, items.size)
        assertEquals("book-1", items[0].mediaItemId)
        assertEquals(250.0, items[0].position, 0.0)
        assertEquals(1000.0, items[0].duration, 0.0)
    }

    @Test
    fun `flush no-ops when there is nothing to sync`() = runTest {
        val store = AudiobookPositionStore(tmp.root)
        val repo = CapturingRepo()
        AudiobookProgressSyncer(store, repo, this).flush()
        assertEquals(0, repo.calls.size)
    }

    @Test
    fun `findLatest returns the newest snapshot across scopes`() {
        val store = AudiobookPositionStore(tmp.root)
        store.write("server-a", "prof", "book-1", AudiobookPositionStore.Snapshot(100.0, 1000.0, 1L))
        store.write("default", "default", "book-1", AudiobookPositionStore.Snapshot(250.0, 1000.0, 9L))

        assertEquals(250.0, store.findLatest("book-1")?.positionSeconds)
    }
}
