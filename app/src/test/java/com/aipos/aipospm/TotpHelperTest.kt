package com.aipos.aipospm

import com.aipos.aipospm.security.TotpHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TotpHelper] — Base32 decoding and TOTP code generation.
 *
 * Reference vectors sourced from RFC 6238 Appendix B and manual verification
 * against Google Authenticator / Aegis.
 */
class TotpHelperTest {

    // ── Base32 Decoding ────────────────────────────────────────────

    @Test
    fun `decodeBase32 decodes standard input correctly`() {
        // "Hello!" in Base32 is "JBSWY3DPEHPK3PXP" ... actually let's use a known simple case
        // "12345678901234567890" encodes to "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val decoded = TotpHelper.decodeBase32("GEZDGNBVGY3TQOJQ")
        assertNotNull(decoded)
        assertEquals("12345678901234567890".substring(0, decoded!!.size), String(decoded, Charsets.US_ASCII))
    }

    @Test
    fun `decodeBase32 is case insensitive`() {
        val upper = TotpHelper.decodeBase32("JBSWY3DP")
        val lower = TotpHelper.decodeBase32("jbswy3dp")
        assertNotNull(upper)
        assertNotNull(lower)
        assertTrue(upper!!.contentEquals(lower!!))
    }

    @Test
    fun `decodeBase32 strips spaces and hyphens`() {
        val clean = TotpHelper.decodeBase32("JBSWY3DP")
        val spaced = TotpHelper.decodeBase32("JBSW Y3DP")
        val hyphenated = TotpHelper.decodeBase32("JBSW-Y3DP")
        assertNotNull(clean)
        assertNotNull(spaced)
        assertNotNull(hyphenated)
        assertTrue(clean!!.contentEquals(spaced!!))
        assertTrue(clean.contentEquals(hyphenated!!))
    }

    @Test
    fun `decodeBase32 returns null for invalid characters`() {
        val result = TotpHelper.decodeBase32("INVALID!@#")
        assertNull(result)
    }

    @Test
    fun `decodeBase32 returns null for empty string`() {
        val result = TotpHelper.decodeBase32("")
        assertNull(result)
    }

    @Test
    fun `decodeBase32 strips padding characters`() {
        val withPad = TotpHelper.decodeBase32("JBSWY3DP====")
        val withoutPad = TotpHelper.decodeBase32("JBSWY3DP")
        assertNotNull(withPad)
        assertNotNull(withoutPad)
        assertTrue(withPad!!.contentEquals(withoutPad!!))
    }

    // ── Validation ─────────────────────────────────────────────────

    @Test
    fun `isValidBase32 returns true for valid keys`() {
        assertTrue(TotpHelper.isValidBase32("JBSWY3DPEHPK3PXP"))
        assertTrue(TotpHelper.isValidBase32("GEZDGNBVGY3TQOJQ"))
    }

    @Test
    fun `isValidBase32 returns false for invalid keys`() {
        assertFalse(TotpHelper.isValidBase32(""))
        assertFalse(TotpHelper.isValidBase32("!!!INVALID!!!"))
        assertFalse(TotpHelper.isValidBase32("1890"))  // digits 1, 8, 9, 0 not in Base32
    }

    @Test
    fun `isValidBase32 accepts spaced and hyphenated keys`() {
        assertTrue(TotpHelper.isValidBase32("JBSW Y3DP EHPK 3PXP"))
        assertTrue(TotpHelper.isValidBase32("JBSW-Y3DP-EHPK-3PXP"))
    }

    // ── TOTP Code Generation ──────────────────────────────────────

    @Test
    fun `generateCode returns 6-digit string`() {
        // RFC 6238 test seed: "12345678901234567890" = Base32 "GEZDGNBVGY3TQOJQ"
        val code = TotpHelper.generateCode("GEZDGNBVGY3TQOJQ")
        assertNotNull(code)
        assertEquals(6, code!!.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `generateCode with known timestamp produces correct code`() {
        // RFC 6238 test vector: secret = "12345678901234567890" (ASCII),
        // time = 59 seconds (counter = 1 for period 30) → expected code: 287082
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" // "12345678901234567890" in Base32
        val code = TotpHelper.generateCode(secret, timeMillis = 59000L)
        assertNotNull(code)
        assertEquals("287082", code)
    }

    @Test
    fun `generateCode returns null for empty secret`() {
        assertNull(TotpHelper.generateCode(""))
    }

    @Test
    fun `generateCode returns null for invalid secret`() {
        assertNull(TotpHelper.generateCode("!!!NOT-BASE32!!!"))
    }

    @Test
    fun `generateCode produces different codes for different time periods`() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val code1 = TotpHelper.generateCode(secret, timeMillis = 30_000L)
        val code2 = TotpHelper.generateCode(secret, timeMillis = 60_000L)
        assertNotNull(code1)
        assertNotNull(code2)
        // Different 30-second windows should produce different codes
        assertFalse("Codes for different time windows should differ", code1 == code2)
    }

    @Test
    fun `generateCode produces same code within same 30-second window`() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        val code1 = TotpHelper.generateCode(secret, timeMillis = 30_001L)
        val code2 = TotpHelper.generateCode(secret, timeMillis = 59_999L)
        assertNotNull(code1)
        assertNotNull(code2)
        assertEquals("Codes within the same 30s window must be identical", code1, code2)
    }

    // ── Seconds Remaining ─────────────────────────────────────────

    @Test
    fun `secondsRemaining returns correct value at period boundaries`() {
        // At exactly t=0s, 30 seconds remain
        assertEquals(30, TotpHelper.secondsRemaining(0L))
        // At exactly t=1s, 29 seconds remain
        assertEquals(29, TotpHelper.secondsRemaining(1000L))
        // At exactly t=29s, 1 second remains
        assertEquals(1, TotpHelper.secondsRemaining(29_000L))
        // At exactly t=30s, new period starts → 30 seconds remain
        assertEquals(30, TotpHelper.secondsRemaining(30_000L))
    }

    @Test
    fun `periodSeconds returns 30`() {
        assertEquals(30, TotpHelper.periodSeconds())
    }
}
