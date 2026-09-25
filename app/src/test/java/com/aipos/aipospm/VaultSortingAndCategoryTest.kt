package com.aipos.aipospm

import com.aipos.aipospm.data.ApiKeyEntry
import com.aipos.aipospm.data.Category
import com.aipos.aipospm.data.CategoryType
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.data.SortOption
import com.aipos.aipospm.ui.viewmodels.BackupPayload
import com.aipos.aipospm.ui.viewmodels.CategoryBackup
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSortingAndCategoryTest {

    private val gson = Gson()

    @Test
    fun testCategoryDefaultsAndTypes() {
        val cat = Category(name = "Social")
        assertEquals(CategoryType.PASSWORD.name, cat.type)

        val apiCat = Category(name = "AI Providers", type = CategoryType.API_KEY.name)
        assertEquals(CategoryType.API_KEY.name, apiCat.type)
    }

    @Test
    fun testCategoryFilteringByType() {
        val categories = listOf(
            Category(id = 1, name = "Social", type = CategoryType.PASSWORD.name),
            Category(id = 2, name = "Work", type = CategoryType.PASSWORD.name),
            Category(id = 3, name = "AI Services", type = CategoryType.API_KEY.name),
            Category(id = 4, name = "Cloud", type = CategoryType.API_KEY.name)
        )

        val passwordCats = categories.filter { it.type == CategoryType.PASSWORD.name }
        val apiKeyCats = categories.filter { it.type == CategoryType.API_KEY.name }

        assertEquals(listOf("Social", "Work"), passwordCats.map { it.name })
        assertEquals(listOf("AI Services", "Cloud"), apiKeyCats.map { it.name })
    }

    @Test
    fun testBackupCategoryBackwardCompatibility() {
        // Test older backup JSON where 'type' was not present
        val oldBackupJson = """
            {
                "version": 4,
                "categories": [
                    { "id": 1, "name": "Banking", "createdAt": 1700000000000 }
                ]
            }
        """.trimIndent()

        val payload = gson.fromJson(oldBackupJson, BackupPayload::class.java)
        val categoryBackup = payload.categories?.firstOrNull()
        org.junit.Assert.assertNotNull(categoryBackup)
        // type is null in json, should fall back to CategoryType.PASSWORD.name
        val resolvedType = categoryBackup?.type ?: CategoryType.PASSWORD.name
        assertEquals(CategoryType.PASSWORD.name, resolvedType)

        // Test new backup JSON where 'type' is present
        val newBackupJson = """
            {
                "version": 5,
                "categories": [
                    { "id": 2, "name": "LLMs", "type": "API_KEY", "createdAt": 1700000000000 }
                ]
            }
        """.trimIndent()

        val newPayload = gson.fromJson(newBackupJson, BackupPayload::class.java)
        val newCategoryBackup = newPayload.categories?.firstOrNull()
        assertEquals("API_KEY", newCategoryBackup?.type)
    }

    @Test
    fun testPasswordSortingAlphabetical() {
        val p1 = createPassword(title = "Zebra", customOrder = 0)
        val p2 = createPassword(title = "apple", customOrder = 1)
        val p3 = createPassword(title = "Mango", customOrder = 2)

        val list = listOf(p1, p2, p3)

        val sortedAsc = sortPasswords(list, SortOption.NAME_ASC, pinFavorites = false)
        assertEquals(listOf("apple", "Mango", "Zebra"), sortedAsc.map { it.title })

        val sortedDesc = sortPasswords(list, SortOption.NAME_DESC, pinFavorites = false)
        assertEquals(listOf("Zebra", "Mango", "apple"), sortedDesc.map { it.title })
    }

    @Test
    fun testPasswordSortingDates() {
        val p1 = createPassword(title = "First", createdAt = 1000L, updatedAt = 5000L)
        val p2 = createPassword(title = "Second", createdAt = 2000L, updatedAt = 3000L)
        val p3 = createPassword(title = "Third", createdAt = 3000L, updatedAt = 4000L)

        val list = listOf(p1, p2, p3)

        val byRecentUpdate = sortPasswords(list, SortOption.UPDATED_DESC, pinFavorites = false)
        assertEquals(listOf("First", "Third", "Second"), byRecentUpdate.map { it.title })

        val byCreatedNewest = sortPasswords(list, SortOption.CREATED_DESC, pinFavorites = false)
        assertEquals(listOf("Third", "Second", "First"), byCreatedNewest.map { it.title })

        val byCreatedOldest = sortPasswords(list, SortOption.CREATED_ASC, pinFavorites = false)
        assertEquals(listOf("First", "Second", "Third"), byCreatedOldest.map { it.title })
    }

    @Test
    fun testPasswordSortingCustomOrder() {
        val p1 = createPassword(title = "Item A", customOrder = 10)
        val p2 = createPassword(title = "Item B", customOrder = 2)
        val p3 = createPassword(title = "Item C", customOrder = 5)

        val list = listOf(p1, p2, p3)

        val sortedCustom = sortPasswords(list, SortOption.CUSTOM, pinFavorites = false)
        assertEquals(listOf("Item B", "Item C", "Item A"), sortedCustom.map { it.title })
    }

    @Test
    fun testPasswordSortingWithPinnedFavorites() {
        val p1 = createPassword(title = "Zebra", isFavorite = true, customOrder = 0)
        val p2 = createPassword(title = "Apple", isFavorite = false, customOrder = 1)
        val p3 = createPassword(title = "Banana", isFavorite = true, customOrder = 2)
        val p4 = createPassword(title = "Mango", isFavorite = false, customOrder = 3)

        val list = listOf(p1, p2, p3, p4)

        // Pinned favorites: Favorites first (sorted A-Z: Banana, Zebra), then non-favorites (sorted A-Z: Apple, Mango)
        val sorted = sortPasswords(list, SortOption.NAME_ASC, pinFavorites = true)
        assertEquals(listOf("Banana", "Zebra", "Apple", "Mango"), sorted.map { it.title })

        // When pinFavorites is false: Strictly A-Z
        val unpinned = sortPasswords(list, SortOption.NAME_ASC, pinFavorites = false)
        assertEquals(listOf("Apple", "Banana", "Mango", "Zebra"), unpinned.map { it.title })
    }

    @Test
    fun testApiKeySortingWithPinnedFavorites() {
        val k1 = createApiKey(serviceName = "OpenAI", isFavorite = false, customOrder = 0)
        val k2 = createApiKey(serviceName = "Anthropic", isFavorite = true, customOrder = 1)
        val k3 = createApiKey(serviceName = "Google Gemini", isFavorite = false, customOrder = 2)

        val list = listOf(k1, k2, k3)

        val sorted = sortApiKeys(list, SortOption.NAME_ASC, pinFavorites = true)
        // Anthropic is favorite -> top. Then Google Gemini, OpenAI
        assertEquals(listOf("Anthropic", "Google Gemini", "OpenAI"), sorted.map { it.serviceName })
    }

    @Test
    fun testCustomOrderPreservationOnEdit() {
        val existingEntry = createPassword(title = "Original Title", customOrder = 7)
        val updatedEntry = existingEntry.copy(
            title = "Updated Title",
            username = "new_username",
            updatedAt = 9999L
        )

        // Verify customOrder is preserved
        assertEquals(7, updatedEntry.customOrder)
        assertEquals("Updated Title", updatedEntry.title)

        val existingApiKey = createApiKey(serviceName = "OpenAI", customOrder = 12)
        val updatedApiKey = existingApiKey.copy(
            serviceName = "OpenAI Production",
            updatedAt = 8888L
        )
        assertEquals(12, updatedApiKey.customOrder)
    }

    @Test
    fun testCategoryDeletionFilterReset() {
        var selectedCategoryIdFilter: Int? = 3
        val currentCategories = listOf(
            Category(id = 1, name = "Cloud", type = CategoryType.API_KEY.name),
            Category(id = 2, name = "AI", type = CategoryType.API_KEY.name)
        )

        // If category 3 was deleted, currentCategories won't contain it
        if (selectedCategoryIdFilter != null && currentCategories.none { it.id == selectedCategoryIdFilter }) {
            selectedCategoryIdFilter = null
        }

        assertEquals(null, selectedCategoryIdFilter)
    }

    // Helper functions mirroring the ViewModels' sorting comparator
    private fun sortPasswords(
        items: List<PasswordEntry>,
        sortOption: SortOption,
        pinFavorites: Boolean
    ): List<PasswordEntry> {
        val comparator = when (sortOption) {
            SortOption.NAME_ASC -> compareBy<PasswordEntry> { it.title.lowercase() }
            SortOption.NAME_DESC -> compareByDescending<PasswordEntry> { it.title.lowercase() }
            SortOption.UPDATED_DESC -> compareByDescending<PasswordEntry> { it.updatedAt }
            SortOption.CREATED_DESC -> compareByDescending<PasswordEntry> { it.createdAt }
            SortOption.CREATED_ASC -> compareBy<PasswordEntry> { it.createdAt }
            SortOption.CUSTOM -> compareBy<PasswordEntry> { it.customOrder }.thenBy { it.id }
        }

        return if (pinFavorites) {
            val favorites = items.filter { it.isFavorite }.sortedWith(comparator)
            val nonFavorites = items.filter { !it.isFavorite }.sortedWith(comparator)
            favorites + nonFavorites
        } else {
            items.sortedWith(comparator)
        }
    }

    private fun sortApiKeys(
        items: List<ApiKeyEntry>,
        sortOption: SortOption,
        pinFavorites: Boolean
    ): List<ApiKeyEntry> {
        val comparator = when (sortOption) {
            SortOption.NAME_ASC -> compareBy<ApiKeyEntry> { it.serviceName.lowercase() }
            SortOption.NAME_DESC -> compareByDescending<ApiKeyEntry> { it.serviceName.lowercase() }
            SortOption.UPDATED_DESC -> compareByDescending<ApiKeyEntry> { it.updatedAt }
            SortOption.CREATED_DESC -> compareByDescending<ApiKeyEntry> { it.createdAt }
            SortOption.CREATED_ASC -> compareBy<ApiKeyEntry> { it.createdAt }
            SortOption.CUSTOM -> compareBy<ApiKeyEntry> { it.customOrder }.thenBy { it.id }
        }

        return if (pinFavorites) {
            val favorites = items.filter { it.isFavorite }.sortedWith(comparator)
            val nonFavorites = items.filter { !it.isFavorite }.sortedWith(comparator)
            favorites + nonFavorites
        } else {
            items.sortedWith(comparator)
        }
    }

    private fun createPassword(
        title: String,
        isFavorite: Boolean = false,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        customOrder: Int = 0
    ) = PasswordEntry(
        id = 0,
        title = title,
        username = "user",
        encryptedPassword = "enc",
        iv = "iv",
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        customOrder = customOrder
    )

    private fun createApiKey(
        serviceName: String,
        isFavorite: Boolean = false,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        customOrder: Int = 0
    ) = ApiKeyEntry(
        id = 0,
        serviceName = serviceName,
        encryptedApiKey = "enc",
        iv = "iv",
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        customOrder = customOrder
    )
}
