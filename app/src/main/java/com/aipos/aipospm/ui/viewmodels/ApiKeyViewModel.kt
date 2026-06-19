package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aipos.aipospm.data.ApiKeyEntry
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.security.CryptoManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ApiKeyUiState(
    val selectedApiKey: ApiKeyEntry? = null,
    val decryptedApiKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

class ApiKeyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val apiKeyDao = db.apiKeyDao()
    private val cryptoManager = CryptoManager()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryIdFilter = MutableStateFlow<Int?>(null)
    val selectedCategoryIdFilter: StateFlow<Int?> = _selectedCategoryIdFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val apiKeys: StateFlow<List<ApiKeyEntry>> = combine(
        _searchQuery,
        _selectedCategoryIdFilter
    ) { query, categoryId ->
        Pair(query, categoryId)
    }.flatMapLatest { (query, _) ->
        if (query.isBlank()) {
            apiKeyDao.getAllApiKeys()
        } else {
            apiKeyDao.searchApiKeys(query)
        }
    }.combine(_selectedCategoryIdFilter) { list, categoryId ->
        if (categoryId == null) {
            list
        } else {
            list.filter { it.categoryId == categoryId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiKeyCount: StateFlow<Int> = apiKeyDao.getApiKeyCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoriteApiKeys: StateFlow<List<ApiKeyEntry>> = apiKeyDao.getFavoriteApiKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ApiKeyUiState())
    val uiState: StateFlow<ApiKeyUiState> = _uiState.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategoryFilter(categoryId: Int?) {
        _selectedCategoryIdFilter.value = categoryId
    }

    fun saveApiKey(
        id: Int? = null,
        serviceName: String,
        apiKey: String,
        notes: String,
        categoryId: Int?,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            try {
                val (encrypted, iv) = cryptoManager.encrypt(apiKey)
                val entry = ApiKeyEntry(
                    id = id ?: 0,
                    serviceName = serviceName,
                    encryptedApiKey = encrypted,
                    iv = iv,
                    notes = notes,
                    categoryId = categoryId,
                    isFavorite = isFavorite,
                    createdAt = if (id != null) {
                        _uiState.value.selectedApiKey?.createdAt ?: System.currentTimeMillis()
                    } else System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                if (id != null) {
                    apiKeyDao.updateApiKey(entry)
                } else {
                    apiKeyDao.insertApiKey(entry)
                }
                _uiState.value = _uiState.value.copy(saveSuccess = true, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to save: ${e.message}")
            }
        }
    }

    fun loadApiKey(id: Int) {
        viewModelScope.launch {
            apiKeyDao.getApiKeyById(id).collect { entry ->
                if (entry != null) {
                    val decrypted = try {
                        cryptoManager.decrypt(entry.encryptedApiKey, entry.iv)
                    } catch (e: Exception) {
                        "*** Decryption failed ***"
                    }
                    _uiState.value = _uiState.value.copy(
                        selectedApiKey = entry,
                        decryptedApiKey = decrypted
                    )
                }
            }
        }
    }

    fun decryptApiKey(entry: ApiKeyEntry): String {
        return try {
            cryptoManager.decrypt(entry.encryptedApiKey, entry.iv)
        } catch (e: Exception) {
            "*** Decryption failed ***"
        }
    }

    fun deleteApiKey(entry: ApiKeyEntry) {
        viewModelScope.launch {
            try {
                apiKeyDao.deleteApiKey(entry)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete: ${e.message}")
            }
        }
    }

    fun deleteApiKeyById(id: Int) {
        viewModelScope.launch {
            try {
                apiKeyDao.deleteApiKeyById(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete: ${e.message}")
            }
        }
    }

    fun toggleFavorite(entry: ApiKeyEntry) {
        viewModelScope.launch {
            apiKeyDao.updateApiKey(entry.copy(isFavorite = !entry.isFavorite))
        }
    }

    fun resetSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun clearSelection() {
        _uiState.value = ApiKeyUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
