package org.siloserver.silo.common.player

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SiloPlayerFactoryMpvAuthSourceTest {
    private val source =
        File("src/androidMain/kotlin/org/siloserver/silo/common/player/SiloPlayerFactory.kt").readText()

    @Test fun mpvHeaderFetchDoesNotRunBlockingOnBuildThread() {
        assertTrue(
            !source.contains("runBlocking"),
            "SiloPlayerFactory must not use runBlocking anywhere; pre-fetch auth headers off the build thread.",
        )
    }
}
