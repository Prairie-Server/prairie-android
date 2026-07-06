package org.siloserver.silo.cast

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SiloCastMessageTest {
    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    @Test
    fun controlCommandUsesAppleSnakeCaseNames() {
        val encoded = json.encodeToString(SiloCastMessage.serializer(), SiloCastMessage.Control(SiloCastControlCommand.playPause()))

        assertTrue(encoded.contains("\"type\":\"control\""))
        assertTrue(encoded.contains("\"name\":\"play_pause\""))
        assertEquals(
            SiloCastMessage.Control(SiloCastControlCommand.playPause()),
            json.decodeFromString(SiloCastMessage.serializer(), encoded),
        )
    }

    @Test
    fun subtitleOffRoundTripsWithNullTrackId() {
        val msg = SiloCastMessage.Control(SiloCastControlCommand.selectSubtitleTrack(null))
        val encoded = json.encodeToString(SiloCastMessage.serializer(), msg)

        assertTrue(encoded.contains("\"name\":\"select_subtitle_track\""))
        assertEquals(msg, json.decodeFromString(SiloCastMessage.serializer(), encoded))
    }

    @Test
    fun helloMatchesServiceVersion() {
        assertEquals(1, SiloCastProtocol.version)
        assertEquals("_silocast._tcp", SiloCastProtocol.serviceType)
    }
}
