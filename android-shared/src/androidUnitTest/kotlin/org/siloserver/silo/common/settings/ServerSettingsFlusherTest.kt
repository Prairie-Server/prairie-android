package org.siloserver.silo.common.settings

import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.api.SettingsApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ServerSettingsFlusherTest {

    @Test
    fun `enqueue debounces multiple writes for same key`() = runTest {
        val api = RecordingSettingsApi()
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 200)

        flusher.enqueue("p1", "key.a", "v1")
        advanceTimeBy(50)
        flusher.enqueue("p1", "key.a", "v2")
        advanceTimeBy(50)
        flusher.enqueue("p1", "key.a", "v3")

        // Not yet — total elapsed 100, debounce 200.
        assertEquals(0, api.calls.size)

        advanceUntilIdle()

        assertEquals(1, api.calls.size, "expected only the latest write to be sent (coalesced)")
        assertEquals("key.a", api.calls.first().key)
        assertEquals("v3", api.calls.first().value)
    }

    @Test
    fun `enqueue with multiple distinct keys flushes all`() = runTest {
        val api = RecordingSettingsApi()
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 200)

        flusher.enqueue("p1", "key.a", "1")
        flusher.enqueue("p1", "key.b", "2")
        flusher.enqueue("p1", "key.c", "3")

        advanceUntilIdle()

        assertEquals(3, api.calls.size)
        val byKey = api.calls.associate { it.key to it.value }
        assertEquals("1", byKey["key.a"])
        assertEquals("2", byKey["key.b"])
        assertEquals("3", byKey["key.c"])
    }

    @Test
    fun `enqueue then enqueue with new value coalesces to latest`() = runTest {
        val api = RecordingSettingsApi()
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 200)

        flusher.enqueue("p1", "key.x", "first")
        flusher.enqueue("p1", "key.x", "second")
        flusher.enqueue("p1", "key.x", "final")

        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        assertEquals("final", api.calls.first().value)
    }

    @Test
    fun `enqueue preserves profile id when flushing same key for multiple profiles`() = runTest {
        val api = RecordingSettingsApi()
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 200)

        flusher.enqueue("p1", "key.shared", "one")
        flusher.enqueue("p2", "key.shared", "two")

        advanceUntilIdle()

        assertEquals(2, api.calls.size)
        val byProfile = api.calls.associate { it.profileId to it.value }
        assertEquals("one", byProfile["p1"])
        assertEquals("two", byProfile["p2"])
    }

    @Test
    fun `enqueueDelete then enqueue coalesces to latest set`() = runTest {
        val api = RecordingSettingsApi()
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 200)

        flusher.enqueueDelete("p1", "key.x")
        flusher.enqueue("p1", "key.x", "after")

        advanceUntilIdle()

        assertEquals(1, api.calls.size, "set should win after delete since it was enqueued later")
        assertEquals(RecordingSettingsApi.Call.Kind.SET, api.calls.first().kind)
        assertEquals("after", api.calls.first().value)
    }

    @Test
    fun `enqueue then enqueueDelete coalesces to delete`() = runTest {
        val api = RecordingSettingsApi()
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 200)

        flusher.enqueue("p1", "key.x", "before")
        flusher.enqueueDelete("p1", "key.x")

        advanceUntilIdle()

        assertEquals(1, api.calls.size, "delete should win after set since it was enqueued later")
        assertEquals(RecordingSettingsApi.Call.Kind.DELETE, api.calls.first().kind)
        assertEquals("key.x", api.calls.first().key)
    }

    @Test
    fun `flushNow drains pending without waiting for debounce`() = runTest {
        val api = RecordingSettingsApi()
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 5_000)

        flusher.enqueue("p1", "key.a", "v1")
        flusher.enqueueDelete("p1", "key.b")

        // Don't advance — flushNow should drain immediately.
        flusher.flushNow()

        assertEquals(2, api.calls.size)
        val byKey = api.calls.associateBy { it.key }
        assertEquals(RecordingSettingsApi.Call.Kind.SET, byKey["key.a"]?.kind)
        assertEquals("v1", byKey["key.a"]?.value)
        assertEquals(RecordingSettingsApi.Call.Kind.DELETE, byKey["key.b"]?.kind)
    }

    @Test
    fun `flushNow with empty queue is a no-op`() = runTest {
        val api = RecordingSettingsApi()
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 200)

        flusher.flushNow()

        assertEquals(0, api.calls.size)
    }

    @Test
    fun `errors from setDeviceSetting are swallowed and do not abort future flushes`() = runTest {
        val api = RecordingSettingsApi(failNext = true)
        val flusher = DefaultServerSettingsFlusher(api, this, debounceMs = 200)

        flusher.enqueue("p1", "key.fail", "boom")
        advanceUntilIdle()

        // Even though that one errored internally, a subsequent enqueue should still flush.
        flusher.enqueue("p1", "key.ok", "yay")
        advanceUntilIdle()

        assertTrue(api.calls.any { it.key == "key.ok" && it.value == "yay" })
    }
}

/**
 * Records every setDeviceSetting / deleteDeviceSetting call. Constructed
 * with a no-op HttpClient because we override the only methods the
 * flusher invokes — the underlying client is never touched.
 */
private class RecordingSettingsApi(
    private val failNext: Boolean = false,
) : SettingsApi(HttpClient()) {
    data class Call(
        val kind: Kind,
        val key: String,
        val value: String?,
        val profileId: String?,
    ) {
        enum class Kind { SET, DELETE }
    }

    val calls = mutableListOf<Call>()
    private var failedOnce = false

    override suspend fun setDeviceSetting(
        key: String,
        value: String,
        profileId: String?,
    ): ApiResult<Unit> {
        calls.add(Call(Call.Kind.SET, key, value, profileId))
        if (failNext && !failedOnce) {
            failedOnce = true
            return ApiResult.Error(500, "internal", "boom")
        }
        return ApiResult.Success(Unit)
    }

    override suspend fun deleteDeviceSetting(key: String): ApiResult<Unit> {
        calls.add(Call(Call.Kind.DELETE, key, null, null))
        return ApiResult.Success(Unit)
    }
}
