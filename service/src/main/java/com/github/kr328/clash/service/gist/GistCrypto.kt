package com.github.kr328.clash.service.gist

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption/decryption utility for Gist backup data.
 * Uses PBKDF2 key derivation for password-based encryption.
 */
object GistCrypto {
    
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128
    private const val SALT_LENGTH = 16
    private const val ITERATIONS = 100000
    
    /**
     * Encrypt data using AES-256-GCM with password-based key derivation.
     * 
     * @param data The plaintext data to encrypt
     * @param password The password/secret key for encryption
     * @return Base64-encoded encrypted data (format: salt:iv:ciphertext)
     */
    fun encrypt(data: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH).apply {
            SecureRandom().nextBytes(this)
        }
        
        val iv = ByteArray(IV_LENGTH).apply {
            SecureRandom().nextBytes(this)
        }
        
        val key = deriveKey(password, salt)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        
        val ciphertext = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        
        // Combine salt + iv + ciphertext
        val combined = salt + iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    /**
     * Decrypt data using AES-256-GCM with password-based key derivation.
     * 
     * @param encryptedData Base64-encoded encrypted data
     * @param password The password/secret key for decryption
     * @return Decrypted plaintext data
     * @throws Exception if decryption fails (wrong password or corrupted data)
     */
    fun decrypt(encryptedData: String, password: String): String {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
        
        // Extract salt, iv, and ciphertext
        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)
        
        val key = deriveKey(password, salt)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }
    
    /**
     * Derive AES key from password using PBKDF2.
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, KEY_ALGORITHM)
    }
}
