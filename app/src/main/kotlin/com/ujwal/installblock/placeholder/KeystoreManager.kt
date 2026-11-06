// SPDX-License-Identifier: GPL-3.0-or-later
package com.ujwal.installblock.placeholder

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate

private const val TAG = "KeystoreManager"

/**
 * Manages the RSA-2048 signing key used to sign every placeholder APK.
 *
 * The key lives in AndroidKeyStore :  not in a file, not in SharedPreferences.
 * AndroidKeyStore keys are:
 *   - Scoped to this app (another app cannot access them even on a rooted device).
 *   - Excluded from cloud backups automatically.
 *   - Non-extractable: the private key bytes can never be read out, only used for signing.
 *   - Lost on factory reset or app uninstall (PRD: "If the key is lost, existing
 *     placeholders can still be uninstalled but not updated.")
 *
 * On first call, [ensureKey] generates the key pair. On subsequent calls it loads it.
 * Both operations are synchronous and cheap; call from a background coroutine.
 *
 * Security note: no code outside this class touches the private key. The public certificate
 * is used to verify that an installed package is one of our placeholders.
 */
class KeystoreManager {

    companion object {
        /** Alias under which the key pair is stored in AndroidKeyStore. */
        private const val KEY_ALIAS = "installblock_placeholder_signing"
    }

    /** The signing certificate. Null before [ensureKey] is called. */
    @Volatile
    var certificate: X509Certificate? = null
        private set

    /**
     * Generates the key pair on first run; loads it on subsequent runs.
     * Safe to call multiple times :  idempotent.
     */
    @Synchronized
    fun ensureKey() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKey()
                keyStore.load(null)
            }

            certificate = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
                ?: run {
                    Log.w(TAG, "Placeholder signing certificate is unavailable in KeyStore")
                    null
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize key in AndroidKeyStore: ${e.message}", e)
        }
    }

    /**
     * Returns the [java.security.PrivateKey] for signing.
     *
     * The returned key is a Keystore proxy :  signing operations happen inside the
     * Keystore (or secure hardware if available), so the key material is never
     * exposed to the JVM heap.
     */
    fun privateKey(): java.security.PrivateKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun generateKey() {
        Log.i(TAG, "Generating RSA-2048 signing key in AndroidKeyStore")
        KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore",
        ).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN,
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
        }.generateKeyPair()
    }
}
