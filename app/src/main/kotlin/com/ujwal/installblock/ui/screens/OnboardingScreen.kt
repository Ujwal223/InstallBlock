// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.ujwal.installblock.R
import com.ujwal.installblock.install.ShizukuState
import com.ujwal.installblock.ui.components.GlassSurface
import com.ujwal.installblock.ui.components.ScreenBackdrop
import kotlinx.coroutines.delay

/**
 * Minimal & Aesthetic Onboarding Flow.
 *
 * Page 0: Clean typographic introduction & inspiration (inspired by primary-onboarding-inspo).
 * Page 1: Dedicated Shizuku authorization page featuring the official Shizuku icon on top.
 *         Does not mix install permissions here. Primary "Continue" is gated until Shizuku is
 *         active; "Skip Shizuku" triggers a 5-second countdown confirmation dialog.
 * Page 2: Dedicated "Install unknown apps" permission guide when user skips Shizuku for Basic mode.
 */
@Composable
fun OnboardingScreen(
    initialPage: Int = 0,
    onFinished: (isBasicMode: Boolean) -> Unit,
    getShizukuState: () -> ShizukuState = { ShizukuState.Ready },
    onRequestShizuku: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var page by remember(initialPage) { mutableIntStateOf(initialPage) }
    var enteredBasicMode by remember(initialPage) { mutableStateOf(initialPage >= 2) }
    var liveShizukuState by remember { mutableStateOf(getShizukuState()) }
    var hasInstallPerm by remember {
        mutableStateOf(context.packageManager.canRequestPackageInstalls())
    }
    var showBasicWarningDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = page > 0) {
        page--
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                liveShizukuState = getShizukuState()
                hasInstallPerm = context.packageManager.canRequestPackageInstalls()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    val isLight = MaterialTheme.colorScheme.background.red > 0.5f

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Navigation & Step Indicator Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (page > 0) {
                    IconButton(
                        onClick = { page-- },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.size(40.dp))
                }

                // Aesthetic step capsule (adapts to 3 steps when user skips to basic setup)
                val totalSteps = if (enteredBasicMode || page >= 2) 3 else 2
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(totalSteps) { dotIndex ->
                        val isSelected = dotIndex == page
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 450f),
                            label = "indicatorWidth",
                        )
                        val dotColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
                        }
                        Box(
                            modifier = Modifier
                                .size(width = dotWidth, height = 5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(dotColor),
                        )
                    }
                }

                if (page == 1) {
                    TextButton(
                        onClick = {
                            if (enteredBasicMode) {
                                page = 2
                            } else {
                                showBasicWarningDialog = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Skip Shizuku",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Spacer(Modifier.size(40.dp))
                }
            }

            // Animated Page Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width / 3 } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width / 3 } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> width / 3 } + fadeOut())
                        }
                    },
                    label = "onboarding-page-anim",
                ) { targetPage ->
                    when (targetPage) {
                        0 -> MinimalWelcomePage(isLight = isLight)
                        1 -> ShizukuSetupPage(
                            shizukuState = liveShizukuState,
                            isLight = isLight,
                            onRequestShizuku = onRequestShizuku,
                            onRefreshStatus = { liveShizukuState = getShizukuState() },
                        )
                        else -> InstallPermissionSetupPage(
                            hasInstallPerm = hasInstallPerm,
                            isLight = isLight,
                            onOpenSettings = openInstallSettings,
                        )
                    }
                }
            }

            // Bottom Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (page == 0) {
                    Button(
                        onClick = { page = 1 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = "Get Started",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                            ),
                        )
                    }

                    // Discreet privacy footnote
                    Text(
                        text = "100% on-device • Zero tracking • Unbreakable",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                    )
                } else if (page == 1) {
                    val isShizukuReady = liveShizukuState == ShizukuState.Ready

                    if (isShizukuReady) {
                        Button(
                            onClick = { onFinished(false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                ),
                            )
                        }
                    } else if (liveShizukuState == ShizukuState.RunningButNotGranted) {
                        Button(
                            onClick = onRequestShizuku,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = "Authorize with Shizuku",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                ),
                            )
                        }
                    } else {
                        Button(
                            onClick = { liveShizukuState = getShizukuState() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Check Status Again",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                ),
                            )
                        }
                    }
                } else {
                    if (hasInstallPerm) {
                        Button(
                            onClick = { onFinished(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                text = "Finish Setup",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                ),
                            )
                        }
                    } else {
                        Button(
                            onClick = openInstallSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Open Settings to Allow",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                ),
                            )
                        }

                        TextButton(
                            onClick = { onFinished(true) },
                        ) {
                            Text(
                                text = "Continue anyway",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
        }
    }

    // 5-second Gated Basic Mode Warning Dialog
    if (showBasicWarningDialog) {
        BasicModeCountdownDialog(
            onDismiss = { showBasicWarningDialog = false },
            onConfirm = {
                showBasicWarningDialog = false
                enteredBasicMode = true
                page = 2
            },
            isLight = isLight,
        )
    }
}

