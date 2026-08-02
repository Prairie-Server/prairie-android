package org.siloserver.silo.common.player.cast

import org.siloserver.silo.model.playback.EXTERNAL_TEXT_SIDECAR_SET_V1_FEATURE
import kotlin.test.Test
import kotlin.test.assertFalse

class CastPlaybackPreparerTest {
    @Test
    fun castContextDoesNotNegotiateLocalMedia3SidecarMounting() {
        assertFalse(
            EXTERNAL_TEXT_SIDECAR_SET_V1_FEATURE in chromecastPlaybackContext("test").features,
        )
    }
}
