package org.siloserver.silo.common.player

import android.util.Log

/**
 * Tracing for the subtitle transaction flow — the mount/replan chain.
 *
 * These are the only observability into that chain, and the defects it hides
 * are diagnosable from nothing else, so the call sites stay. What must not ship
 * is the output: several lines interpolate subtitle identities and session ids,
 * and this is a public client.
 *
 * Off unless someone asks for it. To enable on a device:
 *
 *     adb shell setprop log.tag.SUBDIAG DEBUG
 *
 * The gate is here rather than at the call sites so it cannot be half-applied:
 * all thirty of them are covered by this one check, and each remains a single
 * line that can be deleted mechanically if the tracing is ever retired.
 */
object SubDiag {
    private const val TAG = "SUBDIAG"

    /**
     * Read once. `isLoggable` walks the property table on every call, and these
     * fire inside mount and track-selection paths that run per frame-group.
     */
    private val enabled: Boolean by lazy {
        runCatching { Log.isLoggable(TAG, Log.DEBUG) }.getOrDefault(false)
    }

    fun log(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun trace(message: String) {
        if (enabled) Log.d(TAG, message, Throwable("caller"))
    }
}
