package org.prairieserver.prairie.common.lan

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.prairieserver.prairie.common.cast.PrairieCastFrame
import org.prairieserver.prairie.common.cast.PrairieCastFrameBuffer

/**
 * A dialed PrairieCast control channel that carries whole frame payloads, so the
 * phone's controller is the same over TLS-PSK, plain TCP, and `sealed`.
 * Blocking; call on IO. Reads have a single consumer (the read loop), and
 * callers serialize writes.
 */
interface PrairieCastConnection {
    val isConnected: Boolean

    fun writeFrame(payload: ByteArray)

    /** The next frame payload, or null once the peer has closed. */
    fun readFrame(): ByteArray?

    fun close()
}

/** Length-prefixed frames over a byte stream: plain TCP or TLS application data. */
internal class StreamPrairieCastConnection(
    private val input: InputStream,
    private val output: OutputStream,
    private val isOpen: () -> Boolean,
    private val onClose: () -> Unit,
) : PrairieCastConnection {
    private val frames = PrairieCastFrameBuffer()
    private val ready = ArrayDeque<ByteArray>()
    private val chunk = ByteArray(8 * 1024)

    override val isConnected: Boolean get() = isOpen()

    override fun writeFrame(payload: ByteArray) {
        output.write(PrairieCastFrame.encode(payload))
        output.flush()
    }

    override fun readFrame(): ByteArray? {
        while (ready.isEmpty()) {
            val read = input.read(chunk)
            if (read < 0) return null
            if (read > 0) ready.addAll(frames.append(chunk.copyOf(read)))
        }
        return ready.removeFirst()
    }

    override fun close() = onClose()
}

/**
 * Level `sealed` over a keyed [inner] connection: every frame is sealed with
 * the next sequence number for its direction, and a frame that fails to open
 * ends the connection (the Roku does the same, without answering).
 */
internal class SealedPrairieCastConnection(
    private val inner: PrairieCastConnection,
    private val keys: PrairieCastSealed.SessionKeys,
) : PrairieCastConnection {
    private val writeLock = Any()
    private var sendSeq = 0L
    private var recvSeq = 0L

    override val isConnected: Boolean get() = inner.isConnected

    override fun writeFrame(payload: ByteArray) {
        // The sequence number must follow wire order.
        synchronized(writeLock) {
            inner.writeFrame(PrairieCastSealed.seal(keys, PrairieCastSealed.FROM_PHONE, sendSeq, payload))
            sendSeq += 1
        }
    }

    override fun readFrame(): ByteArray? {
        val body = inner.readFrame() ?: return null
        val opened = PrairieCastSealed.open(keys, PrairieCastSealed.FROM_TV, recvSeq, body)
            ?: throw IOException("A sealed PrairieCast frame did not open.")
        recvSeq += 1
        return opened
    }

    override fun close() = inner.close()
}

fun PrairieCastTlsClientSession.asConnection(): PrairieCastConnection =
    StreamPrairieCastConnection(input = input, output = output, isOpen = { isConnected }, onClose = ::close)
