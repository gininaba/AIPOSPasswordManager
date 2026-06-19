package com.aipos.aipospm.navigation

/**
 * Defines all navigation routes in the app.
 */
sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Auth : Screen("auth")
    data object Home : Screen("home")
    data object Passwords : Screen("passwords")
    data object ApiKeys : Screen("api_keys")
    data object AddEditPassword : Screen("add_edit_password?id={id}") {
        fun createRoute(id: Int? = null): String {
            return if (id != null) "add_edit_password?id=$id" else "add_edit_password"
        }
    }
    data object AddEditApiKey : Screen("add_edit_api_key?id={id}") {
        fun createRoute(id: Int? = null): String {
            return if (id != null) "add_edit_api_key?id=$id" else "add_edit_api_key"
        }
    }
    data object PasswordDetail : Screen("password_detail/{id}") {
        fun createRoute(id: Int): String = "password_detail/$id"
    }
    data object ApiKeyDetail : Screen("api_key_detail/{id}") {
        fun createRoute(id: Int): String = "api_key_detail/$id"
    }
    data object PasswordGenerator : Screen("password_generator")
    data object Settings : Screen("settings")
    data object CategoryManager : Screen("category_manager")
}
