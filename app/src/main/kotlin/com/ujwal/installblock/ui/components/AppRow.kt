// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val RowShape = RoundedCornerShape(20.dp)
private val IconContainerShape = RoundedCornerShape(13.dp)
private val LightCardBg = Color(0xFFFFFFFF)
private val DarkCardBg = Color(0xFF18181B)
private val LightBorderBrush = Brush.verticalGradient(
    listOf(
        Color.White.copy(alpha = 0.90f),
        Color(0x180F172A),
    ),
)
private val DarkBorderBrush = Brush.verticalGradient(
    listOf(
        Color(0x22FFFFFF),
        Color(0x10FFFFFF),
    ),
)

@Composable
fun AppRow(
    appName: String,
    packageName: String,
    stateLabel: String,
    iconModel: Any? = null,
    selected: Boolean = false,
    selectionEnabled: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val cardBg = if (isLight) LightCardBg else DarkCardBg
    val borderBrush = if (isLight) LightBorderBrush else DarkBorderBrush

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 450f),
        label = "rowScale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isLight) 2.dp else 0.dp,
                shape = RowShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            )
            .clip(RowShape)
            .background(cardBg)
            .border(width = 1.dp, brush = borderBrush, shape = RowShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selectionEnabled) {
            val checkBg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            val checkBorder = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onSelectedChange(!selected) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(checkBg)
                        .border(1.5.dp, checkBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Icon container: fast, lightweight, no layout jumps or heavy animations during fast scroll
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(IconContainerShape),
            contentAlignment = Alignment.Center,
        ) {
            if (iconModel != null) {
                AsyncImage(
                    model = iconModel,
                    contentDescription = "$appName icon",
                    modifier = Modifier.size(44.dp),
                )
            } else {
                LetterAvatar(appName = appName)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        StatePill(label = stateLabel)
    }
}
