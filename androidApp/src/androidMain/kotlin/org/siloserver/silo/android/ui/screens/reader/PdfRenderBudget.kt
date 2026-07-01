package org.siloserver.silo.android.ui.screens.reader

import android.graphics.Bitmap

data class PdfRenderBudget(val targetWidth: Int, val config: Bitmap.Config)

fun pdfRenderBudget(pageWidth: Int, pageHeight: Int, memoryClassMb: Int): PdfRenderBudget {
    val lowMem = memoryClassMb <= 96
    val config = if (lowMem) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    val safeW = pageWidth.coerceAtLeast(1)
    val target = if (lowMem) safeW.coerceAtMost(1200) else (safeW * 2).coerceAtMost(2000)
    return PdfRenderBudget(targetWidth = target.coerceAtLeast(1), config = config)
}
