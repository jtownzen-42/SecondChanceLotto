package com.example.data

import android.content.Context
import android.content.SharedPreferences
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
        return UserPreferences(
            accountEmail = prefs.getString("account_email", "") ?: "",
            accountPassword = prefs.getString("account_password", "") ?: "",
            autoFillEnabled = prefs.getBoolean("auto_fill_enabled", true),
            autoSubmitDelayMs = prefs.getLong("auto_submit_delay", 2500L),
            hasAcceptedTerms = prefs.getBoolean("has_accepted_terms", false)
        )
    }

    fun saveAccountCredentials(email: String, password: String) {
        prefs.edit()
            .putString("account_email", email.trim())
            .putString("account_password", password)
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
}
