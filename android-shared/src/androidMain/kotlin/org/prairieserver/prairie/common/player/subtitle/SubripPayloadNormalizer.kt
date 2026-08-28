package org.prairieserver.prairie.common.player.subtitle

private val subripTimingLine = Regex(
    """^\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}.*$""",
)
private val subripCueIndexLine = Regex("""^\s*\d+\s*$""")
private val subripTimestamp = Regex("""(\d{1,2}:\d{2}:\d{2})[,.](\d{1,3})""")

internal fun normalizeSubripPayloadIfNeeded(
    data: ByteArray,
    offset: Int,
    length: Int,
): ByteArray? {
    // Decoded STRICTLY, not leniently. decodeToString substitutes U+FFFD for
    // anything that is not valid UTF-8 — and since a rewrite re-encodes what it
    // decoded, those substitutions become permanent: a Windows-1252 subtitle
    // comes back with "José" rendered as "Jos�" for the rest of its life.
    //
    // Legacy SRT files are commonly Windows-1252 or another single-byte
    // regional encoding, so a failed UTF-8 decode is ordinary rather than
    // exceptional. Falling back to Windows-1252 recovers the accents; if even
    // that cannot be decoded, the payload is left exactly as it arrived, on
    // the grounds that not normalising is better than corrupting.
    val text = decodeSubtitleText(data, offset, length) ?: return null
    val normalized = normalizeSubripTextIfNeeded(text)
    return if (normalized == text) null else normalized.encodeToByteArray()
}

internal fun normalizeSubripTextIfNeeded(text: String): String {
    val body = text.removePrefix("\uFEFF")
    val lines = body
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
    val timingIndexes = lines.indices.filter { lines[it].isSubripTimingLine() }
    if (timingIndexes.isEmpty()) return text

    val hasWebvttHeader = lines.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.equals("WEBVTT", ignoreCase = true) == true
    val hasWebvttStyleTimestamps = timingIndexes.any { lines[it].hasDotSeparatedTimestamp() }
    val needsCueNumbers = timingIndexes.any { timingIndex ->
        val previous = previousNonBlankLine(lines, timingIndex)
        previous == null || !previous.isSubripCueIndexLine()
    }
    if (!needsCueNumbers && !hasWebvttHeader && !hasWebvttStyleTimestamps) return text

    val cues = mutableListOf<SubripCueBlock>()
    var i = 0
    while (i < lines.size) {
        val timingIndex = when {
            lines[i].isSubripTimingLine() -> i
            lines[i].isSubripCueIndexLine() &&
                i + 1 < lines.size &&
                lines[i + 1].isSubripTimingLine() -> i + 1
            else -> {
                i++
                continue
            }
        }

        val timing = normalizeSubripTimingLine(lines[timingIndex].trim())
        i = timingIndex + 1
        val cueText = mutableListOf<String>()
        while (i < lines.size) {
            val line = lines[i]
            val nextCueStartsHere = line.isSubripTimingLine() ||
                (line.isSubripCueIndexLine() &&
                    i + 1 < lines.size &&
                    lines[i + 1].isSubripTimingLine())
            if (nextCueStartsHere) break

            if (line.isBlank()) {
                val next = nextNonBlankIndex(lines, i + 1)
                val blankBeforeNextCue = next == null ||
                    lines[next].isSubripTimingLine() ||
                    (lines[next].isSubripCueIndexLine() &&
                        next + 1 < lines.size &&
                        lines[next + 1].isSubripTimingLine())
                if (blankBeforeNextCue) {
                    i = next ?: lines.size
                    break
                }
            }

            cueText += line
            i++
        }
        cues += SubripCueBlock(timing, cueText.dropLastWhile { it.isBlank() })
    }

    if (cues.isEmpty()) return text
    return buildString {
        cues.forEachIndexed { index, cue ->
            if (index > 0) append("\n\n")
            append(index + 1).append('\n')
            append(cue.timing).append('\n')
            cue.textLines.forEachIndexed { textIndex, line ->
                if (textIndex > 0) append('\n')
                append(line)
            }
        }
    }
}

private data class SubripCueBlock(
    val timing: String,
    val textLines: List<String>,
)

private fun String.isSubripTimingLine(): Boolean = subripTimingLine.matches(this)

private fun String.isSubripCueIndexLine(): Boolean = subripCueIndexLine.matches(this)

private fun String.hasDotSeparatedTimestamp(): Boolean =
    subripTimestamp.findAll(this).any { match ->
        match.value.contains('.')
    }

private fun normalizeSubripTimingLine(line: String): String =
    subripTimestamp.replace(line) { match ->
        val time = match.groupValues[1]
        val millis = match.groupValues[2].padEnd(3, '0').take(3)
        "$time,$millis"
    }

private fun previousNonBlankLine(lines: List<String>, beforeIndex: Int): String? {
    for (i in beforeIndex - 1 downTo 0) {
        if (lines[i].isNotBlank()) return lines[i]
    }
    return null
}

private fun nextNonBlankIndex(lines: List<String>, startIndex: Int): Int? {
    for (i in startIndex until lines.size) {
        if (lines[i].isNotBlank()) return i
    }
    return null
}

private fun decodeSubtitleText(data: ByteArray, offset: Int, length: Int): String? =
    decodeStrictly(data, offset, length, Charsets.UTF_8)
        ?: decodeStrictly(data, offset, length, WINDOWS_1252)

private fun decodeStrictly(
    data: ByteArray,
    offset: Int,
    length: Int,
    charset: java.nio.charset.Charset,
): String? = try {
    charset.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(data, offset, length))
        .toString()
} catch (_: java.nio.charset.CharacterCodingException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private val WINDOWS_1252: java.nio.charset.Charset =
    runCatching { java.nio.charset.Charset.forName("windows-1252") }
        .getOrElse { Charsets.ISO_8859_1 }
