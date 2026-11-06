// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val InstallBlockLightColors = lightColorScheme(
    background = InstallBlockColors.LightBackground,
    surface = InstallBlockColors.LightSurface,
    surfaceVariant = InstallBlockColors.LightSurfaceSubtle,
    onBackground = InstallBlockColors.LightOnBackground,
    onSurface = InstallBlockColors.LightOnSurface,
    onSurfaceVariant = InstallBlockColors.LightMuted,
    primary = InstallBlockColors.LightPrimary,
    onPrimary = InstallBlockColors.LightOnPrimary,
    secondary = InstallBlockColors.LightSurfaceSubtle,
    onSecondary = InstallBlockColors.LightOnBackground,
    tertiary = InstallBlockColors.BlueAccent,
    outline = InstallBlockColors.LightBorder,
    outlineVariant = InstallBlockColors.LightBorderStrong,
)

private val InstallBlockDarkColors = darkColorScheme(
    background = InstallBlockColors.DarkBackground,
    surface = InstallBlockColors.DarkSurface,
    surfaceVariant = InstallBlockColors.DarkSurfaceSubtle,
    onBackground = InstallBlockColors.DarkOnBackground,
    onSurface = InstallBlockColors.DarkOnSurface,
    onSurfaceVariant = InstallBlockColors.DarkMuted,
    primary = InstallBlockColors.DarkPrimary,
    onPrimary = InstallBlockColors.DarkOnPrimary,
    secondary = InstallBlockColors.DarkSurfaceSubtle,
    onSecondary = InstallBlockColors.DarkOnSurface,
    tertiary = InstallBlockColors.BlueAccent,
    outline = InstallBlockColors.DarkBorder,
    outlineVariant = InstallBlockColors.DarkBorderStrong,
)

@Composable
fun InstallBlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) InstallBlockDarkColors else InstallBlockLightColors

    MaterialTheme(
        colorScheme = colors,
        typography = InstallBlockTypography,
        shapes = InstallBlockShapes,
        content = content,
    )
}
