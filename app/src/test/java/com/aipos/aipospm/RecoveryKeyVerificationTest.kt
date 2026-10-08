package com.aipos.aipospm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit tests verifying recovery key cleaning, formatting, and prefix-agnostic matching.
 */
class RecoveryKeyVerificationTest {

    private fun cleanRecoveryKey(key: String): String {
        return key.replace("-", "").replace(" ", "").trim().uppercase()
    }

    /**
     * Simulated verification algorithm mirroring MasterPasswordManager.verifyRecoveryKey.
     */
    private fun verifyKey(savedCleanKey: String, enteredKey: String): Boolean {
        fun hash(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }

        val savedHash = hash(savedCleanKey)
        val cleanEntered = cleanRecoveryKey(enteredKey)
        if (cleanEntered.isBlank()) return false

        // 1. Direct match
        if (hash(cleanEntered) == savedHash) return true

        // 2. Prepend AIPOS if omitted
        if (!cleanEntered.startsWith("AIPOS")) {
            if (hash("AIPOS$cleanEntered") == savedHash) return true
        }

        // 3. Strip AIPOS if stored without prefix
        if (cleanEntered.startsWith("AIPOS")) {
            val stripped = cleanEntered.removePrefix("AIPOS")
            if (hash(stripped) == savedHash) return true
        }

        return false
    }

    @Test
    fun testCleanRecoveryKey() {
        assertEquals("AIPOSCDEFHJKLMNPRTUVW", cleanRecoveryKey("AIPOS-CDEF-HJKL-MNPR-TUVW"))
        assertEquals("AIPOSCDEFHJKLMNPRTUVW", cleanRecoveryKey("aipos-cdef-hjkl-mnpr-tuvw"))
        assertEquals("AIPOSCDEFHJKLMNPRTUVW", cleanRecoveryKey("  AIPOS  cdef  hjkl  mnpr  tuvw  "))
        assertEquals("CDEFHJKLMNPRTUVW", cleanRecoveryKey("cdef-hjkl-mnpr-tuvw"))
        assertEquals("CDEFHJKLMNPRTUVW", cleanRecoveryKey("CDEFHJKLMNPRTUVW"))
    }

    @Test
    fun testVerifyRecoveryKey_StoredWithPrefix_EnteredWithPrefix() {
        val rawSecret = "CDEFHJKLMNPRTUVW"
        val storedCleanKey = "AIPOS$rawSecret"

        // Formatted standard input
        assertTrue(verifyKey(storedCleanKey, "AIPOS-CDEF-HJKL-MNPR-TUVW"))
        // Lowercase input
        assertTrue(verifyKey(storedCleanKey, "aipos-cdef-hjkl-mnpr-tuvw"))
        // Space separated
        assertTrue(verifyKey(storedCleanKey, "AIPOS CDEF HJKL MNPR TUVW"))
    }

    @Test
    fun testVerifyRecoveryKey_StoredWithPrefix_EnteredWithoutPrefix() {
        val rawSecret = "CDEFHJKLMNPRTUVW"
        val storedCleanKey = "AIPOS$rawSecret"

        // User enters without AIPOS prefix (e.g. only the 16 characters with dashes)
        assertTrue(verifyKey(storedCleanKey, "CDEF-HJKL-MNPR-TUVW"))
        // User enters without dashes or prefix
        assertTrue(verifyKey(storedCleanKey, "cdefhjklmnprtuvw"))
        // User enters with spaces
        assertTrue(verifyKey(storedCleanKey, "CDEF HJKL MNPR TUVW"))
    }

    @Test
    fun testVerifyRecoveryKey_StoredWithoutPrefix_EnteredWithPrefix() {
        val rawSecret = "CDEFHJKLMNPRTUVW"
        val storedCleanKey = rawSecret // legacy storage without prefix

        // User enters with prefix
        assertTrue(verifyKey(storedCleanKey, "AIPOS-CDEF-HJKL-MNPR-TUVW"))
        // User enters without prefix
        assertTrue(verifyKey(storedCleanKey, "CDEF-HJKL-MNPR-TUVW"))
    }

    @Test
    fun testVerifyRecoveryKey_WrongKey_ReturnsFalse() {
        val rawSecret = "CDEFHJKLMNPRTUVW"
        val storedCleanKey = "AIPOS$rawSecret"

        assertFalse(verifyKey(storedCleanKey, "AIPOS-XXXX-YYYY-ZZZZ-WWWW"))
        assertFalse(verifyKey(storedCleanKey, "XXXX-YYYY-ZZZZ-WWWW"))
        assertFalse(verifyKey(storedCleanKey, ""))
        assertFalse(verifyKey(storedCleanKey, "   "))
    }
}
