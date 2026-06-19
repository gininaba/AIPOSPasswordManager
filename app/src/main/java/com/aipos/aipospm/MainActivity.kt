package com.aipos.aipospm

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.aipos.aipospm.navigation.NavGraph
import com.aipos.aipospm.navigation.Screen
import com.aipos.aipospm.security.BiometricHelper
import com.aipos.aipospm.security.MasterPasswordManager
import com.aipos.aipospm.ui.theme.AIPOSPasswordManagerTheme
import com.aipos.aipospm.ui.viewmodels.ApiKeyViewModel
import com.aipos.aipospm.ui.viewmodels.AuthViewModel
import com.aipos.aipospm.ui.viewmodels.PasswordGeneratorViewModel
import com.aipos.aipospm.ui.viewmodels.PasswordViewModel
import com.aipos.aipospm.ui.viewmodels.CategoryViewModel

class MainActivity : FragmentActivity() {

    private lateinit var authViewModel: AuthViewModel
    private lateinit var passwordViewModel: PasswordViewModel
    private lateinit var apiKeyViewModel: ApiKeyViewModel
    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var generatorViewModel: PasswordGeneratorViewModel
    private val biometricHelper = BiometricHelper()
    private var lastActiveTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize ViewModels
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        passwordViewModel = ViewModelProvider(this)[PasswordViewModel::class.java]
        apiKeyViewModel = ViewModelProvider(this)[ApiKeyViewModel::class.java]
        categoryViewModel = ViewModelProvider(this)[CategoryViewModel::class.java]
        generatorViewModel = ViewModelProvider(this)[PasswordGeneratorViewModel::class.java]

        val masterPasswordManager = MasterPasswordManager(this)
        val canUseBiometric = biometricHelper.canAuthenticate(this)

        // Determine start destination
        val startDestination = when {
            !masterPasswordManager.isMasterPasswordSet() -> Screen.Setup.route
            else -> Screen.Auth.route
        }

        setContent {
            AIPOSPasswordManagerTheme {
                val navController = rememberNavController()

                NavGraph(
                    navController = navController,
                    startDestination = startDestination,
                    authViewModel = authViewModel,
                    passwordViewModel = passwordViewModel,
                    apiKeyViewModel = apiKeyViewModel,
                    categoryViewModel = categoryViewModel,
                    generatorViewModel = generatorViewModel,
                    canUseBiometric = canUseBiometric,
                    onBiometricAuth = {
                        biometricHelper.authenticate(
                            activity = this@MainActivity,
                            onSuccess = {
                                authViewModel.onBiometricSuccess()
                            },
                            onError = { /* Error is handled by the system prompt */ }
                        )
                    }
                )
            }
        }

        // Auto-trigger biometric on launch if enabled
        if (startDestination == Screen.Auth.route &&
            canUseBiometric &&
            masterPasswordManager.isBiometricEnabled()
        ) {
            biometricHelper.authenticate(
                activity = this,
                onSuccess = { authViewModel.onBiometricSuccess() },
                onError = { /* User can fall back to password */ }
            )
        }
    }

    override fun onStop() {
        super.onStop()
        lastActiveTime = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (lastActiveTime > 0) {
            val timeoutMinutes = authViewModel.uiState.value.autoLockTimeout
            if (timeoutMinutes >= 0) {
                val elapsed = System.currentTimeMillis() - lastActiveTime
                if (elapsed > timeoutMinutes * 60 * 1000) {
                    authViewModel.lock()
                }
            }
        }
    }
}