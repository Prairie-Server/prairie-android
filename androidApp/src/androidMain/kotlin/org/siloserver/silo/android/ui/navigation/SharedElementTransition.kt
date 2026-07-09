package org.siloserver.silo.android.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Plumbing for Compose shared-element ("container transform") hero transitions
 * on phone/tablet. [AppNavigation] wraps its NavHost in a `SharedTransitionLayout`
 * and publishes the [SharedTransitionScope] here; each animated destination
 * publishes its own [AnimatedVisibilityScope]. A poster card (source) and the
 * detail backdrop hero (target) then opt into a shared-bounds morph keyed on the
 * content id — so the thing you tapped visibly carries you into the detail page —
 * without threading either scope through every composable signature.
 *
 * Both locals are null wherever no `SharedTransitionLayout` / destination scope is
 * in play (previews, tests, screens not yet wired). Call sites MUST treat null as
 * "no shared transition" and fall back to a plain modifier — [heroSharedBounds]
 * does exactly that, so it is always safe to call.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * The [AnimatedVisibilityScope] of the current NavHost destination, published by
 * the destination's `composable { }` lambda — its receiver is an
 * `AnimatedContentScope`, which is an [AnimatedVisibilityScope].
 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Shared-element key for an item's hero artwork. The list poster and the detail
 * backdrop for the same content id MUST resolve to the same key to morph into one
 * another, so both ends route through this single helper.
 */
fun heroSharedKey(contentId: String): String = "hero-$contentId"

/**
 * Per-screen registry that lets only ONE visible node own a hero key at a
 * time. The same item can appear in several home rows at once (Continue
 * Watching + a genre row); if both register `sharedBounds` under the same key,
 * the transition machinery treats them as a match and "morphs" between two
 * live cards — posters flicker and draw into the overlay above everything
 * while scrolling. Screens that can show duplicates provide a registry; the
 * first composed instance claims the key (released on dispose), later
 * duplicates render as plain cards — they still navigate, just without the
 * morph. The detail screen provides no registry, so the hero target always
 * registers and pairs with whichever card holds the claim.
 */
class HeroClaimRegistry {
    private val owners = mutableMapOf<String, Any>()

    fun tryClaim(key: String, owner: Any): Boolean {
        val current = owners[key]
        if (current == null) {
            owners[key] = owner
            return true
        }
        return current === owner
    }

    fun release(key: String, owner: Any) {
        if (owners[key] === owner) owners.remove(key)
    }
}

val LocalHeroClaimRegistry = compositionLocalOf<HeroClaimRegistry?> { null }

/**
 * Tags this node as the hero shared element for [contentId]. When both the
 * shared-transition scope and a destination visibility scope are available, a
 * source (poster) and target (detail backdrop) carrying the same id animate their
 * bounds into one another, crossfading the differing artwork. Otherwise this is a
 * no-op, so it is safe to call unconditionally.
 *
 * `sharedBounds` renders into the scope overlay during the transition, so any
 * clipping/shaping MUST be applied AFTER this in the chain —
 * callers do `.heroSharedBounds(id).clip(shape)`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.heroSharedBounds(contentId: String?): Modifier {
    if (contentId.isNullOrBlank()) return this
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val registry = LocalHeroClaimRegistry.current
    if (registry != null) {
        val key = heroSharedKey(contentId)
        val owner = remember { Any() }
        var claimed by remember(key) { mutableStateOf(false) }
        DisposableEffect(key) {
            claimed = registry.tryClaim(key, owner)
            onDispose {
                registry.release(key, owner)
                claimed = false
            }
        }
        if (!claimed) return this
    }
    return with(sharedScope) {
        this@heroSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = heroSharedKey(contentId)),
            animatedVisibilityScope = visibilityScope,
            // Images respond well to being remeasured to the animated bounds, so the
            // artwork crop-fills the morphing rectangle (poster → wide hero) every
            // frame instead of scaling a single stable layout.
            resizeMode = ResizeMode.RemeasureToBounds,
        )
    }
}
