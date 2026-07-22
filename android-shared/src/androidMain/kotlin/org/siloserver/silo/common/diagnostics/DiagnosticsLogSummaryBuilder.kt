package org.siloserver.silo.common.diagnostics

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.siloserver.silo.model.diagnostics.DiagnosticsLogCategory
import org.siloserver.silo.model.diagnostics.DiagnosticsLogSummary

internal object DiagnosticsLogSummaryBuilder {
    private val json = Json { ignoreUnknownKeys = true }
    private val categoriesByWireName = mapOf(
        "playback" to DiagnosticsLogCategory.PLAYBACK,
        "focus" to DiagnosticsLogCategory.FOCUS,
        "network" to DiagnosticsLogCategory.NETWORK,
        "lifecycle" to DiagnosticsLogCategory.LIFECYCLE,
        "browse" to DiagnosticsLogCategory.BROWSE,
        "cast" to DiagnosticsLogCategory.CAST,
        "download" to DiagnosticsLogCategory.DOWNLOAD,
        "crash" to DiagnosticsLogCategory.CRASH,
        "other" to DiagnosticsLogCategory.OTHER,
    )

    fun build(
        logBytes: ByteArray?,
        droppedLines: Long,
        debugLogging: Boolean,
    ): DiagnosticsLogSummary {
        val bytes = logBytes?.takeIf(ByteArray::isNotEmpty)
        val categories = bytes
            ?.decodeToString()
            ?.lineSequence()
            ?.mapNotNull(::categoryFromLine)
            ?.toSet()
            .orEmpty()
        return DiagnosticsLogSummary(
            lines = bytes?.physicalLineCount() ?: 0,
            bytesGzip = bytes?.gzipSize() ?: 0,
            droppedLines = droppedLines.coerceAtLeast(0),
            categories = DiagnosticsLogCategory.entries.filter(categories::contains),
            debugLogging = debugLogging,
        )
    }

    private fun categoryFromLine(line: String): DiagnosticsLogCategory? {
        if (line.isEmpty()) return null
        val category = runCatching {
            json.parseToJsonElement(line).jsonObject["cat"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return categoriesByWireName[category]
    }

    private fun ByteArray.physicalLineCount(): Long =
        count { it == '\n'.code.toByte() }.toLong() + if (last() == '\n'.code.toByte()) 0 else 1

    private fun ByteArray.gzipSize(): Long = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { gzip -> gzip.write(this) }
        output.size().toLong()
    }
}
