package com.aipos.aipospm

import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.security.BitwardenJsonMapper
import com.aipos.aipospm.security.PasswordBreachChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying GitHub Issues #12 and #14:
 * - Empty passwords (Secure Notes / backup-code notes) are never flagged as Compromised or Reused
 * - Fixed 4-5 digit numeric PINs are only flagged if in the known breached list (e.g., "1234")
 * - Per-entry `excludeFromAudit` suppresses security warnings and excludes from Vault Health denominator
 * - Bitwarden JSON Secure Notes (type == 2) import cleanly
 */
class NotesAndPinAuditTest {

    private val sampleBreachSet = setOf("1234", "0000", "123456", "password", "qwerty")

    @Test
    fun testEmptyPasswordNeverFlaggedAsBreached() {
        assertFalse(PasswordBreachChecker.isPasswordBreachedWithSet("", sampleBreachSet))
        assertFalse(PasswordBreachChecker.isPasswordBreachedWithSet("   ", sampleBreachSet))
    }

    @Test
    fun testFixedNumericPinsHandledIntelligently() {
        // Uncommon 4-digit and 5-digit numeric PINs must NOT be falsely labeled Compromised
        assertFalse(PasswordBreachChecker.isPasswordBreachedWithSet("7392", sampleBreachSet))
        assertFalse(PasswordBreachChecker.isPasswordBreachedWithSet("48291", sampleBreachSet))

        // Common breached PINs (in weak_passwords.txt) ARE flagged unless excludeFromAudit is set
        assertTrue(PasswordBreachChecker.isPasswordBreachedWithSet("1234", sampleBreachSet))
        assertTrue(PasswordBreachChecker.isPasswordBreachedWithSet("0000", sampleBreachSet))

        // Non-PIN short strings (< 6 chars) remain flagged as weak/compromised
        assertTrue(PasswordBreachChecker.isPasswordBreachedWithSet("abc", sampleBreachSet))
        assertTrue(PasswordBreachChecker.isPasswordBreachedWithSet("pass", sampleBreachSet))
        assertTrue(PasswordBreachChecker.isPasswordBreachedWithSet("123", sampleBreachSet))
    }

    @Test
    fun testExcludeFromAuditAndNoteOnlyVaultHealthCalculation() {
        val entries = listOf(
            PasswordEntry(id = 1, title = "Backup Codes Note", username = "", encryptedPassword = "enc", iv = "iv", notes = "1111-2222") to "",
            PasswordEntry(id = 2, title = "Bank Card PIN", username = "", encryptedPassword = "enc", iv = "iv", excludeFromAudit = true) to "1234",
            PasswordEntry(id = 3, title = "GitHub", username = "octocat", encryptedPassword = "enc", iv = "iv") to "Str0ng!UniquePass#1",
            PasswordEntry(id = 4, title = "Legacy Site", username = "user", encryptedPassword = "enc", iv = "iv") to "password"
        )

        val compIds = mutableSetOf<Int>()
        val plainMap = mutableMapOf<String, MutableList<Int>>()
        var auditableCount = 0

        for ((entry, plain) in entries) {
            if (entry.excludeFromAudit) continue
            if (plain.isBlank()) continue
            auditableCount++
            if (PasswordBreachChecker.isPasswordBreachedWithSet(plain, sampleBreachSet)) {
                compIds.add(entry.id)
            }
            plainMap.getOrPut(plain) { mutableListOf() }.add(entry.id)
        }
        val reusedIds = plainMap.filter { it.value.size > 1 }.values.flatten().toSet()

        assertEquals(2, auditableCount) // Only GitHub (#3) and Legacy Site (#4)
        assertEquals(setOf(4), compIds) // Note (#1) and Ignored PIN (#2) are NOT flagged
        assertTrue(reusedIds.isEmpty())

        val uniqueIssues = (compIds union reusedIds).size
        val score = (((auditableCount - uniqueIssues).toFloat() / auditableCount) * 100).toInt()
        assertEquals(50, score)
    }

    @Test
    fun testBitwardenSecureNoteOnlyJsonImport() {
        val json = """
            {
              "encrypted": false,
              "folders": [{ "id": "f1", "name": "Recovery" }],
              "items": [
                {
                  "id": "n1",
                  "folderId": "f1",
                  "type": 2,
                  "name": "2FA Backup Codes",
                  "notes": "AAAA-BBBB\nCCCC-DDDD",
                  "favorite": true,
                  "secureNote": { "type": 0 }
                }
              ]
            }
        """.trimIndent()

        val result = BitwardenJsonMapper.parse(json)
        assertTrue(result.isSuccess)
        val imported = result.getOrThrow()
        assertEquals(1, imported.size)
        assertEquals("2FA Backup Codes", imported[0].title)
        assertEquals("", imported[0].username)
        assertEquals("", imported[0].password)
        assertEquals("AAAA-BBBB\nCCCC-DDDD", imported[0].notes)
        assertEquals("Recovery", imported[0].categoryName)
        assertTrue(imported[0].isFavorite)
    }
}

