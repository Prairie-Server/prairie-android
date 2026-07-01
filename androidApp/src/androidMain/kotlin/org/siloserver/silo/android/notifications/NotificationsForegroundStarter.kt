package org.siloserver.silo.android.notifications

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.siloserver.silo.repository.NotificationsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Drives the [NotificationsRepository] realtime connection off the app's
 * foreground lifecycle. ON_START: REST refresh + open socket. ON_STOP: tear
 * the socket down. REST is the source of truth; the socket is only a foreground
 * accelerator, so a missing/failed connection degrades gracefully.
 *
 * Profile switch (mid-session): the socket was opened with the OLD profile's
 * ws-ticket and could keep delivering the previous profile's events into the
 * new session, and the badge would not repopulate for the new profile until
 * some other refresh fires. So while foregrounded we collect
 * [NotificationsRepository.resetSignals] (fired by `reset()` on every profile
 * switch) and, on each signal, recreate the realtime scope — reconnecting the
 * socket with the new profile's ticket — and run a REST refresh. The
 * collection lives on a separate lifecycle scope so it survives the realtime
 * scope teardown it triggers. The landed [ProfileRepository] only exposes a
 * suspend `getActiveProfileId()` (no observable active-profile flow), so the
 * reset signal — not a TokenManager flow — is what we watch.
 */
class NotificationsForegroundStarter(
    private val repository: NotificationsRepository,
) : DefaultLifecycleObserver {

    private var realtimeScope: CoroutineScope? = null
    private var foregroundScope: CoroutineScope? = null

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        startRealtime()
        observeProfileSwitches()
    }

    override fun onStop(owner: LifecycleOwner) {
        stopRealtime()
        foregroundScope?.cancel()
        foregroundScope = null
    }

    private fun startRealtime() {
        if (realtimeScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        realtimeScope = scope
        scope.launch { repository.refresh() }
        // connectRealtime owns its own capped-backoff reconnect loop and honors
        // scope cancellation, so tearing the scope down on ON_STOP stops it.
        repository.connectRealtime(scope)
    }

    private fun stopRealtime() {
        realtimeScope?.cancel()
        realtimeScope = null
    }

    /**
     * On each profile switch (reset signal), tear down the stale-ticket socket
     * and bring it back up against the new profile, then refresh the badge.
     * Restarting [startRealtime] both reconnects the socket and issues the
     * REST refresh for the new profile.
     */
    private fun observeProfileSwitches() {
        if (foregroundScope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        foregroundScope = scope
        scope.launch {
            repository.resetSignals.collect {
                stopRealtime()
                startRealtime()
            }
        }
    }
}
