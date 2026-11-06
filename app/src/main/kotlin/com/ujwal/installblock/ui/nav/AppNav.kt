// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ujwal.installblock.AppViewModel
import com.ujwal.installblock.AppSort
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ujwal.installblock.ui.components.GlassTabBar
import com.ujwal.installblock.ui.screens.BlockedScreen
import com.ujwal.installblock.ui.screens.BrowseScreen
import com.ujwal.installblock.ui.screens.SettingsScreen
import com.ujwal.installblock.install.BlockProgress

@Composable
fun AppNav(viewModel: AppViewModel = viewModel()) {
    val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle()
    val states by viewModel.states.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val offline by viewModel.isOffline.collectAsStateWithLifecycle()
    val loadIcons by viewModel.loadIcons.collectAsStateWithLifecycle(initialValue = true)
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
    val onboardingStartPage by viewModel.debugOnboardingStartPage.collectAsStateWithLifecycle()
    val installMode by viewModel.installMode.collectAsStateWithLifecycle(com.ujwal.installblock.install.InstallMode.Shizuku)
    val isShizukuUser = viewModel.shizukuPermissionGranted && installMode != com.ujwal.installblock.install.InstallMode.Normal
    if (onboardingDone == null) {
        // Cold start preference load: display matching backdrop to prevent flash of OnboardingScreen
        com.ujwal.installblock.ui.components.ScreenBackdrop {}
        return
    }
    if (onboardingDone == false) {
        com.ujwal.installblock.ui.screens.OnboardingScreen(
            initialPage = onboardingStartPage,
            onFinished = { isBasicMode -> viewModel.completeOnboarding(isBasicMode = isBasicMode) },
            getShizukuState = viewModel::getShizukuState,
            onRequestShizuku = viewModel::requestShizukuPermission,
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "browse"
    var scrollToTopRequest by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "browse",
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (targetState.destination.route == "shizuku_guide" || initialState.destination.route == "shizuku_guide") {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeIn(tween(250))
                } else {
                    fadeIn(animationSpec = tween(120))
                }
            },
            exitTransition = {
                if (targetState.destination.route == "shizuku_guide" || initialState.destination.route == "shizuku_guide") {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) + fadeOut(tween(250))
                } else {
                    fadeOut(animationSpec = tween(120))
                }
            },
            popEnterTransition = {
                if (initialState.destination.route == "shizuku_guide") {
                    fadeIn(animationSpec = tween(200))
                } else {
                    fadeIn(animationSpec = tween(120))
                }
            },
            popExitTransition = {
                if (initialState.destination.route == "shizuku_guide") {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) + fadeOut(tween(250))
                } else {
                    fadeOut(animationSpec = tween(120))
                }
            },
        ) {
            composable("browse") {
                val orphanedBlocks by viewModel.orphanedBlocks.collectAsStateWithLifecycle()
                val removedOutsideBlocks by viewModel.removedOutsideBlocks.collectAsStateWithLifecycle()

                BrowseScreen(
                    apps = filteredApps,
                    states = states,
                    query = query,
                    offline = offline,
                    loadIcons = loadIcons,
                    onQueryChanged = viewModel::setQuery,
                    onBlock = viewModel::block,
                    onBatchBlock = viewModel::blockBatch,
                    loadIcon = viewModel::iconFile,
                    loadIconForPackage = viewModel::iconFileForPackage,
                    loadAppIcon = viewModel::appIcon,
                    sort = viewModel.sort.collectAsStateWithLifecycle().value,
                    onSortChanged = viewModel::setSort,
                    onRecordAccess = viewModel::recordAccess,
                    onAddCustomPackage = viewModel::addCustomPackage,
                    scrollToTopRequest = scrollToTopRequest,
                    orphanedBlocks = orphanedBlocks,
                    removedOutsideBlocks = removedOutsideBlocks,
                    shizukuReady = isShizukuUser,
                    onReleaseAllOrphans = viewModel::releaseAll,
                    onDismissRemovedOutside = viewModel::dismissRemovedOutside,
                )
            }
            composable("blocked") {
                BlockedScreen(
                    apps = blockedApps,
                    states = states,
                    onUnblock = viewModel::unblock,
                    onBrowseApps = {
                        navController.navigate("browse") {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                        }
                    },
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onOpenShizukuGuide = { navController.navigate("shizuku_guide") },
                )
            }
            composable("shizuku_guide") {
                val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
                com.ujwal.installblock.ui.screens.ShizukuSetupGuideScreen(
                    shizukuState = shizukuState,
                    isLight = MaterialTheme.colorScheme.background.red > 0.5f,
                    onRequestShizuku = viewModel::requestShizukuPermission,
                    onRefreshStatus = viewModel::refreshShizukuState,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        if (currentRoute != "shizuku_guide") {
            GlassTabBar(
                selectedRoute = currentRoute,
                onTabSelected = { route ->
                    if (route != currentRoute) {
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                        }
                    }
                },
                onHomeDoubleTap = {
                    if (currentRoute == "browse") scrollToTopRequest++
                    else navController.navigate("browse") { launchSingleTop = true }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    when (val currentProgress = progress) {
        BlockProgress.UninstallingReal,
        BlockProgress.BuildingPlaceholder,
        BlockProgress.InstallingPlaceholder,
        BlockProgress.Verifying,
        BlockProgress.FallingBackToNormal,
        -> AlertDialog(
            onDismissRequest = viewModel::clearProgress,
            title = {
                Text(
                    if (isShizukuUser) "Blocking app (Advanced Mode)"
                    else "Blocking app"
                )
            },
            text = {
                Text(currentProgress.description(isShizuku = isShizukuUser))
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearProgress) { Text("Dismiss") }
            },
        )
        is BlockProgress.Failed -> AlertDialog(
            onDismissRequest = viewModel::clearProgress,
            title = { Text("Operation failed") },
            text = {
                Text(
                    if (currentProgress.isVerificationBlock) {
                        "${currentProgress.reason}\n\nAndroid verification rejected this operation. Try Shizuku mode, or enable Android's advanced install flow."
                    } else {
                        currentProgress.reason
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearProgress) { Text("OK") }
            },
        )
        BlockProgress.Done -> AlertDialog(
            onDismissRequest = viewModel::clearProgress,
            title = { Text(if (isShizukuUser) "App blocked" else "App restricted") },
            text = {
                Text(
                    if (isShizukuUser) {
                        "This app is now locked and hidden from your device. You won't be able to open or reinstall it until you unblock it."
                    } else {
                        "This app is now restricted on your device. It will remain locked until you choose to unblock it."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearProgress) { Text("OK") }
            },
        )
        else -> Unit
    }
    notice?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearNotice,
            title = { Text("Install mode changed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearNotice) { Text("OK") }
            },
        )
    }

}

private fun BlockProgress.description(isShizuku: Boolean = false): String = when (this) {
    BlockProgress.UninstallingReal -> if (isShizuku) {
        "Safely clearing the app and its launcher entries from your device."
    } else {
        "Removing the app from your device to enforce your focus boundaries."
    }
    BlockProgress.BuildingPlaceholder -> if (isShizuku) {
        "Configuring system-level barriers to shield this package against reinstalls."
    } else {
        "Configuring boundary protections to safeguard your device focus."
    }
    BlockProgress.InstallingPlaceholder -> if (isShizuku) {
        "Applying strict background restrictions so this app cannot run or reappear in your launcher."
    } else {
        "Applying restriction locks to keep this app inaccessible."
    }
    BlockProgress.Verifying -> if (isShizuku) {
        "Confirming that device boundaries are active and the app is completely locked."
    } else {
        "Confirming that restriction locks are securely active."
    }
    BlockProgress.FallingBackToNormal -> "Shizuku service is currently unavailable; continuing with basic restriction mode."
    else -> ""
}
