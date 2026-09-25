package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aipos.aipospm.data.AppDatabase
import androidx.room.withTransaction
import com.aipos.aipospm.data.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.aipos.aipospm.data.CategoryType

/**
 * ViewModel for managing custom categories/folders.
 */
class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val categoryDao = db.categoryDao()
    private val passwordDao = db.passwordDao()
    private val apiKeyDao = db.apiKeyDao()

    val categories: StateFlow<List<Category>> = categoryDao.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val passwordCategories: StateFlow<List<Category>> = categoryDao.getCategoriesByType(CategoryType.PASSWORD.name)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val apiKeyCategories: StateFlow<List<Category>> = categoryDao.getCategoriesByType(CategoryType.API_KEY.name)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCategory(name: String, type: CategoryType = CategoryType.PASSWORD) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            if (trimmedName.isNotEmpty()) {
                val existing = categoryDao.getCategoriesByTypeSync(type.name)
                if (existing.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                    return@launch
                }
                categoryDao.insertCategory(Category(name = trimmedName, type = type.name))
            }
        }
    }

    fun renameCategory(category: Category, newName: String) {
        viewModelScope.launch {
            val trimmedName = newName.trim()
            if (trimmedName.isNotEmpty() && trimmedName != category.name) {
                val existing = categoryDao.getCategoriesByTypeSync(category.type)
                if (existing.any { it.id != category.id && it.name.equals(trimmedName, ignoreCase = true) }) {
                    return@launch
                }
                categoryDao.updateCategory(category.copy(name = trimmedName))
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            db.withTransaction {
                passwordDao.clearCategoryRef(category.id)
                apiKeyDao.clearCategoryRef(category.id)
                categoryDao.deleteCategory(category)
            }
        }
    }
}