/**
 * Slide 2: Install Unknown Apps Permission Guide for Basic Mode.
 * Instructs users on granting permission to install placeholder packages directly.
 * Auto-detects permission status in real-time when returning from system settings.
 */
@Composable
private fun InstallPermissionSetupPage(
    hasInstallPerm: Boolean,
    isLight: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Squircle Icon on top
        Box(
            modifier = Modifier
                .size(80.dp)
                .shadow(
                    elevation = if (isLight) 10.dp else 16.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.18f),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(if (isLight) Color(0xFFEFF6FF) else Color(0xFF1E293B))
                .border(
                    width = 1.dp,
                    color = if (isLight) Color(0x18000000) else Color(0x28FFFFFF),
                    shape = RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.InstallMobile,
                contentDescription = "Install Permission",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Install Permission",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = (-0.5).sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Basic mode needs Android's permission to install safety placeholder apps when blocking.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        )

        Spacer(Modifier.height(24.dp))

        // Permission Status Capsule
        val (statusText, statusColor) = if (hasInstallPerm) {
            "Permission Granted" to Color(0xFF10B981)
        } else {
            "Permission Required" to Color(0xFFF59E0B)
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(statusColor.copy(alpha = if (isLight) 0.12f else 0.20f))
                .border(1.dp, statusColor.copy(alpha = 0.35f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC),
            )
        }

        Spacer(Modifier.height(20.dp))

        if (hasInstallPerm) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ready to proceed",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "InstallBlock can now present system dialogs to replace target apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SetupStepItem(
                    stepNumber = "1",
                    title = "Open App Settings",
                    description = "Android requires explicit user approval to allow installing apps from this source.",
                    isCompleted = false,
                    isLight = isLight,
                ) {
                    FilledTonalButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isLight) Color(0xFFE2E8F0) else Color(0xFF27272A),
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Go to Settings",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }

                SetupStepItem(
                    stepNumber = "2",
                    title = "Toggle 'Allow from this source'",
                    description = "Switch the toggle to ON. You can revoke this anytime in system settings.",
                    isCompleted = false,
                    isLight = isLight,
                )

                SetupStepItem(
                    stepNumber = "3",
                    title = "Return to InstallBlock",
                    description = "Once toggled, return here. The status above will update automatically.",
                    isCompleted = false,
                    isLight = isLight,
                )
            }
        }
    }
}

/**
 * Slide 0: Minimalist & Aesthetic Introduction.
 * No walls of text or cards :  generous spacing, bold typography, instant inspiration.
 */
