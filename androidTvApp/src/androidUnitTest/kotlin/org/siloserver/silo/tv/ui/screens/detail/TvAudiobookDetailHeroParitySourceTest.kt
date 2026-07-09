package org.siloserver.silo.tv.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvAudiobookDetailHeroParitySourceTest {
    private val detailScreenSource = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvItemDetailScreen.kt",
    ).readText()
    private val audiobookHeroFile = File(
        "src/androidMain/kotlin/org/siloserver/silo/tv/ui/screens/detail/TvAudiobookDetailHero.kt",
    )

    @Test
    fun audiobookDetailsUseDedicatedHeroInsteadOfGenericVideoHero() {
        assertTrue(
            detailScreenSource.contains("if (isAudiobook)") &&
                detailScreenSource.contains("TvAudiobookDetailHero("),
            "Audiobook details must branch to the dedicated tvOS-style audiobook hero.",
        )

        val audiobookHeroCall = detailScreenSource
            .substringAfter("if (isAudiobook)")
            .substringBefore("} else {")
        assertFalse(
            audiobookHeroCall.contains("TvDetailHero("),
            "Audiobook details must not render the generic video backdrop hero.",
        )
    }

    @Test
    fun audiobookHeroUsesSquareCoverAndCoverBlurBackdrop() {
        assertTrue(audiobookHeroFile.exists(), "Dedicated audiobook detail hero file must exist.")
        val heroSource = audiobookHeroFile.readText()

        assertTrue(heroSource.contains("ThumbhashImage("))
        assertTrue(heroSource.contains("url = detail.posterUrl"))
        assertTrue(heroSource.contains("thumbhash = detail.posterThumbhash"))
        assertTrue(heroSource.contains(".blur("))
        assertTrue(heroSource.contains("TvPoster("))
        assertTrue(heroSource.contains(".size(180.dp)"))
        assertTrue(heroSource.contains("text = \"AUDIOBOOK\""))
    }

    @Test
    fun audiobookHeroActionsStayAudioSpecific() {
        assertTrue(audiobookHeroFile.exists(), "Dedicated audiobook detail hero file must exist.")
        val heroSource = audiobookHeroFile.readText()

        assertTrue(heroSource.contains("TvPrimaryPillButton("))
        assertTrue(heroSource.contains("TvSecondaryPillButton("))
        assertTrue(heroSource.contains("title = audiobookPlayLabel("))
        assertTrue(heroSource.contains("title = \"Start Over\""))
        assertFalse(
            heroSource.contains("TvSquareToggleButton(") ||
                heroSource.contains("Watch Together") ||
                heroSource.contains("Version"),
            "Audiobook hero should not expose the video detail action cluster.",
        )
    }

    @Test
    fun audiobookDetailsRenderOverviewInTheFirstViewport() {
        assertTrue(audiobookHeroFile.exists(), "Dedicated audiobook detail hero file must exist.")
        val heroSource = audiobookHeroFile.readText()

        assertTrue(
            detailScreenSource.contains("overview = detail.overview"),
            "Audiobook details should pass the description into the dedicated hero.",
        )
        assertTrue(
            heroSource.contains("overview?.takeIf { it.isNotBlank() }") &&
                heroSource.contains("maxLines = 4"),
            "Audiobook description must render in the first viewport instead of requiring body scrolling.",
        )
        assertFalse(
            detailScreenSource.contains("TvAudiobookAboutSection("),
            "Audiobook details should not hide the primary description below the first fold.",
        )
    }

    @Test
    fun audiobookDetailsRenderPartsAndCollapsedChaptersBelowHero() {
        assertTrue(
            detailScreenSource.contains("buildAudiobookTimeline(") &&
                detailScreenSource.contains("val audiobookParts = audiobookTimeline?.tracks.orEmpty()"),
            "Audiobook details should derive parts from the stitched whole-book timeline tracks.",
        )
        assertTrue(
            detailScreenSource.contains("audiobookDisplayChapters(audiobookTimeline"),
            "Audiobook details should build stitched whole-book chapters from the timeline.",
        )
        assertTrue(
            detailScreenSource.contains("TvAudiobookPartsSection("),
            "Audiobook details should show parts when multiple playable files exist.",
        )
        assertTrue(
            detailScreenSource.contains("TvAudiobookChaptersSection("),
            "Audiobook details should show a collapsed chapter entry when chapters are available.",
        )
        assertTrue(
            detailScreenSource.contains("var chaptersDialogOpen by remember(detail.contentId)") &&
                detailScreenSource.contains("if (chaptersDialogOpen && audiobookChapters.isNotEmpty())"),
            "Audiobook chapters should open only after the viewer selects the collapsed Chapters row.",
        )
        assertTrue(
            detailScreenSource.contains("firstRowUpFocusRequester = playFocus") &&
                detailScreenSource.contains("upFocusRequester = if (audiobookParts.size > 1) null else playFocus"),
            "Audiobook body rows need an explicit Up path back into the hero controls.",
        )
    }

    @Test
    fun audiobookDetailsDoNotRenderGenericInfoDetailsSection() {
        assertTrue(
            detailScreenSource.contains("val showsDetailsSection = !isAudiobook && remember(detail) { detail.hasTvDetailFacts() }"),
            "Audiobook detail pages should not leak the generic video Info/Details section.",
        )
    }

    @Test
    fun audiobookDetailsUseTvOsAudioRelatedSectionsWithHeroEscapeFocus() {
        assertTrue(
            detailScreenSource.contains("val showsSimilarRail = !isAudiobook &&") &&
                detailScreenSource.contains("TvAudiobookRelatedRailSection("),
            "Audiobook detail should use tvOS audiobook related rails, not the generic video More Like This rail.",
        )
        assertTrue(
            detailScreenSource.contains("detail.audiobook?.related?.alsoByAuthor.orEmpty()") &&
                detailScreenSource.contains("detail.audiobook?.related?.similar.orEmpty()") &&
                detailScreenSource.contains("detail.audiobook?.series"),
            "Audiobook detail should read the server's tvOS-compatible audio related model.",
        )
        assertTrue(
            detailScreenSource.contains("onDirectionUp = returnToHero"),
            "Audiobook related rails need an explicit Up route back to Play.",
        )
    }
}
