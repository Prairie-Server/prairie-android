package com.continuum.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.continuum.app.android.ui.navigation.AppNavigation
import com.continuum.app.android.ui.navigation.Route
import com.continuum.app.android.ui.screens.settings.ThemePreference
import com.continuum.app.android.ui.theme.ContinuumTheme
import com.continuum.app.android.ui.theme.ThemeManager
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.common.ui.components.StartupSplashVideo
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

private const val STARTUP_SPLASH_MINIMUM_MILLIS = 1_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeManager = get<ThemeManager>(ThemeManager::class.java)

        setContent {
            val themePref by themeManager.themePreference.collectAsState()
            var startRoute by remember { mutableStateOf<String?>(null) }
            var minimumSplashElapsed by remember { mutableStateOf(false) }
            val darkTheme = when (themePref) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(Unit) {
                startRoute = resolveStartDestination()
            }
            LaunchedEffect(Unit) {
                delay(STARTUP_SPLASH_MINIMUM_MILLIS)
                minimumSplashElapsed = true
            }

            ContinuumTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val resolvedRoute = startRoute
                    if (resolvedRoute == null || !minimumSplashElapsed) {
                        StartupSplashVideo()
                    } else {
                        AppNavigation(startDestination = resolvedRoute)
                    }
                }
            }
        }
    }

    /**
     * Mirrors iOS scene-resign / app-background flush — when the user
     * sends the app to the background, drain any debounced device
     * settings so the next session sees what they just toggled. Without
     * this, a process death within the 750ms debounce window would lose
     * the write.
     */
    override fun onStop() {
        super.onStop()
        val store = get<PlayerSettingsStore>(PlayerSettingsStore::class.java)
        lifecycleScope.launch { store.flushPendingDeviceSettings() }
    }

    /**
     * Decides which auth-flow screen to land on.
     *
     * The [ServerRegistry] is the source of truth for which servers are saved
     * and which one is active. The [TokenManager] holds the per-server tokens
     * for the active entry — both are loaded from EncryptedSharedPreferences
     * during DI construction, so by the time we run we just consult them.
     *
     *  - No active server → `ServerSetup` (registry is empty)
     *  - Active server but no access token → `Login`
     *  - Tokens but no profile selected for THIS server → `ProfileSelection`
     *  - All set → `Home`
     */
    private suspend fun resolveStartDestination(): String {
        val registry = get<ServerRegistry>(ServerRegistry::class.java)
        val tokenManager = get<TokenManager>(TokenManager::class.java)

        val activeEntry = registry.activeEntry.value
            ?: return Route.ServerSetup.route

        val accessToken = tokenManager.getAccessToken()
        if (accessToken.isNullOrBlank()) return Route.Login.route

        // Profile id is per-server: prefer the registry entry's saved value,
        // fall back to whatever the token manager has cached.
        val profileId = activeEntry.profileId ?: tokenManager.getProfileId()
        if (profileId.isNullOrBlank()) return Route.ProfileSelection.route

        return Route.Home.route
    }
}
