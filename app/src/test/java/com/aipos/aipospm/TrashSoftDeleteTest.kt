package com.aipos.aipospm

import com.aipos.aipospm.data.ApiKeyEntry
import com.aipos.aipospm.data.PasswordEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashSoftDeleteTest {

    @Test
    fun testPasswordEntryDefaultSoftDeleteState() {
        val entry = PasswordEntry(
            title = "Test",
            username = "user",
            encryptedPassword = "enc",
            iv = "iv"
        )
        assertFalse("New password entry should not be deleted", entry.isDeleted)
        assertNull("New password entry should have null deletedAt", entry.deletedAt)
    }

    @Test
    fun testApiKeyEntryDefaultSoftDeleteState() {
        val entry = ApiKeyEntry(
            serviceName = "OpenAI",
            encryptedApiKey = "enc",
            iv = "iv"
        )
        assertFalse("New API key entry should not be deleted", entry.isDeleted)
        assertNull("New API key entry should have null deletedAt", entry.deletedAt)
    }

    @Test
    fun testDaysRemainingCalculation() {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24L * 60 * 60 * 1000

        // Deleted just now -> 30 days remaining
        val justDeleted = calculateDaysRemaining(now, now)
        assertEquals(30, justDeleted)

        // Deleted 5 days ago -> 25 days remaining
        val fiveDaysAgo = now - (5 * oneDayMillis)
        assertEquals(25, calculateDaysRemaining(fiveDaysAgo, now))

        // Deleted 29 days ago -> 1 day remaining
        val twentyNineDaysAgo = now - (29 * oneDayMillis)
        assertEquals(1, calculateDaysRemaining(twentyNineDaysAgo, now))

        // Deleted 35 days ago -> 0 days remaining (ready for purge)
        val thirtyFiveDaysAgo = now - (35 * oneDayMillis)
        assertEquals(0, calculateDaysRemaining(thirtyFiveDaysAgo, now))
    }

    @Test
    fun testAutoPurgeCutoffTimestamp() {
        val now = 1_700_000_000_000L
        val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
        val cutoff = now - thirtyDaysMillis

        val deleted20DaysAgo = now - (20L * 24 * 60 * 60 * 1000)
        val deleted40DaysAgo = now - (40L * 24 * 60 * 60 * 1000)

        // Items deleted 20 days ago are newer than cutoff -> keep
        assertTrue(deleted20DaysAgo > cutoff)

        // Items deleted 40 days ago are older than cutoff -> purge
        assertTrue(deleted40DaysAgo < cutoff)
    }

    private fun calculateDaysRemaining(deletedAt: Long, currentTime: Long): Int {
        val elapsedMillis = currentTime - deletedAt
        val elapsedDays = (elapsedMillis / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
        return (30 - elapsedDays).coerceAtLeast(0).toInt()
    }
}
