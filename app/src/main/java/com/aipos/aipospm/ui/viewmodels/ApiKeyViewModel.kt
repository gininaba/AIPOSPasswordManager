package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aipos.aipospm.data.ApiKeyEntry
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiKeyCount: StateFlow<Int> = apiKeyDao.getApiKeyCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoriteApiKeys: StateFlow<List<ApiKeyEntry>> = apiKeyDao.getFavoriteApiKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedApiKeys: StateFlow<List<ApiKeyEntry>> = apiKeyDao.getDeletedApiKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedApiKeyCount: StateFlow<Int> = apiKeyDao.getDeletedApiKeyCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            apiKeyDao.purgeOldDeletedApiKeys(thirtyDaysAgo)
        }
    }

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
                val createdAt = if (id != null && id > 0) {
                    val selected = _uiState.value.selectedApiKey
                    if (selected != null && selected.id == id) {
                        selected.createdAt
                    } else {
                        apiKeyDao.getApiKeyById(id).first()?.createdAt ?: System.currentTimeMillis()
                    }
                } else {
                    System.currentTimeMillis()
                }

                val entry = ApiKeyEntry(
                    id = id ?: 0,
                    serviceName = serviceName,
                    encryptedApiKey = encrypted,
                    iv = iv,
                    notes = notes,
                    categoryId = categoryId,
                    isFavorite = isFavorite,
                    createdAt = createdAt,
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

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadApiKey(id: Int) {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true)
        loadJob = viewModelScope.launch {
            apiKeyDao.getApiKeyById(id).collect { entry ->
                if (entry != null) {
                    val decrypted = try {
                        cryptoManager.decrypt(entry.encryptedApiKey, entry.iv)
                    } catch (e: Exception) {
                        "*** Decryption failed ***"
                    }
                    _uiState.value = _uiState.value.copy(
                        selectedApiKey = entry,
                        decryptedApiKey = decrypted,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        selectedApiKey = null,
                        decryptedApiKey = "",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun restoreApiKey(entry: ApiKeyEntry) {
        restoreApiKeyById(entry.id)
    }

    fun restoreApiKeyById(id: Int) {
        viewModelScope.launch {
            try {
                apiKeyDao.restoreApiKeyById(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to restore: ${e.message}")
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
        deleteApiKeyById(entry.id)
    }

    fun deleteApiKeyById(id: Int) {
        viewModelScope.launch {
            try {
                apiKeyDao.softDeleteApiKey(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete: ${e.message}")
            }
        }
    }

    fun permanentlyDeleteApiKey(id: Int) {
        viewModelScope.launch {
            try {
                apiKeyDao.permanentlyDeleteApiKey(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to permanently delete: ${e.message}")
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            try {
                apiKeyDao.emptyApiKeyTrash()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to empty trash: ${e.message}")
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
        loadJob?.cancel()
        loadJob = null
        _uiState.value = ApiKeyUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
