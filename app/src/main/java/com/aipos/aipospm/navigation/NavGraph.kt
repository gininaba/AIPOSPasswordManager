package com.aipos.aipospm.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aipos.aipospm.ui.screens.AddEditApiKeyScreen
import com.aipos.aipospm.ui.screens.AddEditPasswordScreen
import com.aipos.aipospm.ui.screens.ApiKeyDetailScreen
import com.aipos.aipospm.ui.screens.ApiKeyListScreen
import com.aipos.aipospm.ui.screens.AuthScreen
import com.aipos.aipospm.ui.screens.HomeScreen
import com.aipos.aipospm.ui.screens.PasswordDetailScreen
import com.aipos.aipospm.ui.screens.PasswordGeneratorScreen
import com.aipos.aipospm.ui.screens.PasswordListScreen
import com.aipos.aipospm.ui.screens.SetupScreen
import com.aipos.aipospm.ui.screens.SettingsScreen
import com.aipos.aipospm.ui.screens.CategoryManagerScreen
import com.aipos.aipospm.ui.viewmodels.ApiKeyViewModel
import com.aipos.aipospm.ui.viewmodels.AuthViewModel
import com.aipos.aipospm.ui.viewmodels.PasswordGeneratorViewModel
import com.aipos.aipospm.ui.viewmodels.PasswordViewModel
import com.aipos.aipospm.ui.viewmodels.CategoryViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    authViewModel: AuthViewModel,
    passwordViewModel: PasswordViewModel,
    apiKeyViewModel: ApiKeyViewModel,
    categoryViewModel: CategoryViewModel,
    generatorViewModel: PasswordGeneratorViewModel,
    canUseBiometric: Boolean,
    onBiometricAuth: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(authState.isAuthenticated) {
        if (!authState.isAuthenticated && authState.isMasterPasswordSet) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Screen.Auth.route && currentRoute != Screen.Setup.route) {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Setup screen (first launch)
        composable(Screen.Setup.route) {
            SetupScreen(
                authViewModel = authViewModel,
                canUseBiometric = canUseBiometric,
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        // Auth/Lock screen
        composable(Screen.Auth.route) {
            AuthScreen(
                authViewModel = authViewModel,
                onAuthenticated = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onBiometricClick = onBiometricAuth
            )
        }

        // Home Dashboard
        composable(Screen.Home.route) {
            HomeScreen(
                passwordViewModel = passwordViewModel,
                apiKeyViewModel = apiKeyViewModel,
                onNavigateToPasswords = {
                    navController.navigate(Screen.Passwords.route)
                },
                onNavigateToApiKeys = {
                    navController.navigate(Screen.ApiKeys.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAddPassword = {
                    navController.navigate(Screen.AddEditPassword.createRoute())
                },
                onNavigateToAddApiKey = {
                    navController.navigate(Screen.AddEditApiKey.createRoute())
                },
                onNavigateToPasswordDetail = { id ->
                    navController.navigate(Screen.PasswordDetail.createRoute(id))
                },
                onNavigateToPasswordGenerator = {
                    navController.navigate(Screen.PasswordGenerator.route)
                }
            )
        }

        // Password List
        composable(Screen.Passwords.route) {
            PasswordListScreen(
                passwordViewModel = passwordViewModel,
                categoryViewModel = categoryViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdd = {
                    navController.navigate(Screen.AddEditPassword.createRoute())
                },
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.PasswordDetail.createRoute(id))
                }
            )
        }

        // API Key List
        composable(Screen.ApiKeys.route) {
            ApiKeyListScreen(
                apiKeyViewModel = apiKeyViewModel,
                categoryViewModel = categoryViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdd = {
                    navController.navigate(Screen.AddEditApiKey.createRoute())
                },
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.ApiKeyDetail.createRoute(id))
                }
            )
        }

        // Add/Edit Password
        composable(
            route = Screen.AddEditPassword.route,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: -1
            AddEditPasswordScreen(
                passwordViewModel = passwordViewModel,
                categoryViewModel = categoryViewModel,
                passwordId = if (id > 0) id else null,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGenerator = {
                    navController.navigate(Screen.PasswordGenerator.route)
                }
            )
        }

        // Add/Edit API Key
        composable(
            route = Screen.AddEditApiKey.route,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: -1
            AddEditApiKeyScreen(
                apiKeyViewModel = apiKeyViewModel,
                categoryViewModel = categoryViewModel,
                apiKeyId = if (id > 0) id else null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Password Detail
        composable(
            route = Screen.PasswordDetail.route,
            arguments = listOf(
                navArgument("id") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: return@composable
            PasswordDetailScreen(
                passwordViewModel = passwordViewModel,
                passwordId = id,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { editId ->
                    navController.navigate(Screen.AddEditPassword.createRoute(editId))
                }
            )
        }

        // API Key Detail
        composable(
            route = Screen.ApiKeyDetail.route,
            arguments = listOf(
                navArgument("id") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: return@composable
            ApiKeyDetailScreen(
                apiKeyViewModel = apiKeyViewModel,
                apiKeyId = id,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { editId ->
                    navController.navigate(Screen.AddEditApiKey.createRoute(editId))
                }
            )
        }

        // Password Generator
        composable(Screen.PasswordGenerator.route) {
            PasswordGeneratorScreen(
                generatorViewModel = generatorViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Settings
        composable(Screen.Settings.route) {
            SettingsScreen(
                authViewModel = authViewModel,
                passwordViewModel = passwordViewModel,
                categoryViewModel = categoryViewModel,
                canUseBiometric = canUseBiometric,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToManageCategories = {
                    navController.navigate(Screen.CategoryManager.route)
                }
            )
        }

        // Category Manager
        composable(Screen.CategoryManager.route) {
            CategoryManagerScreen(
                categoryViewModel = categoryViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
