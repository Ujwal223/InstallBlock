// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.icons

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.IBinder
import java.io.ByteArrayOutputStream

class IconDecoderService : Service() {
    override fun onBind(intent: Intent?): IBinder = object : IIconDecoder.Stub() {
        override fun decode(bytes: ByteArray): ByteArray? = runCatching {
            if (bytes.size > MAX_BYTES) return null
            if (!hasSupportedHeader(bytes)) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..512 || bounds.outHeight !in 1..512) return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                minOf(bitmap.width, 128),
                minOf(bitmap.height, 128),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            ByteArrayOutputStream().use { output ->
                @Suppress("DEPRECATION")
                if (!scaled.compress(Bitmap.CompressFormat.WEBP, 85, output)) null
                else {
                    scaled.recycle()
                    output.toByteArray()
                }
            }
        }.getOrNull()
    }

    private fun hasSupportedHeader(bytes: ByteArray): Boolean =
        bytes.size >= 12 && (
            bytes.copyOfRange(0, 8).contentEquals(
                byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10),
            ) ||
                bytes.copyOfRange(0, 3).contentEquals(
                    byteArrayOf(255.toByte(), 216.toByte(), 255.toByte()),
                ) ||
                bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(82, 73, 70, 70)) &&
                bytes.copyOfRange(8, 12).contentEquals(byteArrayOf(87, 69, 66, 80))
            )

    private companion object {
        const val MAX_BYTES = 128 * 1024
    }
}
