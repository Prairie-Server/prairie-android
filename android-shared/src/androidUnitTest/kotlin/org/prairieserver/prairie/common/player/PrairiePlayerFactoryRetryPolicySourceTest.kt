package org.prairieserver.prairie.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class PrairiePlayerFactoryRetryPolicySourceTest {
    private val source = File("src/androidMain/kotlin/org/prairieserver/prairie/common/player/PrairiePlayerFactory.kt")
        .readText()

    @Test
    fun mediaSourceFactoryUsesServerRestartRetryPolicy() {
        assertTrue(
            source.contains("val mediaLoadErrorHandlingPolicy = PrairieMediaLoadErrorHandlingPolicy(") &&
                source.split(".setLoadErrorHandlingPolicy(mediaLoadErrorHandlingPolicy)").size - 1 >= 2,
            "DefaultMediaSourceFactory must keep retrying transient manifest/segment failures during server restarts.",
        )
    }
}
