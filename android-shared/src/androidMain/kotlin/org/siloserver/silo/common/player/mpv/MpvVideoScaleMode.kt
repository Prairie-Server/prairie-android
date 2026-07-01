package org.siloserver.silo.common.player.mpv

enum class MpvVideoScaleMode {
    Fit,
    Zoom,
    Stretch,
}

interface MpvVideoScaleController {
    fun setVideoScaleMode(mode: MpvVideoScaleMode)
}
