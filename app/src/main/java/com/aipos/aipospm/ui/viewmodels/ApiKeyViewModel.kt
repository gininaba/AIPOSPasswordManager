package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.aipos.aipospm.data.ApiKeyEntry
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.data.CategoryType
import com.aipos.aipospm.data.SortOption
import com.aipos.aipospm.data.VaultPreferencesManager
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
import kotlinx.coroutines.flow.map
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
    private val vaultPrefs = VaultPreferencesManager.getInstance(application)

    private val _sortOption = MutableStateFlow(vaultPrefs.getApiKeySortOption())
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _pinFavorites = MutableStateFlow(vaultPrefs.getApiKeyPinFavorites())
    val pinFavorites: StateFlow<Boolean> = _pinFavorites.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryIdFilter = MutableStateFlow<Int?>(null)
    val selectedCategoryIdFilter: StateFlow<Int?> = _selectedCategoryIdFilter.asStateFlow()

    init {
        viewModelScope.launch {
            db.categoryDao().getCategoriesByType(CategoryType.API_KEY.name).collect { cats ->
                val selected = _selectedCategoryIdFilter.value
                if (selected != null && cats.none { it.id == selected }) {
                    _selectedCategoryIdFilter.value = null
                }
            }
        }
    }

    private data class ApiKeyFilterSortParams(
        val query: String,
        val categoryId: Int?,
        val sort: SortOption,
        val pinFavorites: Boolean
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val apiKeys: StateFlow<List<ApiKeyEntry>> = combine(
        _searchQuery,
        _selectedCategoryIdFilter,
        _sortOption,
        _pinFavorites
    ) { query, categoryId, sort, pinFav ->
        ApiKeyFilterSortParams(query, categoryId, sort, pinFav)
    }.flatMapLatest { params ->
        val baseFlow = if (params.query.isBlank()) {
            apiKeyDao.getAllApiKeys()
        } else {
            apiKeyDao.searchApiKeys(params.query)
        }
        baseFlow.map { list ->
            val filtered = if (params.categoryId == null) {
                list
            } else {
                list.filter { it.categoryId == params.categoryId }
            }
            sortApiKeyList(filtered, params.sort, params.pinFavorites)
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sortApiKeyList(list: List<ApiKeyEntry>, sort: SortOption, pinFavorites: Boolean): List<ApiKeyEntry> {
        val baseComparator: Comparator<ApiKeyEntry> = when (sort) {
            SortOption.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.serviceName }
            SortOption.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.serviceName }
            SortOption.UPDATED_DESC -> compareByDescending { it.updatedAt }
            SortOption.CREATED_DESC -> compareByDescending { it.createdAt }
            SortOption.CREATED_ASC -> compareBy { it.createdAt }
            SortOption.CUSTOM -> compareBy<ApiKeyEntry> { it.customOrder }.thenByDescending { it.updatedAt }.thenBy { it.id }
        }
        return if (pinFavorites) {
            list.sortedWith(compareByDescending<ApiKeyEntry> { it.isFavorite }.then(baseComparator))
        } else {
            list.sortedWith(baseComparator)
        }
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        vaultPrefs.setApiKeySortOption(option)
    }

    fun setPinFavorites(pin: Boolean) {
        _pinFavorites.value = pin
        vaultPrefs.setApiKeyPinFavorites(pin)
    }

    fun reorderApiKeys(items: List<ApiKeyEntry>, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in items.indices || toIndex !in items.indices) return
        val reordered = items.toMutableList()
        val movedItem = reordered.removeAt(fromIndex)
        reordered.add(toIndex, movedItem)
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                val allApiKeys = apiKeyDao.getActiveApiKeysList()
                val sortedAll = sortApiKeyList(allApiKeys, SortOption.CUSTOM, pinFavorites = _pinFavorites.value)
                val itemIds = items.map { it.id }.toSet()
                val fullIndices = sortedAll.mapIndexedNotNull { index, entry ->
                    if (entry.id in itemIds) index else null
                }
                if (fullIndices.size == reordered.size) {
                    val updatedFullList = sortedAll.toMutableList()
                    for (i in fullIndices.indices) {
                        updatedFullList[fullIndices[i]] = reordered[i]
                    }
                    updatedFullList.forEachIndexed { index, entry ->
                        if (entry.customOrder != index) {
                            apiKeyDao.updateApiKeyOrder(entry.id, index)
                        }
                    }
                } else {
                    reordered.forEachIndexed { index, entry ->
                        if (entry.customOrder != index) {
                            apiKeyDao.updateApiKeyOrder(entry.id, index)
                        }
                    }
                }
            }
        }
    }

    fun moveApiKey(items: List<ApiKeyEntry>, currentIndex: Int, direction: Int) {
        val targetIndex = currentIndex + direction
        if (currentIndex in items.indices && targetIndex in items.indices) {
            reorderApiKeys(items, currentIndex, targetIndex)
        }
    }

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
        viewModelScope.launch {
            db.categoryDao().getCategoriesByType(CategoryType.API_KEY.name).collect { cats ->
                val selected = _selectedCategoryIdFilter.value
                if (selected != null && cats.none { it.id == selected }) {
                    _selectedCategoryIdFilter.value = null
                }
            }
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
        isFavorite: Boolean,
        icon: String? = null
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

                val customOrder = if (id != null && id > 0) {
                    val selected = _uiState.value.selectedApiKey
                    if (selected != null && selected.id == id) {
                        selected.customOrder
                    } else {
                        apiKeyDao.getApiKeyById(id).first()?.customOrder ?: 0
                    }
                } else {
                    0
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
                    updatedAt = System.currentTimeMillis(),
                    customOrder = customOrder,
                    icon = icon
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
