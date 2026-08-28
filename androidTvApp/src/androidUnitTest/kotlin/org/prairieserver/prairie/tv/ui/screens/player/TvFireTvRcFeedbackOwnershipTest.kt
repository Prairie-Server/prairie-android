package org.prairieserver.prairie.tv.ui.screens.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TvFireTvRcFeedbackOwnershipTest {

    @Test
    fun `release display version keeps the complete tag`() {
        val gradle = source("build.gradle.kts")
        val workflow = source("../.github/workflows/release.yml")

        assertTrue(gradle.contains("PRAIRIE_DISPLAY_VERSION"))
        assertTrue(gradle.contains("\"DISPLAY_VERSION\""))
        assertTrue(workflow.contains("PRAIRIE_DISPLAY_VERSION: \${{ needs.setup.outputs.version }}"))
    }

    private fun source(path: String): String = File(path).readText()
}
