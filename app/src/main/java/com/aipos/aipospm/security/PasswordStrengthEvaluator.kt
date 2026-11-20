package com.aipos.aipospm.security

import com.aipos.aipospm.ui.viewmodels.PasswordStrength

/**
 * Unified password strength evaluator shared across setup, detail audit, and generator screens.
 */
object PasswordStrengthEvaluator {
    fun evaluate(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.NONE
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.length >= 16) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return when {
            score <= 2 -> PasswordStrength.WEAK
            score <= 4 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }
}

