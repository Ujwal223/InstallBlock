// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.placeholder

import android.content.Context
import android.os.Build
import com.android.apksig.ApkSigner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID

/**
 * Builds a minimal, signed placeholder APK for a given package name.
 *
 * The APK contains:
 *  - `AndroidManifest.xml` encoded in Android Binary XML (AXML) format.
 *  - `classes.dex` :  an empty DEX 035 (140 bytes), required because we do NOT set
 *    `android:hasCode="false"` (the resource ID for that attribute is fragile to hard-code).
 *
 * Signing is done with [ApkSigner] (V2+V3, V1 disabled) using the key from [KeystoreManager].
 * The APK is written to [Context.externalCacheDir] as a temp file, signed in place, and the
 * bytes read back and returned. Both temp files are deleted on completion.
 *
 * APK install semantics: `versionCode = Int.MAX_VALUE` so the placeholder always wins an
 * update-incompatible comparison against any real app :  the real app cannot be restored via
 * normal update channels without first uninstalling the placeholder.
 */
class PlaceholderBuilder {

    fun build(
        context: Context,
        packageName: String,
        label: String = "Blocked by InstallBlock",
        keystoreManager: KeystoreManager,
    ): ByteArray {
        require(PACKAGE_NAME_RE.matches(packageName)) { "Invalid package name" }
        require(label.isNotBlank() && label.length <= MAX_LABEL_LENGTH) {
            "Invalid application label"
        }
        val manifest = buildManifest(packageName, label)
        val dex      = buildEmptyDex()
        val unsigned = buildUnsignedApk(manifest, dex)
        return signApk(context, unsigned, keystoreManager)
    }

    // ── AXML manifest ─────────────────────────────────────────────────────────

