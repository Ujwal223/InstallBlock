// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.installblock.AppSort
import com.ujwal.installblock.data.model.AppEntry
import com.ujwal.installblock.data.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.ujwal.installblock.data.model.BlockType
import com.ujwal.installblock.ui.components.AppRow
import com.ujwal.installblock.ui.components.GlassSurface
import com.ujwal.installblock.ui.components.ScreenBackdrop
import java.io.File

@Composable
fun BrowseScreen(
    apps: List<AppEntry>,
    states: Map<String, AppState>,
    query: String,
    offline: Boolean,
    loadIcons: Boolean,
    onQueryChanged: (String) -> Unit,
    onBlock: (AppEntry) -> Unit,
    onBatchBlock: (List<AppEntry>) -> Unit,
    loadIcon: suspend (String) -> File?,
    loadIconForPackage: suspend (String) -> File? = { null },
    loadAppIcon: (String) -> Drawable? = { null },
    sort: AppSort = AppSort.Alphabetical,
    onSortChanged: (AppSort) -> Unit = {},
    onRecordAccess: (AppEntry) -> Unit = {},
    onAddCustomPackage: (String) -> Result<AppEntry> = {
        Result.failure(UnsupportedOperationException())
    },
    scrollToTopRequest: Int = 0,
    orphanedBlocks: List<String> = emptyList(),
    removedOutsideBlocks: List<Pair<String, BlockType>> = emptyList(),
    shizukuReady: Boolean = false,
    onReleaseAllOrphans: () -> Unit = {},
    onDismissRemovedOutside: (String) -> Unit = {},
) {
    var pendingEntry by remember { mutableStateOf<AppEntry?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBatch by remember { mutableStateOf(false) }
    var customPackageDialog by remember { mutableStateOf(false) }
    var customPackage by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isLight = MaterialTheme.colorScheme.background.red > 0.5f
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    ScreenBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))

            // ── Top Header (Inspo aligned) ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Browse",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                    Text(
                        text = "Block the apps that pull you away",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // Header action buttons (circular buttons with soft background)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val iconBtnBg = if (isLight) Color(0xFFFFFFFF) else Color(0xFF1E1E22)
                    val iconBtnBorder = if (isLight) Color(0x1C0F172A) else Color(0x22FFFFFF)
                    val iconTint = if (isLight) Color(0xFF0F172A) else Color(0xFFF8FAFC)

                    // Add custom package button (48dp accessible touch target)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { customPackageDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(0.04f))
                                .clip(CircleShape)
                                .background(iconBtnBg)
                                .border(1.dp, iconBtnBorder, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add package",
                                tint = iconTint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Sort menu button (48dp accessible touch target)
                    Box {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { sortMenuExpanded = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(0.04f))
                                    .clip(CircleShape)
                                    .background(iconBtnBg)
                                    .border(1.dp, iconBtnBorder, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Sort apps",
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false },
                            modifier = Modifier.background(
                                if (isLight) Color.White else Color(0xFF18181B),
                            ),
                        ) {
                            AppSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            option.label(),
                                            fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                    onClick = {
                                        onSortChanged(option)
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Search & Filter Pill ──────────────────────────────────────────
            val searchBg = if (isLight) Color(0xFFFFFFFF) else Color(0xFF18181B)
            val searchBorder = if (isLight) Color(0x1C0F172A) else Color(0x20FFFFFF)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(0.04f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(searchBg)
                    .border(1.dp, searchBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.size(20.dp),
                )

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Search ${apps.size} apps…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                            )
                        }
                        innerTextField()
                    },
                )

                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { onQueryChanged("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            // Quick mode chips (Batch select toggle, active indicator)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val selectBtnBg = if (selectionMode) {
                    MaterialTheme.colorScheme.primary
                } else if (isLight) {
                    Color(0xFFF1F5F9)
                } else {
                    Color(0xFF27272A)
                }
                val selectBtnText = if (selectionMode) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(CircleShape)
                        .background(selectBtnBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectionMode = !selectionMode
                            if (!selectionMode) selectedPackages = emptySet()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = null,
                        tint = selectBtnText,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (selectionMode) "Cancel selection" else "Select multiple",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = selectBtnText,
                    )
                }

                if (offline) {
                    Text(
                        text = "Offline catalog",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                    )
                }
            }

            // Batch action bar when items are selected
            AnimatedVisibility(
                visible = selectionMode && selectedPackages.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Button(
                    onClick = { confirmBatch = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        "Block ${selectedPackages.size} selected with Shizuku",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ── Orphaned hidden blocks from earlier install (HB-7) ────────────
            if (orphanedBlocks.isNotEmpty()) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Found ${orphanedBlocks.size} blocks from earlier install",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Kept-data placeholders are holding these packages.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            )
                        }
                        FilledTonalButton(
                            onClick = onReleaseAllOrphans,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Release all", fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── Removed outside notification (LK-4) ───────────────────────────
            if (removedOutsideBlocks.isNotEmpty()) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Blocks modified outside InstallBlock",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        removedOutsideBlocks.take(3).forEach { (pkg, type) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "$pkg ($type)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { onDismissRemovedOutside(pkg) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text("Dismiss", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            val appEntry = apps.firstOrNull { it.packageName == pkg } ?: AppEntry(pkg, pkg, "")
                                            onBlock(appEntry)
                                            onDismissRemovedOutside(pkg)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text("Block again", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── LazyColumn with fast-scroll alphabet slider ───────────────────
            val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            val alphabetGrouped = remember(apps, sort) {
                if (sort == AppSort.Alphabetical) {
                    apps.groupBy { entry ->
                        val firstChar = entry.name.trim().firstOrNull()?.uppercaseChar()
                        if (firstChar != null && firstChar in 'A'..'Z') firstChar else '#'
                    }.toSortedMap()
                } else {
                    emptyMap()
                }
            }

            val letterHeaderIndices = remember(alphabetGrouped) {
                val map = mutableMapOf<Char, Int>()
                var currentIndex = 0
                alphabetGrouped.forEach { (char, list) ->
                    map[char] = currentIndex
                    currentIndex += 1 + list.size
                }
                map
            }

            val alphabetLetters = remember(alphabetGrouped) { alphabetGrouped.keys.toList() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = if (sort == AppSort.Alphabetical && alphabetLetters.size > 1) 22.dp else 0.dp),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = 10.dp,
                        bottom = 112.dp + navBottomInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (sort == AppSort.Alphabetical) {
                        alphabetGrouped.forEach { (letterChar, entries) ->
                            item(key = "header-$letterChar", contentType = "header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = letterChar.toString(),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = "${entries.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f),
                                    )
                                }
                            }
                            items(entries, key = { it.packageName }, contentType = { "app_row" }) { entry ->
                                AppRowContent(
                                    entry = entry,
                                    states = states,
                                    selectionMode = selectionMode,
                                    selectedPackages = selectedPackages,
                                    loadIcons = loadIcons,
                                    loadIcon = loadIcon,
                                    loadIconForPackage = loadIconForPackage,
                                    loadAppIcon = loadAppIcon,
                                    onRecordAccess = onRecordAccess,
                                    onOpen = { pendingEntry = entry },
                                    onSelectedChange = { checked ->
                                        selectedPackages = if (checked) {
                                            selectedPackages + entry.packageName
                                        } else {
                                            selectedPackages - entry.packageName
                                        }
                                    },
                                )
                            }
                        }
                    } else {
                        items(apps, key = { it.packageName }, contentType = { "app_row" }) { entry ->
                            AppRowContent(
                                entry = entry,
                                states = states,
                                selectionMode = selectionMode,
                                selectedPackages = selectedPackages,
                                loadIcons = loadIcons,
                                loadIcon = loadIcon,
                                loadIconForPackage = loadIconForPackage,
                                loadAppIcon = loadAppIcon,
                                onRecordAccess = onRecordAccess,
                                onOpen = { pendingEntry = entry },
                                onSelectedChange = { checked ->
                                    selectedPackages = if (checked) {
                                        selectedPackages + entry.packageName
                                    } else {
                                        selectedPackages - entry.packageName
                                    }
                                },
                            )
                        }
                    }
                }

                if (sort == AppSort.Alphabetical && alphabetLetters.size > 1) {
                    AlphabetFastScroll(
                        alphabetLetters = alphabetLetters,
                        letterHeaderIndices = letterHeaderIndices,
                        listState = listState,
                        isLight = isLight,
                        navBottomInset = navBottomInset,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }

    if (customPackageDialog) {
        AlertDialog(
            onDismissRequest = { customPackageDialog = false },
            title = { Text("Custom package", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Add any package name to your catalog to block it with a placeholder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                    )
                    OutlinedTextField(
                        value = customPackage,
                        onValueChange = {
                            customPackage = it
                            customError = null
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        label = { Text("Package (e.g. com.example.app)") },
                        isError = customError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    customError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        onAddCustomPackage(customPackage)
                            .onSuccess {
                                customPackageDialog = false
                                customPackage = ""
                            }
                            .onFailure { customError = it.message }
                    },
                ) { Text("Add to list") }
            },
            dismissButton = {
                TextButton(onClick = { customPackageDialog = false }) { Text("Cancel") }
            },
        )
    }

    pendingEntry?.let { entry ->
        val state = states[entry.packageName] ?: AppState.NotBlocked

        AlertDialog(
            onDismissRequest = { pendingEntry = null },
            title = { Text(entry.name, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (state == AppState.Installed) {
                        "InstallBlock will uninstall ${entry.name} and keep it hidden to prevent future reinstalls."
                    } else {
                        "InstallBlock will block ${entry.name} so it cannot be installed on this device."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        val targetEntry = entry
                        pendingEntry = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBlock(targetEntry)
                    },
                    enabled = state !is AppState.Blocked && state != AppState.Blocking,
                ) {
                    Text(if (state is AppState.Blocked) "Blocked" else "Block app")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingEntry = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmBatch) {
        val selected = apps.filter { it.packageName in selectedPackages }
        AlertDialog(
            onDismissRequest = { confirmBatch = false },
            title = { Text("Block ${selected.size} apps?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "InstallBlock will remove installed selected apps and install empty placeholder packages silently using Shizuku.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        confirmBatch = false
                        selectionMode = false
                        selectedPackages = emptySet()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBatchBlock(selected)
                    },
                ) {
                    Text("Proceed with Shizuku", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatch = false }) { Text("Cancel") }
            },
        )
    }
}

private fun AppState?.label(): String = when (this) {
    AppState.NotBlocked -> "Not blocked"
    AppState.Installed -> "Installed"
    AppState.Blocking -> "Blocking"
    is AppState.Blocked -> "Blocked"
    is AppState.Failed -> "Failed"
    null -> "Not blocked"
}

@Composable
private fun AppRowContent(
    entry: AppEntry,
    states: Map<String, AppState>,
    selectionMode: Boolean,
    selectedPackages: Set<String>,
    loadIcons: Boolean,
    loadIcon: suspend (String) -> File?,
    loadIconForPackage: suspend (String) -> File?,
    loadAppIcon: (String) -> Drawable?,
    onRecordAccess: (AppEntry) -> Unit,
    onOpen: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    val state = states[entry.packageName]
    val iconModel = if (!loadIcons) {
        null
    } else {
        produceState<Any?>(initialValue = null, entry.packageName, entry.iconUrl) {
            if (entry.iconUrl.isNotBlank()) {
                val file = loadIcon(entry.iconUrl)
                if (file != null) {
                    value = file
                    return@produceState
                }
            }
            val localDrawable = withContext(Dispatchers.IO) { loadAppIcon(entry.packageName) }
            if (localDrawable != null) {
                value = localDrawable
                return@produceState
            }
            if (entry.iconUrl.isBlank()) {
                value = loadIconForPackage(entry.packageName)
            }
        }.value
    }

    AppRow(
        appName = entry.name,
        packageName = entry.packageName,
        stateLabel = state.label(),
        onClick = {
            onRecordAccess(entry)
            onOpen()
        },
        selectionEnabled = selectionMode,
        selected = entry.packageName in selectedPackages,
        onSelectedChange = onSelectedChange,
        iconModel = iconModel,
    )
}

@Composable
private fun AlphabetFastScroll(
    alphabetLetters: List<Char>,
    letterHeaderIndices: Map<Char, Int>,
    listState: LazyListState,
    isLight: Boolean,
    navBottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var isDraggingSlider by remember { mutableStateOf(false) }
    var draggedLetter by remember { mutableStateOf<Char?>(null) }

    val currentVisibleLetter by remember(listState, letterHeaderIndices, alphabetLetters) {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex
            letterHeaderIndices.entries.lastOrNull { it.value <= idx }?.key ?: alphabetLetters.firstOrNull() ?: 'A'
        }
    }

    // Floating glass alphabet hint badge
    AnimatedVisibility(
        visible = isDraggingSlider,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier.padding(end = 48.dp, bottom = 60.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.20f),
                )
                .clip(CircleShape)
                .background(if (isLight) Color(0xFF0F172A) else Color(0xFFFFFFFF))
                .border(
                    width = 1.dp,
                    color = if (isLight) Color(0x33FFFFFF) else Color(0x22000000),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (draggedLetter ?: currentVisibleLetter).toString(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                ),
                color = if (isLight) Color(0xFFFFFFFF) else Color(0xFF09090B),
            )
        }
    }

    val onPositionTouch: (Float, Float) -> Unit = { y, totalHeight ->
        if (alphabetLetters.isNotEmpty() && totalHeight > 0f) {
            val fraction = (y / totalHeight).coerceIn(0f, 1f)
            val letterIdx = (fraction * (alphabetLetters.size - 1)).roundToInt().coerceIn(0, alphabetLetters.size - 1)
            val char = alphabetLetters[letterIdx]
            draggedLetter = char
            letterHeaderIndices[char]?.let { targetIdx ->
                coroutineScope.launch {
                    listState.scrollToItem(targetIdx)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .padding(bottom = 60.dp + navBottomInset)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDraggingSlider) {
                    if (isLight) Color(0x16000000) else Color(0x26FFFFFF)
                } else {
                    Color.Transparent
                },
            )
            .pointerInput(alphabetLetters, letterHeaderIndices) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isDraggingSlider = true
                        onPositionTouch(offset.y, size.height.toFloat())
                    },
                    onDragEnd = {
                        isDraggingSlider = false
                        draggedLetter = null
                    },
                    onDragCancel = {
                        isDraggingSlider = false
                        draggedLetter = null
                    },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        onPositionTouch(change.position.y, size.height.toFloat())
                    },
                )
            }
            .pointerInput(alphabetLetters, letterHeaderIndices) {
                detectTapGestures(
                    onPress = { offset ->
                        isDraggingSlider = true
                        onPositionTouch(offset.y, size.height.toFloat())
                        tryAwaitRelease()
                        isDraggingSlider = false
                        draggedLetter = null
                    },
                )
            }
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(0.68f),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            alphabetLetters.forEach { char ->
                val isSelectedLetter = char == (if (isDraggingSlider) draggedLetter else currentVisibleLetter)
                Text(
                    text = char.toString(),
                    fontSize = 9.sp,
                    fontWeight = if (isSelectedLetter) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelectedLetter) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

private fun AppSort.label(): String = when (this) {
    AppSort.Alphabetical -> "Alphabetical"
    AppSort.Popularity -> "Most popular"
    AppSort.RecentlyAccessed -> "Recently accessed"
}