@Composable
private fun MinimalWelcomePage(isLight: Boolean) {
    val context = LocalContext.current
    val appIcon = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName)
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // App icon squircle emblem
        Box(
            modifier = Modifier
                .size(80.dp)
                .shadow(
                    elevation = if (isLight) 10.dp else 16.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.18f),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF222226))
                .border(
                    width = 1.dp,
                    color = if (isLight) Color(0x18000000) else Color(0x28FFFFFF),
                    shape = RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (appIcon != null) {
                AsyncImage(
                    model = appIcon,
                    contentDescription = "InstallBlock icon",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "InstallBlock icon",
                    modifier = Modifier.size(64.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Block apps.\nProtect your device.",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 38.sp,
                letterSpacing = (-1.2).sp,
                lineHeight = 44.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Stop unwanted apps, system update bloatware, and unauthorized installations permanently.",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "What do you want to keep off your device?",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
        )

        Spacer(Modifier.height(12.dp))

        val protectionTargets = remember {
            listOf("📦 OEM Bloatware", "🔄 System Updates", "👾 Malicious Apps", "📊 Trackers", "⚠️ Unverified APKs")
        }
        var selectedTargets by remember { mutableStateOf(setOf("📦 OEM Bloatware", "🔄 System Updates")) }
        val haptic = LocalHapticFeedback.current

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            protectionTargets.forEach { item ->
                val isSelected = item in selectedTargets
                val chipBg = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isLight) 0.12f else 0.22f)
                } else {
                    if (isLight) Color(0xFFF1F5F9) else Color(0xFF222226)
                }
                val chipBorder = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    if (isLight) Color(0x18000000) else Color(0x28FFFFFF)
                }
                val chipTextColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(chipBg)
                        .border(1.dp, chipBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTargets = if (isSelected) selectedTargets - item else selectedTargets + item
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                        ),
                        color = chipTextColor,
                    )
                }
            }
        }
    }
}

/**
 * Slide 1: Shizuku Engine Page.
 * Displays official Shizuku icon on top.
 * Dedicated strictly to Shizuku setup :  does not mix unknown app permissions.
 */
@Composable
private fun ShizukuSetupPage(
    shizukuState: ShizukuState,
    isLight: Boolean,
    onRequestShizuku: () -> Unit,
    onRefreshStatus: () -> Unit,
) {
    val context = LocalContext.current
    val shizukuInstalledIcon = remember(context) {
        runCatching {
            context.packageManager.getApplicationIcon("moe.shizuku.privileged.api")
        }.getOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Official Shizuku Icon on top
        Box(
            modifier = Modifier
                .size(80.dp)
                .shadow(
                    elevation = if (isLight) 10.dp else 16.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.18f),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF222226))
                .border(
                    width = 1.dp,
                    color = if (isLight) Color(0x18000000) else Color(0x28FFFFFF),
                    shape = RoundedCornerShape(24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (shizukuInstalledIcon != null) {
                AsyncImage(
                    model = shizukuInstalledIcon,
                    contentDescription = "Shizuku",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_shizuku_logo),
                    contentDescription = "Shizuku",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Setup Shizuku",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                letterSpacing = (-0.6).sp,
                lineHeight = 34.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Follow these 4 simple steps to unlock automatic, silent app blocking on your device.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp),
        )

        Spacer(Modifier.height(24.dp))

        // Shizuku Status Capsule
        val (statusText, statusColor) = when (shizukuState) {
            ShizukuState.Ready -> "Active & Authorized" to Color(0xFF10B981)
            ShizukuState.RunningButNotGranted -> "Running • Authorization Needed" to Color(0xFFF59E0B)
            ShizukuState.Stopped -> "Service Stopped" to Color(0xFFEF4444)
            ShizukuState.NotInstalled -> "Not Installed" to Color(0xFF94A3B8)
        }

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(statusColor.copy(alpha = if (isLight) 0.12f else 0.20f))
                .border(1.dp, statusColor.copy(alpha = 0.35f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC),
            )
        }

        Spacer(Modifier.height(20.dp))

        // State-specific minimal action card
        when (shizukuState) {
            ShizukuState.Ready -> {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All set!",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Silent blocking and invisible placeholders are enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }

            ShizukuState.RunningButNotGranted,
            ShizukuState.Stopped,
            ShizukuState.NotInstalled,
            -> {
                ShizukuSetupGuideView(
                    shizukuState = shizukuState,
                    isLight = isLight,
                    onRequestShizuku = onRequestShizuku,
                    onRefreshStatus = onRefreshStatus,
                )
            }
        }
    }
}

