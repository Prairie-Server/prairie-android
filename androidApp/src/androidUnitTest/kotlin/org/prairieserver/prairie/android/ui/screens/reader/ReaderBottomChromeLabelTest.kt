package org.prairieserver.prairie.android.ui.screens.reader

import org.prairieserver.prairie.common.ebook.ReaderCapabilities
import org.prairieserver.prairie.model.book.BookFormat
import kotlin.test.Test
import kotlin.test.assertFalse

class ReaderBottomChromeLabelTest {
    @Test
    fun externalBottomChromeLabelDoesNotPretendToHavePages() {
        val label = readerBottomChromeLabel(
            ReaderUiState(
                capabilities = ReaderCapabilities.forFormat(BookFormat.Unknown),
                format = BookFormat.Unknown,
            ),
            pageProgressLabel = "0% - Page 1",
            externalReaderLabel = "External reader",
        )

        assertFalse(label.orEmpty().contains("Page"))
    }
}
