package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserPreferences(
    val accountEmail: String = "",
    val accountPassword: String = "",
    val autoFillEnabled: Boolean = true,
    val autoSubmitDelayMs: Long = 2500L,
    val hasAcceptedTerms: Boolean = false
)

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("second_chance_prefs", Context.MODE_PRIVATE)

    private val _userPreferences = MutableStateFlow(loadPreferences())
    val userPreferences: StateFlow<UserPreferences> = _userPreferences.asStateFlow()

    private fun loadPreferences(): UserPreferences {
        val rawPass = prefs.getString("account_password", "") ?: ""
        val decodedPass = decryptPasswordIfNeeded(rawPass)
        if (rawPass.isNotEmpty() && !rawPass.startsWith(ENCRYPTED_PREFIX) && decodedPass.isNotEmpty()) {
            // Migrate legacy plaintext password to encrypted form if Keystore is available
            val encrypted = encryptPassword(decodedPass)
            if (encrypted.startsWith(ENCRYPTED_PREFIX)) {
                prefs.edit().putString("account_password", encrypted).apply()
            }
        }
        return UserPreferences(
            accountEmail = prefs.getString("account_email", "") ?: "",
            accountPassword = decodedPass,
            autoFillEnabled = prefs.getBoolean("auto_fill_enabled", true),
            autoSubmitDelayMs = prefs.getLong("auto_submit_delay", 2500L),
            hasAcceptedTerms = prefs.getBoolean("has_accepted_terms", false)
        )
    }

    fun saveAccountCredentials(email: String, password: String) {
        val storedPassword = if (password.isEmpty()) "" else encryptPassword(password)
        prefs.edit()
            .putString("account_email", email.trim())
            .putString("account_password", storedPassword)
            .apply()
        _userPreferences.value = loadPreferences()
    }

    fun updateAutoFillEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_fill_enabled", enabled).apply()
        _userPreferences.value = loadPreferences()
    }

    fun updateAutoSubmitDelay(delayMs: Long) {
        prefs.edit().putLong("auto_submit_delay", delayMs).apply()
        _userPreferences.value = loadPreferences()
    }

    fun setTermsAccepted() {
        prefs.edit().putBoolean("has_accepted_terms", true).apply()
        _userPreferences.value = loadPreferences()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove("account_email")
            .remove("account_password")
            .apply()
        _userPreferences.value = loadPreferences()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "second_chance_credentials_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        internal const val ENCRYPTED_PREFIX = "enc:v1:"

        private fun getOrCreateSecretKey(): SecretKey? {
            return try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (existing != null) {
                    existing.secretKey
                } else {
                    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    val spec = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                    keyGenerator.init(spec)
                    keyGenerator.generateKey()
                }
            } catch (_: Exception) {
                null
            }
        }

        internal fun encryptPassword(plainText: String): String {
            if (plainText.isEmpty()) return ""
            val secretKey = getOrCreateSecretKey() ?: return plainText
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val cipherText = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
                "$ENCRYPTED_PREFIX$iv:$cipherText"
            } catch (_: Exception) {
                plainText
            }
        }

        internal fun decryptPasswordIfNeeded(stored: String): String {
            if (!stored.startsWith(ENCRYPTED_PREFIX)) return stored
            val payload = stored.removePrefix(ENCRYPTED_PREFIX)
            val separatorIndex = payload.indexOf(':')
            if (separatorIndex <= 0) return ""
            val secretKey = getOrCreateSecretKey() ?: return ""
            return try {
                val iv = Base64.decode(payload.substring(0, separatorIndex), Base64.NO_WRAP)
                val cipherBytes = Base64.decode(payload.substring(separatorIndex + 1), Base64.NO_WRAP)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
    }
}
