package org.siloserver.silo.android.ui.screens.player

import org.siloserver.silo.model.catalog.AudioTrack
import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class MobileSubtitleAutoSelectionTest {

    @Test
    fun autoSubtitlePreferenceDemotesClosedCaptionTitledTracksWhenPlainDialogueExists() {
        val subtitles = listOf(
            subtitle(index = 4, label = "English (CC)", language = "en"),
            subtitle(index = 7, label = "English", language = "en"),
        )

        assertEquals(
            MobileSubtitleAutoSelection.Select(1),
            resolveMobileAutoSubtitleSelection(
                audioTracks = listOf(audio(index = 0, language = "ja")),
                selectedAudioIndex = 0,
                subtitles = subtitles,
                preferredLanguage = "en",
                subtitleMode = "auto",
                showForcedSubtitles = true,
            ),
        )
    }

    @Test
    fun autoSubtitlePreferenceKeepsClosedCaptionTrackWhenItIsOnlyLanguageMatch() {
        val subtitles = listOf(
            subtitle(index = 4, label = "English (CC)", language = "en"),
        )

        assertEquals(
            MobileSubtitleAutoSelection.Select(0),
            resolveMobileAutoSubtitleSelection(
                audioTracks = listOf(audio(index = 0, language = "ja")),
                selectedAudioIndex = 0,
                subtitles = subtitles,
                preferredLanguage = "en",
                subtitleMode = "auto",
                showForcedSubtitles = true,
            ),
        )
    }

    @Test
    fun autoSubtitlePreferenceDoesNotTreatCcInsideWordsAsClosedCaption() {
        val subtitles = listOf(
            subtitle(index = 4, label = "Soccer Cut", language = "en"),
            subtitle(index = 7, label = "English", language = "en"),
        )

        assertEquals(
            MobileSubtitleAutoSelection.Select(0),
            resolveMobileAutoSubtitleSelection(
                audioTracks = listOf(audio(index = 0, language = "ja")),
                selectedAudioIndex = 0,
                subtitles = subtitles,
                preferredLanguage = "en",
                subtitleMode = "auto",
                showForcedSubtitles = true,
            ),
        )
    }

    @Test
    fun autoSubtitleResolverDisablesWhenAudioAlreadyMatchesPreferredLanguage() {
        assertEquals(
            MobileSubtitleAutoSelection.Disable,
            resolveMobileAutoSubtitleSelection(
                audioTracks = listOf(audio(index = 2, language = "eng")),
                selectedAudioIndex = 2,
                subtitles = listOf(subtitle(index = 1, label = "English", language = "en")),
                preferredLanguage = "en",
                subtitleMode = "auto",
                showForcedSubtitles = true,
            ),
        )
    }

    @Test
    fun autoSubtitleResolverSelectsForcedTrackWhenAudioAlreadyMatchesPreferredLanguage() {
        val subtitles = listOf(
            subtitle(index = 1, label = "English", language = "en"),
            subtitle(index = 2, label = "English Forced", language = "en", forced = true),
        )

        assertEquals(
            MobileSubtitleAutoSelection.Select(1),
            resolveMobileAutoSubtitleSelection(
                audioTracks = listOf(audio(index = 2, language = "eng")),
                selectedAudioIndex = 2,
                subtitles = subtitles,
                preferredLanguage = "en",
                subtitleMode = "auto",
                showForcedSubtitles = true,
            ),
        )
    }

    @Test
    fun alwaysModeSelectsPreferredLanguageEvenWhenAudioAlreadyMatches() {
        assertEquals(
            MobileSubtitleAutoSelection.Select(0),
            resolveMobileAutoSubtitleSelection(
                audioTracks = listOf(audio(index = 0, language = "en")),
                selectedAudioIndex = 0,
                subtitles = listOf(subtitle(index = 1, label = "English", language = "en")),
                preferredLanguage = "en",
                subtitleMode = "always",
                showForcedSubtitles = false,
            ),
        )
    }

    private fun audio(
        index: Int,
        language: String?,
    ): AudioTrack = AudioTrack(index = index, language = language)

    private fun subtitle(
        index: Int,
        label: String,
        language: String?,
        forced: Boolean = false,
        codec: String = "srt",
    ): PlayerSubtitleInfo = PlayerSubtitleInfo(
        index = index,
        language = language,
        codec = codec,
        label = label,
        source = null,
        forced = forced,
        url = "/stream/subtitles/$index.vtt",
    )
}