/**
 * Dedicated full-screen Onboarding-style Shizuku Setup Guide.
 * Replaces popup dialogs with a full, beautiful, scrollable onboarding page.
 * Completely automatic state detection without confusing manual toggles.
 */
@Composable
fun ShizukuSetupGuideScreen(
    shizukuState: ShizukuState,
    isLight: Boolean,
    onRequestShizuku: () -> Unit,
    onRefreshStatus: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isShizukuReady = shizukuState == ShizukuState.Ready

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                // Minimal Setup Guide capsule
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Setup Guide",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                IconButton(
                    onClick = onRefreshStatus,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh Status",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Scrollable Step-by-Step Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Official Shizuku squircle icon
                val shizukuInstalledIcon = remember(context) {
                    runCatching {
                        context.packageManager.getApplicationIcon("moe.shizuku.privileged.api")
                    }.getOrNull()
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(
                            elevation = if (isLight) 10.dp else 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = Color.Black.copy(alpha = 0.12f),
                            spotColor = Color.Black.copy(alpha = 0.18f),
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isLight) Color(0xFFF1F5F9) else Color(0xFF222226))
                        .border(
                            width = 1.dp,
                            color = if (isLight) Color(0x18000000) else Color(0x28FFFFFF),
                            shape = RoundedCornerShape(24.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (shizukuInstalledIcon != null) {
                        AsyncImage(
                            model = shizukuInstalledIcon,
                            contentDescription = "Shizuku",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_shizuku_logo),
                            contentDescription = "Shizuku",
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Setup Shizuku",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.6).sp,
                        lineHeight = 34.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Follow these 4 simple steps in order to enable automatic background blocking.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )

                Spacer(Modifier.height(20.dp))

                // Automatically detected Status Capsule
                val (statusText, statusColor) = when (shizukuState) {
                    ShizukuState.Ready -> "Active & Authorized" to Color(0xFF10B981)
                    ShizukuState.RunningButNotGranted -> "Running • Authorization Needed" to Color(0xFFF59E0B)
                    ShizukuState.Stopped -> "Service Stopped" to Color(0xFFEF4444)
                    ShizukuState.NotInstalled -> "Not Installed" to Color(0xFF94A3B8)
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = if (isLight) 0.12f else 0.20f))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
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
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    )
                }

                Spacer(Modifier.height(24.dp))

                // The 4 sequential steps
                ShizukuSetupGuideView(
                    shizukuState = shizukuState,
                    isLight = isLight,
                    onRequestShizuku = onRequestShizuku,
                    onRefreshStatus = onRefreshStatus,
                )
            }

            // Bottom Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isShizukuReady) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                            ),
                        )
                    }
                } else if (shizukuState == ShizukuState.RunningButNotGranted) {
                    Button(
                        onClick = onRequestShizuku,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = "Authorize with Shizuku",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                            ),
                        )
                    }
                } else {
                    Button(
                        onClick = onRefreshStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Check Status Again",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp,
                            ),
                        )
                    }
                }

                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Return",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    )
                }
            }
        }
    }
}

/**
 * Sequential step-by-step guidance for Shizuku setup.
 * Dynamically detects installed / running status and checks off completed steps.
 * Does NOT require users to self-diagnose or toggle between states.
 */
