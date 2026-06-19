package com.aipos.aipospm.ui.viewmodels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GeneratorUiState(
    val generatedPassword: String = "",
    val length: Int = 16,
    val useUppercase: Boolean = true,
    val useLowercase: Boolean = true,
    val useDigits: Boolean = true,
    val useSymbols: Boolean = true,
    val strength: PasswordStrength = PasswordStrength.NONE
)

class PasswordGeneratorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GeneratorUiState())
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    init {
        generatePassword()
    }

    fun updateLength(length: Int) {
        _uiState.value = _uiState.value.copy(length = length)
        generatePassword()
    }

    fun toggleUppercase(enabled: Boolean) {
        if (!enabled && !hasOtherCharSets(uppercase = false)) return
        _uiState.value = _uiState.value.copy(useUppercase = enabled)
        generatePassword()
    }

    fun toggleLowercase(enabled: Boolean) {
        if (!enabled && !hasOtherCharSets(lowercase = false)) return
        _uiState.value = _uiState.value.copy(useLowercase = enabled)
        generatePassword()
    }

    fun toggleDigits(enabled: Boolean) {
        if (!enabled && !hasOtherCharSets(digits = false)) return
        _uiState.value = _uiState.value.copy(useDigits = enabled)
        generatePassword()
    }

    fun toggleSymbols(enabled: Boolean) {
        if (!enabled && !hasOtherCharSets(symbols = false)) return
        _uiState.value = _uiState.value.copy(useSymbols = enabled)
        generatePassword()
    }

    fun generatePassword() {
        val state = _uiState.value
        val charPool = buildString {
            if (state.useUppercase) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (state.useLowercase) append("abcdefghijklmnopqrstuvwxyz")
            if (state.useDigits) append("0123456789")
            if (state.useSymbols) append("!@#\$%^&*()_+-=[]{}|;:',.<>?/~`")
        }

        if (charPool.isEmpty()) return

        val secureRandom = java.security.SecureRandom()
        val password = buildString {
            repeat(state.length) {
                append(charPool[secureRandom.nextInt(charPool.length)])
            }
        }

        val strength = evaluateStrength(password)
        _uiState.value = state.copy(generatedPassword = password, strength = strength)
    }

    private fun evaluateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.NONE
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.length >= 20) score++
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

    private fun hasOtherCharSets(
        uppercase: Boolean = _uiState.value.useUppercase,
        lowercase: Boolean = _uiState.value.useLowercase,
        digits: Boolean = _uiState.value.useDigits,
        symbols: Boolean = _uiState.value.useSymbols
    ): Boolean {
        return listOf(uppercase, lowercase, digits, symbols).count { it } >= 1
    }
}
