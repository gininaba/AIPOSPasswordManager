package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aipos.aipospm.security.MasterPasswordManager
import com.aipos.aipospm.security.PasswordBreachChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isMasterPasswordSet: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val error: String? = null,
    val passwordStrength: PasswordStrength = PasswordStrength.NONE,
    val autoLockTimeout: Int = -1,
    val isPasswordBreached: Boolean = false
)

enum class PasswordStrength {
    NONE, WEAK, MEDIUM, STRONG
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val masterPasswordManager = MasterPasswordManager(application)

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isMasterPasswordSet = masterPasswordManager.isMasterPasswordSet(),
            isBiometricEnabled = masterPasswordManager.isBiometricEnabled(),
            autoLockTimeout = masterPasswordManager.getAutoLockTimeout()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            PasswordBreachChecker.init(application)
        }
    }

    fun setupMasterPassword(password: String, confirmPassword: String): Boolean {
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "Passwords do not match")
            return false
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return false
        }
        masterPasswordManager.setMasterPassword(password)
        _uiState.value = _uiState.value.copy(
            isMasterPasswordSet = true,
            isAuthenticated = true,
            error = null
        )
        return true
    }

    fun verifyMasterPassword(password: String): Boolean {
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your master password")
            return false
        }
        val result = masterPasswordManager.verifyMasterPassword(password)
        _uiState.value = if (result) {
            _uiState.value.copy(isAuthenticated = true, error = null)
        } else {
            _uiState.value.copy(error = "Incorrect password")
        }
        return result
    }

    fun onBiometricSuccess() {
        _uiState.value = _uiState.value.copy(isAuthenticated = true, error = null)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        masterPasswordManager.setBiometricEnabled(enabled)
        _uiState.value = _uiState.value.copy(isBiometricEnabled = enabled)
    }

    fun changeMasterPassword(oldPassword: String, newPassword: String, confirmNew: String): Boolean {
        if (newPassword != confirmNew) {
            _uiState.value = _uiState.value.copy(error = "New passwords do not match")
            return false
        }
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return false
        }
        val result = masterPasswordManager.changeMasterPassword(oldPassword, newPassword)
        _uiState.value = if (result) {
            _uiState.value.copy(error = null)
        } else {
            _uiState.value.copy(error = "Current password is incorrect")
        }
        return result
    }

    fun evaluatePasswordStrength(password: String): PasswordStrength {
        val isBreached = PasswordBreachChecker.isPasswordBreached(getApplication(), password)
        if (password.isEmpty()) {
            _uiState.value = _uiState.value.copy(passwordStrength = PasswordStrength.NONE, isPasswordBreached = false)
            return PasswordStrength.NONE
        }
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        val strength = when {
            score <= 1 -> PasswordStrength.WEAK
            score <= 3 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
        _uiState.value = _uiState.value.copy(passwordStrength = strength, isPasswordBreached = isBreached)
        return strength
    }

    fun lock() {
        _uiState.value = _uiState.value.copy(isAuthenticated = false)
    }

    fun setAutoLockTimeout(timeoutMinutes: Int) {
        masterPasswordManager.setAutoLockTimeout(timeoutMinutes)
        _uiState.value = _uiState.value.copy(autoLockTimeout = timeoutMinutes)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun isBiometricEnabled(): Boolean = masterPasswordManager.isBiometricEnabled()

    fun generateRecoveryKey(): String {
        return masterPasswordManager.generateRecoveryKey()
    }

    fun getDecryptedRecoveryKey(): String? {
        return masterPasswordManager.getDecryptedRecoveryKey()
    }

    fun resetMasterPasswordWithRecoveryKey(key: String, newPw: String): Boolean {
        if (newPw.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return false
        }
        val success = masterPasswordManager.resetMasterPasswordWithRecoveryKey(key, newPw)
        _uiState.value = if (success) {
            _uiState.value.copy(
                isMasterPasswordSet = true,
                isAuthenticated = true,
                error = null
            )
        } else {
            _uiState.value.copy(error = "Invalid recovery key")
        }
        return success
    }
}
