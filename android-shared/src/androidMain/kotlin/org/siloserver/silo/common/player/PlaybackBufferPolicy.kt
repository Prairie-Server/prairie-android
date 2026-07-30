package org.siloserver.silo.common.player

/**
 * Buffering policy derived from what the player can observe. There is no user
 * setting and no server-supplied mode: the numbers follow from the device and
 * the stream.
 */
data class PlaybackBufferPolicy(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
    val prioritizeTimeOverSizeThresholds: Boolean,
) {
    companion object {
        /**
         * How long the load control may stop reading the socket.
         *
         * DefaultLoadControl fills to maxBufferMs, then requests nothing until
         * the buffer drains below minBufferMs — so the gap between them is
         * literally how long the connection sits idle. Upstream proxies close
         * an idle response body: nginx's send_timeout defaults to 60s. The old
         * hand-written 50s/120s pair left a 70s gap and dropped the connection
         * every time the buffer filled on a long direct-play file.
         *
         * maxBufferMs is therefore never written by hand; it is always
         * minBufferMs + this. Depth can grow without ever widening the window.
         */
        const val MAX_LOAD_IDLE_MS = 30_000

        /** The timeout MAX_LOAD_IDLE_MS is chosen to stay clear of. */
        const val ASSUMED_PROXY_SEND_TIMEOUT_MS = 60_000

        /** Never buffer less than this, however constrained the device. */
        const val MIN_DEPTH_MS = 20_000

        /**
         * Never buffer more than this even when memory allows. Past a few
         * minutes we are mostly prefetching content the viewer may seek away
         * from — wasted bandwidth, and wasted allowance on mobile data.
         */
        const val MAX_DEPTH_MS = 180_000

        private const val START_MS = 2_000

        /**
         * After a stall the viewer is watching a spinner, so the cushion we
         * rebuild before resuming is deliberately small.
         */
        private const val REBUFFER_MS = 5_000

        fun forConditions(
            deviceProfile: PlaybackBufferDeviceProfile = PlaybackBufferDeviceProfile.Unknown,
        ): PlaybackBufferPolicy {
            val depthMs = MAX_DEPTH_MS
            return PlaybackBufferPolicy(
                minBufferMs = depthMs,
                maxBufferMs = depthMs + MAX_LOAD_IDLE_MS,
                bufferForPlaybackMs = START_MS,
                bufferForPlaybackAfterRebufferMs = REBUFFER_MS,
                targetBufferBytes = memoryBudgetBytes(deviceProfile),
                prioritizeTimeOverSizeThresholds = false,
            )
        }

        /**
         * The byte ceiling this device can afford. SiloLoadControl sizes the
         * real target from the stream's bitrate and clamps it to this.
         *
         * This is a fraction of the app's own heap rather than a pick from
         * fixed tiers. A fixed tier either starves a small-heap device (a
         * flat 48 MiB floor is half of a 96 MB heap — a real OOM risk) or
         * leaves a large-heap device's headroom unused (a flat 160 MiB
         * ceiling caps a 1 GB heap the same as a 384 MB one). Scaling with
         * memoryClassMb keeps the budget proportionate at both ends without
         * hand-picking where the tier boundaries should sit.
         */
        internal fun memoryBudgetBytes(deviceProfile: PlaybackBufferDeviceProfile): Int {
            if (deviceProfile.isLowRamDevice || deviceProfile.memoryClassMb <= 0) {
                return LOW_RAM_MEMORY_BUDGET_BYTES
            }
            val proportionalBytes =
                deviceProfile.memoryClassMb.toLong() * MIB / MEMORY_BUDGET_HEAP_DIVISOR
            return proportionalBytes
                .coerceIn(MIN_MEMORY_BUDGET_BYTES.toLong(), MAX_MEMORY_BUDGET_BYTES.toLong())
                .toInt()
        }

        private const val MIB = 1024 * 1024

        /** The budget is this fraction (1/4) of the app heap — see [memoryBudgetBytes]. */
        private const val MEMORY_BUDGET_HEAP_DIVISOR = 4L

        /** Never budget less than this, however small the heap. */
        private const val MIN_MEMORY_BUDGET_BYTES = 16 * MIB

        /** Never budget more than this even on a very large heap. */
        private const val MAX_MEMORY_BUDGET_BYTES = 192 * MIB

        /**
         * Fixed fallback for devices that report no usable heap size, or that
         * flag themselves as low-RAM outright — conservative rather than
         * proportional, since a quarter of an unknown or explicitly
         * constrained heap is not a number worth trusting.
         */
        private const val LOW_RAM_MEMORY_BUDGET_BYTES = 24 * MIB
    }
}

data class PlaybackBufferDeviceProfile(
    val memoryClassMb: Int,
    val isLowRamDevice: Boolean,
) {
    companion object {
        val Unknown = PlaybackBufferDeviceProfile(memoryClassMb = 0, isLowRamDevice = false)
    }
}
