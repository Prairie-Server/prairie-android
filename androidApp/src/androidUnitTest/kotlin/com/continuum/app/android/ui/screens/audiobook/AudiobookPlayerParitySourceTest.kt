package com.continuum.app.android.ui.screens.audiobook

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AudiobookPlayerParitySourceTest {
    private val root = "src/androidMain/kotlin/com/continuum/app/android/ui/screens/audiobook"
    private val player = File("$root/AudiobookPlayerScreen.kt").readText()
    private val cover = File("$root/AudiobookCoverHeader.kt").readText()
    private val transport = File("$root/AudiobookTransport.kt").readText()

    @Test
    fun fullPlayerUsesCoverTintedBackdropLikeIosNowPlaying() {
        assertTrue(
            player.contains("rememberDominantColor(state.coverUrl") &&
                player.contains("audiobookPlayerBackgroundBrush("),
            "Audiobook player should use cover-sampled tint behind the Now Playing surface.",
        )
        assertTrue(
            player.contains("Brush.verticalGradient"),
            "Audiobook player backdrop should be a full-screen vertical wash, not a flat app background.",
        )
    }

    @Test
    fun coverAndTransportUseIosPlayerScale() {
        assertTrue(
            cover.contains("AudiobookCoverArtSizeDp = 280") &&
                cover.contains(".shadow(") &&
                cover.contains("RoundedCornerShape(20.dp)"),
            "Audiobook player cover should keep the large iOS scale with a soft premium shadow.",
        )
        assertTrue(
            transport.contains(".size(82.dp)") &&
                transport.contains(".shadow("),
            "Audiobook player primary play control should match the iOS oversized 82pt accent button.",
        )
    }
}
