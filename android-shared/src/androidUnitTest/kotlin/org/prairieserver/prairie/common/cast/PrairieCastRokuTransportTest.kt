package org.prairieserver.prairie.common.cast

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.prairieserver.prairie.cast.PrairieCastProtocol
import org.prairieserver.prairie.common.lan.PrairieCastSealed

/**
 * The phone's side of the Roku receiver (prairie-roku `docs/prairiecontrol.md`):
 * discovery records, the pairing link, and plain and `sealed` dialing against
 * a loopback stand-in that speaks the Roku's side.
 */
class PrairieCastRokuTransportTest {
    private val key = hex("000102030405060708090a0b0c0d0e0f")
    private val tvHello =
        """{"type":"hello","v":2,"hello":{"role":"tv","deviceName":"Den Roku","deviceId":"roku-1","serverId":"c3J2","serverName":"Home","supportedVersions":[2],"features":["volume_mute_only"]}}"""

    @Test
    fun theServiceTypeAloneDecidesTheTransport() {
        val txt = mapOf("v" to "2", "id" to "roku-1", "name" to "Den Roku", "sec" to "plain")
        val roku = assertNotNull(record(txt, PrairieCastProtocol.rokuServiceType + "."))
        assertEquals(PrairieCastTransport.Plain, roku.transport)
        assertEquals("roku-1", roku.deviceId)
        // A TLS TV's TXT can never talk the phone into plain TCP.
        assertEquals(PrairieCastTransport.TlsPsk, assertNotNull(record(txt, PrairieCastProtocol.serviceType)).transport)

        val sealed = assertNotNull(record(txt + mapOf("sec" to "sealed", "kid" to "BE45CB26"), PrairieCastProtocol.rokuServiceType))
        assertEquals(PrairieCastTransport.Sealed, sealed.transport)
        assertEquals("be45cb26", sealed.keyId)
        // A level this build can't speak is left out.
        assertNull(record(txt + mapOf("sec" to "quantum"), PrairieCastProtocol.rokuServiceType))
    }

    @Test
    fun readsThePairingLink() {
        val sealed = assertNotNull(
            PrairieCastPairingLink.parse(
                "prairie://cast?v=2&host=192.168.1.20&port=52817&id=roku-1&name=Den%20Roku&server=c3J2&security=sealed&key=AAAQEAYEAUDAOCAJBIFQYDIOB4&later=1",
            ),
        )
        assertEquals("192.168.1.20", sealed.target.host)
        assertEquals(52817, sealed.target.port)
        assertEquals("Den Roku", sealed.target.name)
        assertEquals("c3J2", sealed.target.serverId)
        assertEquals(PrairieCastTransport.Sealed, sealed.target.transport)
        assertEquals("be45cb26", sealed.target.keyId)
        assertContentEquals(key, sealed.pairingKey)

        val plain = assertNotNull(PrairieCastPairingLink.parse("prairie://cast?v=2&host=10.0.0.5&port=52818&id=roku-2&security=plain"))
        assertEquals(PrairieCastTransport.Plain, plain.target.transport)
        assertNull(plain.pairingKey)

        assertNull(PrairieCastPairingLink.parse("prairie://cast?v=1&host=10.0.0.5&port=52818&id=roku-2"))
        assertNull(PrairieCastPairingLink.parse("prairie://cast?v=2&host=roku.local&port=52818&id=roku-2"))
        assertNull(PrairieCastPairingLink.parse("prairie://cast?v=2&host=10.0.0.5&port=52818&id=roku-2&security=sealed"))
        assertNull(PrairieCastPairingLink.parse("prairie://play?v=2&host=10.0.0.5&port=52818&id=roku-2"))
    }

    @Test
    fun dialsAPlainRoku() = withRoku({ socket ->
        frame(socket, tvHello.encodeToByteArray())
        read(socket).decodeToString()
    }) { port, result ->
        val connection = PrairieCastDialer.dial(roku(port, PrairieCastTransport.Plain), pairingKey = null, connectTimeoutMs = 5_000)
        try {
            assertEquals(tvHello, connection.readFrame()?.decodeToString())
            connection.writeFrame("""{"type":"pong","v":2}""".encodeToByteArray())
            assertEquals("""{"type":"pong","v":2}""", result.get(5, TimeUnit.SECONDS))
        } finally {
            connection.close()
        }
    }

    @Test
    fun dialsASealedRoku() = withRoku({ socket ->
        val phone = assertNotNull(PrairieCastSealed.readHello(read(socket).decodeToString()))
        assertEquals("be45cb26", phone.keyId)
        val tvNonce = hex("202122232425262728292a2b2c2d2e2f")
        frame(socket, PrairieCastSealed.helloText("be45cb26", tvNonce).encodeToByteArray())
        val keys = PrairieCastSealed.sessionKeys(key, phone.nonce, tvNonce)
        frame(socket, PrairieCastSealed.seal(keys, PrairieCastSealed.FROM_TV, 0, tvHello.encodeToByteArray()))
        val first = PrairieCastSealed.open(keys, PrairieCastSealed.FROM_PHONE, 0, read(socket))
        val second = PrairieCastSealed.open(keys, PrairieCastSealed.FROM_PHONE, 1, read(socket))
        "${first?.decodeToString()} ${second?.decodeToString()}"
    }) { port, result ->
        val connection = PrairieCastDialer.dial(roku(port, PrairieCastTransport.Sealed), pairingKey = key, connectTimeoutMs = 5_000)
        try {
            assertEquals(tvHello, connection.readFrame()?.decodeToString())
            connection.writeFrame("a".encodeToByteArray())
            connection.writeFrame("b".encodeToByteArray())
            assertEquals("a b", result.get(5, TimeUnit.SECONDS))
        } finally {
            connection.close()
        }
    }

    @Test
    fun aRokuThatRefusesTheKeyIsReportedAsSuch() = withRoku({ socket ->
        read(socket)
        frame(socket, """{"type":"close","v":2,"reason":"unauthorized"}""".encodeToByteArray())
        ""
    }) { port, _ ->
        assertFailsWith<PrairieCastPairingRefusedException> {
            PrairieCastDialer.dial(roku(port, PrairieCastTransport.Sealed), pairingKey = key, connectTimeoutMs = 5_000)
        }
    }

    private fun record(txt: Map<String, String>, type: String) =
        PrairieCastNsdBrowser.targetFromRecord(host = "10.0.0.5", port = 52817, serviceName = "Prairie-0123abcd", txt = txt, serviceType = type)

    private fun roku(port: Int, transport: PrairieCastTransport) = PrairieCastTarget(
        deviceId = "roku-1",
        name = "Den Roku",
        host = "127.0.0.1",
        port = port,
        version = 2,
        transport = transport,
    )

    private fun withRoku(side: (Socket) -> String, body: (Int, CompletableFuture<String>) -> Unit) {
        val listener = ServerSocket(0)
        val result = CompletableFuture<String>()
        val worker = thread(isDaemon = true, name = "silocast-roku-test") {
            runCatching { listener.accept().use { socket -> result.complete(side(socket)) } }
                .onFailure(result::completeExceptionally)
        }
        try {
            body(listener.localPort, result)
        } finally {
            listener.close()
            worker.join(5_000)
        }
    }

    private fun frame(socket: Socket, payload: ByteArray) {
        DataOutputStream(socket.getOutputStream()).apply {
            writeInt(payload.size)
            write(payload)
            flush()
        }
    }

    private fun read(socket: Socket): ByteArray {
        val input = DataInputStream(socket.getInputStream())
        return ByteArray(input.readInt()).also(input::readFully)
    }

    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
