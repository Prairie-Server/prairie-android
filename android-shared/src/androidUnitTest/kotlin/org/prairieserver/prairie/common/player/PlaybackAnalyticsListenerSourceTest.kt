package org.prairieserver.prairie.common.player

import kotlin.test.Test
import kotlin.test.assertTrue

class PlaybackAnalyticsListenerSourceTest {
    private val source = java.io.File(
        "src/androidMain/kotlin/org/prairieserver/prairie/common/player/PlaybackAnalyticsListener.kt",
    ).readText()

    @Test
    fun analyticsListenerLogsPlayerErrorsWithDiagnosticDetails() {
        assertTrue(
            source.contains("override fun onPlayerError("),
            "Playback analytics must log Media3 player errors, not just load errors",
        )
        assertTrue(
            source.contains("error.errorCodeName"),
            "Player error logs should include Media3's stable error code name",
        )
        assertTrue(
            source.contains("Log.e(TAG, \"Player error"),
            "Player errors should be emitted at error level with the Throwable attached",
        )
    }

    @Test
    fun analyticsListenerLogsTrackSnapshotsWhenMedia3SelectionChanges() {
        assertTrue(
            source.contains("override fun onTracksChanged("),
            "Track-selection changes must be logged so parser warnings can be tied to selected formats",
        )
        assertTrue(
            source.contains("Track snapshot:"),
            "Track logs should use a stable prefix for logcat filtering",
        )
        assertTrue(
            source.contains("sampleMimeType"),
            "Track snapshots should include MIME information for each active format",
        )
        assertTrue(
            source.contains("isTrackSelected(trackIndex)"),
            "Track snapshots should identify which formats Media3 actually selected",
        )
    }
}
