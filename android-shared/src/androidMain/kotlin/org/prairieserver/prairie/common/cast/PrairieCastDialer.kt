package org.prairieserver.prairie.common.cast

import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import org.prairieserver.prairie.common.lan.SealedPrairieCastConnection
import org.prairieserver.prairie.common.lan.PrairieCastConnection
import org.prairieserver.prairie.common.lan.PrairieCastSealed
import org.prairieserver.prairie.common.lan.PrairieCastTls
import org.prairieserver.prairie.common.lan.StreamPrairieCastConnection
import org.prairieserver.prairie.common.lan.asConnection

/** A sealed Roku refused this phone's pairing key: it was reset, or never matched. */
class PrairieCastPairingRefusedException : IOException("The TV refused the pairing key.")

/**
 * Opens a receiver's control channel over the transport its discovery record
 * fixed ([PrairieCastTarget.transport]). There is no fallback between them: a
 * TLS-PSK TV that fails the handshake is never retried in plain text.
 * Blocking; call on IO.
 */
object PrairieCastDialer {
    private const val HANDSHAKE_TIMEOUT_MS = 10_000

    fun dial(target: PrairieCastTarget, pairingKey: ByteArray?, connectTimeoutMs: Int): PrairieCastConnection =
        when (target.transport) {
            PrairieCastTransport.TlsPsk -> PrairieCastTls.connect(target.host, target.port, connectTimeoutMs).asConnection()
            PrairieCastTransport.Plain -> plain(target.host, target.port, connectTimeoutMs).second
            PrairieCastTransport.Sealed -> sealed(
                target.host,
                target.port,
                connectTimeoutMs,
                requireNotNull(pairingKey) { "A sealed TV needs its pairing key." },
            )
        }

    private fun plain(host: String, port: Int, connectTimeoutMs: Int): Pair<Socket, PrairieCastConnection> {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.tcpNoDelay = true
            return socket to StreamPrairieCastConnection(
                input = socket.getInputStream(),
                output = socket.getOutputStream(),
                isOpen = { socket.isConnected && !socket.isClosed },
                onClose = { runCatching { socket.close() } },
            )
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    /**
     * The `sealed_hello` exchange (prairie-roku `docs/prairiecontrol.md` section
     * 9.2): the phone's plain frame first, then the Roku's. A Roku that does
     * not know the key answers `close unauthorized` in plain text instead.
     */
    private fun sealed(host: String, port: Int, connectTimeoutMs: Int, key: ByteArray): PrairieCastConnection {
        val (socket, plain) = plain(host, port, connectTimeoutMs)
        try {
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            val keyId = PrairieCastSealed.keyId(key)
            val nonce = PrairieCastSealed.newNonce()
            plain.writeFrame(PrairieCastSealed.helloText(keyId, nonce).encodeToByteArray())
            val reply = plain.readFrame() ?: throw EOFException("The TV closed the connection during pairing.")
            val hello = PrairieCastSealed.readHello(reply.decodeToString())
            if (hello == null || hello.keyId != keyId) throw PrairieCastPairingRefusedException()
            socket.soTimeout = 0
            return SealedPrairieCastConnection(plain, PrairieCastSealed.sessionKeys(key, nonce, hello.nonce))
        } catch (t: Throwable) {
            plain.close()
            throw t
        }
    }
}
