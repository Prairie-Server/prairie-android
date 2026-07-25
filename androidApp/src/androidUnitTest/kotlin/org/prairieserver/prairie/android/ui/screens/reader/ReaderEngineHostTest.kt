package org.prairieserver.prairie.android.ui.screens.reader

import org.prairieserver.prairie.common.ebook.ReaderCapabilities
import org.prairieserver.prairie.model.book.BookFormat
import org.prairieserver.prairie.model.ebook.EbookReadMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderEngineHostTest {
    @Test
    fun externalPanelRoutingRequiresExternalOnlyReadMode() {
        val unsupportedExternalEngineState = ReaderUiState(
            isLoading = false,
            readMode = EbookReadMode.Unsupported,
            capabilities = ReaderCapabilities.forFormat(BookFormat.Unknown),
        )

        assertFalse(shouldShowExternalReadingPanel(unsupportedExternalEngineState))
        assertTrue(
            shouldShowExternalReadingPanel(
                unsupportedExternalEngineState.copy(readMode = EbookReadMode.ExternalOnly),
            ),
        )
        assertFalse(
            shouldShowExternalReadingPanel(
                unsupportedExternalEngineState.copy(
                    readMode = EbookReadMode.ExternalOnly,
                    capabilities = ReaderCapabilities.forFormat(BookFormat.Epub),
                ),
            ),
        )
    }
}
