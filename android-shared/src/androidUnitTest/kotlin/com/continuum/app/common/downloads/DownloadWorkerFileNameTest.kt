package com.continuum.app.common.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadWorkerFileNameTest {

    @Test
    fun `existing catalog filename wins over response header`() {
        assertEquals(
            "Catalog.epub",
            downloadFileNameForTarget(
                catalogFileName = "Catalog.epub",
                contentDisposition = "attachment; filename=\"Header.epub\"",
            ),
        )
    }

    @Test
    fun `content disposition filename is used when catalog filename is missing`() {
        assertEquals(
            "Project Hail Mary.epub",
            downloadFileNameForTarget(
                catalogFileName = null,
                contentDisposition = "attachment; filename=\"Project Hail Mary.epub\"",
            ),
        )
    }

    @Test
    fun `rfc5987 content disposition filename is decoded`() {
        assertEquals(
            "Project Hail Mary.epub",
            downloadFileNameForTarget(
                catalogFileName = null,
                contentDisposition = "attachment; filename*=UTF-8''Project%20Hail%20Mary.epub",
            ),
        )
    }

    @Test
    fun `blank filenames are ignored`() {
        assertNull(downloadFileNameForTarget(catalogFileName = " ", contentDisposition = "attachment"))
    }
}
