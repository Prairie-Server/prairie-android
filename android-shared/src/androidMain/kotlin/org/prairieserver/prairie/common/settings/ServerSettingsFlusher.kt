package org.prairieserver.prairie.common.settings

import android.util.Log
import org.prairieserver.prairie.network.ApiResult
import org.prairieserver.prairie.network.api.SettingsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ServerSettingsFlusher {
    fun enqueue(profileId: String, key: String, value: String)
    fun enqueueDelete(profileId: String, key: String)

    /**
     * Cancel any in-flight debounce, drain every pending op, and suspend
     * until each one has been ack'd (or errored). Mirrors the iOS
     * `PlayerSettings.flushPendingDeviceSettings()` semantics —
     * re-entrant calls coalesce, and the second caller waits for the
     * first to finish.
     */
    suspend fun flushNow()
}

private sealed class PendingOp {
    data class Set(val value: String) : PendingOp()
    object Delete : PendingOp()
}

class DefaultServerSettingsFlusher(
    private val settingsApi: SettingsApi,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 750,
) : ServerSettingsFlusher {

    private val lock = Any()
    private val pending = mutableMapOf<Pair<String, String>, PendingOp>()
    private var flushJob: Job? = null
    private val flushMutex = Mutex()

    override fun enqueue(profileId: String, key: String, value: String) {
        scheduleDebounced(profileId, key, PendingOp.Set(value))
    }

    override fun enqueueDelete(profileId: String, key: String) {
        scheduleDebounced(profileId, key, PendingOp.Delete)
    }

    private fun scheduleDebounced(profileId: String, key: String, op: PendingOp) {
        synchronized(lock) {
            pending[profileId to key] = op
            flushJob?.cancel()
            flushJob = scope.launch {
                delay(debounceMs)
                drainAndFlush()
            }
        }
    }

    override suspend fun flushNow() {
        synchronized(lock) {
            flushJob?.cancel()
            flushJob = null
        }
        drainAndFlush()
    }

    private suspend fun drainAndFlush() {
        flushMutex.withLock {
            while (true) {
                val snapshot: Map<Pair<String, String>, PendingOp> = synchronized(lock) {
                    if (pending.isEmpty()) return@withLock
                    val copy = pending.toMap()
                    pending.clear()
                    copy
                }
                snapshot.forEach { (composite, op) ->
                    val (profileId, key) = composite
                    flushOne(profileId, key, op)
                }
            }
        }
    }

    private suspend fun flushOne(profileId: String, key: String, op: PendingOp) {
        try {
            val result = when (op) {
                is PendingOp.Set ->
                    settingsApi.setDeviceSetting(key, op.value, profileId = profileId)
                is PendingOp.Delete ->
                    settingsApi.deleteDeviceSetting(key)
            }
            if (result is ApiResult.Error) {
                Log.w(TAG, "$op profile=$profileId key=$key code=${result.code}: ${result.message}")
            } else if (result is ApiResult.NetworkError) {
                Log.w(TAG, "$op profile=$profileId key=$key network error: ${result.exception}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "$op profile=$profileId key=$key threw: $t")
        }
    }

    private companion object {
        const val TAG = "ServerSettingsFlusher"
    }
}
