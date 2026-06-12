package com.continuum.app.android.audiobook

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.continuum.app.common.audiobook.AudiobookProgressSyncer

/**
 * Triggers [AudiobookProgressSyncer.requestFlush] on the two moments the user
 * chose: app foreground (process `ON_START`) and network reconnect
 * (`onAvailable`). Both are cheap and idempotent — the syncer serializes
 * flushes and no-ops when there's nothing to push — so over-triggering is fine.
 */
class AudiobookProgressSyncStarter(
    private val context: Context,
    private val syncer: AudiobookProgressSyncer,
) : DefaultLifecycleObserver {

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        syncer.requestFlush()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            syncer.requestFlush()
        }
    }
}
