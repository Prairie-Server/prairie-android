package org.siloserver.silo.tv.ui.screens.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class TvServerSetupUrlProbeTest {
    @Test
    fun bareHostProbesHttpsBeforeHttpFallback() {
        assertEquals(
            listOf("https://media.local", "http://media.local"),
            serverSetupUrlProbeCandidates(" media.local/ "),
        )
    }

    @Test
    fun explicitSchemeIsRespectedWithoutFallback() {
        assertEquals(
            listOf("http://media.local:8090"),
            serverSetupUrlProbeCandidates(" HTTP://MEDIA.LOCAL:8090/ "),
        )
        assertEquals(
            listOf("https://lib.strm.cafe"),
            serverSetupUrlProbeCandidates("https://lib.strm.cafe"),
        )
    }
}
