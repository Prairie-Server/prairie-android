package com.continuum.app.common.ui.components

import android.net.Uri
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val diceBearPresetPrefix = "preset:dicebear:"
private const val diceBearBaseUrl = "https://api.dicebear.com/9.x"

private val imageAvatarPrefixes = listOf(
    "http://",
    "https://",
    "data:image/",
    "content://",
    "file://",
    "/",
)

private val imageAvatarExtensions = listOf(
    ".png",
    ".jpg",
    ".jpeg",
    ".webp",
    ".gif",
    ".svg",
    ".avif",
)

fun isImageAvatar(avatar: String?): Boolean {
    val value = avatar?.trim().orEmpty()
    if (value.isEmpty()) return false

    val lowercased = value.lowercase()
    return isDiceBearPresetAvatar(value)
        || imageAvatarPrefixes.any(lowercased::startsWith)
        || "/" in lowercased
        || imageAvatarExtensions.any(lowercased::contains)
}

fun resolveAvatarUrl(serverUrl: String, avatar: String): String? {
    val trimmedAvatar = avatar.trim()
    if (trimmedAvatar.isEmpty()) return null

    resolveDiceBearPresetUrl(trimmedAvatar)?.let { return it }

    val normalizedServerUrl = serverUrl.trim().trimEnd('/')
    val lowercasedAvatar = trimmedAvatar.lowercase()
    val isAbsoluteAvatar = imageAvatarPrefixes
        .dropLast(1)
        .any(lowercasedAvatar::startsWith)

    if (isAbsoluteAvatar) {
        return trimmedAvatar
    }

    if (!isImageAvatar(trimmedAvatar) || normalizedServerUrl.isEmpty()) {
        return null
    }

    return if (trimmedAvatar.startsWith("/")) {
        normalizedServerUrl + trimmedAvatar
    } else {
        "$normalizedServerUrl/${trimmedAvatar.trimStart('/')}"
    }
}

fun profileAvatarDisplayText(avatar: String?, name: String): String {
    val trimmedAvatar = avatar?.trim().orEmpty()
    return if (trimmedAvatar.isNotEmpty() && !isImageAvatar(trimmedAvatar)) {
        trimmedAvatar
    } else {
        name.profileInitials()
    }
}

@Composable
fun rememberProfileServerUrl(): String {
    val context = LocalContext.current
    return remember(context) {
        context
            .getSharedPreferences("continuum_auth", Context.MODE_PRIVATE)
            .getString("serverUrl", "")
            .orEmpty()
            .trim()
            .trimEnd('/')
    }
}

fun String.profileInitials(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return "?"

    val parts = trimmed.split("\\s+".toRegex())
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        else -> "${trimmed.first().uppercaseChar()}"
    }
}

private fun isDiceBearPresetAvatar(avatar: String): Boolean =
    avatar.trim().lowercase().startsWith(diceBearPresetPrefix)

private fun resolveDiceBearPresetUrl(avatar: String): String? {
    if (!isDiceBearPresetAvatar(avatar)) return null

    val parts = avatar.trim().split(':', limit = 4)
    if (parts.size < 4) return null

    val style = parts[2].trim()
    val seed = parts[3].trim()
    if (style.isEmpty() || seed.isEmpty()) return null

    return "$diceBearBaseUrl/${Uri.encode(style)}/png?seed=${Uri.encode(seed)}&size=256"
}
