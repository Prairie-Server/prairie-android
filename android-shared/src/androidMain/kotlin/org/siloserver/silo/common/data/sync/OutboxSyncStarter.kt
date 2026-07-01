package org.siloserver.silo.common.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import org.siloserver.silo.network.ServerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Kicks an outbox drain on app launch, when connectivity returns, and when the
 * active server changes — so ops left pending by a previous session, by an
 * offline period, or by a scope the worker wasn't draining when they were
 * queued, all sync without waiting for a fresh mutation. The mutation path
 * already enqueues on `resolve(RETRIABLE)`; this covers the "reopened / back
 * online / switched back" cases.
 *
 * Used by both the phone and TV `Application`s (both perform content-level
 * mutations). Enqueues are unique-KEEP, so concurrent triggers coalesce.
 *
 * Known limitation (documented, not data-loss): while a retry chain for one
 * scope is backing off, `KEEP` can briefly defer an immediately-due op until the
 * chain's next tick. A dedicated scheduler would close that latency gap; the op
 * is never lost.
 */
class OutboxSyncStarter(
    private val context: Context,
    private val registry: ServerRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    fun start() {
        // Drain leftovers on launch; the worker's CONNECTED constraint defers
        // the actual run until the network is available.
        SyncWorker.enqueue(context)

        // Drain when the active server changes (e.g. switched back to a scope
        // whose ops were queued earlier and never got a trigger).
        scope.launch {
            var seenInitial = false
            registry.activeServerId.collect {
                if (seenInitial) SyncWorker.enqueue(context) else seenInitial = true
            }
        }

        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            Log.w(TAG, "No ConnectivityManager; outbox will drain on launch/activation/mutation only")
            return
        }
        runCatching {
            cm.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        SyncWorker.enqueue(context)
                    }
                },
            )
        }.onFailure { Log.w(TAG, "registerDefaultNetworkCallback failed", it) }
    }

    companion object {
        private const val TAG = "OutboxSyncStarter"
    }
}
