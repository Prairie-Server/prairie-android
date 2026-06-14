package com.continuum.app.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import com.continuum.app.android.ui.navigation.deviceLoginPairRouteOrNull
import com.continuum.app.android.ui.navigation.hasLocalDownloadsForScope
import com.continuum.app.android.ui.screens.settings.ThemePreference
import com.continuum.app.android.ui.theme.ContinuumTheme
import com.continuum.app.android.ui.theme.ThemeManager
import com.continuum.app.common.settings.PlayerSettingsStore
import com.continuum.app.common.ui.components.StartupSplashVideo
import com.continuum.app.network.ServerRegistry
import com.continuum.app.network.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

private const val STARTUP_SPLASH_MINIMUM_MILLIS = 1_000L

class MainActivity : ComponentActivity() {

    private val incomingDeviceLoginRoutes = MutableSharedFlow<String>(extraBufferCapacity = 1)

    // POST_NOTIFICATIONS is required on Android 13+ for any notification —
    // download progress / completion notifications silently never appear
    // without it.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* fire-and-forget */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()

        val themeManager = get<ThemeManager>(ThemeManager::class.java)

        setContent {
            val themePref by themeManager.themePreference.collectAsState()
            var startRoute by remember { mutableStateOf<String?>(null) }
            var pendingExternalRoute by remember { mutableStateOf<String?>(null) }
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
                incomingDeviceLoginRoutes.collect { route ->
                    pendingExternalRoute = route
                }
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
                        AppNavigation(
                            startDestination = resolvedRoute,
                            pendingExternalRoute = pendingExternalRoute,
                            onExternalRouteConsumed = { pendingExternalRoute = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deviceLoginPairRouteOrNull(intent.dataString)?.let { route ->
            incomingDeviceLoginRoutes.tryEmit(route)
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

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val perm = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) return
        requestNotificationPermission.launch(perm)
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
        deviceLoginPairRouteOrNull(intent?.dataString)?.let { return it }

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

        // Offline-with-downloads fast path: if the device has no network AND
        // we have downloaded media on disk, land directly on the Downloads
        // tab so the user isn't greeted with HomeScreen's "Something went
        // wrong / Check your connection" error. Online flows are unchanged.
        if (!isOnline() && hasLocalDownloads(activeEntry.id, profileId)) {
            return Route.Downloads.route
        }

        return Route.Home.route
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
            ?: return true   // unknown — assume online so we don't surprise online users
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun hasLocalDownloads(serverId: String, profileId: String): Boolean {
        val storage = get<com.continuum.app.common.downloads.DownloadStorage>(
            com.continuum.app.common.downloads.DownloadStorage::class.java
        )
        return hasLocalDownloadsForScope(storage, serverId, profileId)
    }
}
