package org.prairieserver.prairie.model.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackSettingsKeysTest {
    @Test
    fun deviceSettingsContainsSyncedKeysAndExcludesLocalOnly() {
        assertTrue(PlaybackSettingsKeys.PreferredQuality in PlaybackSettingsKeys.DeviceSettings)
        assertTrue(PlaybackSettingsKeys.SubtitleAppearance in PlaybackSettingsKeys.DeviceSettings)
        assertTrue(PlaybackSettingsKeys.DownloadsWifiOnly !in PlaybackSettingsKeys.DeviceSettings)
        assertTrue(PlaybackSettingsKeys.ResumeRewindSeconds !in PlaybackSettingsKeys.DeviceSettings)
        assertTrue(PlaybackSettingsKeys.DeviceSettings.size >= 20)
        assertEquals("playback.preferred_quality", PlaybackSettingsKeys.PreferredQuality)
        assertEquals("player.passout_threshold", PlaybackSettingsKeys.PassOutThreshold)
    }

    @Test
    fun effectiveSettingAndSubtitleAppearanceRoundTripHelpers() {
        val setting = EffectiveSetting(
            key = "k",
            effectiveValue = "v",
            source = "user",
            userValue = "v",
        )
        assertEquals("k", setting.key)
        val appearance = EffectiveSubtitleAppearance(
            key = "subtitle_appearance",
            globalValue = "{}",
            effectiveValue = "{}",
        )
        assertEquals("{}", appearance.effectiveValue)
        assertTrue(SubtitleAppearance.DEFAULT.toJsonString().contains("fontSize"))
        assertTrue(SubtitleAppearance(fontColor = "bad").sanitized().fontColor.startsWith("#"))
        assertEquals(36.0, SubtitleFontSizePreset.Small.pointSize)
        assertEquals(100, SubtitlePositionPreset.Bottom.legacyPosition)
    }
}