@Composable
fun ShizukuSetupGuideView(
    shizukuState: ShizukuState,
    isLight: Boolean,
    onRequestShizuku: () -> Unit = {},
    onRefreshStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isInstalled = shizukuState != ShizukuState.NotInstalled
    val isRunning = shizukuState == ShizukuState.RunningButNotGranted || shizukuState == ShizukuState.Ready
    val isReady = shizukuState == ShizukuState.Ready

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Step 1: Download Shizuku
        SetupStepItem(
            stepNumber = "1",
            title = if (isInstalled) "Shizuku Installed" else "Download Shizuku",
            description = if (isInstalled) {
                "The official Shizuku app is installed on your device."
            } else {
                "Install Shizuku from Google Play or download the APK directly from GitHub Releases."
            },
            isCompleted = isInstalled,
            isLight = isLight,
        ) {
            if (!isInstalled) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Google Play", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/RikkaApps/Shizuku/releases"),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("GitHub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Step 2: Enable Wireless Debugging
        SetupStepItem(
            stepNumber = "2",
            title = if (isRunning) "Wireless Debugging Active" else "Enable Wireless Debugging",
            description = if (isRunning) {
                "Wireless debugging communication is active."
            } else {
                "Connect to Wi-Fi, open Android Settings, and turn on 'Wireless debugging' in Developer Options."
            },
            isCompleted = isRunning,
            isLight = isLight,
        ) {
            if (!isRunning) {
                FilledTonalButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        } catch (_: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Developer Options", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Step 3: Pair & Start Service
        SetupStepItem(
            stepNumber = "3",
            title = if (isRunning) "Service Running" else "Pair & Start in Shizuku",
            description = if (isRunning) {
                "The Shizuku privileged background service is running."
            } else {
                "Open Shizuku → tap 'Pairing' under Wireless Debugging → enter pairing code from notification, then tap 'Start'."
            },
            isCompleted = isRunning,
            isLight = isLight,
        ) {
            if (!isRunning) {
                FilledTonalButton(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"),
                                    ),
                                )
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Shizuku App", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Step 4: Verify & Authorize
        SetupStepItem(
            stepNumber = "4",
            title = if (isReady) "Authorized & Ready" else "Verify Connection",
            description = if (isReady) {
                "InstallBlock is authorized. Silent app blocking and hiding are enabled."
            } else if (shizukuState == ShizukuState.RunningButNotGranted) {
                "Shizuku service is running! Tap below to grant permission."
            } else {
                "Once Shizuku shows 'Shizuku is running', tap below to verify and grant permission."
            },
            isCompleted = isReady,
            isLight = isLight,
        ) {
            if (!isReady) {
                if (shizukuState == ShizukuState.RunningButNotGranted) {
                    FilledTonalButton(
                        onClick = onRequestShizuku,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Authorize with Shizuku", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onRefreshStatus,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Check Shizuku Status", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStepItem(
    stepNumber: String,
    title: String,
    description: String,
    isCompleted: Boolean = false,
    isLight: Boolean,
    content: @Composable () -> Unit = {},
) {
    val containerBg = if (isLight) Color(0xFFF8FAFC) else Color(0xFF1E1E22)
    val borderColor = if (isLight) Color(0x18000000) else Color(0x28FFFFFF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isCompleted) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = if (isLight) 0.15f else 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Completed",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(15.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isLight) 0.12f else 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp, fontSize = 12.5.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )

        content()
    }
}

/**
 * 5-second countdown warning modal when user chooses "Continue with basic mode".
 */
@Composable
private fun BasicModeCountdownDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isLight: Boolean,
) {
    var secondsLeft by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
    }

    val canProceed = secondsLeft == 0

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Limited Functionality",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = "InstallBlock will not function as effectively in basic mode. Without Shizuku, blocks cannot be installed silently and apps will not be fully hidden from device settings.",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canProceed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = if (isLight) Color(0xFFE2E8F0) else Color(0xFF27272A),
                    disabledContentColor = if (isLight) Color(0xFF94A3B8) else Color(0xFF71717A),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (canProceed) "Continue anyway" else "Continue anyway (${secondsLeft}s)",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Set up Shizuku")
            }
        },
        shape = RoundedCornerShape(24.dp),
    )
}
