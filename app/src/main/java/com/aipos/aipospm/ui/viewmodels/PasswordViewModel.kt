package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.security.BackupManager
import com.aipos.aipospm.security.CryptoManager
import com.aipos.aipospm.security.PasswordBreachChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

// Classes for backup payload structure
data class BackupPayload(
    val version: Int,
    val categories: List<CategoryBackup>,
    val passwords: List<PasswordBackup>,
    val apiKeys: List<ApiKeyBackup>
)

data class CategoryBackup(
    val id: Int,
    val name: String,
    val createdAt: Long
)

data class PasswordBackup(
    val id: Int,
    val title: String,
    val username: String,
    val plaintext: String,
    val url: String,
    val notes: String,
    val categoryId: Int?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class ApiKeyBackup(
    val id: Int,
    val serviceName: String,
    val plaintext: String,
    val notes: String,
    val categoryId: Int?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class PasswordUiState(
    val selectedPassword: PasswordEntry? = null,
    val decryptedPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

class PasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val passwordDao = db.passwordDao()
    private val cryptoManager = CryptoManager()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryIdFilter = MutableStateFlow<Int?>(null)
    val selectedCategoryIdFilter: StateFlow<Int?> = _selectedCategoryIdFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val passwords: StateFlow<List<PasswordEntry>> = combine(
        _searchQuery,
        _selectedCategoryIdFilter
    ) { query, categoryId ->
        Pair(query, categoryId)
    }.flatMapLatest { (query, _) ->
        if (query.isBlank()) {
            passwordDao.getAllPasswords()
        } else {
            passwordDao.searchPasswords(query)
        }
    }.combine(_selectedCategoryIdFilter) { list, categoryId ->
        if (categoryId == null) {
            list
        } else {
            list.filter { it.categoryId == categoryId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val passwordCount: StateFlow<Int> = passwordDao.getPasswordCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoritePasswords: StateFlow<List<PasswordEntry>> = passwordDao.getFavoritePasswords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategoryFilter(categoryId: Int?) {
        _selectedCategoryIdFilter.value = categoryId
    }

    fun savePassword(
        id: Int? = null,
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        categoryId: Int?,
        isFavorite: Boolean
    ) {
        viewModelScope.launch {
            try {
                val (encrypted, iv) = cryptoManager.encrypt(password)
                val entry = PasswordEntry(
                    id = id ?: 0,
                    title = title,
                    username = username,
                    encryptedPassword = encrypted,
                    iv = iv,
                    url = url,
                    notes = notes,
                    categoryId = categoryId,
                    isFavorite = isFavorite,
                    createdAt = if (id != null) {
                        _uiState.value.selectedPassword?.createdAt ?: System.currentTimeMillis()
                    } else System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                if (id != null) {
                    passwordDao.updatePassword(entry)
                } else {
                    passwordDao.insertPassword(entry)
                }
                _uiState.value = _uiState.value.copy(saveSuccess = true, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to save: ${e.message}")
            }
        }
    }

    fun isPasswordBreached(password: String): Boolean {
        return PasswordBreachChecker.isPasswordBreached(getApplication(), password)
    }

    fun exportBackup(
        uri: Uri,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Fetch categories
                val categoriesList = db.categoryDao().getAllCategoriesSync()
                val categoriesBackup = categoriesList.map {
                    CategoryBackup(it.id, it.name, it.createdAt)
                }

                // Fetch passwords & decrypt
                val passwordsList = passwordDao.getAllPasswords().first()
                val passwordsBackup = passwordsList.map {
                    val plaintext = decryptPassword(it)
                    PasswordBackup(
                        id = it.id,
                        title = it.title,
                        username = it.username,
                        plaintext = plaintext,
                        url = it.url,
                        notes = it.notes,
                        categoryId = it.categoryId,
                        isFavorite = it.isFavorite,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                }

                // Fetch API keys & decrypt
                val apiKeysList = db.apiKeyDao().getAllApiKeys().first()
                val apiKeysBackup = apiKeysList.map {
                    val plaintext = try {
                        cryptoManager.decrypt(it.encryptedApiKey, it.iv)
                    } catch (e: Exception) {
                        ""
                    }
                    ApiKeyBackup(
                        id = it.id,
                        serviceName = it.serviceName,
                        plaintext = plaintext,
                        notes = it.notes,
                        categoryId = it.categoryId,
                        isFavorite = it.isFavorite,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                }

                // Create JSON payload
                val payload = BackupPayload(
                    version = 1,
                    categories = categoriesBackup,
                    passwords = passwordsBackup,
                    apiKeys = apiKeysBackup
                )
                val gson = com.google.gson.Gson()
                val payloadJson = gson.toJson(payload)

                // Encrypt payload
                val backupManager = BackupManager()
                val encryptedBackup = backupManager.encryptBackup(payloadJson, password)

                // Write to Uri
                val contentResolver = getApplication<Application>().contentResolver
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(encryptedBackup)
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun importBackup(
        uri: Uri,
        password: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Read from Uri
                val contentResolver = getApplication<Application>().contentResolver
                val stringBuilder = StringBuilder()
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line)
                            line = reader.readLine()
                        }
                    }
                }
                val backupJson = stringBuilder.toString()

                // Decrypt backup
                val backupManager = BackupManager()
                val decryptedJson = backupManager.decryptBackup(backupJson, password)

                // Parse payload
                val gson = com.google.gson.Gson()
                val payload = gson.fromJson(decryptedJson, BackupPayload::class.java)

                // Validate payload version
                if (payload.version != 1) {
                    throw IllegalArgumentException("Unsupported backup version")
                }

                // Restore DB in transaction
                db.withTransaction {
                    db.passwordDao().clearTable()
                    db.apiKeyDao().clearTable()
                    db.categoryDao().clearTable()

                    // 1. Insert Categories & map IDs
                    val categoryIdMap = mutableMapOf<Int, Int>()
                    for (cat in payload.categories) {
                        val newCat = com.aipos.aipospm.data.Category(
                            name = cat.name,
                            createdAt = cat.createdAt
                        )
                        val newId = db.categoryDao().insertCategory(newCat).toInt()
                        categoryIdMap[cat.id] = newId
                    }

                    // 2. Encrypt & Insert Passwords
                    for (p in payload.passwords) {
                        val (encrypted, iv) = cryptoManager.encrypt(p.plaintext)
                        val newCategoryId = p.categoryId?.let { categoryIdMap[it] }
                        val newPassword = PasswordEntry(
                            title = p.title,
                            username = p.username,
                            encryptedPassword = encrypted,
                            iv = iv,
                            url = p.url,
                            notes = p.notes,
                            categoryId = newCategoryId,
                            isFavorite = p.isFavorite,
                            createdAt = p.createdAt,
                            updatedAt = p.updatedAt
                        )
                        passwordDao.insertPassword(newPassword)
                    }

                    // 3. Encrypt & Insert API Keys
                    for (k in payload.apiKeys) {
                        val (encrypted, iv) = cryptoManager.encrypt(k.plaintext)
                        val newCategoryId = k.categoryId?.let { categoryIdMap[it] }
                        val newApiKey = com.aipos.aipospm.data.ApiKeyEntry(
                            serviceName = k.serviceName,
                            encryptedApiKey = encrypted,
                            iv = iv,
                            notes = k.notes,
                            categoryId = newCategoryId,
                            isFavorite = k.isFavorite,
                            createdAt = k.createdAt,
                            updatedAt = k.updatedAt
                        )
                        db.apiKeyDao().insertApiKey(newApiKey)
                    }
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to decrypt or restore backup")
            }
        }
    }

    fun loadPassword(id: Int) {
        viewModelScope.launch {
            passwordDao.getPasswordById(id).collect { entry ->
                if (entry != null) {
                    val decrypted = try {
                        cryptoManager.decrypt(entry.encryptedPassword, entry.iv)
                    } catch (e: Exception) {
                        "*** Decryption failed ***"
                    }
                    _uiState.value = _uiState.value.copy(
                        selectedPassword = entry,
                        decryptedPassword = decrypted
                    )
                }
            }
        }
    }

    fun decryptPassword(entry: PasswordEntry): String {
        return try {
            cryptoManager.decrypt(entry.encryptedPassword, entry.iv)
        } catch (e: Exception) {
            "*** Decryption failed ***"
        }
    }

    fun deletePassword(entry: PasswordEntry) {
        viewModelScope.launch {
            try {
                passwordDao.deletePassword(entry)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete: ${e.message}")
            }
        }
    }

    fun deletePasswordById(id: Int) {
        viewModelScope.launch {
            try {
                passwordDao.deletePasswordById(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete: ${e.message}")
            }
        }
    }

    fun toggleFavorite(entry: PasswordEntry) {
        viewModelScope.launch {
            passwordDao.updatePassword(entry.copy(isFavorite = !entry.isFavorite))
        }
    }

    fun resetSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun clearSelection() {
        _uiState.value = PasswordUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
