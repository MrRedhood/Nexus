package com.mrredhood.nexus.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Small boundary for secrets. OAuth tokens must never be stored in plain preferences,
 * databases, logs, analytics events, or UI state.
 */
class SecureStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        "nexus_secure",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun putSecret(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun getSecret(key: String): String? = preferences.getString(key, null)

    fun removeSecret(key: String) {
        preferences.edit().remove(key).apply()
    }
}
