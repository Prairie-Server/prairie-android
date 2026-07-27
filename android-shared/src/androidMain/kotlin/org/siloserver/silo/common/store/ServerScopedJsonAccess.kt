package org.siloserver.silo.common.store

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide, bounded-lock coordination between scoped JSON writers and
 * identity purge. Invalidating a server key prevents a stale coroutine from
 * recreating its directory after deletion; observing the server registered
 * again explicitly enables new writes.
 */
internal object ServerScopedJsonAccess {
    private const val STRIPE_COUNT = 64
    private val stripes = Array(STRIPE_COUNT) { Any() }
    private val invalidated = ConcurrentHashMap.newKeySet<String>()

    fun <T> withWriter(root: File, serverStorageKey: String, block: () -> T): T? {
        val identityKey = identityKey(root, serverStorageKey)
        return synchronized(stripe(identityKey)) {
            if (identityKey in invalidated) null else block()
        }
    }

    fun invalidateAndRun(root: File, serverStorageKey: String, block: () -> Unit) {
        val identityKey = identityKey(root, serverStorageKey)
        synchronized(stripe(identityKey)) {
            invalidated += identityKey
            block()
        }
    }

    fun allow(root: File, serverStorageKey: String) {
        val identityKey = identityKey(root, serverStorageKey)
        synchronized(stripe(identityKey)) {
            invalidated -= identityKey
        }
    }

    private fun identityKey(root: File, serverStorageKey: String): String =
        root.canonicalPath + '\u0000' + serverStorageKey

    private fun stripe(identityKey: String): Any =
        stripes[(identityKey.hashCode() and Int.MAX_VALUE) % STRIPE_COUNT]
}
