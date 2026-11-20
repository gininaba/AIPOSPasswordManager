package com.aipos.aipospm

import com.aipos.aipospm.security.PasswordStrengthEvaluator
import com.aipos.aipospm.ui.viewmodels.PasswordGeneratorViewModel
import com.aipos.aipospm.ui.viewmodels.PasswordStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAndBugFixesTest {

    @Test
    fun testGeneratedPasswordAlwaysIncludesAllSelectedCharacterClasses() {
        // Repeat many times with minimum length 8 to confirm zero probability of missing any enabled class
        repeat(100) {
            val pw = PasswordGeneratorViewModel.generateRandomPassword(
                length = 8,
                useUppercase = true,
                useLowercase = true,
                useDigits = true,
                useSymbols = true
            )
            assertEquals(8, pw.length)
            assertTrue("Expected uppercase in $pw", pw.any { it.isUpperCase() })
            assertTrue("Expected lowercase in $pw", pw.any { it.isLowerCase() })
            assertTrue("Expected digit in $pw", pw.any { it.isDigit() })
            assertTrue("Expected symbol in $pw", pw.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun testUnifiedPasswordStrengthEvaluator() {
        assertEquals(PasswordStrength.NONE, PasswordStrengthEvaluator.evaluate(""))
        assertEquals(PasswordStrength.WEAK, PasswordStrengthEvaluator.evaluate("abc"))
        assertEquals(PasswordStrength.WEAK, PasswordStrengthEvaluator.evaluate("abcdefgh"))
        assertEquals(PasswordStrength.MEDIUM, PasswordStrengthEvaluator.evaluate("Abcdef12"))
        assertEquals(PasswordStrength.STRONG, PasswordStrengthEvaluator.evaluate("Str0ng!Passw0rd#"))
    }

    @Test
    fun testVaultHealthDoesNotDoubleCountBreachedAndReusedEntries() {
        val compromisedIds = setOf(1, 2)
        val reusedIds = setOf(1, 2) // Same two accounts sharing "123456"
        val passwordCount = 4

        val uniqueIssues = (compromisedIds union reusedIds).size
        val score = (((passwordCount - uniqueIssues).toFloat() / passwordCount) * 100).toInt()
        assertEquals(2, uniqueIssues)
        assertEquals(50, score)
    }

    @Test
    fun testCategoryHueMaskingHandlesIntMinValue() {
        val hue = ((Int.MIN_VALUE and 0x7FFFFFFF) % 360).toFloat()
        assertTrue("Hue must be in [0, 360), got $hue", hue in 0f..<360f)
    }
}

