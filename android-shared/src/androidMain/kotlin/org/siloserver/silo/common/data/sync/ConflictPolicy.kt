package org.siloserver.silo.common.data.sync

/**
 * Per-field conflict resolution for the offline-first sync engine (Track B).
 *
 * Today's server mutation APIs return Unit and expose no per-field `updated_at`,
 * so these policies are applied on the **read/refresh** path (reconciling a
 * server GET with the local Room projection), not on the mutation ack. Pure so
 * the policy is unit-tested.
 */
object ConflictPolicy {
    /**
     * Last-write-wins for current resume position / CFI / track selections.
     * Local wins when the server has no timestamp or is not strictly newer
     * (ties keep local so an in-flight optimistic write isn't clobbered by an
     * equal-stamped server echo).
     */
    fun localWins(localUpdatedMs: Long, serverUpdatedMs: Long?): Boolean =
        serverUpdatedMs == null || localUpdatedMs >= serverUpdatedMs

    /** Monotonic furthest for continue-watching ranking / furthest-read. */
    fun furthest(localSeconds: Double, serverSeconds: Double): Double =
        maxOf(localSeconds, serverSeconds)
}
