package org.prairieserver.prairie.android.ui.screens.reader.reflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReflowLocator(
    val sectionIndex: Int,
    val pageProgression: Double, // 0..1 within the section
    val bookProgression: Double, // 0..1 across the book
)

fun ReflowLocator.coerceForSectionCount(sectionCount: Int): ReflowLocator {
    val lastSection = (sectionCount - 1).coerceAtLeast(0)
    return copy(
        sectionIndex = sectionIndex.coerceIn(0, lastSection),
        pageProgression = pageProgression.coerceIn(0.0, 1.0),
        bookProgression = bookProgression.coerceIn(0.0, 1.0),
    )
}

object ReflowLocatorCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(locator: ReflowLocator): String = json.encodeToString(ReflowLocator.serializer(), locator)
    fun decode(location: String?): ReflowLocator? {
        if (location.isNullOrBlank() || !location.trimStart().startsWith("{")) return null
        return runCatching { json.decodeFromString(ReflowLocator.serializer(), location) }.getOrNull()
    }
}
