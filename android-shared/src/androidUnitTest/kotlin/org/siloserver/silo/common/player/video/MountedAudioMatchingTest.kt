package org.siloserver.silo.common.player.video

import org.siloserver.silo.model.catalog.AudioTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Strings here are the real ones observed on an NVIDIA Shield playing a
 * two-audio-track fixture: the catalog says `dts` / `aac` while Media3 reports
 * `audio/vnd.dts` / `audio/mp4a-latm`, and the catalog says `en` / `nl`.
 */
class MountedAudioMatchingTest {

    private val englishDts = AudioTrack(
        codec = "dts", channels = 6, language = "en", title = "English DTS 5.1", isDefault = true,
    )
    private val dutchAac = AudioTrack(
        codec = "aac", channels = 2, language = "nl", title = "Dutch AAC Stereo",
    )

    private val mountedBoth = listOf(
        MountedAudioTrack(
            ordinal = 0, language = "nl", codecOrMime = "audio/mp4a-latm",
            channelCount = 2, label = "Dutch AAC Stereo",
        ),
        MountedAudioTrack(
            ordinal = 1, language = "en", codecOrMime = "audio/vnd.dts",
            channelCount = 6, label = "English DTS 5.1",
        ),
    )

    @Test
    fun matchesAcrossCatalogAndMedia3Spellings() {
        assertEquals(1, matchMountedAudioTrack(englishDts, mountedBoth)?.ordinal)
        assertEquals(0, matchMountedAudioTrack(dutchAac, mountedBoth)?.ordinal)
    }

    /**
     * Media3 ordinals are not catalog ordinals. Here the mounted order is the
     * reverse of the catalog's, so a positional answer would be wrong both ways.
     */
    @Test
    fun resolvesByIdentityNotPosition() {
        assertEquals(1, matchMountedAudioTrack(englishDts, mountedBoth)?.ordinal)
    }

    /**
     * The transcode case, and the reason this must not be greedy: a DTS 5.1
     * source delivered as undetermined-language stereo AAC is NOT that track.
     * Returning it would play the wrong audio and skip the replan that was the
     * actual fix.
     */
    @Test
    fun transcodedRepresentationDoesNotMatchItsSource() {
        val delivered = listOf(
            MountedAudioTrack(
                ordinal = 0, language = null, codecOrMime = "audio/mp4a-latm",
                channelCount = 2, label = null,
            ),
        )
        assertNull(matchMountedAudioTrack(englishDts, delivered))
    }

    @Test
    fun ambiguousCandidatesReturnNullRatherThanGuessing() {
        val twoIdenticalEnglish = listOf(
            MountedAudioTrack(0, "en", "audio/mp4a-latm", 2, null),
            MountedAudioTrack(1, "en", "audio/mp4a-latm", 2, null),
        )
        val catalog = AudioTrack(codec = "aac", channels = 2, language = "en")
        assertNull(matchMountedAudioTrack(catalog, twoIdenticalEnglish))
    }

    /** Title is the only thing separating a commentary from the main mix. */
    @Test
    fun titleBreaksAnOtherwiseIdenticalTie() {
        val mounted = listOf(
            MountedAudioTrack(0, "en", "audio/mp4a-latm", 2, "Main"),
            MountedAudioTrack(1, "en", "audio/mp4a-latm", 2, "Director Commentary"),
        )
        val commentary = AudioTrack(
            codec = "aac", channels = 2, language = "en", title = "Director Commentary",
        )
        assertEquals(1, matchMountedAudioTrack(commentary, mounted)?.ordinal)
    }

    @Test
    fun emptyMountedListNeverMatches() {
        assertNull(matchMountedAudioTrack(englishDts, emptyList()))
    }

    @Test
    fun codecFamilyCanonicalisesBothSides() {
        assertEquals("aac", canonicalAudioCodecFamily("aac"))
        assertEquals("aac", canonicalAudioCodecFamily("audio/mp4a-latm"))
        assertEquals("aac", canonicalAudioCodecFamily("mp4a.40.2"))
        assertEquals("dts", canonicalAudioCodecFamily("dts"))
        assertEquals("dts", canonicalAudioCodecFamily("audio/vnd.dts"))
        assertEquals("eac3", canonicalAudioCodecFamily("audio/eac3"))
        assertEquals("eac3", canonicalAudioCodecFamily("ec-3"))
        assertEquals("ac3", canonicalAudioCodecFamily("audio/ac3"))
        assertEquals("truehd", canonicalAudioCodecFamily("audio/true-hd"))
        assertNull(canonicalAudioCodecFamily(null))
        assertNull(canonicalAudioCodecFamily("  "))
    }

    /** A catalog language with no mounted counterpart must not match blindly. */
    @Test
    fun languageMismatchIsNotAMatch() {
        val onlyDutch = listOf(
            MountedAudioTrack(0, "nl", "audio/mp4a-latm", 2, "Dutch AAC Stereo"),
        )
        assertNull(matchMountedAudioTrack(englishDts, onlyDutch))
    }
}
