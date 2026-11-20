package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aipos.aipospm.security.MasterPasswordManager
import com.aipos.aipospm.security.PasswordBreachChecker
import com.aipos.aipospm.security.PasswordStrengthEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isMasterPasswordSet: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val error: String? = null,
    val passwordStrength: PasswordStrength = PasswordStrength.NONE,
    val autoLockTimeout: Int = 5,
    val isPasswordBreached: Boolean = false,
    val isScreenSecurityEnabled: Boolean = true
)

enum class PasswordStrength {
    NONE, WEAK, MEDIUM, STRONG
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MIN_MASTER_PASSWORD_LENGTH = 8
    }

    private val masterPasswordManager = MasterPasswordManager.getInstance(application)

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isMasterPasswordSet = masterPasswordManager.isMasterPasswordSet(),
            isBiometricEnabled = masterPasswordManager.isBiometricEnabled(),
            autoLockTimeout = masterPasswordManager.getAutoLockTimeout(),
            isScreenSecurityEnabled = masterPasswordManager.isScreenSecurityEnabled()
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            PasswordBreachChecker.init(application)
        }
    }

    fun setupMasterPassword(
        password: String,
        confirmPassword: String,
        enableBiometric: Boolean = false,
        onSuccess: (recoveryKey: String) -> Unit = {}
    ) {
        if (_uiState.value.isLoading) return
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "Passwords do not match")
            return
        }
        if (password.length < MIN_MASTER_PASSWORD_LENGTH) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least $MIN_MASTER_PASSWORD_LENGTH characters")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val recoveryKey = withContext(Dispatchers.Default) {
                masterPasswordManager.setMasterPassword(password)
                if (enableBiometric) {
                    masterPasswordManager.setBiometricEnabled(true)
                }
                masterPasswordManager.generateRecoveryKey()
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isMasterPasswordSet = true,
                isAuthenticated = true,
                isBiometricEnabled = if (enableBiometric) true else _uiState.value.isBiometricEnabled,
                error = null
            )
            onSuccess(recoveryKey)
        }
    }

    fun verifyMasterPassword(password: String, onResult: (Boolean) -> Unit = {}) {
        if (_uiState.value.isLoading) return
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your master password")
            onResult(false)
            return
        }
        val lockoutSec = masterPasswordManager.getRemainingLockoutSeconds()
        if (lockoutSec > 0L) {
            _uiState.value = _uiState.value.copy(error = "Too many attempts. Try again in ${lockoutSec}s.")
            onResult(false)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                masterPasswordManager.verifyMasterPassword(password)
            }
            _uiState.value = if (result) {
                _uiState.value.copy(isLoading = false, isAuthenticated = true, error = null)
            } else {
                val remaining = masterPasswordManager.getRemainingLockoutSeconds()
                val msg = if (remaining > 0L) {
                    "Too many attempts. Try again in ${remaining}s."
                } else {
                    "Incorrect password"
                }
                _uiState.value.copy(isLoading = false, error = msg)
            }
            onResult(result)
        }
    }

    /**
     * Verifies the master password for in-dialog actions (e.g., viewing the recovery key)
     * without mutating global AuthUiState.error so callers can show inline errors cleanly.
     */
    fun verifyMasterPasswordForAction(
        password: String,
        onResult: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        val lockoutSec = masterPasswordManager.getRemainingLockoutSeconds()
        if (lockoutSec > 0L) {
            onResult(false, "Too many attempts. Try again in ${lockoutSec}s.")
            return
        }
        viewModelScope.launch {
            val valid = withContext(Dispatchers.Default) {
                masterPasswordManager.verifyMasterPassword(password)
            }
            if (valid) {
                onResult(true, null)
            } else {
                val remaining = masterPasswordManager.getRemainingLockoutSeconds()
                val msg = if (remaining > 0L) {
                    "Too many attempts. Try again in ${remaining}s."
                } else {
                    "Incorrect master password"
                }
                onResult(false, msg)
            }
        }
    }

    fun onBiometricSuccess() {
        _uiState.value = _uiState.value.copy(isAuthenticated = true, error = null)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        masterPasswordManager.setBiometricEnabled(enabled)
        _uiState.value = _uiState.value.copy(isBiometricEnabled = enabled)
    }

    fun changeMasterPassword(
        oldPassword: String,
        newPassword: String,
        confirmNew: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (_uiState.value.isLoading) return
        if (newPassword != confirmNew) {
            _uiState.value = _uiState.value.copy(error = "New passwords do not match")
            onResult(false)
            return
        }
        if (newPassword.length < MIN_MASTER_PASSWORD_LENGTH) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least $MIN_MASTER_PASSWORD_LENGTH characters")
            onResult(false)
            return
        }
        val lockoutSec = masterPasswordManager.getRemainingLockoutSeconds()
        if (lockoutSec > 0L) {
            _uiState.value = _uiState.value.copy(error = "Too many attempts. Try again in ${lockoutSec}s.")
            onResult(false)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                masterPasswordManager.changeMasterPassword(oldPassword, newPassword)
            }
            _uiState.value = if (result) {
                _uiState.value.copy(isLoading = false, error = null)
            } else {
                val remaining = masterPasswordManager.getRemainingLockoutSeconds()
                val msg = if (remaining > 0L) {
                    "Too many attempts. Try again in ${remaining}s."
                } else {
                    "Current password is incorrect"
                }
                _uiState.value.copy(isLoading = false, error = msg)
            }
            onResult(result)
        }
    }

    fun evaluatePasswordStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            _uiState.value = _uiState.value.copy(passwordStrength = PasswordStrength.NONE, isPasswordBreached = false)
            return PasswordStrength.NONE
        }
        val isBreached = PasswordBreachChecker.isPasswordBreached(getApplication(), password)
        val strength = PasswordStrengthEvaluator.evaluate(password)
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

    fun setScreenSecurity(enabled: Boolean) {
        masterPasswordManager.setScreenSecurityEnabled(enabled)
        _uiState.value = _uiState.value.copy(isScreenSecurityEnabled = enabled)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getDecryptedRecoveryKeyAsync(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val key = withContext(Dispatchers.Default) {
                masterPasswordManager.getDecryptedRecoveryKey()
            }
            onResult(key)
        }
    }

    fun resetMasterPasswordWithRecoveryKey(
        key: String,
        newPw: String,
        onResult: (success: Boolean, errorMessage: String?) -> Unit = { _, _ -> }
    ) {
        if (_uiState.value.isLoading) return
        if (newPw.length < MIN_MASTER_PASSWORD_LENGTH) {
            val msg = "Password must be at least $MIN_MASTER_PASSWORD_LENGTH characters"
            onResult(false, msg)
            return
        }
        val lockoutSec = masterPasswordManager.getRemainingLockoutSeconds()
        if (lockoutSec > 0L) {
            onResult(false, "Too many attempts. Try again in ${lockoutSec}s.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val success = withContext(Dispatchers.Default) {
                masterPasswordManager.resetMasterPasswordWithRecoveryKey(key, newPw)
            }
            if (success) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isMasterPasswordSet = true,
                    isAuthenticated = true,
                    error = null
                )
                onResult(true, null)
            } else {
                val remaining = masterPasswordManager.getRemainingLockoutSeconds()
                val msg = if (remaining > 0L) {
                    "Too many attempts. Try again in ${remaining}s."
                } else {
                    "Invalid recovery key"
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                onResult(false, msg)
            }
        }
    }
}
