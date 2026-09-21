package com.aipos.aipospm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReusedPasswordDetectorTest {

    data class MockPassword(
        val id: Int,
        val title: String,
        val plaintext: String
    )

    private fun detectReusedPasswordIds(entries: List<MockPassword>): Set<Int> {
        val plainMap = mutableMapOf<String, MutableList<Int>>()
        for (entry in entries) {
            val plain = entry.plaintext
            if (plain.isNotBlank() && plain != "*** Decryption failed ***") {
                plainMap.getOrPut(plain) { mutableListOf() }.add(entry.id)
            }
        }
        return plainMap.filter { it.value.size > 1 }.values.flatten().toSet()
    }

    private fun calculateHealthScore(passwordCount: Int, breachedCount: Int, reusedCount: Int): Int {
        if (passwordCount == 0) return 100
        val issueCount = (breachedCount + reusedCount).coerceAtMost(passwordCount)
        return (((passwordCount - issueCount).toFloat() / passwordCount) * 100).toInt()
    }

    @Test
    fun testNoDuplicatePasswordsReturnsEmpty() {
        val list = listOf(
            MockPassword(1, "Google", "P@ssw0rd123!"),
            MockPassword(2, "GitHub", "DifferentS3cret#"),
            MockPassword(3, "Slack", "AnotherUniqueOne$")
        )
        val reused = detectReusedPasswordIds(list)
        assertTrue("No passwords should be flagged as reused", reused.isEmpty())
        assertEquals(0, reused.size)
        assertEquals(100, calculateHealthScore(list.size, 0, reused.size))
    }

    @Test
    fun testReusedPasswordsIdentifiedAccurately() {
        val list = listOf(
            MockPassword(1, "Google", "SharedPassword123!"),
            MockPassword(2, "GitHub", "SharedPassword123!"),
            MockPassword(3, "Slack", "UniqueSecret#"),
            MockPassword(4, "Twitter", "AnotherShared789"),
            MockPassword(5, "Reddit", "AnotherShared789"),
            MockPassword(6, "Amazon", "AnotherShared789")
        )
        val reused = detectReusedPasswordIds(list)

        assertEquals(5, reused.size)
        assertTrue(reused.contains(1))
        assertTrue(reused.contains(2))
        assertFalse(reused.contains(3))
        assertTrue(reused.contains(4))
        assertTrue(reused.contains(5))
        assertTrue(reused.contains(6))
    }

    @Test
    fun testBlankOrFailedDecryptionsNeverFlaggedAsReused() {
        val list = listOf(
            MockPassword(1, "Service1", ""),
            MockPassword(2, "Service2", ""),
            MockPassword(3, "Service3", "*** Decryption failed ***"),
            MockPassword(4, "Service4", "*** Decryption failed ***"),
            MockPassword(5, "Service5", "RealPassword!")
        )
        val reused = detectReusedPasswordIds(list)
        assertTrue("Blank and decryption failed passwords must not be counted as reused", reused.isEmpty())
    }

    @Test
    fun testHealthScoreCalculationWithBreachesAndReused() {
        // 10 passwords: 2 breached, 3 reused (total issues = 5) -> score = 50%
        val score = calculateHealthScore(passwordCount = 10, breachedCount = 2, reusedCount = 3)
        assertEquals(50, score)

        // 4 passwords: 1 breached, 1 reused -> score = 50%
        assertEquals(50, calculateHealthScore(passwordCount = 4, breachedCount = 1, reusedCount = 1))

        // 5 passwords: 0 issues -> score = 100%
        assertEquals(100, calculateHealthScore(passwordCount = 5, breachedCount = 0, reusedCount = 0))

        // 0 passwords -> default 100%
        assertEquals(100, calculateHealthScore(passwordCount = 0, breachedCount = 0, reusedCount = 0))
    }
}
