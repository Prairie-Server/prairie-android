package org.prairieserver.prairie.common.cast

import org.prairieserver.prairie.pairing.PairingFrame
import org.prairieserver.prairie.pairing.PairingFrameBuffer
import org.prairieserver.prairie.pairing.PairingFrameTooLargeException

object PrairieCastFrame {
    const val maxFrameBytes: Int = PairingFrame.maxFrameBytes

    fun encode(payload: ByteArray): ByteArray = PairingFrame.encode(payload)
}

class PrairieCastFrameBuffer {
    private val delegate = PairingFrameBuffer()

    @Throws(PairingFrameTooLargeException::class)
    fun append(bytes: ByteArray): List<ByteArray> = delegate.append(bytes)
}
