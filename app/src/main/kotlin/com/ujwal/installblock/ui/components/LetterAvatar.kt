// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LetterAvatar(
    appName: String,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val bgColor = if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A)
    val textColor = if (isLight) Color(0xFF334155) else Color(0xFFE2E8F0)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = appName.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            ),
            color = textColor,
        )
    }
}
