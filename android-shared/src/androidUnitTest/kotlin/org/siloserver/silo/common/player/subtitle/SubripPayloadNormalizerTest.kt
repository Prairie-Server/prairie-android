package org.siloserver.silo.common.player.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubripPayloadNormalizerTest {
    @Test
    fun timecodeFirstSubripIsNumberedForMedia3() {
        val loose = """
            00:40:29.476 --> 00:40:32.356
            We need to get to the target
            as soon as possible.
            00:40:32.356 --> 00:40:35.516
            The journey to Smarhon's six hours.
        """.trimIndent()

        assertEquals(
            """
                1
                00:40:29,476 --> 00:40:32,356
                We need to get to the target
                as soon as possible.

                2
                00:40:32,356 --> 00:40:35,516
                The journey to Smarhon's six hours.
            """.trimIndent(),
            normalizeSubripTextIfNeeded(loose),
        )
    }

    @Test
    fun numberedSubripIsLeftAlone() {
        val numbered = """
            1
            00:00:01,000 --> 00:00:02,000
            Hello.

            2
            00:00:03,000 --> 00:00:04,000
            Goodbye.
        """.trimIndent()

        assertEquals(numbered, normalizeSubripTextIfNeeded(numbered))
    }

    @Test
    fun webvttHeaderWithTimecodeFirstCuesIsConvertedToNumberedSubrip() {
        val loose = """
            WEBVTT

            00:00:08.092 --> 00:00:10.532
            The populist politician Manfred Fest

            00:00:10.532 --> 00:00:12.932
            was struck by a sniper in Munich.
        """.trimIndent()

        assertEquals(
            """
                1
                00:00:08,092 --> 00:00:10,532
                The populist politician Manfred Fest

                2
                00:00:10,532 --> 00:00:12,932
                was struck by a sniper in Munich.
            """.trimIndent(),
            normalizeSubripTextIfNeeded(loose),
        )
    }

    @Test
    fun numberedWebvttStyleTimingsAreConvertedToStrictSubrip() {
        val loose = """
            WEBVTT

            1
            00:00:08.092 --> 00:00:10.532
            The populist politician Manfred Fest

            2
            00:00:10.532 --> 00:00:12.932
            was struck by a sniper in Munich.
        """.trimIndent()

        assertEquals(
            """
                1
                00:00:08,092 --> 00:00:10,532
                The populist politician Manfred Fest

                2
                00:00:10,532 --> 00:00:12,932
                was struck by a sniper in Munich.
            """.trimIndent(),
            normalizeSubripTextIfNeeded(loose),
        )
    }

    @Test
    fun normalizerDoesNotLogEverySubtitlePayloadAtInfo() {
        val source = java.io.File(
            "src/androidMain/kotlin/org/siloserver/silo/common/player/subtitle/SubripPayloadNormalizer.kt",
        ).readText()

        assertFalse(
            source.contains("Log.i("),
            "subtitle normalization runs per payload during playback and must not flood normal logcat",
        )
        assertFalse(source.contains("SubRip payload len="))
    }
}
