// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

class IconRepository(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val cacheDir = File(context.cacheDir, "clean_icons").apply { mkdirs() }
    private val failures = mutableSetOf<String>()
    private val memoryCache = LruCache<String, File>(250)

    // Decoder service management: keep bound while active, unbind after 30s idle (PRD line 122)
    private val mutex = Mutex()
    private var decoderService: IIconDecoder? = null
    private var serviceConnection: ServiceConnection? = null
    private var unbindJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val decodeSemaphore = Semaphore(4)

    suspend fun load(url: String): File? = withContext(Dispatchers.IO) {
        if (url.startsWith("asset://")) return@withContext loadBundled(url)
        val safeUrl = sanitizeUrl(url) ?: return@withContext null

        synchronized(memoryCache) {
            val cached = memoryCache.get(safeUrl)
            if (cached != null && cached.exists()) return@withContext cached
        }

        val target = File(cacheDir, "${sha256(safeUrl)}.webp")
        if (target.exists()) {
            synchronized(memoryCache) { memoryCache.put(safeUrl, target) }
            return@withContext target
        }

        synchronized(failures) {
            if (safeUrl in failures) return@withContext null
        }

        val bytes = runCatching {
            client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .callTimeout(3, TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url(safeUrl).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body
                    if (body.contentLength() > MAX_BYTES) return@use null
                    body.byteStream().use { input ->
                        val output = ByteArray(MAX_BYTES + 1)
                        var total = 0
                        while (total < output.size) {
                            val read = input.read(output, total, output.size - total)
                            if (read < 0) break
                            total += read
                        }

                        if (total > MAX_BYTES) null else output.copyOf(total)
                    }
                }
        }.getOrNull()

        val cleanBytes = bytes?.let { decodeInIsolatedProcess(it) }
        if (cleanBytes == null) {
            synchronized(failures) { failures += safeUrl }
            return@withContext null
        }

        runCatching {
            val temp = File(cacheDir, "${target.name}.tmp")
            temp.outputStream().use { it.write(cleanBytes) }
            if (!temp.renameTo(target)) {
                temp.delete()
                return@runCatching null
            }
            synchronized(memoryCache) { memoryCache.put(safeUrl, target) }
            target
        }.getOrElse {
            target.delete()
            null
        }
    }

    suspend fun loadForPackage(packageName: String): File? = withContext(Dispatchers.IO) {
        val page = "https://play.google.com/store/apps/details?id=$packageName&hl=en"
        val html = runCatching {
            client.newBuilder().callTimeout(5, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(page).build())
                .execute().use { if (it.isSuccessful) it.body.string() else null }
        }.getOrNull() ?: return@withContext null
        val matcher = OG_IMAGE.matcher(html)
        if (!matcher.find()) return@withContext null
        val imageUrl = matcher.group(1)?.replace("&amp;", "&") ?: return@withContext null
        load(imageUrl)
    }

    private fun loadBundled(url: String): File? {
        val assetPath = url.removePrefix("asset://")
        if (!assetPath.matches(Regex("""icons/[a-z0-9_-]+\.png"""))) return null

        synchronized(memoryCache) {
            val cached = memoryCache.get(url)
            if (cached != null && cached.exists()) return cached
        }

        val target = File(cacheDir, "${sha256(url)}.png")
        if (target.exists()) {
            synchronized(memoryCache) { memoryCache.put(url, target) }
            return target
        }
        return runCatching {
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            synchronized(memoryCache) { memoryCache.put(url, target) }
            target
        }.getOrElse {
            target.delete()
            null
        }
    }

    fun sanitizeUrl(rawUrl: String): String? {
        val uri = Uri.parse(rawUrl)
        val host = uri.host ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1) return null
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return null
        if (addresses.any(::isPrivateOrLocalAddress)) {
            return null
        }
        return uri.toString()
    }

    private fun isPrivateOrLocalAddress(address: InetAddress): Boolean {
        val bytes = address.address
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        return bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
    }

    private suspend fun decodeInIsolatedProcess(bytes: ByteArray): ByteArray? = decodeSemaphore.withPermit {
        runCatching {
            val decoder = getOrBindDecoder() ?: return@runCatching null
            decoder.decode(bytes)
        }.getOrNull()
    }

    private suspend fun getOrBindDecoder(): IIconDecoder? = mutex.withLock {
        unbindJob?.cancel()
        unbindJob = null

        decoderService?.asBinder()?.let { binder ->
            if (binder.isBinderAlive && binder.pingBinder()) {
                scheduleUnbindLocked()
                return decoderService
            }
        }

        disconnectLocked()

        suspendCancellableCoroutine<IIconDecoder?> { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    val service = IIconDecoder.Stub.asInterface(binder)
                    decoderService = service
                    scheduleUnbindLocked()
                    if (continuation.isActive) continuation.resume(service)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    decoderService = null
                    serviceConnection = null
                }
            }
            serviceConnection = connection
            val intent = Intent(context, IconDecoderService::class.java)
            val bound = runCatching {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)

            if (!bound) {
                serviceConnection = null
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun scheduleUnbindLocked() {
        unbindJob?.cancel()
        unbindJob = serviceScope.launch {
            delay(30_000L) // 30 seconds idle per PRD specification
            mutex.withLock {
                disconnectLocked()
            }
        }
    }

    private fun disconnectLocked() {
        unbindJob?.cancel()
        unbindJob = null
        serviceConnection?.let {
            runCatching { context.unbindService(it) }
        }
        serviceConnection = null
        decoderService = null
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_BYTES = 128 * 1024
        val OG_IMAGE = Pattern.compile(
            """<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)""",
            Pattern.CASE_INSENSITIVE,
        )
    }
}
