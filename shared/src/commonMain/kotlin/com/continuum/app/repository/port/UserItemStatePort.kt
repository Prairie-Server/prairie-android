package com.continuum.app.repository.port

import com.continuum.app.network.AuthScopeSnapshot

/**
 * Local-first side-channel for **content-level** user-state mutations
 * (watched / favorite / rating). The strangler entry point for Track B:
 * [com.continuum.app.repository.PersonalDataRepository] records each mutation
 * through this port (optimistic local projection + a durable outbox op) before
 * calling the server, then [resolve]s the op with the network outcome.
 *
 * The port lives in `commonMain` so the shared repository can depend on it; the
 * real Room-backed implementation is Android-only and bound in the platform DI
 * module. On any target without that binding (commonMain tests), the
 * [NoOpUserItemStatePort] default keeps behaviour exactly network-only.
 *
 * Contract: `record*` persists the projection + a pending outbox op atomically
 * and returns a [OutboxHandle]; the caller then calls [resolve] with the
 * server result so the op can be acked, kept for retry, or dropped.
 */
interface UserItemStatePort {

    /** rating `null` clears the rating. */
    suspend fun recordWatched(contentId: String, watched: Boolean): OutboxHandle
    suspend fun recordFavorite(contentId: String, favorite: Boolean): OutboxHandle
    suspend fun recordRating(contentId: String, rating: Int?): OutboxHandle

    /** Reconcile the enqueued op with the inline network result. */
    suspend fun resolve(handle: OutboxHandle, outcome: WriteOutcome)
}

/**
 * Opaque reference to an enqueued outbox op. [NONE] means nothing was recorded
 * (e.g. no active server/profile scope, or the no-op port) — [resolve] ignores it.
 *
 * [scope] is the auth snapshot captured atomically when the op was recorded.
 * The repository pins the inline network call to it so a server/profile switch
 * between recording and sending can't route the write to the wrong account (and
 * then false-ack/delete the op). Null for the no-op port (single-scope) →
 * unpinned, preserving the original behaviour.
 */
data class OutboxHandle(val opId: Long, val scope: AuthScopeSnapshot? = null) {
    companion object {
        val NONE = OutboxHandle(-1L)
    }
}

/**
 * What the inline network call told us about a recorded op:
 * - [SYNCED] — server accepted it; delete the op (Task 5 must not replay it).
 * - [RETRIABLE] — transient (no network / 5xx / 408 / 429); keep it pending.
 * - [TERMINAL] — server rejected it (4xx); drop the op so a doomed write is not replayed.
 */
enum class WriteOutcome { SYNCED, RETRIABLE, TERMINAL }

/** Network-only behaviour: records nothing, resolves to nothing. */
object NoOpUserItemStatePort : UserItemStatePort {
    override suspend fun recordWatched(contentId: String, watched: Boolean) = OutboxHandle.NONE
    override suspend fun recordFavorite(contentId: String, favorite: Boolean) = OutboxHandle.NONE
    override suspend fun recordRating(contentId: String, rating: Int?) = OutboxHandle.NONE
    override suspend fun resolve(handle: OutboxHandle, outcome: WriteOutcome) {}
}
