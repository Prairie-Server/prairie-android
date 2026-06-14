package com.continuum.app.tv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
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
import com.continuum.app.tv.ui.screens.player.TvPlayerRemoteKeyBridge
import com.continuum.app.tv.ui.theme.ContinuumTvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.get

private const val STARTUP_SPLASH_MINIMUM_MILLIS = 1_000L
class MainTvActivity : ComponentActivity() {

    // Shared flow with [TvAppNavigation]. We publish the launching Uri here so
    // the navigation Composable can consume it once the auth chain has landed
    // the user on Main — see [handleIntent] and the collector in TvAppNavigation.
    private val pendingDeepLink: MutableStateFlow<Uri?> by inject(named("pendingDeepLink"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Capture the launching intent's Uri (if any) before Compose starts so
        // the navigation collector observes it as soon as it subscribes.
        handleIntent(intent)

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
     * Warm-launch deep links arrive here while the Activity is already alive
     * (singleTop / singleTask). Forward to [handleIntent] and update the
     * Activity's stored intent so [getIntent] reflects the latest payload.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (TvPlayerRemoteKeyBridge.dispatch(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * Pushes a `continuum://` deep-link Uri into the shared [pendingDeepLink]
     * flow for [TvAppNavigation] to consume. Non-`continuum` schemes (and
     * intents without data) are ignored so unrelated launch intents don't
     * clobber a queued URI. Nullable parameter to accommodate the cold-launch
     * call site where the Activity's intent may be null.
     */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "continuum") {
            pendingDeepLink.value = data
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
