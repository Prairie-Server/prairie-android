package com.continuum.app.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ContinuumPlayerFactoryMpvAuthSourceTest {
    private val source =
        File("src/androidMain/kotlin/com/continuum/app/common/player/ContinuumPlayerFactory.kt").readText()

    @Test fun mpvHeaderFetchDoesNotRunBlockingOnBuildThread() {
        assertTrue(
            !source.contains("runBlocking"),
            "ContinuumPlayerFactory must not use runBlocking anywhere; pre-fetch auth headers off the build thread.",
        )
    }
}
