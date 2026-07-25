package org.prairieserver.prairie.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Mirrors PrairieTheme.swift radii: 6 / 8 / 14 / capsule(100).
val PillShape = RoundedCornerShape(100.dp)

val PrairieShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(14.dp),
)
