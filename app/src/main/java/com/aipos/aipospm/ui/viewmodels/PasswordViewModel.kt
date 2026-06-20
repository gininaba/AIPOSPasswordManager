package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.aipos.aipospm.data.ApiKeyEntry
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.data.Category
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.security.BackupManager
import com.aipos.aipospm.security.CryptoManager
import com.aipos.aipospm.security.PasswordBreachChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

// Classes for backup payload structure
data class BackupPayload(
    val version: Int?,
    val categories: List<CategoryBackup>?,
    val passwords: List<PasswordBackup>?,
    val apiKeys: List<ApiKeyBackup>?
)

data class CategoryBackup(
    val id: Int?,
    val name: String?,
    val createdAt: Long?
)

data class PasswordBackup(
    val id: Int?,
    val title: String?,
    val username: String?,
    val plaintext: String?,
    val url: String?,
    val notes: String?,
    val categoryId: Int?,
    val isFavorite: Boolean?,
    val createdAt: Long?,
    val updatedAt: Long?,
    val plaintextTotp: String? = null
)

data class ApiKeyBackup(
    val id: Int?,
    val serviceName: String?,
    val plaintext: String?,
    val notes: String?,
    val categoryId: Int?,
    val isFavorite: Boolean?,
    val createdAt: Long?,
    val updatedAt: Long?
)

