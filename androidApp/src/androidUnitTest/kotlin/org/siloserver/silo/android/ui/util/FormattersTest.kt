package org.siloserver.silo.android.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {

    @Test
    fun zeroAndNegativeBytesShowZero() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("0 B", formatBytes(-42))
    }

    @Test
    fun bytesBelowOneKilobyteStayInBytes() {
        assertEquals("500.0 B", formatBytes(500))
    }

    @Test
    fun kilobytesMegabytesGigabytesUseOneDecimal() {
        assertEquals("1.0 KB", formatBytes(1_024))
        assertEquals("1.5 KB", formatBytes(1_536))
        assertEquals("250.0 MB", formatBytes(262_144_000))
        assertEquals("1.0 GB", formatBytes(1_073_741_824))
    }

    @Test
    fun terabytesAreTheLargestUnit() {
        assertEquals("1.0 TB", formatBytes(1_099_511_627_776))
        assertEquals("1024.0 TB", formatBytes(1_125_899_906_842_624))
    }

    @Test
    fun clockTimeFormatsMinutesAndSeconds() {
        assertEquals("0:00", formatClockTime(0.0))
        assertEquals("0:59", formatClockTime(59.9)) // truncates, never rounds up
        assertEquals("1:05", formatClockTime(65.0))
    }

    @Test
    fun clockTimeFormatsHours() {
        assertEquals("1:00:00", formatClockTime(3600.0))
        assertEquals("2:03:04", formatClockTime(7384.5))
    }

    @Test
    fun clockTimeGuardsNanAndNegatives() {
        assertEquals("0:00", formatClockTime(Double.NaN))
        assertEquals("0:00", formatClockTime(-12.0))
    }
}
