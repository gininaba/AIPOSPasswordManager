package com.aipos.aipospm.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.aipos.aipospm.security.PasswordStrengthEvaluator
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
    val strength: PasswordStrength = PasswordStrength.NONE,
    val history: List<String> = emptyList()
)

class PasswordGeneratorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GeneratorUiState())
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    // In-memory handoff channel (avoids serializing plaintext passwords to SavedStateHandle/Bundle)
    private val _pendingGeneratedPassword = MutableStateFlow<String?>(null)
    val pendingGeneratedPassword: StateFlow<String?> = _pendingGeneratedPassword.asStateFlow()

    init {
        generatePassword()
    }

    fun selectGeneratedPassword(password: String) {
        _pendingGeneratedPassword.value = password
    }

    fun consumePendingGeneratedPassword() {
        _pendingGeneratedPassword.value = null
    }

    fun clearHistory() {
        _pendingGeneratedPassword.value = null
        _uiState.value = _uiState.value.copy(history = emptyList())
    }

    fun recordCurrentPasswordInHistory() {
        val current = _uiState.value.generatedPassword
        if (current.isNotEmpty() && !_uiState.value.history.contains(current)) {
            _uiState.value = _uiState.value.copy(
                history = (listOf(current) + _uiState.value.history).take(10)
            )
        }
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

    fun generatePassword(): String {
        val state = _uiState.value
        val password = generateRandomPassword(
            length = state.length,
            useUppercase = state.useUppercase,
            useLowercase = state.useLowercase,
            useDigits = state.useDigits,
            useSymbols = state.useSymbols
        )
        val strength = PasswordStrengthEvaluator.evaluate(password)
        _uiState.value = state.copy(generatedPassword = password, strength = strength)
        return password
    }

    companion object {
        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val DIGITS = "0123456789"
        private const val SYMBOLS = "!@#\$%^&*()_+-=[]{}|;:',.<>?/~`"

        private val SECURE_RANDOM = java.security.SecureRandom()

        fun generateRandomPassword(
            length: Int = 16,
            useUppercase: Boolean = true,
            useLowercase: Boolean = true,
            useDigits: Boolean = true,
            useSymbols: Boolean = true
        ): String {
            if (length <= 0) return ""
            val activePools = buildList {
                if (useUppercase) add(UPPERCASE)
                if (useLowercase) add(LOWERCASE)
                if (useDigits) add(DIGITS)
                if (useSymbols) add(SYMBOLS)
            }
            if (activePools.isEmpty()) return ""
            val combinedPool = activePools.joinToString("")

            val chars = CharArray(length)
            var index = 0
            // Guarantee at least one character from each enabled class when length permits
            if (length >= activePools.size) {
                for (pool in activePools) {
                    chars[index++] = pool[SECURE_RANDOM.nextInt(pool.length)]
                }
            }
            while (index < length) {
                chars[index++] = combinedPool[SECURE_RANDOM.nextInt(combinedPool.length)]
            }
            // Fisher-Yates shuffle using SecureRandom
            for (i in length - 1 downTo 1) {
                val j = SECURE_RANDOM.nextInt(i + 1)
                val tmp = chars[i]
                chars[i] = chars[j]
                chars[j] = tmp
            }
            return String(chars)
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
