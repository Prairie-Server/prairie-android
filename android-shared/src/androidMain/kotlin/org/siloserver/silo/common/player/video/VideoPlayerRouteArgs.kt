package org.siloserver.silo.common.player.video

object VideoPlayerRouteArgs {
    const val RESUME_POSITION = "resumePosition"

    fun parseResumePosition(value: String?): Double? {
        val parsed = value?.toDoubleOrNull() ?: return null
        return parsed.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun encodeResumePosition(value: Double?): String? {
        val valid = value?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        return valid.toString()
    }
}
