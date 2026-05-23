package com.continuum.app.android.ui.theme

import android.content.Context
import com.continuum.app.android.ui.screens.settings.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the app-wide theme preference, persisted to SharedPreferences.
 * Observable via [themePreference] so that the Activity recomposes when the theme changes.
 */
class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences("continuum_settings", Context.MODE_PRIVATE)

    private val _themePreference = MutableStateFlow(loadTheme())
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    fun setTheme(theme: ThemePreference) {
        prefs.edit().putString("theme", theme.name).apply()
        _themePreference.value = theme
    }

    private fun loadTheme(): ThemePreference {
        val name = prefs.getString("theme", null) ?: return ThemePreference.SYSTEM
        return try {
            ThemePreference.valueOf(name)
        } catch (_: IllegalArgumentException) {
            ThemePreference.SYSTEM
        }
    }
}
