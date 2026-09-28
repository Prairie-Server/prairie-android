package org.prairieserver.prairie.model.settings

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettingsPresentationRulesTest {

    @Test
    fun cardPresentationLabelsAndRawParsing() {
        assertEquals(listOf("Compact", "Standard", "Large"), CardPosterSize.entries.map { it.displayName })
        assertEquals(CardPosterSize.Large, CardPosterSize.fromRaw("large"))
        assertNull(CardPosterSize.fromRaw("huge"))
        assertNull(CardPosterSize.fromRaw(null))

        assertEquals(listOf("Title & Metadata", "Title Only", "Artwork Only"), CardCaption.entries.map { it.displayName })
        assertEquals(CardCaption.Artwork, CardCaption.fromRaw("artwork"))
        assertNull(CardCaption.fromRaw("TITLE"))
        assertNull(CardCaption.fromRaw(null))

        assertEquals(listOf("Balanced", "Compact", "Cinema", "Artwork Only"), CardPresentationPreset.entries.map { it.displayName })
    }

    @Test
    fun cardPresentationDecodeOrDefaultFallsBackOnBadElements() {
        val cinema = buildJsonObject {
            put("poster_size", "large")
            put("caption", "title")
        }
        assertEquals(CardPresentationPreset.Cinema, CardPresentation.decodeOrDefault(cinema).preset)
        assertEquals(CardPresentation.DEFAULT, CardPresentation.decodeOrDefault(JsonNull))
        assertEquals(CardPresentation.DEFAULT, CardPresentation.decodeOrDefault(buildJsonObject { put("caption", "subtitle") }))
        assertEquals(CardPresentation.DEFAULT, CardPresentation.decodeOrDefault(null as String?))
        // A preset-less pair renders as "Custom".
        assertNull(CardPresentation(CardPosterSize.Compact, CardCaption.Artwork).preset)
    }

    @Test
    fun seekIntervalPairEditsOneDirectionAndConvertsToMillis() {
        val pair = SeekIntervalPair(10, 30)
        assertEquals(SeekIntervalPair(5, 30), pair.with(SeekDirection.Back, 5))
        assertEquals(SeekIntervalPair(10, 90), pair.with(SeekDirection.Forward, 90))
        assertEquals(10_000L, pair.backMs)
        assertEquals(30_000L, pair.forwardMs)
        assertEquals("45 seconds", SeekIntervals.label(45))
    }

    @Test
    fun seekIntervalStateEditsOnlyTheChosenMedia() {
        val state = SeekIntervalState(support = SeekIntervalSupport.Supported)
        val edited = state.with(SeekMedia.Audiobook, SeekDirection.Forward, 60)
        assertEquals(SeekIntervals.DEFAULTS, edited.pair(SeekMedia.Video))
        assertEquals(SeekIntervalPair(10, 60), edited.pair(SeekMedia.Audiobook))

        val video = state.with(SeekMedia.Video, SeekDirection.Back, 15)
        assertEquals(SeekIntervalPair(15, 30), video.pair(SeekMedia.Video))
        assertEquals(SeekIntervals.DEFAULTS, video.pair(SeekMedia.Audiobook))
    }

    @Test
    fun legacyIntervalsOnlyOfferContractValuesForImport() {
        assertFalse(LegacyAudiobookIntervals().hasImportable)
        assertFalse(LegacyAudiobookIntervals(backSeconds = 7).hasImportable)
        val legacy = LegacyAudiobookIntervals(backSeconds = 7, forwardSeconds = 45)
        assertTrue(legacy.hasImportable)
        assertEquals(mapOf(SeekDirection.Forward to 45), legacy.importable)
    }

    @Test
    fun seekImportDescribesFailuresWithAndWithoutMessage() {
        val result = SeekImportResult(
            back = SeekImportOutcome.Failed(10, "  "),
            forward = SeekImportOutcome.Invalid(7),
        )
        assertTrue(result.anyFailed)
        assertFalse(result.allImported)
        assertEquals("Skip back (10s) failed. Skip forward value 7s is not supported.", result.describe())
        val onlyForwardFailed = SeekImportResult(SeekImportOutcome.NotStored, SeekImportOutcome.Failed(30, "offline"))
        assertTrue(onlyForwardFailed.anyFailed)
        assertEquals("Skip back had no device value. Skip forward (30s) failed: offline.", onlyForwardFailed.describe())
        assertFalse(SeekImportResult(SeekImportOutcome.Imported(5), SeekImportOutcome.NotStored).anyFailed)
    }

    @Test
    fun seekDecodeRejectsStringsFractionsAndOffListNumbers() {
        assertEquals(15, SeekIntervals.decode(JsonPrimitive(15), SeekDirection.Back))
        assertEquals(10, SeekIntervals.decode(JsonPrimitive("15"), SeekDirection.Back))
        assertEquals(30, SeekIntervals.decode(JsonPrimitive(15.5), SeekDirection.Forward))
        assertEquals(30, SeekIntervals.decode(JsonPrimitive(20), SeekDirection.Forward))
    }

    @Test
    fun subtitlePositionPresetsMapToLegacyPercentages() {
        assertEquals(0, SubtitlePositionPreset.Top.legacyPosition)
        assertEquals(70, SubtitlePositionPreset.LowerThird.legacyPosition)
        assertEquals(100, SubtitlePositionPreset.Bottom.legacyPosition)
    }

    @Test
    fun subtitleAppearanceSanitizesColorsAndOpacity() {
        val clean = SubtitleAppearance(fontColor = "#ABCDEF")
        assertSame(clean, clean.sanitized())

        val dirty = SubtitleAppearance(
            fontColor = "red",
            backgroundColor = "#12345",
            textOutlineColor = "#GGGGGG",
            backgroundOpacity = 140,
        ).sanitized()
        assertEquals("#ffffff", dirty.fontColor)
        assertEquals("#000000", dirty.backgroundColor)
        assertEquals("#000000", dirty.textOutlineColor)
        assertEquals(100, dirty.backgroundOpacity)
        assertEquals(0, SubtitleAppearance(backgroundOpacity = -5).sanitized().backgroundOpacity)
        // Hex without a leading '#' is accepted as-is.
        assertEquals("00ff00", SubtitleAppearance(fontColor = "00ff00").sanitized().fontColor)
    }

    @Test
    fun subtitleAppearanceJsonRoundTripIsSanitizedAndTolerant() {
        val encoded = SubtitleAppearance(backgroundOpacity = 250, position = SubtitlePositionPreset.Top).toJsonString()
        val decoded = SubtitleAppearance.decode(encoded)
        assertEquals(100, decoded.backgroundOpacity)
        assertEquals(SubtitlePositionPreset.Top, decoded.position)
        assertEquals(SubtitleAppearance.DEFAULT, SubtitleAppearance.decode("{not json"))
        assertEquals(SubtitleAppearance.DEFAULT, SubtitleAppearance.decode("""{"fontSize":"gigantic"}"""))
        assertEquals(SubtitleAppearance.DEFAULT, SubtitleAppearance.decode(""))
    }
}
