package org.siloserver.silo.android.ui.screens.browse

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class CatalogViewDensity(
    val label: String,
    val minCardWidth: Dp,
) {
    Comfortable("Comfortable", 128.dp),
    Normal("Normal", 104.dp),
    Compact("Compact", 92.dp),
}
