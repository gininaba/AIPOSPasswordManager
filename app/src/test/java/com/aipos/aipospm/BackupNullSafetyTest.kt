package com.aipos.aipospm

import com.aipos.aipospm.ui.viewmodels.BackupPayload
import com.aipos.aipospm.ui.viewmodels.CategoryBackup
import com.aipos.aipospm.ui.viewmodels.PasswordBackup
import com.aipos.aipospm.ui.viewmodels.ApiKeyBackup
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNullSafetyTest {

    private val gson = Gson()

    @Test
    fun testEmptyJsonDeserialization() {
        val emptyJson = "{}"
        val payload = gson.fromJson(emptyJson, BackupPayload::class.java)

        assertNull(payload.version)
        assertNull(payload.categories)
        assertNull(payload.passwords)
        assertNull(payload.apiKeys)

        // Verify that .orEmpty() safely returns empty collections
        assertTrue(payload.categories.orEmpty().isEmpty())
        assertTrue(payload.passwords.orEmpty().isEmpty())
        assertTrue(payload.apiKeys.orEmpty().isEmpty())
    }

    @Test
    fun testPartialJsonDeserialization() {
        val partialJson = """
            {
                "version": 1,
                "passwords": [
                    {
                        "id": 101,
                        "title": "GitHub"
                    }
                ]
            }
        """.trimIndent()

        val payload = gson.fromJson(partialJson, BackupPayload::class.java)

        assertEquals(1, payload.version)
        assertNull(payload.categories)
        assertNull(payload.apiKeys)

        val passwords = payload.passwords.orEmpty()
        assertEquals(1, passwords.size)

        val firstPassword = passwords[0]
        assertEquals(101, firstPassword.id)
        assertEquals("GitHub", firstPassword.title)

        // Non-provided fields must be null (previously non-nullable, now nullable)
        assertNull(firstPassword.username)
        assertNull(firstPassword.plaintext)
        assertNull(firstPassword.url)
        assertNull(firstPassword.notes)
        assertNull(firstPassword.isFavorite)
        assertNull(firstPassword.createdAt)
        assertNull(firstPassword.updatedAt)

        // Test safe fallbacks matching importBackup logic
        assertEquals("", firstPassword.username ?: "")
        assertEquals("", firstPassword.plaintext ?: "")
        assertEquals("", firstPassword.url ?: "")
        assertEquals("", firstPassword.notes ?: "")
        assertEquals(false, firstPassword.isFavorite ?: false)
        assertTrue((firstPassword.createdAt ?: 1000L) == 1000L)
    }
}
