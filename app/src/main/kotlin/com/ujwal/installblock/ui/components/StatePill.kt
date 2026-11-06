// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.installblock.ui.theme.InstallBlockColors

@Composable
fun StatePill(
    label: String,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f

    val (bgColor, textColor, dotColor) = when (label.lowercase()) {
        "blocked" -> Triple(
            if (isLight) InstallBlockColors.LightBlockedBg else InstallBlockColors.DarkBlockedBg,
            if (isLight) InstallBlockColors.LightBlockedText else InstallBlockColors.DarkBlockedText,
            if (isLight) InstallBlockColors.Success else InstallBlockColors.DarkBlockedText,
        )
        "plain" -> Triple(
            if (isLight) InstallBlockColors.LightBlockedBg else InstallBlockColors.DarkBlockedBg,
            if (isLight) InstallBlockColors.LightBlockedText else InstallBlockColors.DarkBlockedText,
            if (isLight) InstallBlockColors.Success else InstallBlockColors.DarkBlockedText,
        )
        "locked", "blocked (locked)" -> Triple(
            if (isLight) Color(0xFFF3E8FF) else Color(0x33A855F7),
            if (isLight) Color(0xFF7E22CE) else Color(0xFFC084FC),
            if (isLight) Color(0xFF9333EA) else Color(0xFFC084FC),
        )
        "hidden", "blocked (hidden)" -> Triple(
            if (isLight) Color(0xFFECFEFF) else Color(0x3306B6D4),
            if (isLight) Color(0xFF0E7490) else Color(0xFF22D3EE),
            if (isLight) Color(0xFF0891B2) else Color(0xFF22D3EE),
        )
        "installed" -> Triple(
            if (isLight) InstallBlockColors.LightInstalledBg else InstallBlockColors.DarkInstalledBg,
            if (isLight) InstallBlockColors.LightInstalledText else InstallBlockColors.DarkInstalledText,
            if (isLight) InstallBlockColors.Warning else InstallBlockColors.DarkInstalledText,
        )
        "blocking" -> Triple(
            if (isLight) InstallBlockColors.LightBlockingBg else InstallBlockColors.DarkBlockingBg,
            if (isLight) InstallBlockColors.LightBlockingText else InstallBlockColors.DarkBlockingText,
            if (isLight) InstallBlockColors.BlueAccent else InstallBlockColors.DarkBlockingText,
        )
        "failed" -> Triple(
            if (isLight) Color(0xFFFEF2F2) else Color(0x26EF4444),
            if (isLight) Color(0xFFDC2626) else Color(0xFFF87171),
            if (isLight) Color(0xFFDC2626) else Color(0xFFF87171),
        )
        else -> Triple(
            if (isLight) InstallBlockColors.LightNotBlockedBg else InstallBlockColors.DarkNotBlockedBg,
            if (isLight) InstallBlockColors.LightNotBlockedText else InstallBlockColors.DarkNotBlockedText,
            if (isLight) Color(0xFF94A3B8) else Color(0xFF64748B),
        )
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                ),
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
