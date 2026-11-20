package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.UserPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserPreferencesRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("second_chance_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `encryptPassword fails closed instead of returning plaintext when Keystore is unavailable`() {
        assertNull(UserPreferencesRepository.encryptPassword("super-secret-pw"))
        assertEquals("", UserPreferencesRepository.encryptPassword(""))
    }

    @Test
    fun `saveAccountCredentials refuses to write plaintext when Keystore fails`() {
        val repo = UserPreferencesRepository(context)

        val saved = repo.saveAccountCredentials("player@example.com", "super-secret-pw")

        assertFalse(saved)
        val prefs = context.getSharedPreferences("second_chance_prefs", Context.MODE_PRIVATE)
        assertNull(prefs.getString("account_email", null))
        assertNull(prefs.getString("account_password", null))
        assertEquals("", repo.userPreferences.value.accountEmail)
        assertEquals("", repo.userPreferences.value.accountPassword)
    }

    @Test
    fun `clearCredentials removes saved email and password`() {
        val prefs = context.getSharedPreferences("second_chance_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("account_email", "player@example.com")
            .putString("account_password", "enc:v1:invalid:payload")
            .commit()

        val repo = UserPreferencesRepository(context)
        assertEquals("player@example.com", repo.userPreferences.value.accountEmail)

        repo.clearCredentials()

        assertTrue(prefs.getString("account_email", null) == null)
        assertTrue(prefs.getString("account_password", null) == null)
        assertEquals("", repo.userPreferences.value.accountEmail)
        assertEquals("", repo.userPreferences.value.accountPassword)
    }
}
