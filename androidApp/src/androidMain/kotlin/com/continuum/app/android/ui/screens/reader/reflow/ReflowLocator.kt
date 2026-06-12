package com.continuum.app.android.ui.screens.reader.reflow

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReflowLocator(
    val sectionIndex: Int,
    val pageProgression: Double, // 0..1 within the section
    val bookProgression: Double, // 0..1 across the book
)

object ReflowLocatorCodec {
    private val json = Json { ignoreUnknownKeys = true }
    fun encode(locator: ReflowLocator): String = json.encodeToString(ReflowLocator.serializer(), locator)
    fun decode(location: String?): ReflowLocator? {
        if (location.isNullOrBlank() || !location.trimStart().startsWith("{")) return null
        return runCatching { json.decodeFromString(ReflowLocator.serializer(), location) }.getOrNull()
    }
}
