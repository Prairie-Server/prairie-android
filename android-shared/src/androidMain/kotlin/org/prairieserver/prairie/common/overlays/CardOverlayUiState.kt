package org.prairieserver.prairie.common.overlays

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import org.prairieserver.prairie.common.settings.OverlayPrefsStore
import org.prairieserver.prairie.overlays.CardOverlayPrefs
import org.prairieserver.prairie.overlays.OverlaySchema

/**
 * Ambient card-overlay configuration published once near the app shell and
 * consumed by every poster/still card. Keeps cards from each injecting the
 * [OverlayPrefsStore] (and re-hydrating it) per instance — the shell hydrates
 * once and provides the resolved [enabled] + [prefs] down the tree.
 *
 * Default is disabled with registry-default prefs, so any card rendered
 * outside a [ProvideCardOverlays] scope simply draws no overlay layer.
 */
@Immutable
data class CardOverlayUiState(
    val enabled: Boolean,
    val prefs: CardOverlayPrefs,
)

val LocalCardOverlayUiState: ProvidableCompositionLocal<CardOverlayUiState> =
    staticCompositionLocalOf {
        CardOverlayUiState(enabled = false, prefs = OverlaySchema.buildDefaults())
    }

/**
 * Hydrates the [store], collects `enabled` + `prefs`, and publishes them via
 * [LocalCardOverlayUiState] for the [content] subtree. Place this high in each
 * client (app shell / main screen) so the prefs are loaded once per session.
 * The caller injects the singleton [OverlayPrefsStore] (each client has Koin
 * compose support) and hands it in here.
 *
 * [sessionKey] is the authenticated identity that the prefs belong to — the
 * active profile id (or session token). The hydration [LaunchedEffect] is keyed
 * on it rather than on a one-shot `Unit`, because this composable mounts above
 * the whole nav graph and is first composed on the *unauthenticated* Login
 * screen, where the `/settings` calls fail and the store leaves
 * `hasHydrated=false`. When the user signs in / selects a profile the key flips
 * from `null` to the profile id, the effect re-runs, and `hydrateIfNeeded()`
 * retries and succeeds — so cards pick up the admin kill-switch and the user's
 * saved prefs instead of rendering registry defaults forever. A profile switch
 * (key changes to a different id) likewise re-hydrates for the new identity.
 */
@Composable
fun ProvideCardOverlays(
    store: OverlayPrefsStore,
    sessionKey: Any? = null,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(store, sessionKey) {
        // Only attempt hydration once we have an authenticated identity; on the
        // Login screen `sessionKey` is null and the settings calls would 401.
        if (sessionKey != null) store.hydrateIfNeeded()
    }
    val enabled by store.enabled.collectAsState()
    val prefs by store.prefs.collectAsState()
    val state = remember(enabled, prefs) { CardOverlayUiState(enabled = enabled, prefs = prefs) }
    CompositionLocalProvider(LocalCardOverlayUiState provides state, content = content)
}
