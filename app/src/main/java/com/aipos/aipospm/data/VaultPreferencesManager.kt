package com.aipos.aipospm.data

import android.content.Context
import android.content.SharedPreferences

enum class SortOption(val displayName: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    UPDATED_DESC("Recently Updated"),
    CREATED_DESC("Date Created (Newest)"),
    CREATED_ASC("Date Created (Oldest)"),
    CUSTOM("Custom Order")
}

/**
 * Manages user sorting preferences and display configurations.
 */
class VaultPreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "aipos_vault_prefs"
        private const val KEY_PASSWORD_SORT_OPTION = "password_sort_option"
        private const val KEY_API_KEY_SORT_OPTION = "api_key_sort_option"
        private const val KEY_PASSWORD_PIN_FAVORITES = "password_pin_favorites"
        private const val KEY_API_KEY_PIN_FAVORITES = "api_key_pin_favorites"

        @Volatile
        private var instance: VaultPreferencesManager? = null

        fun getInstance(context: Context): VaultPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: VaultPreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getPasswordSortOption(): SortOption {
        val name = prefs.getString(KEY_PASSWORD_SORT_OPTION, SortOption.UPDATED_DESC.name)
        return try {
            SortOption.valueOf(name ?: SortOption.UPDATED_DESC.name)
        } catch (_: Exception) {
            SortOption.UPDATED_DESC
        }
    }

    fun setPasswordSortOption(option: SortOption) {
        prefs.edit().putString(KEY_PASSWORD_SORT_OPTION, option.name).apply()
    }

    fun getPasswordPinFavorites(): Boolean {
        return prefs.getBoolean(KEY_PASSWORD_PIN_FAVORITES, false)
    }

    fun setPasswordPinFavorites(pin: Boolean) {
        prefs.edit().putBoolean(KEY_PASSWORD_PIN_FAVORITES, pin).apply()
    }

    fun getApiKeySortOption(): SortOption {
        val name = prefs.getString(KEY_API_KEY_SORT_OPTION, SortOption.UPDATED_DESC.name)
        return try {
            SortOption.valueOf(name ?: SortOption.UPDATED_DESC.name)
        } catch (_: Exception) {
            SortOption.UPDATED_DESC
        }
    }

    fun setApiKeySortOption(option: SortOption) {
        prefs.edit().putString(KEY_API_KEY_SORT_OPTION, option.name).apply()
    }

    fun getApiKeyPinFavorites(): Boolean {
        return prefs.getBoolean(KEY_API_KEY_PIN_FAVORITES, false)
    }

    fun setApiKeyPinFavorites(pin: Boolean) {
        prefs.edit().putBoolean(KEY_API_KEY_PIN_FAVORITES, pin).apply()
    }
}
