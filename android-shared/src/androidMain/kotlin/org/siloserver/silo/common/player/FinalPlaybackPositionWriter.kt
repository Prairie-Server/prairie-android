package org.siloserver.silo.common.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

data class FinalPlaybackPosition(
    val contentId: String,
    val fileId: Int,
    val positionSeconds: Double,
    val durationSeconds: Double?,
)

/**
 * Application-owned final-position queue. Submission never waits for Room;
 * pending updates for the same content/file collapse to the newest snapshot.
 */
class FinalPlaybackPositionWriter(
    scope: CoroutineScope,
    private val write: suspend (FinalPlaybackPosition) -> Unit,
) {
    private data class Key(val contentId: String, val fileId: Int)

    private val lock = Any()
    private val pending = linkedMapOf<Key, FinalPlaybackPosition>()
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in wakeUp) {
                while (true) {
                    val batch = synchronized(lock) {
                        if (pending.isEmpty()) {
                            emptyList()
                        } else {
                            pending.values.toList().also { pending.clear() }
                        }
                    }
                    if (batch.isEmpty()) break
                    batch.forEach { snapshot -> runCatching { write(snapshot) } }
                }
            }
        }
    }

    fun submit(snapshot: FinalPlaybackPosition): Boolean {
        if (!snapshot.isValid()) return false
        synchronized(lock) {
            pending[Key(snapshot.contentId, snapshot.fileId)] = snapshot
        }
        wakeUp.trySend(Unit)
        return true
    }
}

private fun FinalPlaybackPosition.isValid(): Boolean =
    contentId.isNotBlank() &&
        fileId > 0 &&
        positionSeconds.isFinite() &&
        positionSeconds >= 0.0 &&
        (durationSeconds == null || durationSeconds.isFinite() && durationSeconds > 0.0)
