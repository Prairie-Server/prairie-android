package org.siloserver.silo.common.cast

import org.siloserver.silo.pairing.PairingFrame
import org.siloserver.silo.pairing.PairingFrameBuffer
import org.siloserver.silo.pairing.PairingFrameTooLargeException

object SiloCastFrame {
    const val maxFrameBytes: Int = PairingFrame.maxFrameBytes

    fun encode(payload: ByteArray): ByteArray = PairingFrame.encode(payload)
}

class SiloCastFrameBuffer {
    private val delegate = PairingFrameBuffer()

    @Throws(PairingFrameTooLargeException::class)
    fun append(bytes: ByteArray): List<ByteArray> = delegate.append(bytes)
}
