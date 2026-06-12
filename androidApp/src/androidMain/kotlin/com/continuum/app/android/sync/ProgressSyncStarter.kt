package com.continuum.app.android.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Drives the playback/reading progress syncers (audiobook + ebook) on the two
 * moments offline-recorded progress should reach the server: app foreground
 * (process `ON_START`) and network reconnect (`onAvailable`). Each [flushers]
 * entry is a syncer's `requestFlush` — cheap, idempotent, and self-serializing,
 * so over-triggering is fine.
 */
class ProgressSyncStarter(
    private val context: Context,
    private val flushers: List<() -> Unit>,
) : DefaultLifecycleObserver {

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)
                ?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun onStart(owner: LifecycleOwner) = flushAll()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = flushAll()
    }

    private fun flushAll() {
        flushers.forEach { it() }
    }
}
