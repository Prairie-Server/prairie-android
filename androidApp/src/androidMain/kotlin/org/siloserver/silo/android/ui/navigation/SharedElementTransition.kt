package org.siloserver.silo.android.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
 * first composed instance claims the key and non-claimants render as plain
 * cards.
 *
 * Ownership is snapshot-backed ([mutableStateMapOf]), so composables that read
 * it via [ownerOf] recompose when the owner changes. This makes the claim
 * *re-offerable*: when the current claimant disposes (e.g. a LazyRow recycles
 * its card off-screen) it releases the key, the map change wakes the surviving
 * duplicates, and one of them re-claims — so the still-visible copy takes over
 * the morph instead of the key going dead until Home next recomposes.
 *
 * The detail screen provides no registry, so the hero target always registers
 * and pairs with whichever card currently holds the claim.
 */
class HeroClaimRegistry {
    private val owners = mutableStateMapOf<String, Any>()

    /**
     * Current owner token for [key], or null if unclaimed. Reading this inside
     * composition subscribes the caller to ownership changes (snapshot map), so
     * a released/reassigned key re-triggers the reading composable.
     */
    fun ownerOf(key: String): Any? = owners[key]

    /**
     * Atomically take [key] for [owner] if it is free, or confirm [owner]
     * already holds it. Returns false when another owner holds the claim, so a
     * duplicate never double-claims a key that is already spoken for.
     */
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
 *
 * When a [HeroClaimRegistry] is provided (screens that can show the same item
 * in multiple rows), only the card holding the claim opts into the morph;
 * non-claimant duplicates render as plain cards. Note this does NOT mean a
 * non-claimant "navigates without a morph": if any claimant card is on screen,
 * the shared key is still matched, so the morph animates from that claimant
 * card — even when you tapped a different duplicate. The plain duplicate simply
 * isn't the visual source. The claim is snapshot-observed and re-taken whenever
 * the key falls free (see [HeroClaimRegistry]); the very first claim still lands
 * one frame after composition (the source card renders plain for that frame),
 * which is acceptable and unchanged.
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
        // Observe live ownership; the snapshot map recomposes us when the owner
        // changes, so a released key re-offers itself to survivors.
        val currentOwner = registry.ownerOf(key)
        val claimed = currentOwner === owner
        // (Re-)claim whenever the key is unowned. This covers the initial claim
        // AND re-offering after the previous claimant disposes (LazyRow
        // recycling). tryClaim is atomic, so concurrent survivors can't
        // double-own — losers see currentOwner become non-null and stay plain.
        LaunchedEffect(key, currentOwner) {
            if (currentOwner == null) registry.tryClaim(key, owner)
        }
        DisposableEffect(key, owner) {
            onDispose { registry.release(key, owner) }
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
