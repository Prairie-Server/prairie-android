package org.prairieserver.prairie.common.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide "which media file is the player currently using" marker.
 *
 * Exists because offline playback has no server session for the lifecycle
 * tracker to know about, yet destructive flows (Reclaim Watched) must not
 * delete the bytes out from under a live player (reachable via PiP →
 * Downloads). Set on player mount, cleared on teardown; a plain nullable
 * fileId is enough — only one player exists per process.
 */
object ActivePlaybackFile {
    private val _fileId = MutableStateFlow<Int?>(null)
    val fileId: StateFlow<Int?> = _fileId.asStateFlow()

    fun set(fileId: Int?) {
        _fileId.value = fileId
    }

    fun clear(fileId: Int?) {
        _fileId.compareAndSet(fileId, null)
    }
}
