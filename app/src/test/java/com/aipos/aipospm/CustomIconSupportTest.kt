package com.aipos.aipospm

import com.aipos.aipospm.data.ApiKeyEntry
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.ui.components.VaultIconCategory
import com.aipos.aipospm.ui.components.VaultIconRegistry
import com.aipos.aipospm.ui.viewmodels.ApiKeyBackup
import com.aipos.aipospm.ui.viewmodels.BackupPayload
import com.aipos.aipospm.ui.viewmodels.PasswordBackup
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIconSupportTest {

    private val gson = Gson()

    @Test
    fun testPasswordEntryIconFieldDefaultsToNull() {
        val entry = PasswordEntry(
            id = 1,
            title = "Test",
            username = "user",
            encryptedPassword = "enc",
            iv = "iv"
        )
        assertNull(entry.icon)

        val customEntry = entry.copy(icon = "database")
        assertEquals("database", customEntry.icon)
    }

    @Test
    fun testApiKeyEntryIconFieldDefaultsToNull() {
        val entry = ApiKeyEntry(
            id = 1,
            serviceName = "OpenAI",
            encryptedApiKey = "enc",
            iv = "iv"
        )
        assertNull(entry.icon)

        val customEntry = entry.copy(icon = "cloud")
        assertEquals("cloud", customEntry.icon)
    }

    @Test
    fun testVaultIconRegistryUniqueIds() {
        val allIcons = VaultIconRegistry.ALL_ICONS
        assertTrue("Icon registry should have icons defined", allIcons.isNotEmpty())

        val ids = allIcons.map { it.id }
        val uniqueIds = ids.toSet()
        assertEquals("All icon ids in registry must be unique", uniqueIds.size, ids.size)
    }

    @Test
    fun testVaultIconRegistryCategories() {
        val categories = VaultIconCategory.entries
        assertTrue("Should have multiple icon categories", categories.size >= 4)

        for (category in categories) {
            assertTrue("Category display name must not be empty", category.displayName.isNotBlank())
        }

        // Verify that every category (except ALL) has mapped icons
        for (category in categories.filter { it != VaultIconCategory.ALL }) {
            val iconsForCat = VaultIconRegistry.ALL_ICONS.filter { it.category == category }
            assertTrue("Category ${category.displayName} should contain icons", iconsForCat.isNotEmpty())
        }
    }

    @Test
    fun testVaultIconRegistryLookup() {
        assertNull(VaultIconRegistry.getIcon(null))
        assertNull(VaultIconRegistry.getIcon("unknown_icon_id_12345"))

        assertNotNull(VaultIconRegistry.getIcon("terminal"))
        assertNotNull(VaultIconRegistry.getIcon("key"))
        assertNotNull(VaultIconRegistry.getIcon("cloud"))
        assertNotNull(VaultIconRegistry.getIcon("shield"))
    }

    @Test
    fun testBackupBackwardCompatibilityWithoutIcon() {
        val oldBackupJson = """
            {
                "version": 5,
                "passwords": [
                    {
                        "id": 1,
                        "title": "Google",
                        "username": "user@example.com",
                        "plaintext": "secret",
                        "url": "https://google.com"
                    }
                ],
                "apiKeys": [
                    {
                        "id": 2,
                        "serviceName": "Gemini",
                        "plaintext": "key-123"
                    }
                ]
            }
        """.trimIndent()

        val payload = gson.fromJson(oldBackupJson, BackupPayload::class.java)
        val passwordBackup = payload.passwords?.firstOrNull()
        val apiKeyBackup = payload.apiKeys?.firstOrNull()

        assertNotNull(passwordBackup)
        assertNotNull(apiKeyBackup)

        assertNull("Old backup without icon field should deserialize icon as null", passwordBackup?.icon)
        assertNull("Old backup without icon field should deserialize icon as null", apiKeyBackup?.icon)
    }

    @Test
    fun testBackupSerializationAndDeserializationWithIcon() {
        val payload = BackupPayload(
            version = 6,
            categories = emptyList(),
            passwords = listOf(
                PasswordBackup(
                    id = 10,
                    title = "AWS Console",
                    username = "admin",
                    plaintext = "secret",
                    url = "https://aws.amazon.com",
                    notes = "Root account",
                    categoryId = null,
                    isFavorite = false,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                    icon = "cloud"
                )
            ),
            apiKeys = listOf(
                ApiKeyBackup(
                    id = 20,
                    serviceName = "Stripe",
                    plaintext = "sk_live_123",
                    notes = "Production",
                    categoryId = null,
                    isFavorite = true,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                    icon = "card"
                )
            )
        )

        val json = gson.toJson(payload)
        assertTrue(json.contains("\"icon\":\"cloud\""))
        assertTrue(json.contains("\"icon\":\"card\""))

        val deserialized = gson.fromJson(json, BackupPayload::class.java)
        val deserializedPassword = deserialized.passwords?.firstOrNull()
        val deserializedApiKey = deserialized.apiKeys?.firstOrNull()

        assertEquals("cloud", deserializedPassword?.icon)
        assertEquals("card", deserializedApiKey?.icon)
    }
}
