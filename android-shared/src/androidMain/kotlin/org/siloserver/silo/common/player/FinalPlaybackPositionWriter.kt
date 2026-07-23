package org.siloserver.silo.common.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.repository.port.PlaybackWriteScope

data class FinalPlaybackPosition(
    val scope: PlaybackWriteScope,
    val contentId: String,
    val fileId: Int,
    val positionSeconds: Double,
    val durationSeconds: Double?,
)

/**
 * Application-owned final-position queue. Submission never waits for Room;
 * pending updates for the same identity/content/file collapse to the newest snapshot.
 */
class FinalPlaybackPositionWriter(
    scope: CoroutineScope,
    private val scopeProvider: suspend () -> AuthScopeSnapshot?,
    private val write: suspend (FinalPlaybackPosition) -> Unit,
) {
    private data class Key(
        val scope: PlaybackWriteScope,
        val contentId: String,
        val fileId: Int,
    )

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

    suspend fun captureScope(): PlaybackWriteScope? {
        val snapshot = scopeProvider() ?: return null
        val profileId = snapshot.profileId ?: return null
        return PlaybackWriteScope(
            serverId = snapshot.serverId,
            profileId = profileId,
            credentialGenerationId = snapshot.credentialGenerationId,
            identityGeneration = snapshot.identityGeneration,
        )
    }

    fun submit(snapshot: FinalPlaybackPosition): Boolean {
        if (!snapshot.isValid()) return false
        synchronized(lock) {
            pending[Key(snapshot.scope, snapshot.contentId, snapshot.fileId)] = snapshot
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
