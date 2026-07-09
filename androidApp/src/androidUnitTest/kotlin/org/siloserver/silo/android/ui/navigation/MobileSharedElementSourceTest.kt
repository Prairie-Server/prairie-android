package org.siloserver.silo.android.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Source guard for the phase-6 shared-element hero transition. The morph only
 * works when every link in the chain is present: the host publishes the scopes,
 * the poster cards enroll as sources, and the detail backdrop enrolls as the
 * matching target. This codebase has a recurring failure mode of polish that is
 * built but silently unwired — these assertions fail loudly if any end is dropped.
 */
class MobileSharedElementSourceTest {
    private fun read(path: String) = File(path).readText()

    private val sharedElement = read(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/navigation/SharedElementTransition.kt",
    )
    private val appNavigation = read(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/navigation/AppNavigation.kt",
    )
    private val mediaCard = read(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/components/MediaCard.kt",
    )
    private val mediaRow = read(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/components/MediaRow.kt",
    )
    private val similarRail = read(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/SimilarRail.kt",
    )
    private val detailShared = read(
        "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens/detail/DetailSharedComponents.kt",
    )

    @Test
    fun sharedScopesArePublishedFromTheHost() {
        assertTrue(
            appNavigation.contains("SharedTransitionLayout(modifier = Modifier.fillMaxSize())") &&
                appNavigation.contains("LocalSharedTransitionScope provides this") &&
                appNavigation.contains("LocalHeroSourceHandoff provides heroSourceHandoff") &&
                appNavigation.contains("CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this)"),
            "AppNavigation must wrap the NavHost in a SharedTransitionLayout and publish " +
                "the shared-transition scope, the per-destination visibility scope, and the " +
                "hero source hand-off.",
        )
    }

    @Test
    fun helperDefinesScopesAndBoundsModifier() {
        assertTrue(
            sharedElement.contains("val LocalSharedTransitionScope") &&
                sharedElement.contains("val LocalNavAnimatedVisibilityScope") &&
                sharedElement.contains("class HeroSourceHandoff") &&
                sharedElement.contains("fun Modifier.heroSource(") &&
                sharedElement.contains("fun Modifier.heroTarget(") &&
                sharedElement.contains("ResizeMode.RemeasureToBounds"),
            "SharedElementTransition must expose both scope locals, the source hand-off, and " +
                "heroSource/heroTarget modifiers that remeasure artwork to the animated bounds.",
        )
    }

    @Test
    fun posterCardsEnrollAsHeroSources() {
        assertTrue(
            mediaCard.contains("sharedContentId: String? = null") &&
                mediaCard.contains(".heroSource(heroKey)") &&
                // Unique per-placement key so duplicate content ids in different
                // rows never collide (the flicker fix).
                mediaCard.contains("UUID.randomUUID()") &&
                mediaCard.contains("heroHandoff?.pendingKey = heroKey"),
            "MediaCard must derive a unique per-placement hero key, publish it on tap, and " +
                "tag its poster as the hero source.",
        )
        assertTrue(
            mediaRow.contains("sharedContentId = item.contentId"),
            "Home/media rails must pass each item's id so its poster can morph into the hero.",
        )
        assertTrue(
            similarRail.contains("sharedContentId = item.contentId"),
            "The detail 'More Like This' rail must enroll its posters as hero sources.",
        )
    }

    @Test
    fun detailBackdropIsTheHeroTarget() {
        assertTrue(
            detailShared.contains("contentId = detail.contentId") &&
                detailShared.contains(".heroTarget()"),
            "The detail backdrop must tag itself as the hero target that pairs with the " +
                "tapped poster via the source hand-off.",
        )
    }
}
