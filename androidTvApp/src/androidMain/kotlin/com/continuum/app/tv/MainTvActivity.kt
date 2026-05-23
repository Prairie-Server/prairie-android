package com.continuum.app.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.common.ui.components.StartupSplashVideo
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import com.continuum.app.tv.ui.navigation.TvAppNavigation
import com.continuum.app.tv.ui.navigation.TvRoute
import com.continuum.app.tv.ui.theme.ContinuumTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

private const val STARTUP_SPLASH_MINIMUM_MILLIS = 1_000L

class MainTvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var startRoute by remember { mutableStateOf<String?>(null) }
            var minimumSplashElapsed by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                startRoute = resolveStartDestination()
            }
            LaunchedEffect(Unit) {
                delay(STARTUP_SPLASH_MINIMUM_MILLIS)
                minimumSplashElapsed = true
            }

            ContinuumTvTheme {
                val resolvedRoute = startRoute
                if (resolvedRoute == null || !minimumSplashElapsed) {
                    StartupSplashVideo()
                } else {
                    TvAppNavigation(
                        startDestination = resolvedRoute,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    /**
     * Mirror of the phone app's app-background flush — drain pending
     * device-setting writes when the user leaves so a process kill in
     * the debounce window doesn't lose what they just toggled.
     */
    override fun onStop() {
        super.onStop()
        val store = get<PlayerSettingsStore>(PlayerSettingsStore::class.java)
        lifecycleScope.launch { store.flushPendingDeviceSettings() }
    }

    /**
     * Mirrors the phone app's [com.continuum.app.android.MainActivity] startup
     * flow on top of the multi-server [ServerRegistry]. See that file for the
     * routing rules — they're identical: registry empty ⇒ ServerSetup,
     * tokens missing ⇒ Login, no profile for this server ⇒ ProfileSelection,
     * else Main.
     */
    private suspend fun resolveStartDestination(): String {
        val registry = get<ServerRegistry>(ServerRegistry::class.java)
        val tokenManager = get<TokenManager>(TokenManager::class.java)

        val activeEntry = registry.activeEntry.value
            ?: return TvRoute.ServerSetup.route

        val accessToken = tokenManager.getAccessToken()
        if (accessToken.isNullOrBlank()) return TvRoute.Login.route

        val profileId = activeEntry.profileId ?: tokenManager.getProfileId()
        if (profileId.isNullOrBlank()) return TvRoute.ProfileSelection.route

        return TvRoute.Main.route
    }
}
