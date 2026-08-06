package org.siloserver.silo.common.player.video

import org.siloserver.silo.model.catalog.AudioTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A remount can hand back the same tracks in a different order. Confirming a
 * pending audio switch by comparing ordinals would then either commit the wrong
 * language or strand the request forever, so confirmation has to re-resolve
 * identity against the current snapshot — which is what these cases model.
 */
class MountedAudioReorderTest {

    private val dutch = AudioTrack(
        codec = "aac", channels = 2, language = "nl", title = "Dutch AAC Stereo",
    )

    private val beforeRemount = listOf(
        MountedAudioTrack(0, "nl", "audio/mp4a-latm", 2, "Dutch AAC Stereo"),
        MountedAudioTrack(1, "en", "audio/vnd.dts", 6, "English DTS 5.1"),
    )

    /** Same tracks, opposite order: the ordinal that meant Dutch now means English. */
    private val afterRemount = listOf(
        MountedAudioTrack(0, "en", "audio/vnd.dts", 6, "English DTS 5.1"),
        MountedAudioTrack(1, "nl", "audio/mp4a-latm", 2, "Dutch AAC Stereo"),
    )

    @Test
    fun theSameChoiceResolvesToADifferentOrdinalAfterAReorder() {
        assertEquals(0, matchMountedAudioTrack(dutch, beforeRemount)?.ordinal)
        assertEquals(1, matchMountedAudioTrack(dutch, afterRemount)?.ordinal)
    }

    /**
     * Confirmation asks "is the selected track the one I wanted", by identity.
     * Ordinal 0 satisfies that before the reorder and must not after it.
     */
    @Test
    fun identityConfirmationSurvivesAReorderThatOrdinalComparisonWouldNotMatch() {
        val selectedOrdinalZeroBefore = beforeRemount.first { it.ordinal == 0 }
        val selectedOrdinalZeroAfter = afterRemount.first { it.ordinal == 0 }

        assertEquals(0, matchMountedAudioTrack(dutch, listOf(selectedOrdinalZeroBefore))?.ordinal)
        assertNull(
            matchMountedAudioTrack(dutch, listOf(selectedOrdinalZeroAfter)),
            "ordinal 0 is English after the reorder and must not confirm a Dutch request",
        )
    }

    /** An empty or partial snapshot resolves nothing, so the intent stays live. */
    @Test
    fun partialSnapshotsResolveNothingRatherThanMisResolving() {
        assertNull(matchMountedAudioTrack(dutch, emptyList()))
        assertNull(
            matchMountedAudioTrack(
                dutch,
                listOf(MountedAudioTrack(0, "en", "audio/vnd.dts", 6, "English DTS 5.1")),
            ),
        )
    }
}
