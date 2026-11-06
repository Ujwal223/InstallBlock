// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun ScreenBackdrop(content: @Composable () -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    val isLight = bg.red > 0.5f

    val backdropModifier = if (isLight) {
        // Pure luminous slate background for light mode :  no dirty gray/black center band
        Modifier
            .fillMaxSize()
            .background(bg)
    } else {
        val surfaceSubtle = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        bg,
                        surfaceSubtle,
                        bg,
                    ),
                ),
            )
    }

    Box(modifier = backdropModifier) { content() }
}
