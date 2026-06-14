package com.continuum.app.model.ebook

import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.catalog.FileVersion

private val supportedEbookExtensions = setOf(
    "epub",
    "pdf",
    "txt",
    "md",
    "markdown",
    "mobi",
    "azw",
    "azw3",
    "fb2",
    "fbz",
    "cbz",
    "cbr",
)

private val preferredOrder = listOf("epub", "pdf", "cbz", "txt", "md", "markdown", "fb2", "fbz", "cbr", "mobi", "azw3", "azw")

enum class EbookReadMode {
    InApp,
    ExternalOnly,
    Unsupported,
}

data class EbookFormatSupport(
    val key: String?,
    val readMode: EbookReadMode,
    val displayName: String,
    val reason: String,
) {
    val canReadInApp: Boolean get() = readMode == EbookReadMode.InApp
    val canDownloadOriginal: Boolean get() = readMode != EbookReadMode.Unsupported
}

fun ebookFormatKey(container: String?, fileName: String?): String? {
    val containerKey = container
        ?.trim()
        ?.lowercase()
        ?.removePrefix(".")
        ?.takeIf { it in supportedEbookExtensions }
        ?.normalizedEbookFormatKey()
    if (containerKey != null) return containerKey

    val name = fileName?.lowercase().orEmpty()
    if (name.endsWith(".fb2.zip")) return "fbz"
    return name.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it in supportedEbookExtensions }
        ?.normalizedEbookFormatKey()
}

fun FileVersion.ebookFormatKey(): String? =
    ebookFormatKey(container = container, fileName = fileName)

fun FileVersion.isSupportedEbookVersion(): Boolean = ebookFormatKey() != null

fun ebookFormatSupport(formatKey: String?): EbookFormatSupport {
    val key = formatKey
        ?.trim()
        ?.lowercase()
        ?.removePrefix(".")
        ?.takeIf { it.isNotBlank() }
    val normalizedKey = key?.normalizedEbookFormatKey()
    val displayName = normalizedKey.orEmpty().ebookFormatDisplayName()
    return when (normalizedKey) {
        "epub", "pdf", "cbz", "txt", "md", "fb2", "fbz" -> EbookFormatSupport(
            key = normalizedKey,
            readMode = EbookReadMode.InApp,
            displayName = displayName,
            reason = "$displayName can be read in Silo.",
        )
        "cbr", "mobi", "azw", "azw3" -> EbookFormatSupport(
            key = normalizedKey,
            readMode = EbookReadMode.ExternalOnly,
            displayName = displayName,
            reason = "$displayName downloads stay in their original format and can be opened with another reader.",
        )
        else -> EbookFormatSupport(
            key = normalizedKey,
            readMode = EbookReadMode.Unsupported,
            displayName = displayName,
            reason = "This file format is not supported as an ebook download.",
        )
    }
}

private fun String.normalizedEbookFormatKey(): String =
    if (this == "markdown") "md" else this

fun FileVersion.ebookFormatSupport(): EbookFormatSupport =
    ebookFormatSupport(ebookFormatKey())

fun String.ebookFormatDisplayName(): String {
    val key = trim().lowercase().removePrefix(".")
    return when (key) {
        "epub" -> "EPUB"
        "pdf" -> "PDF"
        "cbz" -> "Comic Book ZIP"
        "cbr" -> "Comic Book RAR"
        "fb2", "fbz" -> "FictionBook"
        "mobi" -> "MOBI"
        "azw", "azw3" -> "Kindle"
        "md" -> "Markdown"
        "txt" -> "Plain text"
        "", "unknown" -> "Unknown"
        else -> key.uppercase()
    }
}

fun String.isInAppReadableEbookFormat(): Boolean =
    ebookFormatSupport(this).canReadInApp

fun FileVersion.ebookFormatDisplayName(): String =
    ebookFormatKey().orEmpty().ebookFormatDisplayName()

fun FileVersion.isInAppReadableEbookVersion(): Boolean =
    ebookFormatKey().orEmpty().isInAppReadableEbookFormat()

fun FileVersion.bookFormatFromEbookVersion(): BookFormat {
    val formatFromKey = BookFormat.fromWire(ebookFormatKey())
    return if (formatFromKey != BookFormat.Unknown) {
        formatFromKey
    } else {
        BookFormat.fromPath(fileName)
    }
}

fun ebookPageNumberFromProgressLocation(location: String?): Int? =
    location
        ?.trim()
        ?.takeIf { it.startsWith("page:") }
        ?.removePrefix("page:")
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it >= 0 }

fun ebookProgressPercentForPage(
    page: Int,
    pageCount: Int?,
    currentProgress: Double,
): Double {
    if (pageCount == null || pageCount <= 0) {
        return currentProgress.coerceIn(0.0, 1.0)
    }
    if (pageCount == 1) {
        return if (page <= 0) 0.0 else 1.0
    }
    return (page.coerceAtLeast(0).toDouble() / (pageCount - 1).toDouble()).coerceIn(0.0, 1.0)
}

fun chooseEbookVersion(
    versions: List<FileVersion>,
    requestedFileId: Int?,
): FileVersion? {
    if (requestedFileId != null) {
        versions.firstOrNull { it.fileId == requestedFileId && it.isInAppReadableEbookVersion() }?.let { return it }
    }

    val supported = versions.filter { it.isInAppReadableEbookVersion() }
    if (supported.isEmpty()) return null

    return supported.minBy { version ->
        preferredOrder.indexOf(version.ebookFormatKey()).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }
}
