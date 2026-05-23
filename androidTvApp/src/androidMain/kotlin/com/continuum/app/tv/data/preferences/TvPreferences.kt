package com.continuum.app.tv.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Playback quality ladder. Mirrors what the server's transcode pipeline accepts
 * and what tvOS exposes in Settings. "Auto" defers to the server.
 */
enum class PlaybackQuality(val label: String, val wireValue: String) {
    Auto("Auto", "auto"),
    Original("Original", "original"),
    P1080("1080p", "1080p"),
    P720("720p", "720p"),
    P480("480p", "480p");

    companion object {
        fun fromWire(value: String?): PlaybackQuality =
            values().firstOrNull { it.wireValue == value } ?: Auto
    }
}

/**
 * Subtitle rendering mode. Off hides subtitles entirely; Auto uses the user's
 * preferred language when available; Always forces subtitles on every track.
 */
enum class SubtitleMode(val label: String, val wireValue: String) {
    Off("Off", "off"),
    Auto("Auto", "auto"),
    Always("Always", "always");

    companion object {
        fun fromWire(value: String?): SubtitleMode =
            values().firstOrNull { it.wireValue == value } ?: Auto
    }
}

/**
 * Subtitle text size, rendered via the player cue style.
 */
enum class SubtitleSize(val label: String, val scale: Float) {
    Small("Small", 0.85f),
    Medium("Medium", 1.0f),
    Large("Large", 1.25f);

    companion object {
        fun fromLabel(value: String?): SubtitleSize =
            values().firstOrNull { it.label == value } ?: Medium
    }
}

/**
 * DataStore-backed user preferences for the Android TV app.
 *
 * Keys intentionally mirror the phone app's `SettingsViewModel` state so that
 * if we later sync preferences across clients we already speak the same wire
 * values (see [PlaybackQuality.wireValue], [SubtitleMode.wireValue]).
 *
 * Reads are cheap flows that the Settings screen can collect as state.
 * Writes are suspending; call from a ViewModel coroutine scope.
 */
class TvPreferences(private val context: Context) {

    private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "tv_prefs")

    private object Keys {
        val PlaybackQualityKey = stringPreferencesKey("playback_quality")
        val AudioLanguageKey = stringPreferencesKey("audio_language")
        val SubtitleModeKey = stringPreferencesKey("subtitle_mode")
        val SubtitleLanguageKey = stringPreferencesKey("subtitle_language")
        val SubtitleSizeKey = stringPreferencesKey("subtitle_size")
        val AutoPlayNextEpisodeKey = booleanPreferencesKey("auto_play_next")
        val AutoSkipIntroKey = booleanPreferencesKey("auto_skip_intro")
        val AutoSkipCreditsKey = booleanPreferencesKey("auto_skip_credits")
        val LibrariesSelectedLibraryIdKey = intPreferencesKey("libraries_selected_library_id")
    }

    val playbackQuality: Flow<PlaybackQuality> = context.store.data.map { prefs ->
        PlaybackQuality.fromWire(prefs[Keys.PlaybackQualityKey])
    }

    val audioLanguage: Flow<String> = context.store.data.map { prefs ->
        prefs[Keys.AudioLanguageKey] ?: ""
    }

    val subtitleMode: Flow<SubtitleMode> = context.store.data.map { prefs ->
        SubtitleMode.fromWire(prefs[Keys.SubtitleModeKey])
    }

    val subtitleLanguage: Flow<String> = context.store.data.map { prefs ->
        prefs[Keys.SubtitleLanguageKey] ?: ""
    }

    val subtitleSize: Flow<SubtitleSize> = context.store.data.map { prefs ->
        SubtitleSize.fromLabel(prefs[Keys.SubtitleSizeKey])
    }

    val autoPlayNextEpisode: Flow<Boolean> = context.store.data.map { prefs ->
        prefs[Keys.AutoPlayNextEpisodeKey] ?: true
    }

    val autoSkipIntro: Flow<Boolean> = context.store.data.map { prefs ->
        prefs[Keys.AutoSkipIntroKey] ?: false
    }

    val autoSkipCredits: Flow<Boolean> = context.store.data.map { prefs ->
        prefs[Keys.AutoSkipCreditsKey] ?: false
    }

    val selectedLibraryId: Flow<Int?> = context.store.data.map { prefs ->
        prefs[Keys.LibrariesSelectedLibraryIdKey]
    }

    suspend fun setPlaybackQuality(value: PlaybackQuality) {
        context.store.edit { it[Keys.PlaybackQualityKey] = value.wireValue }
    }

    suspend fun setAudioLanguage(value: String) {
        context.store.edit { it[Keys.AudioLanguageKey] = value }
    }

    suspend fun setSubtitleMode(value: SubtitleMode) {
        context.store.edit { it[Keys.SubtitleModeKey] = value.wireValue }
    }

    suspend fun setSubtitleLanguage(value: String) {
        context.store.edit { it[Keys.SubtitleLanguageKey] = value }
    }

    suspend fun setSubtitleSize(value: SubtitleSize) {
        context.store.edit { it[Keys.SubtitleSizeKey] = value.label }
    }

    suspend fun setAutoPlayNextEpisode(value: Boolean) {
        context.store.edit { it[Keys.AutoPlayNextEpisodeKey] = value }
    }

    suspend fun setAutoSkipIntro(value: Boolean) {
        context.store.edit { it[Keys.AutoSkipIntroKey] = value }
    }

    suspend fun setAutoSkipCredits(value: Boolean) {
        context.store.edit { it[Keys.AutoSkipCreditsKey] = value }
    }

    suspend fun setSelectedLibraryId(value: Int?) {
        context.store.edit { prefs ->
            if (value == null) {
                prefs.remove(Keys.LibrariesSelectedLibraryIdKey)
            } else {
                prefs[Keys.LibrariesSelectedLibraryIdKey] = value
            }
        }
    }
}
