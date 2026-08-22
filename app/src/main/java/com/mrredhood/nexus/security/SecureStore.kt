package com.mrredhood.nexus.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed secret storage for OAuth tokens and other high-sensitivity values.
 * Plaintext secrets are never written to disk, logs, analytics, or UI state.
 */
class SecureStore(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("nexus_secure_store", Context.MODE_PRIVATE)

    fun putSecret(key: String, value: String) {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        preferences.edit().putString(key, encoded).apply()
    }

    fun getSecret(key: String): String? {
        val stored = preferences.getString(key, null) ?: return null
        return runCatching {
            val parts = stored.split(":", limit = 2)
            require(parts.size == 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    fun removeSecret(key: String) {
        preferences.edit().remove(key).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "nexus_secret_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
