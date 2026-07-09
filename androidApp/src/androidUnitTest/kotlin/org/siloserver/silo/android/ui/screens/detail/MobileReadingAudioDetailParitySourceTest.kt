package org.siloserver.silo.android.ui.screens.detail

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class MobileReadingAudioDetailParitySourceTest {
    private val root = "src/androidMain/kotlin/org/siloserver/silo/android/ui/screens"
    private val bookDetail = File("$root/book/BookDetailContent.kt").readText()
    private val audiobookDetail = File("$root/audiobook/AudiobookDetailContent.kt").readText()

    @Test
    fun bookDetailUsesPremiumCenteredHeaderAndLibraryActions() {
        assertTrue(
            bookDetail.contains("BookCoverWidthDp = 168"),
            "Book detail should use a stable premium cover width instead of the old compact side-by-side header.",
        )
        assertTrue(
            bookDetail.contains("horizontalAlignment = Alignment.CenterHorizontally"),
            "Book detail header should center the cover, title, and metadata like the phone detail/audiobook master.",
        )
        assertTrue(
            bookDetail.contains("CircleActionButton(") &&
                bookDetail.contains("onClick = onFavoriteClick") &&
                bookDetail.contains("onClick = onWatchlistClick"),
            "Book detail should expose favorite and watchlist controls instead of accepting unused callbacks.",
        )
        assertTrue(
            bookDetail.contains("text = \"About\""),
            "Book overview should be presented as an explicit About section.",
        )
    }

    @Test
    fun audiobookDetailMatchesIosCenteredHeaderAndImmediateChapters() {
        assertTrue(
            audiobookDetail.contains("AudiobookCoverSizeDp = 220"),
            "Audiobook detail should use the iOS phone cover scale.",
        )
        assertTrue(
            audiobookDetail.contains("horizontalAlignment = Alignment.CenterHorizontally"),
            "Audiobook detail should center cover, title, metadata, and actions on phone.",
        )
        assertTrue(
            audiobookDetail.contains("Text(\"Start Over\")") &&
                audiobookDetail.contains("onPlayFromStartClick()"),
            "Audiobook detail should keep the iOS Start Over action visible next to Play/Resume.",
        )
        assertTrue(
            audiobookDetail.contains("mutableStateOf(true)"),
            "Audiobook chapters should be expanded by default, matching the iOS detail surface.",
        )
        assertTrue(
            audiobookDetail.contains("CircleActionButton(") &&
                audiobookDetail.contains("onClick = onFavoriteClick") &&
                audiobookDetail.contains("onClick = onWatchlistClick"),
            "Audiobook detail should expose favorite and watchlist controls instead of accepting unused callbacks.",
        )
    }
}
