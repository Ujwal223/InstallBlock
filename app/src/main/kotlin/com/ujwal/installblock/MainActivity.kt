// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ujwal.installblock.AppViewModel
import com.ujwal.installblock.ui.nav.AppNav
import com.ujwal.installblock.ui.theme.InstallBlockTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        viewModel.refreshShizukuState()
    }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModel.refreshShizukuState()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        viewModel.refreshShizukuState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {}

        setContent {
            val theme by viewModel.darkTheme.collectAsStateWithLifecycle(initialValue = "auto")
            InstallBlockTheme(
                darkTheme = when (theme) {
                    "dark" -> true
                    "light" -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
            ) {
                AppNav(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshShizukuState()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (_: Throwable) {}
    }
}
