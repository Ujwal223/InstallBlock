// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.components

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tab(
    val route: String,
    val outlined: ImageVector,
    val filled: ImageVector,
    val label: String,
)

/**
 * Apple Photos-style frosted glass floating bottom tab bar.
 *
 * Utilizes translucent milky glass diffusion, specular gradient rim reflection,
 * an inner refraction sheen, and fluid spring micro-interactions.
 */
@Composable
fun GlassTabBar(
    selectedRoute: String,
    onTabSelected: (String) -> Unit,
    onHomeDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var lastHomeTap by remember { mutableLongStateOf(0L) }
    val haptic = LocalHapticFeedback.current
    val tabs = remember {
        listOf(
            Tab("browse", Icons.Outlined.Home, Icons.Filled.Home, "Browse"),
            Tab("blocked", Icons.Outlined.Block, Icons.Filled.Block, "Blocked"),
            Tab("settings", Icons.Outlined.Settings, Icons.Filled.Settings, "Settings"),
        )
    }

    val isLight = MaterialTheme.colorScheme.background.red > 0.5f

    // 1. Translucent milky / obsidian glass base
    val glassBgBrush = if (isLight) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xD9FFFFFF), // 85% opacity white
                Color(0xA6F8FAFC), // 65% opacity subtle off-white
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xD0222228), // 81% opacity smoked glass
                Color(0x9E121216), // 62% opacity deep dark glass
            ),
        )
    }

    // 2. Specular glass rim border catching overhead light (Apple glass signature)
    val glassBorderBrush = if (isLight) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xB3FFFFFF), // Crisp top highlight
                Color(0x18000000), // Subtle bottom contour
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x59FFFFFF), // Top specular reflection
                Color(0x12FFFFFF), // Muted bottom contour
            ),
        )
    }

    // 3. Diffused ambient depth
    val ambientShadow = if (isLight) Color.Black.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.40f)
    val spotShadow = if (isLight) Color.Black.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.55f)

    // Nav bar height increased by 15%: 66.dp * 1.15 = 75.9.dp -> 76.dp
    val dockShape = RoundedCornerShape(38.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = dockShape,
                    clip = false,
                    ambientColor = ambientShadow,
                    spotColor = spotShadow,
                )
                .clip(dockShape)
                .background(glassBgBrush)
                .border(width = 1.dp, brush = glassBorderBrush, shape = dockShape),
        ) {
            // Inner specular sheen line right beneath top curve
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 24.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (isLight) Color(0x66FFFFFF) else Color(0x33FFFFFF),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            val pillShape = RoundedCornerShape(26.dp)
            val activeGlassBrush = if (isLight) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF).copy(alpha = 0.95f),
                        Color(0xFFE2E8F0).copy(alpha = 0.70f),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3A3A44).copy(alpha = 0.85f),
                        Color(0xFF222228).copy(alpha = 0.60f),
                    ),
                )
            }

            val activeGlassBorder = if (isLight) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White,
                        Color(0x24000000),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.40f),
                        Color.White.copy(alpha = 0.08f),
                    ),
                )
            }

            val selectedIndex = tabs.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                val tabCount = tabs.size
                val slotWidth = maxWidth / tabCount
                val basePillWidth = 98.dp.coerceAtMost(slotWidth - 4.dp)
                val targetOffsetX = slotWidth * selectedIndex + (slotWidth - basePillWidth) / 2

                // Smooth, ultra-responsive sliding spring animation (no layout passes on frames)
                val animatedOffsetX by animateDpAsState(
                    targetValue = targetOffsetX,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 850f,
                    ),
                    label = "fluidPillOffset",
                )

                // Single shared sliding liquid glass indicator pill rendered on GPU layer
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = animatedOffsetX.toPx()
                        }
                        .size(width = basePillWidth, height = 60.dp)
                        .shadow(
                            elevation = 6.dp,
                            shape = pillShape,
                            clip = false,
                            ambientColor = if (isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.04f),
                            spotColor = if (isLight) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.08f),
                        )
                        .clip(pillShape)
                        .background(activeGlassBrush)
                        .border(width = 1.dp, brush = activeGlassBorder, shape = pillShape),
                ) {
                    // Top liquid meniscus sheen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 14.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        if (isLight) Color.White else Color(0x66FFFFFF),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }

                // Interactive tab items positioned seamlessly over the sliding liquid
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        val selected = selectedRoute == tab.route

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.92f else 1f,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
                            label = "tabScale",
                        )

                        val activeContentColor = MaterialTheme.colorScheme.primary
                        val inactiveContentColor = if (isLight) Color(0xFF64748B) else Color(0xFF94A3B8)

                        val contentColor by animateColorAsState(
                            targetValue = if (selected) activeContentColor else inactiveContentColor,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
                            label = "tabContentColor",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) {
                                    val now = SystemClock.elapsedRealtime()
                                    if (tab.route == "browse" && now - lastHomeTap < 350L) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onHomeDoubleTap()
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTabSelected(tab.route)
                                    }
                                    if (tab.route == "browse") {
                                        lastHomeTap = now
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = if (selected) tab.filled else tab.outlined,
                                    contentDescription = tab.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    letterSpacing = (-0.2).sp,
                                    color = contentColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
