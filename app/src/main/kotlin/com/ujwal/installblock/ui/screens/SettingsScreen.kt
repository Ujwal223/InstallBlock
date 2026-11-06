// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.installblock.AppViewModel
import com.ujwal.installblock.BuildConfig
import com.ujwal.installblock.data.model.AppState
import com.ujwal.installblock.data.model.BlockType
import com.ujwal.installblock.install.BlockProgress
import com.ujwal.installblock.install.InstallMode
import com.ujwal.installblock.install.ShizukuState
import com.ujwal.installblock.ui.components.GlassSurface
import com.ujwal.installblock.ui.components.ScreenBackdrop

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onOpenShizukuGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val loadIcons by viewModel.loadIcons.collectAsState(initial = true)
    val installMode by viewModel.installMode.collectAsState(initial = InstallMode.Shizuku)
    val theme by viewModel.darkTheme.collectAsState(initial = "auto")
    var showReleaseAllConfirm by remember { mutableStateOf(false) }

    var hasInstallPerm by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    val shizukuState by viewModel.shizukuState.collectAsState()

    val openInstallSettings: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
                )
            }.onFailure {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasInstallPerm = context.packageManager.canRequestPackageInstalls()
                viewModel.refreshShizukuState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(14.dp))

            // ── Header ────────────────────────────────────────────────────────
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = "Preferences and install configurations",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // ── Appearance Card ───────────────────────────────────────────────
            SettingsCard(
                title = "Appearance",
                icon = Icons.Outlined.Brightness6,
            ) {
                Text(
                    text = "Choose your preferred color theme.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                SegmentedChoiceRow(
                    selected = theme,
                    onSelected = viewModel::setDarkTheme,
                    choices = listOf(
                        Choice("auto", "System", Icons.Outlined.Brightness6),
                        Choice("light", "Light", Icons.Outlined.LightMode),
                        Choice("dark", "Dark", Icons.Outlined.DarkMode),
                    ),
                )
            }

            // ── Blocking Mode Card ────────────────────────────────────────────
            val engineCardIcon = when (installMode) {
                InstallMode.Normal -> Icons.Outlined.InstallMobile
                InstallMode.Auto -> Icons.Outlined.SettingsSuggest
                InstallMode.Shizuku -> Icons.Outlined.Security
            }

            SettingsCard(
                title = "Blocking Engine",
                icon = engineCardIcon,
            ) {
                val hasShizukuPerm = shizukuState == ShizukuState.Ready

                // Engine Status Badge based on selected mode
                val (statusText, statusColor) = when (installMode) {
                    InstallMode.Normal -> {
                        if (hasInstallPerm) {
                            "Basic Mode: Active & Ready" to Color(0xFF10B981)
                        } else {
                            "Basic Mode: Permission Required" to Color(0xFFF59E0B)
                        }
                    }
                    InstallMode.Shizuku -> {
                        when (shizukuState) {
                            ShizukuState.Ready -> "Shizuku: Running & Authorized" to Color(0xFF10B981)
                            ShizukuState.RunningButNotGranted -> "Shizuku: Running • Authorization Needed" to Color(0xFFF59E0B)
                            ShizukuState.Stopped -> "Shizuku: Stopped (restart via Wireless Debugging)" to Color(0xFFEF4444)
                            ShizukuState.NotInstalled -> "Shizuku: Not Installed" to Color(0xFF94A3B8)
                        }
                    }
                    InstallMode.Auto -> {
                        when {
                            hasShizukuPerm -> "Auto Mode: Shizuku Active (Silent)" to Color(0xFF10B981)
                            hasInstallPerm -> "Auto Mode: Basic Active (Fallback)" to Color(0xFF10B981)
                            else -> "Auto Mode: Permissions Needed" to Color(0xFFF59E0B)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = if (isLight) 0.12f else 0.20f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isLight) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                    )
                }

                val modeDescription = when (installMode) {
                    InstallMode.Normal ->
                        "Standard Android mode. Replaces target apps using system package installer dialogs. Requires permission to install unknown apps."
                    InstallMode.Shizuku ->
                        "Advanced silent mode. Uses Shizuku to lock apps silently and keep placeholders hidden from device settings so they cannot be removed."
                    InstallMode.Auto ->
                        "Automatic selection. Uses silent Shizuku blocking when available, and seamlessly falls back to standard installer dialogs otherwise."
                }

                Text(
                    text = modeDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    lineHeight = 20.sp,
                )

                SegmentedChoiceRow(
                    selected = installMode.name,
                    onSelected = { viewModel.setInstallMode(InstallMode.valueOf(it)) },
                    choices = listOf(
                        Choice("Shizuku", "Shizuku", Icons.Outlined.Security),
                        Choice("Normal", "Basic", Icons.Outlined.InstallMobile),
                        Choice("Auto", "Auto", Icons.Outlined.SettingsSuggest),
                    ),
                )

                when (installMode) {
                    InstallMode.Normal -> {
                        if (!hasInstallPerm) {
                            FilledTonalButton(
                                onClick = openInstallSettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Allow Unknown Apps Permission", maxLines = 1)
                            }
                        }
                    }

                    InstallMode.Shizuku -> {
                        if (!hasShizukuPerm) {
                            val buttonText = when (shizukuState) {
                                ShizukuState.NotInstalled -> "Setup Guide"
                                ShizukuState.Stopped -> "Start Guide"
                                ShizukuState.RunningButNotGranted -> "Authorize"
                                ShizukuState.Ready -> "Authorized"
                            }
                            val buttonIcon = when (shizukuState) {
                                ShizukuState.NotInstalled -> Icons.Outlined.Info
                                ShizukuState.Stopped -> Icons.AutoMirrored.Outlined.OpenInNew
                                else -> Icons.Outlined.Security
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (shizukuState == ShizukuState.NotInstalled || shizukuState == ShizukuState.Stopped) {
                                        onOpenShizukuGuide()
                                    } else {
                                        viewModel.requestShizukuPermission()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(buttonText, maxLines = 1)
                            }
                        }
                    }

                    InstallMode.Auto -> {
                        val showShizukuAction = !hasShizukuPerm
                        val showInstallAction = !hasInstallPerm

                        if (showShizukuAction || showInstallAction) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (showShizukuAction) {
                                    val buttonText = when (shizukuState) {
                                        ShizukuState.NotInstalled -> "Setup Guide"
                                        ShizukuState.Stopped -> "Start Guide"
                                        ShizukuState.RunningButNotGranted -> "Authorize"
                                        ShizukuState.Ready -> "Authorized"
                                    }
                                    val buttonIcon = when (shizukuState) {
                                        ShizukuState.NotInstalled -> Icons.Outlined.Info
                                        ShizukuState.Stopped -> Icons.AutoMirrored.Outlined.OpenInNew
                                        else -> Icons.Outlined.Security
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            if (shizukuState == ShizukuState.NotInstalled || shizukuState == ShizukuState.Stopped) {
                                                onOpenShizukuGuide()
                                            } else {
                                                viewModel.requestShizukuPermission()
                                            }
                                        },
                                        modifier = if (showInstallAction) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    ) {
                                        Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(buttonText, maxLines = 1)
                                    }
                                }

                                if (showInstallAction) {
                                    FilledTonalButton(
                                        onClick = openInstallSettings,
                                        modifier = if (showShizukuAction) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                    ) {
                                        Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Allow Unknown Apps", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Blocklist & Network Card ──────────────────────────────────────
            SettingsCard(
                title = "Data & Maintenance",
                icon = Icons.Outlined.CloudDownload,
            ) {
                Text(
                    text = "InstallBlock uses an offline seed list and updates in the background on launch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    lineHeight = 20.sp,
                )

                SettingRow(
                    label = "Load remote app icons",
                    subtitle = "Decoded in an isolated sandbox process",
                    checked = loadIcons,
                    onCheckedChange = viewModel::setLoadIcons,
                )

                FilledTonalButton(
                    onClick = { showReleaseAllConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isLight) Color(0xFFFEF2F2) else Color(0x33EF4444),
                        contentColor = if (isLight) Color(0xFFDC2626) else Color(0xFFF87171),
                    ),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Release all blocks", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Debug Menu (Only compiled/shown in Debug Builds) ───────────────
            if (BuildConfig.DEBUG) {
                DebugMenuCard(
                    viewModel = viewModel,
                    isLight = isLight,
                    onOpenSetupGuide = onOpenShizukuGuide,
                )
            }

            // ── Crucial bottom spacer so the last options are NEVER blocked by GlassTabBar ──
            Spacer(Modifier.height(112.dp + navBottomInset))
        }
    }

    if (showReleaseAllConfirm) {
        AlertDialog(
            onDismissRequest = { showReleaseAllConfirm = false },
            title = { Text("Release all blocks?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will remove all placeholder records and clear any active device admin entries. All blocked apps will become reinstallable.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        showReleaseAllConfirm = false
                        viewModel.releaseAll()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Release everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseAllConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

private data class Choice(
    val value: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = if (isLight) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            content()
        }
    }
}

@Composable
private fun SegmentedChoiceRow(
    selected: String,
    onSelected: (String) -> Unit,
    choices: List<Choice>,
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val containerBg = if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A)
    val activePillBg = if (isLight) Color(0xFF0F172A) else Color(0xFFFFFFFF)
    val activePillText = if (isLight) Color(0xFFFFFFFF) else Color(0xFF09090B)
    val inactiveText = if (isLight) Color(0xFF475569) else Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            choices.forEach { choice ->
                val isSelected = choice.value.equals(selected, ignoreCase = true)

                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) activePillBg else Color.Transparent,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 450f),
                    label = "segmentBg",
                )
                val textTint by animateColorAsState(
                    targetValue = if (isSelected) activePillText else inactiveText,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 450f),
                    label = "segmentText",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pillBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(choice.value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = choice.icon,
                            contentDescription = null,
                            tint = textTint,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = choice.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color = textTint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (isLight) Color.White else Color(0xFF09090B),
                checkedTrackColor = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (isLight) Color.White else Color(0xFFA1A1AA),
                uncheckedTrackColor = if (isLight) Color(0xFFE2E8F0) else Color(0xFF27272A),
                uncheckedBorderColor = if (isLight) Color(0xFFCBD5E1) else Color(0xFF3F3F46),
            ),
        )
    }
}

/**
 * Developer and debug tools card, exclusively rendered in debug builds.
 * Clean, structured, and every single feature verified to work.
 */
@Composable
private fun DebugMenuCard(
    viewModel: AppViewModel,
    isLight: Boolean,
    onOpenSetupGuide: () -> Unit,
) {
    val debugShizukuOverride by viewModel.debugShizukuOverride.collectAsState()
    var stateTestResult by remember { mutableStateOf<String?>(null) }
    var syncResult by remember { mutableStateOf<String?>(null) }
    var selectedTestApp by remember { mutableStateOf("com.zhiliaoapp.musically" to "TikTok") }

    SettingsCard(
        title = "Debug & Developer Tools",
        icon = Icons.Outlined.BugReport,
    ) {
        // Debug Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isLight) Color(0xFFFEF3C7) else Color(0xFF451A03))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B)),
            )
            Text(
                text = "DEVELOPER SUITE • Local testing only",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isLight) Color(0xFF92400E) else Color(0xFFFDE68A),
            )
        }

        // 1. Onboarding Flow Navigation
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Onboarding Flow Navigation",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { viewModel.debugResetOnboarding(startPage = 0) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Page 0", fontSize = 11.5.sp)
                }

                FilledTonalButton(
                    onClick = { viewModel.debugResetOnboarding(startPage = 1) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Shizuku (1)", fontSize = 11.5.sp)
                }

                FilledTonalButton(
                    onClick = { viewModel.debugResetOnboarding(startPage = 2) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.InstallMobile, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Basic (2)", fontSize = 11.5.sp)
                }
            }
        }

        // 2. Simulate Shizuku State & Guide Preview
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Simulate Shizuku Engine (${debugShizukuOverride?.name ?: "Live / Real"})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val states = listOf(
                    null to "Live",
                    ShizukuState.Ready to "Ready",
                    ShizukuState.RunningButNotGranted to "Auth",
                    ShizukuState.Stopped to "Stopped",
                    ShizukuState.NotInstalled to "Not Installed",
                )
                states.forEach { (st, label) ->
                    val isSelected = debugShizukuOverride == st
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else if (isLight) Color(0xFFF1F5F9)
                                else Color(0xFF27272A),
                            )
                            .clickable {
                                viewModel.debugSetShizukuStateOverride(st)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Quick button to open setup guide full page
            FilledTonalButton(
                onClick = onOpenSetupGuide,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open Shizuku Setup Page", fontSize = 12.sp)
            }
        }

        // 3. Mock Blocked Apps for Blocklist Testing
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Blocklist Mock Apps",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.debugAddDummyBlocked("com.zhiliaoapp.musically", "TikTok")
                        viewModel.debugAddDummyBlocked("com.instagram.android", "Instagram")
                        stateTestResult = "Added TikTok & Instagram to Blocklist!"
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Mocks", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        viewModel.debugClearDummyBlocked()
                        stateTestResult = "Cleared mock apps from Blocklist"
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear Mocks", fontSize = 12.sp)
                }
            }
        }

        // 4. Test Catalog App State Simulation
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Simulate State for Catalog App",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            // App Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val testApps = listOf(
                    "com.zhiliaoapp.musically" to "TikTok",
                    "com.instagram.android" to "Instagram",
                    "com.google.android.youtube" to "YouTube",
                )
                testApps.forEach { appPair ->
                    val isSelected = selectedTestApp == appPair
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else if (isLight) Color(0xFFF1F5F9)
                                else Color(0xFF27272A),
                            )
                            .clickable { selectedTestApp = appPair }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = appPair.second,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // State Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val appStates = listOf(
                    AppState.Blocked(BlockType.Hidden) to "Blocked",
                    AppState.Installed to "Installed",
                    AppState.NotBlocked to "Reset",
                    AppState.Failed("Simulated error") to "Failed",
                )
                appStates.forEach { (st, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLight) Color(0xFFF8FAFC) else Color(0xFF202024))
                            .border(1.dp, if (isLight) Color(0x18000000) else Color(0x28FFFFFF), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.debugSimulateState(selectedTestApp.first, st)
                                stateTestResult = "Set ${selectedTestApp.second} to $label"
                            }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        stateTestResult?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        // 5. Remote List Fetching & Sync
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Remote Catalog & Cache",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        viewModel.debugForceSyncCatalog()
                        syncResult = "Force sync triggered successfully!"
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Force Sync", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        viewModel.debugClearAllCache()
                        syncResult = "Cache wiped and local catalog reloaded!"
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear Cache", fontSize = 12.sp)
                }
            }
        }
        syncResult?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981))
        }

        // 6. Test Block Progress Dialogs
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Simulate Dialogs (Dismissable)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { viewModel.debugSimulateProgress(BlockProgress.InstallingPlaceholder) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("In Progress", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        viewModel.debugSimulateProgress(
                            BlockProgress.Failed("Play Protect blocked installation", isVerificationBlock = true),
                        )
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Fail Dialog", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = { viewModel.debugSimulateProgress(BlockProgress.Done) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Done Dialog", fontSize = 12.sp)
                }
            }
        }
    }
}
