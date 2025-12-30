package com.github.kr328.clash.service.gist

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for Gist configuration using EncryptedSharedPreferences.
 * Stores GitHub Token and encryption secret securely with AES256-SIV encryption.
 */
class GistStore(context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    /**
     * GitHub Personal Access Token for Gist API access.
     * Requires 'gist' scope permission.
     */
    var githubToken: String?
        get() = prefs.getString(KEY_GITHUB_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_GITHUB_TOKEN, value).apply()
    
    /**
     * Secret key for AES encryption of backup data.
     * User-defined password used to encrypt/decrypt backup content.
     */
    var encryptionSecret: String?
        get() = prefs.getString(KEY_ENCRYPTION_SECRET, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_ENCRYPTION_SECRET, value).apply()
    
    /**
     * Check if Gist backup is properly configured with both token and secret.
     */
    val isConfigured: Boolean
        get() = !githubToken.isNullOrBlank() && !encryptionSecret.isNullOrBlank()
    
    /**
     * Clear all stored credentials.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
    
    companion object {
        private const val PREFS_NAME = "gist_secure_prefs"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_ENCRYPTION_SECRET = "encryption_secret"
    }
}
