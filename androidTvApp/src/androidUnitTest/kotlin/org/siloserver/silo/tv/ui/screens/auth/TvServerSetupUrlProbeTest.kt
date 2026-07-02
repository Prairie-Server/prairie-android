package org.siloserver.silo.tv.ui.screens.auth

import kotlinx.coroutines.test.runTest
import org.siloserver.silo.model.auth.SetupStatusResponse
import org.siloserver.silo.model.auth.SignupStatusResponse
import org.siloserver.silo.network.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    @Test
    fun probeCandidatesContinueToHttpFallbackAfterHttpsHttpError() = runTest {
        val setupRequests = mutableListOf<String>()
        val signupRequests = mutableListOf<String>()

        val result = probeTvServerSetupCandidates(
            candidates = serverSetupUrlProbeCandidates("media.local"),
            getSetupStatus = { candidate ->
                setupRequests += candidate
                if (candidate.startsWith("https://")) {
                    ApiResult.Error(502, "bad_gateway", "TLS endpoint failed")
                } else {
                    ApiResult.Success(SetupStatusResponse(needsSetup = false))
                }
            },
            getSignupStatus = { candidate ->
                signupRequests += candidate
                ApiResult.Success(SignupStatusResponse(enabled = true))
            },
        )

        val success = assertIs<TvServerSetupProbeResult.Success>(result)
        assertEquals("http://media.local", success.serverUrl)
        assertEquals(TvServerSetupDestination.Login(signupEnabled = true), success.destination)
        assertEquals(listOf("https://media.local", "http://media.local"), setupRequests)
        assertEquals(listOf("http://media.local"), signupRequests)
    }
}
