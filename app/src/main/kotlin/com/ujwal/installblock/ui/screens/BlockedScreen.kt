// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.installblock.data.model.AppEntry
import com.ujwal.installblock.data.model.AppState
import com.ujwal.installblock.ui.components.AppRow
import com.ujwal.installblock.ui.components.GlassSurface
import com.ujwal.installblock.ui.components.ScreenBackdrop

@Composable
fun BlockedScreen(
    apps: List<AppEntry>,
    states: Map<String, AppState>,
    onUnblock: (String) -> Unit,
    onBrowseApps: () -> Unit = {},
) {
    var pendingPackage by remember { mutableStateOf<String?>(null) }
    val blocked = apps.filter { states[it.packageName] is AppState.Blocked }
    val isLight = MaterialTheme.colorScheme.background.red > 0.5f

    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))

            // ── Top Header ────────────────────────────────────────────────────
            Column {
                Text(
                    text = "Blocklist",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                Text(
                    text = if (blocked.isEmpty()) {
                        "No apps currently blocked"
                    } else {
                        "${blocked.size} placeholder${if (blocked.size == 1) "" else "s"} actively locking package names"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (blocked.isEmpty()) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    elevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isLight) Color(0xFFF1F5F9) else Color(0xFF27272A),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                        }

                        Text(
                            text = "No boundaries active",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Text(
                            text = "When you block an app, an empty signed placeholder is installed to keep that package name locked. Apps you block will show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp,
                        )

                        Button(
                            onClick = onBrowseApps,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = "Find apps to block",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 6.dp,
                        bottom = 112.dp + navBottomInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(blocked, key = { it.packageName }, contentType = { "app_row" }) { entry ->
                        AppRow(
                            appName = entry.name,
                            packageName = entry.packageName,
                            stateLabel = "Blocked",
                            onClick = { pendingPackage = entry.packageName },
                        )
                    }
                }
            }
        }
    }

    pendingPackage?.let { packageName ->
        val entry = blocked.firstOrNull { it.packageName == packageName }
        AlertDialog(
            onDismissRequest = { pendingPackage = null },
            title = {
                Text(
                    "Unblock ${entry?.name ?: packageName}?",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    "The placeholder package will be removed. The original app remains uninstalled until you manually install it again from a store.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White,
                    ),
                    onClick = {
                        pendingPackage = null
                        onUnblock(packageName)
                    },
                ) { Text("Unblock app") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPackage = null }) { Text("Cancel") }
            },
        )
    }
}
