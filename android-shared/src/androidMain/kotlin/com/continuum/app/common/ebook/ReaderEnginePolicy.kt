package com.continuum.app.common.ebook

import com.continuum.app.model.book.BookFormat
import com.continuum.app.model.ebook.EbookReadMode

enum class ReaderEngineKind { Reflowable, FixedDocument, ComicManga, External }

enum class ReaderContentClass { Ebook, Document, ComicManga, ExternalOriginal }

data class ReaderEnginePolicy(
    val engineKind: ReaderEngineKind,
    val contentClass: ReaderContentClass,
    val supportsInAppReading: Boolean,
)

fun readerEnginePolicyFor(format: BookFormat, readMode: EbookReadMode): ReaderEnginePolicy {
    if (readMode != EbookReadMode.InApp) {
        return externalReaderEnginePolicy()
    }

    return when (format) {
        BookFormat.Epub,
        BookFormat.Fb2,
        BookFormat.Fbz,
        BookFormat.Txt,
        BookFormat.Markdown -> ReaderEnginePolicy(
            engineKind = ReaderEngineKind.Reflowable,
            contentClass = ReaderContentClass.Ebook,
            supportsInAppReading = true,
        )
        BookFormat.Pdf -> ReaderEnginePolicy(
            engineKind = ReaderEngineKind.FixedDocument,
            contentClass = ReaderContentClass.Document,
            supportsInAppReading = true,
        )
        BookFormat.Cbz -> ReaderEnginePolicy(
            engineKind = ReaderEngineKind.ComicManga,
            contentClass = ReaderContentClass.ComicManga,
            supportsInAppReading = true,
        )
        BookFormat.Cbr,
        BookFormat.Mobi,
        BookFormat.Azw,
        BookFormat.Azw3,
        BookFormat.Unknown -> externalReaderEnginePolicy()
    }
}

private fun externalReaderEnginePolicy(): ReaderEnginePolicy =
    ReaderEnginePolicy(
        engineKind = ReaderEngineKind.External,
        contentClass = ReaderContentClass.ExternalOriginal,
        supportsInAppReading = false,
    )
