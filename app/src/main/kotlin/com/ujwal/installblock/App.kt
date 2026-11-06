// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import com.ujwal.installblock.data.BlockListRepository
import com.ujwal.installblock.data.Prefs
import com.ujwal.installblock.install.BlockController
import com.ujwal.installblock.install.NormalInstaller
import com.ujwal.installblock.install.ShizukuInstaller
import com.ujwal.installblock.icons.IconRepository
import com.ujwal.installblock.placeholder.KeystoreManager
import com.ujwal.installblock.placeholder.PlaceholderBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Application entry point.
 *
 * Acts as a minimal service locator so ViewModels can reach singletons without a DI framework.
 * Construction order matters: OkHttpClient → Coil → Repository (which needs OkHttp and Keystore).
 *
 * Key generation is deferred to a background coroutine because AndroidKeyStore operations
 * do IO. The key is available well before any user action that needs it.
 */
class App : Application() {

    /** Background scope tied to the application lifetime. Cancelled on process death. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── Singletons ────────────────────────────────────────────────────────────

    val keystoreManager = KeystoreManager()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
    val iconRepository: IconRepository by lazy { IconRepository(this, okHttpClient) }

    val prefs: Prefs by lazy { Prefs(this) }

    val repository: BlockListRepository by lazy {
        BlockListRepository(this, okHttpClient, keystoreManager, prefs)
    }

    val blockController: BlockController by lazy {
        BlockController(
            normal             = NormalInstaller(this),
            shizuku            = ShizukuInstaller(this),
            placeholderBuilder = PlaceholderBuilder(),
            keystoreManager    = keystoreManager,
            repository         = repository,
        )
    }

    val shizukuInstaller: ShizukuInstaller by lazy { ShizukuInstaller(this) }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        // Only initialize key management and app singletons in the primary application process.
        // Secondary/isolated processes (such as :icon_decoder) run with restricted isolated UIDs
        // and must never touch AndroidKeyStore or launch main-process tasks.
        if (!isMainProcess()) return

        // Generate the placeholder-signing key on first launch; load it on subsequent ones.
        // Safe to call multiple times :  idempotent.
        appScope.launch(Dispatchers.IO) { keystoreManager.ensureKey() }

        // Coil: share the same OkHttpClient used by the repository to reuse the connection pool.
        SingletonImageLoader.setSafe(object : SingletonImageLoader.Factory {
            override fun newImageLoader(context: android.content.Context): ImageLoader =
                ImageLoader.Builder(context)
                    .components { add(OkHttpNetworkFetcherFactory(okHttpClient)) }
                    .networkCachePolicy(CachePolicy.DISABLED)
                    .build()
        })
    }

    private fun isMainProcess(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getProcessName() == packageName
        }
        val pid = Process.myPid()
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        val proc = activityManager?.runningAppProcesses?.firstOrNull { it.pid == pid }
        return proc == null || proc.processName == packageName
    }
}