data class PasswordUiState(
    val selectedPassword: PasswordEntry? = null,
    val decryptedPassword: String = "",
    val decryptedTotpSecret: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

class PasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val passwordDao = db.passwordDao()
    private val cryptoManager = CryptoManager()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            PasswordBreachChecker.init(application)
        }
    }

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

    val breachedPasswordCount: StateFlow<Int> = passwords
        .map { list -> list.count { isPasswordBreached(decryptPassword(it)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
        isFavorite: Boolean,
        totpSecret: String? = null
    ) {
        viewModelScope.launch {
            try {
                val (encrypted, iv) = cryptoManager.encrypt(password)

                // Encrypt TOTP secret if provided
                val encryptedTotp: Pair<String, String>? = if (!totpSecret.isNullOrBlank()) {
                    cryptoManager.encrypt(totpSecret)
                } else null

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
                    updatedAt = System.currentTimeMillis(),
                    encryptedTotpSecret = encryptedTotp?.first,
                    totpIv = encryptedTotp?.second
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
                // Step 1: Fetch data from Room.
                // Room 2.7 is main-safe — do NOT wrap these calls in withContext(Dispatchers.IO).
                val categoriesList = db.categoryDao().getAllCategoriesSync()
                val passwordsList = passwordDao.getAllPasswords().first()
                val apiKeysList = db.apiKeyDao().getAllApiKeys().first()

                // Step 2: Decrypt Keystore-encrypted passwords into plaintext for the backup.
                val categoriesBackup = categoriesList.map {
                    CategoryBackup(it.id, it.name, it.createdAt)
                }
                val passwordsBackup = passwordsList.map {
                    val plaintextTotp = if (it.encryptedTotpSecret != null && it.totpIv != null) {
                        try { cryptoManager.decrypt(it.encryptedTotpSecret, it.totpIv) } catch (_: Exception) { null }
                    } else null
                    PasswordBackup(
                        id = it.id,
                        title = it.title,
                        username = it.username,
                        plaintext = decryptPassword(it),
                        url = it.url,
                        notes = it.notes,
                        categoryId = it.categoryId,
                        isFavorite = it.isFavorite,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        plaintextTotp = plaintextTotp
                    )
                }
                val apiKeysBackup = apiKeysList.map {
                    val plaintext = try {
                        cryptoManager.decrypt(it.encryptedApiKey, it.iv)
                    } catch (e: Exception) { "" }
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

                // Step 3: Serialize, PBKDF2-encrypt, and write to file.
                // This is CPU + I/O heavy — run on Dispatchers.IO.
                withContext(Dispatchers.IO) {
                    val payload = BackupPayload(
                        version = 1,
                        categories = categoriesBackup,
                        passwords = passwordsBackup,
                        apiKeys = apiKeysBackup
                    )
                    val payloadJson = com.google.gson.Gson().toJson(payload)
                    val encryptedBackup = BackupManager().encryptBackup(payloadJson, password)

                    val contentResolver = getApplication<Application>().contentResolver
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                            writer.write(encryptedBackup)
                            writer.flush()
                        }
                    } ?: throw IllegalStateException("Could not open output stream for the selected file.")
                }

                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error during export")
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
                // Step 1: Read the backup file and run PBKDF2 decryption on Dispatchers.IO.
                val payload = withContext(Dispatchers.IO) {
                    val contentResolver = getApplication<Application>().contentResolver
                    val backupJson = contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalStateException("Could not open the selected backup file.")

                    if (backupJson.isBlank()) {
                        throw IllegalArgumentException("Backup file is empty or unreadable.")
                    }

                    val decryptedJson = try {
                        BackupManager().decryptBackup(backupJson, password)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Wrong backup password or corrupted backup file.")
                    }

                    val parsed = try {
                        com.google.gson.Gson().fromJson(decryptedJson, BackupPayload::class.java)
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid backup file format.")
                    }

                    if (parsed == null || parsed.version != 1) {
                        throw IllegalArgumentException("Unsupported or corrupt backup version.")
                    }
                    parsed
                }

                // Step 2: Re-encrypt all plaintext values with the Android Keystore key.
                // IMPORTANT: Keystore crypto must happen BEFORE db.withTransaction — it cannot
                // be called from inside a Room transaction block (causes threading/locking issues).
                data class EncryptedPwEntry(val enc: String, val iv: String, val totpEnc: String?, val totpIv: String?, val backup: PasswordBackup)
                val encryptedPasswords = payload.passwords.orEmpty().map { p ->
                    val (enc, iv) = cryptoManager.encrypt(p.plaintext ?: "")
                    val totpPair = if (!p.plaintextTotp.isNullOrBlank()) {
                        cryptoManager.encrypt(p.plaintextTotp)
                    } else null
                    EncryptedPwEntry(enc, iv, totpPair?.first, totpPair?.second, p)
                }
                val encryptedApiKeys = payload.apiKeys.orEmpty().map { k ->
                    val (enc, iv) = cryptoManager.encrypt(k.plaintext ?: "")
                    Triple(enc, iv, k)
                }

                // Step 3: Atomically restore the database.
                // Room 2.7 manages its own transaction thread — do NOT nest this inside
                // withContext(Dispatchers.IO), as that causes connection pool conflicts.
                db.withTransaction {
                    db.passwordDao().clearTable()
                    db.apiKeyDao().clearTable()
                    db.categoryDao().clearTable()

                    // Insert categories and build an old-ID → new-ID mapping
                    val categoryIdMap = mutableMapOf<Int, Int>()
                    for (cat in payload.categories.orEmpty()) {
                        val name = cat.name ?: ""
                        if (name.isNotEmpty()) {
                            val newId = db.categoryDao().insertCategory(
                                Category(name = name, createdAt = cat.createdAt ?: System.currentTimeMillis())
                            ).toInt()
                            cat.id?.let { categoryIdMap[it] = newId }
                        }
                    }

                    // Insert passwords (already Keystore-encrypted)
                    for (entry in encryptedPasswords) {
                        passwordDao.insertPassword(
                            PasswordEntry(
                                title = entry.backup.title ?: "",
                                username = entry.backup.username ?: "",
                                encryptedPassword = entry.enc,
                                iv = entry.iv,
                                url = entry.backup.url ?: "",
                                notes = entry.backup.notes ?: "",
                                categoryId = entry.backup.categoryId?.let { categoryIdMap[it] },
                                isFavorite = entry.backup.isFavorite ?: false,
                                createdAt = entry.backup.createdAt ?: System.currentTimeMillis(),
                                updatedAt = entry.backup.updatedAt ?: System.currentTimeMillis(),
                                encryptedTotpSecret = entry.totpEnc,
                                totpIv = entry.totpIv
                            )
                        )
                    }

                    // Insert API keys (already Keystore-encrypted)
                    for ((enc, iv, k) in encryptedApiKeys) {
                        db.apiKeyDao().insertApiKey(
                            ApiKeyEntry(
                                serviceName = k.serviceName ?: "",
                                encryptedApiKey = enc,
                                iv = iv,
                                notes = k.notes ?: "",
                                categoryId = k.categoryId?.let { categoryIdMap[it] },
                                isFavorite = k.isFavorite ?: false,
                                createdAt = k.createdAt ?: System.currentTimeMillis(),
                                updatedAt = k.updatedAt ?: System.currentTimeMillis()
                            )
                        )
                    }
                }

                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to decrypt or restore backup")
            }
        }
    }

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadPassword(id: Int) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            passwordDao.getPasswordById(id).collect { entry ->
                if (entry != null) {
                    val decrypted = try {
                        cryptoManager.decrypt(entry.encryptedPassword, entry.iv)
                    } catch (e: Exception) {
                        "*** Decryption failed ***"
                    }
                    val decryptedTotp = if (entry.encryptedTotpSecret != null && entry.totpIv != null) {
                        try {
                            cryptoManager.decrypt(entry.encryptedTotpSecret, entry.totpIv)
                        } catch (_: Exception) { "" }
                    } else ""
                    _uiState.value = _uiState.value.copy(
                        selectedPassword = entry,
                        decryptedPassword = decrypted,
                        decryptedTotpSecret = decryptedTotp
                    )
                }
            }
        }
    }

    fun restorePassword(entry: PasswordEntry) {
        viewModelScope.launch {
            try {
                passwordDao.insertPassword(entry)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to restore: ${e.message}")
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