    /**
     * Encodes a minimal AndroidManifest.xml in Android Binary XML (AXML) format.
     *
     * String pool layout (15 strings, indices 0-14):
     *   0: ""  1: "android"  2: "http://schemas.android.com/apk/res/android"
     *   3: "package"  4: "versionCode"  5: "versionName"  6: "minSdkVersion"
     *   7: "targetSdkVersion"  8: "label"  9: "manifest"  10: "uses-sdk"
     *   11: "application"  12: packageName  13: "1"  14: label
     *
     * Resource map (indices 0-8):
     *   [0,0,0,0, 0x0101021b, 0x0101021c, 0x0101020c, 0x01010270, 0x01010001]
     */
    private fun buildManifest(packageName: String, label: String): ByteArray {
        val targetSdk = maxOf(Build.VERSION.SDK_INT, BASE_TARGET_SDK)
            .coerceAtMost(MAX_SUPPORTED_TARGET_SDK)
        val strings = listOf(
            "", "android",
            "http://schemas.android.com/apk/res/android",
            "package", "versionCode", "versionName",
            "minSdkVersion", "targetSdkVersion", "label",
            "manifest", "uses-sdk", "application",
            packageName, PLACEHOLDER_VERSION_NAME, label,
        )

        val stringPoolBytes = encodeStringPool(strings)
        val resMapBytes     = encodeResMap()
        val xmlChunks = buildXmlChunks(
            packageName = 12,
            labelIdx = 14,
            targetSdk = targetSdk,
        )

        // XML_DOC chunk wraps: string pool + res map + all XML chunks.
        val inner = stringPoolBytes + resMapBytes + xmlChunks
        val docHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0003)              // XML_DOC type
            putShort(8)                   // header size
            putInt(8 + inner.size)        // total size
        }.array()

        return docHeader + inner
    }

    /** Encodes the UTF-8 string pool chunk. */
    private fun encodeStringPool(strings: List<String>): ByteArray {
        // Encode each string as: UTF-8 length, UTF-16 length, UTF-8 bytes, terminator.
        val encodedStrings = strings.map { s ->
            val utf8 = s.toByteArray(Charsets.UTF_8)
            encodeLength(s.length) + encodeLength(utf8.size) + utf8 + byteArrayOf(0)
        }

        val stringsLen = encodedStrings.sumOf { it.size }
        val paddedStringsLen = (stringsLen + 3) and -4

        // Offsets: each string's byte offset within the strings block.
        val offsets = IntArray(strings.size)
        var cursor = 0
        encodedStrings.forEachIndexed { i, bytes ->
            offsets[i] = cursor
            cursor += bytes.size
        }

        // Header: type, headerSize, chunkSize, stringCount, styleCount, flags,
        //         stringsStart, stylesStart, offsets[n], strings[...]
        val headerSize   = 28                               // fixed
        val offsetsBytes = strings.size * 4
        val stringsStart = headerSize + offsetsBytes        // relative to chunk start
        val chunkSize    = stringsStart + paddedStringsLen

        val buf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0001)            // STRING_POOL type
            putShort(headerSize.toShort())
            putInt(chunkSize)
            putInt(strings.size)        // stringCount
            putInt(0)                   // styleCount
            putInt(1 shl 8)             // UTF-8 flag (0x100)
            putInt(stringsStart)        // stringsStart (relative to chunk start)
            putInt(0)                   // stylesStart
            offsets.forEach { putInt(it) }
            encodedStrings.forEach { put(it) }
            repeat(paddedStringsLen - stringsLen) { put(0) }
        }
        return buf.array()
    }

    private fun encodeLength(length: Int): ByteArray =
        if (length < 0x80) {
            byteArrayOf(length.toByte())
        } else {
            byteArrayOf(
                ((length shr 7) or 0x80).toByte(),
                (length and 0x7F).toByte(),
            )
        }

    /** Resource map for attribute name strings at indices 4-8. */
    private fun encodeResMap(): ByteArray {
        val ids = intArrayOf(
            0, 0, 0, 0,          // indices 0-3: no resource ID
            0x0101021b,          // 4: versionCode
            0x0101021c,          // 5: versionName
            0x0101020c,          // 6: minSdkVersion
            0x01010270,          // 7: targetSdkVersion
            0x01010001,          // 8: label
        )
        val size = 8 + ids.size * 4
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0180)      // RES_MAP type
            putShort(8)           // header size
            putInt(size)
            ids.forEach { putInt(it) }
        }
        return buf.array()
    }

    /** Builds all XML event chunks (namespace, elements). */
    private fun buildXmlChunks(
        packageName: Int,
        labelIdx: Int,
        targetSdk: Int,
    ): ByteArray {
        val chunks = mutableListOf<ByteArray>()

        // StartNamespace: prefix=1 ("android"), uri=2 (xmlns)
        chunks += nsChunk(type = 0x0100, prefix = 1, uri = 2)

        // <manifest package=".." android:versionCode=".." android:versionName="..">
        chunks += startElem(
            name = 9,
            attrs = listOf(
                attr(ns = -1, name = 3,  raw = packageName, type = 0x03, data = packageName), // package
                attr(ns = 2,  name = 4,  raw = -1,          type = 0x10, data = Int.MAX_VALUE), // versionCode
                attr(ns = 2,  name = 5,  raw = 13,          type = 0x03, data = 13),            // versionName="1"
            ),
        )

        // <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="device API level" />
        chunks += startElem(
            name = 10,
            attrs = listOf(
                attr(ns = 2, name = 6, raw = -1, type = 0x10, data = 26),  // minSdkVersion
                attr(ns = 2, name = 7, raw = -1, type = 0x10, data = targetSdk), // targetSdkVersion
            ),
        )
        chunks += endElem(name = 10)

        // <application android:label="..">
        chunks += startElem(
            name = 11,
            attrs = listOf(
                attr(ns = 2, name = 8, raw = labelIdx, type = 0x03, data = labelIdx), // label
            ),
        )
        chunks += endElem(name = 11)

        chunks += endElem(name = 9) // </manifest>

        // EndNamespace
        chunks += nsChunk(type = 0x0101, prefix = 1, uri = 2)

        return chunks.fold(byteArrayOf()) { acc, b -> acc + b }
    }

    private data class AttrSpec(val ns: Int, val name: Int, val raw: Int, val type: Int, val data: Int)

    private fun attr(ns: Int, name: Int, raw: Int, type: Int, data: Int) =
        AttrSpec(ns, name, raw, type, data)

    private fun nsChunk(type: Int, prefix: Int, uri: Int): ByteArray =
        ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(type.toShort())
            putShort(16)        // header size
            putInt(24)          // chunk size
            putInt(1)           // line number (placeholder)
            putInt(-1)          // comment: none
            putInt(prefix)
            putInt(uri)
        }.array()

    private fun startElem(name: Int, attrs: List<AttrSpec>): ByteArray {
        val attrCount = attrs.size
        val chunkSize = 36 + attrCount * 20
        return ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0102)        // START_ELEM
            putShort(16)            // header size
            putInt(chunkSize)
            putInt(1)               // line number
            putInt(-1)              // comment
            putInt(-1)              // ns: none for element itself
            putInt(name)
            putShort(20)            // attributeStart
            putShort(20)            // attributeSize (each attr = 20 bytes)
            putShort(attrCount.toShort())
            putShort(0)             // idAttribute index
            putShort(0)             // classAttribute index
            putShort(0)             // styleAttribute index
            attrs.forEach { a ->
                putInt(a.ns)
                putInt(a.name)
                putInt(a.raw)
                putInt((a.type shl 24) or 8)  // dataType | size=8
                putInt(a.data)
            }
        }.array()
    }

    private fun endElem(name: Int): ByteArray =
        ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0103)    // END_ELEM
            putShort(16)        // header size
            putInt(24)          // chunk size
            putInt(1)           // line number
            putInt(-1)          // comment
            putInt(-1)          // ns
            putInt(name)
        }.array()

    // ── Empty DEX ─────────────────────────────────────────────────────────────

    /**
     * Produces a valid, minimal DEX 035 file with zero classes.
     *
     * Layout (140 bytes total):
     *  - 112-byte header
     *  - 28-byte map list (TYPE_HEADER_ITEM + TYPE_MAP_LIST)
     *
     * Checksums (Adler-32 over bytes[12..end] and SHA-1 over bytes[32..end])
     * are computed here from the standard Java library.
     */
    private fun buildEmptyDex(): ByteArray {
        val dex = ByteArray(140)
        val buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)

        // Magic + version
        val magic = "dex\n035\u0000".toByteArray(Charsets.US_ASCII)
        buf.put(magic)                        // bytes 0-7: magic (8 bytes)

        // Skip checksum (bytes 8-11) and SHA-1 (bytes 12-31) for now :  filled below.
        buf.position(32)

        buf.putInt(140)             // file_size
        buf.putInt(112)             // header_size
        buf.putInt(0x12345678)      // endian_tag (ENDIAN_CONSTANT)
        buf.putInt(0)               // link_size
        buf.putInt(0)               // link_off
        buf.putInt(112)             // map_off (immediately after header)
        buf.putInt(0)               // string_ids_size
        buf.putInt(0)               // string_ids_off
        buf.putInt(0)               // type_ids_size
        buf.putInt(0)               // type_ids_off
        buf.putInt(0)               // proto_ids_size
        buf.putInt(0)               // proto_ids_off
        buf.putInt(0)               // field_ids_size
        buf.putInt(0)               // field_ids_off
        buf.putInt(0)               // method_ids_size
        buf.putInt(0)               // method_ids_off
        buf.putInt(0)               // class_defs_size
        buf.putInt(0)               // class_defs_off
        buf.putInt(28)              // data_size (map list)
        buf.putInt(112)             // data_off

        // Map list at offset 112.
        buf.position(112)
        buf.putInt(2)               // map_list.size = 2 items
        // Item 0: TYPE_HEADER_ITEM (0x0000)
        buf.putShort(0x0000)        // type
        buf.putShort(0)             // unused
        buf.putInt(1)               // size = 1 header
        buf.putInt(0)               // offset = 0
        // Item 1: TYPE_MAP_LIST (0x1000)
        buf.putShort(0x1000.toShort())
        buf.putShort(0)
        buf.putInt(1)               // size = 1 map list
        buf.putInt(112)             // offset = 112

        // SHA-1 over bytes[32..139]
        val sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(dex.copyOfRange(32, 140))
        System.arraycopy(sha1, 0, dex, 12, 20)   // bytes 12-31

        // Adler-32 over bytes[12..139]
        val adler = java.util.zip.Adler32().also { it.update(dex, 12, 128) }
        ByteBuffer.wrap(dex, 8, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(adler.value.toInt())

        return dex
    }

    // ── ZIP builder ───────────────────────────────────────────────────────────

    private fun buildUnsignedApk(manifest: ByteArray, dex: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // STORED (no compression) for manifest is required by some parsers.
            addStored(zip, "AndroidManifest.xml", manifest)
            addStored(zip, "classes.dex", dex)
        }
        return out.toByteArray()
    }

    private fun addStored(zip: ZipOutputStream, name: String, data: ByteArray) {
        val crc = CRC32().also { it.update(data) }
        val entry = ZipEntry(name).apply {
            method       = ZipEntry.STORED
            size         = data.size.toLong()
            compressedSize = data.size.toLong()
            setCrc(crc.value)
        }
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    // ── APK signing ───────────────────────────────────────────────────────────

    private fun signApk(
        context: Context,
        unsignedBytes: ByteArray,
        keystoreManager: KeystoreManager,
    ): ByteArray {
        keystoreManager.ensureKey()
        val cacheDir = context.cacheDir
        val token = UUID.randomUUID().toString()

        val unsignedFile = File(cacheDir, "placeholder_${token}_unsigned.apk")
        val signedFile   = File(cacheDir, "placeholder_${token}_signed.apk")
        try {
            unsignedFile.writeBytes(unsignedBytes)

            val signerConfig = ApkSigner.SignerConfig.Builder(
                "CERT",
                keystoreManager.privateKey(),
                listOf(keystoreManager.certificate!!),
            ).build()

            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsignedFile)
                .setOutputApk(signedFile)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build()
                .sign()

            return signedFile.readBytes()
        } finally {
            unsignedFile.delete()
            signedFile.delete()
        }
    }

    companion object {
        const val PLACEHOLDER_VERSION_NAME = "installblock-placeholder-1"
        const val PLACEHOLDER_VERSION_CODE = Int.MAX_VALUE
        val PACKAGE_NAME_RE = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")
        const val MAX_LABEL_LENGTH = 128
        const val BASE_TARGET_SDK = 36
        const val MAX_SUPPORTED_TARGET_SDK = 37
    }
}
