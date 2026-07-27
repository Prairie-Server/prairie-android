package org.prairieserver.prairie.common.store

import java.io.File
import java.security.MessageDigest

private val SAFE_PATH_SEGMENT = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]{0,119}")
private const val MAX_SAFE_SEGMENT_LENGTH = 120
private const val ENCODED_PREFIX = "~"
private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/**
 * Converts an untrusted identity value into one filesystem path segment.
 *
 * Already-safe identifiers remain readable on disk. Other values use a
 * reserved `~` prefix plus unpadded base64url; exceptionally long encodings
 * use a fixed-size SHA-256 digest.
 */
internal fun safePathSegment(value: String): String {
    if (value != "." && value != ".." && SAFE_PATH_SEGMENT.matches(value)) return value

    val encoded = ENCODED_PREFIX + value.toByteArray(Charsets.UTF_8).toBase64Url()
    if (encoded.length <= MAX_SAFE_SEGMENT_LENGTH) return encoded

    val digest = MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray(Charsets.UTF_8))
    return ENCODED_PREFIX + digest.joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

/**
 * Resolves encoded identity [segments] under [root].
 *
 * Explicit traversal components are rejected instead of silently normalised.
 * All other unsafe values are encoded before canonical containment is checked.
 */
internal fun containedChild(root: File, vararg segments: String): File? {
    if (segments.any(::containsTraversalComponent)) return null
    return containedSafeChild(root, *segments)
}

/** Encodes identity values and contains them even when the raw value resembles traversal. */
internal fun containedSafeChild(root: File, vararg segments: String): File? {
    return runCatching {
        val canonicalRoot = root.canonicalFile
        val child = segments.fold(canonicalRoot) { parent, value ->
            File(parent, safePathSegment(value))
        }.canonicalFile
        child.takeIf { it.isContainedBy(canonicalRoot, allowRoot = segments.isEmpty()) }
    }.getOrNull()
}

/**
 * Resolves an old, unencoded path only when its canonical target is a strict
 * descendant of [root]. Values beginning with the reserved encoded namespace
 * marker (`~`) are deliberately never probed as raw legacy names, preventing a
 * raw identity from aliasing a different identity's encoded path. This is for
 * read/delete compatibility only.
 */
internal fun containedLegacyChild(root: File, vararg segments: String): File? =
    if (segments.any { it.startsWith(ENCODED_PREFIX) }) {
        null
    } else {
        runCatching {
            val canonicalRoot = root.canonicalFile
            val child = segments.fold(canonicalRoot) { parent, value -> File(parent, value) }.canonicalFile
            child.takeIf { it.isContainedBy(canonicalRoot, allowRoot = false) }
        }.getOrNull()
    }

private fun containsTraversalComponent(value: String): Boolean =
    value.replace('\\', '/').split('/').any { component -> component == "." || component == ".." }

private fun File.isContainedBy(root: File, allowRoot: Boolean): Boolean =
    (allowRoot && path == root.path) || path.startsWith(root.path + File.separator)

private fun ByteArray.toBase64Url(): String {
    if (isEmpty()) return ""
    val result = StringBuilder((size * 4 + 2) / 3)
    var index = 0
    while (index < size) {
        val first = this[index].toInt() and 0xff
        val second = if (index + 1 < size) this[index + 1].toInt() and 0xff else -1
        val third = if (index + 2 < size) this[index + 2].toInt() and 0xff else -1

        result.append(BASE64_URL_ALPHABET[first ushr 2])
        result.append(BASE64_URL_ALPHABET[((first and 0x03) shl 4) or if (second >= 0) second ushr 4 else 0])
        if (second >= 0) {
            result.append(BASE64_URL_ALPHABET[((second and 0x0f) shl 2) or if (third >= 0) third ushr 6 else 0])
        }
        if (third >= 0) {
            result.append(BASE64_URL_ALPHABET[third and 0x3f])
        }
        index += 3
    }
    return result.toString()
}
