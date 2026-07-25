package org.prairieserver.prairie.common.diagnostics

fun interface DiagnosticsPlaybackSessionRecorder {
    fun record(sessionId: String)

    fun recording(): DiagnosticsPlaybackSessionRecording = DiagnosticsPlaybackSessionRecording(::record)

    data object None : DiagnosticsPlaybackSessionRecorder {
        override fun record(sessionId: String) = Unit
    }
}

fun interface DiagnosticsPlaybackSessionRecording {
    fun record(sessionId: String)

    data object None : DiagnosticsPlaybackSessionRecording {
        override fun record(sessionId: String) = Unit
    }
}

class DiagnosticsPlaybackSessionScope internal constructor(
    internal val generation: Long,
    internal val identityKey: DiagnosticsIdentityKey,
)

class DiagnosticsPlaybackSessionTracker(
    private val maxSessions: Int = 20,
    private val maxIdChars: Int = 128,
) : DiagnosticsPlaybackSessionRecorder {
    private val sessions = ArrayDeque<String>()
    private var generation = 0L
    private var scope: DiagnosticsPlaybackSessionScope? = null

    init {
        require(maxSessions > 0)
        require(maxIdChars > 0)
    }

    @Synchronized
    override fun record(sessionId: String) {
        val current = scope ?: return
        record(current, sessionId)
    }

    @Synchronized
    override fun recording(): DiagnosticsPlaybackSessionRecording {
        val captured = scope ?: return DiagnosticsPlaybackSessionRecording.None
        return DiagnosticsPlaybackSessionRecording { sessionId -> record(captured, sessionId) }
    }

    @Synchronized
    fun open(identityKey: DiagnosticsIdentityKey): DiagnosticsPlaybackSessionScope {
        scope?.takeIf { it.identityKey == identityKey }?.let { return it }
        sessions.clear()
        val opened = DiagnosticsPlaybackSessionScope(++generation, identityKey)
        scope = opened
        return opened
    }

    @Synchronized
    fun close() {
        generation += 1
        scope = null
        sessions.clear()
    }

    @Synchronized
    fun commitIfCurrent(
        expected: DiagnosticsPlaybackSessionScope,
        block: (List<String>) -> Unit,
    ): Boolean {
        if (scope != expected) return false
        block(sessions.toList())
        return true
    }

    @Synchronized
    private fun record(expected: DiagnosticsPlaybackSessionScope, sessionId: String) {
        if (scope != expected) return
        val bounded = sessionId.trim().take(maxIdChars).takeIf(SAFE_ID::matches) ?: return
        sessions.remove(bounded)
        sessions.addLast(bounded)
        while (sessions.size > maxSessions) sessions.removeFirst()
        CrashCapture.updatePlaybackSessionIds(expected.identityKey, sessions.toList())
    }

    @Synchronized
    fun snapshot(): List<String> = sessions.toList()

    private companion object {
        val SAFE_ID = Regex("[A-Za-z0-9._:-]+")
    }
}
