package com.continuum.app.common.pairing

import com.continuum.app.pairing.PairingFrame
import com.continuum.app.pairing.PairingFrameBuffer
import com.continuum.app.pairing.PairingMessage
import com.continuum.app.pairing.PairingMessageCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bouncycastle.tls.AlertLevel
import org.bouncycastle.tls.BasicTlsPSKExternal
import org.bouncycastle.tls.CipherSuite
import org.bouncycastle.tls.DefaultTlsServer
import org.bouncycastle.tls.PRFAlgorithm
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.TlsPSKExternal
import org.bouncycastle.tls.TlsServerProtocol
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import java.util.Vector
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.SecureRandom

/**
 * The fixed (non-secret) pre-shared key + identity compiled into the app. This
 * is NOT an authentication boundary — it merely lets the tvOS NWListener and
 * Android sockets negotiate TLS without certificate management. Must match the
 * silo-apple [PairingSession.tlsParameters] byte-for-byte.
 */
internal object PairingPsk {
    val key: ByteArray = "silo-companion-pairing-v1".toByteArray(Charsets.UTF_8)
    val identity: ByteArray = "silo-pairing".toByteArray(Charsets.UTF_8)

    /**
     * iOS (Network.framework) appends [tls_ciphersuite_t.AES_128_GCM_SHA256],
     * which is the TLS 1.3 suite TLS_AES_128_GCM_SHA256 (0x1301) negotiated with
     * an external PSK — NOT the TLS 1.2 TLS_PSK_WITH_AES_128_GCM_SHA256 suite.
     */
    const val cipherSuite: Int = CipherSuite.TLS_AES_128_GCM_SHA256
}

/**
 * A [PairingTransport] over a TLS-PSK server handshake on an accepted socket,
 * matching the iOS receiver exactly (PSK = "silo-companion-pairing-v1", PSK
 * identity = "silo-pairing", TLS 1.3 external PSK, cipher
 * TLS_AES_128_GCM_SHA256).
 *
 * BouncyCastle's [TlsServerProtocol] drives the handshake over the raw socket
 * streams; outbound frames are written and inbound bytes are fed to
 * [PairingFrameBuffer] → [PairingMessageCodec].
 */
class TlsPskPairingTransport private constructor(
    private val socket: Socket,
    private val protocol: TlsServerProtocol,
    private val tlsIn: InputStream,
    private val tlsOut: OutputStream,
) : PairingTransport {

    private val sendMutex = Mutex()

    override val incoming: Flow<PairingMessage> = callbackFlow {
        val buffer = PairingFrameBuffer()
        val chunk = ByteArray(64 * 1024)
        try {
            while (true) {
                val read = tlsIn.read(chunk)
                if (read < 0) break // clean EOF
                if (read == 0) continue
                val received = chunk.copyOf(read)
                for (payload in buffer.append(received)) {
                    val message = PairingMessageCodec.decode(payload.toString(Charsets.UTF_8))
                    trySend(message)
                }
            }
            close()
        } catch (t: Throwable) {
            close(t)
        }
        awaitClose { closeQuietly() }
    }.flowOn(Dispatchers.IO)

    override suspend fun send(message: PairingMessage) {
        val payload = PairingMessageCodec.encode(message).toByteArray(Charsets.UTF_8)
        val framed = PairingFrame.encode(payload)
        sendMutex.withLock {
            tlsOut.write(framed)
            tlsOut.flush()
        }
    }

    override fun close() = closeQuietly()

    private fun closeQuietly() {
        runCatching { protocol.close() }
        runCatching { socket.close() }
    }

    companion object {
        /**
         * Perform the TLS-PSK server handshake over [socket] and return a ready
         * transport. Blocking — call off the main thread (the advertiser's
         * accept loop runs on [Dispatchers.IO]).
         */
        fun accept(socket: Socket): TlsPskPairingTransport {
            val crypto = BcTlsCrypto(SecureRandom())
            val protocol = TlsServerProtocol(socket.getInputStream(), socket.getOutputStream())
            val server = SiloPskTlsServer(crypto)
            protocol.accept(server)
            return TlsPskPairingTransport(
                socket = socket,
                protocol = protocol,
                tlsIn = protocol.inputStream,
                tlsOut = protocol.outputStream,
            )
        }
    }
}

/**
 * TLS 1.3 server pinned to the fixed pairing PSK as an EXTERNAL PSK and the
 * single AES-128-GCM cipher suite iOS negotiates (TLS_AES_128_GCM_SHA256).
 *
 * iOS's Network.framework uses `sec_protocol_options_add_pre_shared_key` +
 * `tls_ciphersuite_t.AES_128_GCM_SHA256`, i.e. a TLS 1.3 external PSK — not the
 * TLS 1.2 `TLS_PSK_WITH_AES_128_GCM_SHA256` suite. We therefore offer TLS 1.3
 * only and supply the fixed key/identity via [getExternalPSK], the BouncyCastle
 * 1.3 external-PSK hook. The PRF for this suite is HKDF-SHA256.
 *
 * NOTE: this TLS 1.3 external-PSK interop with the real iOS NWConnection CANNOT
 * be verified headless — it needs real-device interop verification. Implemented
 * to match iOS's external-PSK configuration as closely as BouncyCastle allows.
 */
private class SiloPskTlsServer(
    private val bcCrypto: BcTlsCrypto,
) : DefaultTlsServer(bcCrypto) {

    override fun getCipherSuites(): IntArray = intArrayOf(PairingPsk.cipherSuite)

    override fun getSupportedVersions(): Array<ProtocolVersion> =
        ProtocolVersion.TLSv13.only()

    /**
     * TLS 1.3 external-PSK hook: the client offers identities in its
     * pre_shared_key extension; return the fixed pairing PSK for our known
     * identity (and, defensively, regardless of the offered identity — the key
     * is fixed and non-secret). Returning a [TlsPSKExternal] selects PSK-only
     * key exchange with no certificate, mirroring iOS.
     */
    override fun getExternalPSK(identities: Vector<*>?): TlsPSKExternal =
        BasicTlsPSKExternal(
            PairingPsk.identity,
            bcCrypto.createSecret(PairingPsk.key),
            PRFAlgorithm.tls13_hkdf_sha256,
        )

    override fun notifyAlertRaised(
        alertLevel: Short,
        alertDescription: Short,
        message: String?,
        cause: Throwable?,
    ) {
        if (alertLevel == AlertLevel.fatal) {
            // Swallow — the transport surfaces the failure via the stream closing.
        }
    }
}
