// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 4.dp,
    content: @Composable () -> Unit,
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val surfaceColor = if (isLight) Color(0xFFFFFFFF) else Color(0xFF18181B)
    val borderBrush = if (isLight) {
        // Subtle glassy specular border for light mode
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.95f),
                Color(0x1F0F172A),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0x28FFFFFF),
                Color(0x12FFFFFF),
            ),
        )
    }
    val ambientShadow = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.25f)
    val spotShadow = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = ambientShadow,
                spotColor = spotShadow,
            )
            .clip(shape)
            .background(surfaceColor)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape,
            ),
    ) { content() }
}
