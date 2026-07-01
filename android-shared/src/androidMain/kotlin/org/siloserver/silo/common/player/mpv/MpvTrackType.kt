package org.siloserver.silo.common.player.mpv

import androidx.media3.common.C

enum class MpvTrackType(val type: String, val propertyName: String) {
    VIDEO("video", "vid"),
    AUDIO("audio", "aid"),
    SUBTITLE("sub", "sid");

    companion object {
        fun fromMedia3TrackType(trackType: Int): MpvTrackType = when (trackType) {
            C.TRACK_TYPE_VIDEO -> VIDEO
            C.TRACK_TYPE_AUDIO -> AUDIO
            C.TRACK_TYPE_TEXT -> SUBTITLE
            else -> SUBTITLE
        }
    }
}
