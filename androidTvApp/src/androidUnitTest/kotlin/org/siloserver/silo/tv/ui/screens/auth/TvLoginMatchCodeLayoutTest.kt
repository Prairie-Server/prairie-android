package org.siloserver.silo.tv.ui.screens.auth

import kotlin.test.Test
import kotlin.test.assertTrue

class TvLoginMatchCodeLayoutTest {

    @Test
    fun `standard match code fits inside QR card content width`() {
        val rowWidthDp = matchCodeRowWidthDp("WILLOW-GRO")

        assertTrue(
            actual = rowWidthDp <= 252,
            message = "Expected match-code row to fit within 252dp, but it was ${rowWidthDp}dp",
        )
    }
}
