package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.aipos.aipospm.data.ApiKeyEntry
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.data.Category
import com.aipos.aipospm.data.CategoryType
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.data.SortOption
import com.aipos.aipospm.data.VaultPreferencesManager
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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
    val createdAt: Long?,
    val type: String? = null
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
    val plaintextTotp: String? = null,
    val customOrder: Int? = null,
    val icon: String? = null
)

data class ApiKeyBackup(
    val id: Int?,
    val serviceName: String?,
    val plaintext: String?,
    val notes: String?,
    val categoryId: Int?,
    val isFavorite: Boolean?,
    val createdAt: Long?,
    val updatedAt: Long?,
    val customOrder: Int? = null,
    val icon: String? = null
)

data class VaultAudit(
    val compromisedIds: Set<Int> = emptySet(),
    val reusedIds: Set<Int> = emptySet()
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
    private val vaultPrefs = VaultPreferencesManager.getInstance(application)
    private val decryptionCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun clearDecryptionCache() {
        decryptionCache.clear()
    }

    private val _sortOption = MutableStateFlow(vaultPrefs.getPasswordSortOption())
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _pinFavorites = MutableStateFlow(vaultPrefs.getPasswordPinFavorites())
    val pinFavorites: StateFlow<Boolean> = _pinFavorites.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            PasswordBreachChecker.init(application)
            // Auto-purge items in trash older than 30 days
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            passwordDao.purgeOldDeletedPasswords(thirtyDaysAgo)
        }
        viewModelScope.launch {
            db.categoryDao().getCategoriesByType(CategoryType.PASSWORD.name).collect { cats ->
                val selected = _selectedCategoryIdFilter.value
                if (selected != null && cats.none { it.id == selected }) {
                    _selectedCategoryIdFilter.value = null
                }
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryIdFilter = MutableStateFlow<Int?>(null)
    val selectedCategoryIdFilter: StateFlow<Int?> = _selectedCategoryIdFilter.asStateFlow()

    private val _showCompromisedOnlyFilter = MutableStateFlow(false)
    val showCompromisedOnlyFilter: StateFlow<Boolean> = _showCompromisedOnlyFilter.asStateFlow()

    private val _showReusedOnlyFilter = MutableStateFlow(false)
    val showReusedOnlyFilter: StateFlow<Boolean> = _showReusedOnlyFilter.asStateFlow()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val vaultAudit: StateFlow<VaultAudit> = passwordDao.getAllPasswords()
        .debounce(300L)
        .map { list ->
            val compIds = mutableSetOf<Int>()
            val plainMap = mutableMapOf<String, MutableList<Int>>()

            for (entry in list) {
                val plain = decryptPassword(entry)
                if (isPasswordBreached(plain)) {
                    compIds.add(entry.id)
                }
                if (plain.isNotBlank() && plain != "*** Decryption failed ***") {
                    plainMap.getOrPut(plain) { mutableListOf() }.add(entry.id)
                }
            }

            val rIds = plainMap.filter { it.value.size > 1 }.values.flatten().toSet()
            VaultAudit(compromisedIds = compIds, reusedIds = rIds)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultAudit())

    val breachedPasswordCount: StateFlow<Int> = vaultAudit
        .map { it.compromisedIds.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val reusedPasswordCount: StateFlow<Int> = vaultAudit
        .map { it.reusedIds.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val reusedPasswordIds: StateFlow<Set<Int>> = vaultAudit
        .map { it.reusedIds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val compromisedPasswordIds: StateFlow<Set<Int>> = vaultAudit
        .map { it.compromisedIds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private data class PasswordFilterSortParams(
        val query: String,
        val categoryId: Int?,
        val compFilter: Pair<Boolean, Set<Int>>,
        val reusedFilter: Pair<Boolean, Set<Int>>,
        val sort: SortOption,
        val pinFavorites: Boolean
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val passwordFilterState = combine(
        _searchQuery,
        _selectedCategoryIdFilter,
        _showCompromisedOnlyFilter,
        _showReusedOnlyFilter,
        vaultAudit
    ) { query, categoryId, compromisedOnly, reusedOnly, audit ->
        Triple(
            query,
            categoryId,
            Pair(
                compromisedOnly to if (compromisedOnly) audit.compromisedIds else emptySet(),
                reusedOnly to if (reusedOnly) audit.reusedIds else emptySet()
            )
        )
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val passwords: StateFlow<List<PasswordEntry>> = combine(
        passwordFilterState,
        _sortOption,
        _pinFavorites
    ) { (query, categoryId, filterPair), sort, pinFav ->
        PasswordFilterSortParams(
            query = query,
            categoryId = categoryId,
            compFilter = filterPair.first,
            reusedFilter = filterPair.second,
            sort = sort,
            pinFavorites = pinFav
        )
    }.flatMapLatest { params ->
        val baseFlow = if (params.query.isBlank()) {
            passwordDao.getAllPasswords()
        } else {
            passwordDao.searchPasswords(params.query)
        }
        baseFlow.map { list ->
            var filtered = list
            if (params.categoryId != null) {
                filtered = filtered.filter { it.categoryId == params.categoryId }
            }
            if (params.compFilter.first) {
                filtered = filtered.filter { it.id in params.compFilter.second }
            }
            if (params.reusedFilter.first) {
                filtered = filtered.filter { it.id in params.reusedFilter.second }
            }
            sortPasswordList(filtered, params.sort, params.pinFavorites)
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sortPasswordList(list: List<PasswordEntry>, sort: SortOption, pinFavorites: Boolean): List<PasswordEntry> {
        val baseComparator: Comparator<PasswordEntry> = when (sort) {
            SortOption.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortOption.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortOption.UPDATED_DESC -> compareByDescending { it.updatedAt }
            SortOption.CREATED_DESC -> compareByDescending { it.createdAt }
            SortOption.CREATED_ASC -> compareBy { it.createdAt }
            SortOption.CUSTOM -> compareBy<PasswordEntry> { it.customOrder }.thenByDescending { it.updatedAt }.thenBy { it.id }
        }
        return if (pinFavorites) {
            list.sortedWith(compareByDescending<PasswordEntry> { it.isFavorite }.then(baseComparator))
        } else {
            list.sortedWith(baseComparator)
        }
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        vaultPrefs.setPasswordSortOption(option)
    }

    fun setPinFavorites(pin: Boolean) {
        _pinFavorites.value = pin
        vaultPrefs.setPasswordPinFavorites(pin)
    }

    fun reorderPasswords(items: List<PasswordEntry>, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in items.indices || toIndex !in items.indices) return
        val reordered = items.toMutableList()
        val movedItem = reordered.removeAt(fromIndex)
        reordered.add(toIndex, movedItem)
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                val allPasswords = passwordDao.getActivePasswordsList()
                val sortedAll = sortPasswordList(allPasswords, SortOption.CUSTOM, pinFavorites = _pinFavorites.value)
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
                            passwordDao.updatePasswordOrder(entry.id, index)
                        }
                    }
                } else {
                    reordered.forEachIndexed { index, entry ->
                        if (entry.customOrder != index) {
                            passwordDao.updatePasswordOrder(entry.id, index)
                        }
                    }
                }
            }
        }
    }

    fun movePassword(items: List<PasswordEntry>, currentIndex: Int, direction: Int) {
        val targetIndex = currentIndex + direction
        if (currentIndex in items.indices && targetIndex in items.indices) {
            reorderPasswords(items, currentIndex, targetIndex)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategoryFilter(categoryId: Int?) {
        _selectedCategoryIdFilter.value = categoryId
        _showCompromisedOnlyFilter.value = false
        _showReusedOnlyFilter.value = false
    }

    fun setShowCompromisedOnlyFilter(showOnly: Boolean) {
        _showCompromisedOnlyFilter.value = showOnly
        if (showOnly) {
            _selectedCategoryIdFilter.value = null
            _showReusedOnlyFilter.value = false
        }
    }

    fun setShowReusedOnlyFilter(showOnly: Boolean) {
        _showReusedOnlyFilter.value = showOnly
        if (showOnly) {
            _selectedCategoryIdFilter.value = null
            _showCompromisedOnlyFilter.value = false
        }
    }

    val passwordCount: StateFlow<Int> = passwordDao.getPasswordCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoritePasswords: StateFlow<List<PasswordEntry>> = passwordDao.getFavoritePasswords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedPasswords: StateFlow<List<PasswordEntry>> = passwordDao.getDeletedPasswords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedPasswordCount: StateFlow<Int> = passwordDao.getDeletedPasswordCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState: StateFlow<PasswordUiState> = _uiState.asStateFlow()

    fun savePassword(
        id: Int? = null,
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
        categoryId: Int?,
        isFavorite: Boolean,
        totpSecret: String? = null,
        icon: String? = null
    ) {
        viewModelScope.launch {
            try {
                val (encrypted, iv) = cryptoManager.encrypt(password)
                decryptionCache["${encrypted}:${iv}"] = password

                // Encrypt TOTP secret if provided
                val encryptedTotp: Pair<String, String>? = if (!totpSecret.isNullOrBlank()) {
                    cryptoManager.encrypt(totpSecret)
                } else null

                val createdAt = if (id != null && id > 0) {
                    val selected = _uiState.value.selectedPassword
                    if (selected != null && selected.id == id) {
                        selected.createdAt
                    } else {
                        passwordDao.getPasswordById(id).first()?.createdAt ?: System.currentTimeMillis()
                    }
                } else {
                    System.currentTimeMillis()
                }

                val customOrder = if (id != null && id > 0) {
                    val selected = _uiState.value.selectedPassword
                    if (selected != null && selected.id == id) {
                        selected.customOrder
                    } else {
                        passwordDao.getPasswordById(id).first()?.customOrder ?: 0
                    }
                } else {
                    0
                }

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
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis(),
                    encryptedTotpSecret = encryptedTotp?.first,
                    totpIv = encryptedTotp?.second,
                    customOrder = customOrder,
                    icon = icon
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

                // Step 2: Decrypt Keystore-encrypted passwords into plaintext for the backup on Dispatchers.Default.
                val (categoriesBackup, passwordsBackup, apiKeysBackup) = withContext(Dispatchers.Default) {
                    val catBackup = categoriesList.map {
                        CategoryBackup(it.id, it.name, it.createdAt, it.type)
                    }
                    val pwBackup = passwordsList.map {
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
                            plaintextTotp = plaintextTotp,
                            customOrder = it.customOrder,
                            icon = it.icon
                        )
                    }
                    val apiBackup = apiKeysList.map {
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
                            updatedAt = it.updatedAt,
                            customOrder = it.customOrder,
                            icon = it.icon
                        )
                    }
                    Triple(catBackup, pwBackup, apiBackup)
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

                // Step 2: Re-encrypt all plaintext values with the Android Keystore key off the main thread.
                data class EncryptedPwEntry(val enc: String, val iv: String, val totpEnc: String?, val totpIv: String?, val backup: PasswordBackup)
                val (encryptedPasswords, encryptedApiKeys) = withContext(Dispatchers.Default) {
                    val pwList = payload.passwords.orEmpty().map { p ->
                        val (enc, iv) = cryptoManager.encrypt(p.plaintext ?: "")
                        val totpPair = if (!p.plaintextTotp.isNullOrBlank()) {
                            cryptoManager.encrypt(p.plaintextTotp)
                        } else null
                        EncryptedPwEntry(enc, iv, totpPair?.first, totpPair?.second, p)
                    }
                    val apiList = payload.apiKeys.orEmpty().map { k ->
                        val (enc, iv) = cryptoManager.encrypt(k.plaintext ?: "")
                        Triple(enc, iv, k)
                    }
                    Pair(pwList, apiList)
                }

                // Step 3: Atomically restore the database using batch insertions.
                db.withTransaction {
                    db.passwordDao().clearTable()
                    db.apiKeyDao().clearTable()
                    db.categoryDao().clearTable()

                    // Insert categories and build an old-ID → new-ID mapping
                    val categoryIdMap = mutableMapOf<Int, Int>()
                    for (cat in payload.categories.orEmpty()) {
                        val name = cat.name ?: ""
                        val catType = cat.type ?: CategoryType.PASSWORD.name
                        if (name.isNotEmpty()) {
                            val newId = db.categoryDao().insertCategory(
                                Category(name = name, type = catType, createdAt = cat.createdAt ?: System.currentTimeMillis())
                            ).toInt()
                            cat.id?.let { categoryIdMap[it] = newId }
                        }
                    }

                    // Batch insert passwords
                    val passwordsToInsert = encryptedPasswords.map { entry ->
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
                            totpIv = entry.totpIv,
                            customOrder = entry.backup.customOrder ?: 0,
                            icon = entry.backup.icon
                        )
                    }
                    passwordDao.insertPasswords(passwordsToInsert)

                    // Batch insert API keys
                    val apiKeysToInsert = encryptedApiKeys.map { (enc, iv, k) ->
                        ApiKeyEntry(
                            serviceName = k.serviceName ?: "",
                            encryptedApiKey = enc,
                            iv = iv,
                            notes = k.notes ?: "",
                            categoryId = k.categoryId?.let { categoryIdMap[it] },
                            isFavorite = k.isFavorite ?: false,
                            createdAt = k.createdAt ?: System.currentTimeMillis(),
                            updatedAt = k.updatedAt ?: System.currentTimeMillis(),
                            customOrder = k.customOrder ?: 0,
                            icon = k.icon
                        )
                    }
                    db.apiKeyDao().insertApiKeys(apiKeysToInsert)
                }

                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to decrypt or restore backup")
            }
        }
    }

    fun importCsv(
        uri: Uri,
        onSuccess: (insertedCount: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val importEntries = withContext(Dispatchers.IO) {
                    val contentResolver = getApplication<Application>().contentResolver
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val parsed = com.aipos.aipospm.security.CsvParser.parse(stream)
                        com.aipos.aipospm.security.CsvImportMapper.map(parsed)
                    } ?: throw IllegalStateException("Could not open the selected CSV file.")
                }

                if (importEntries.isEmpty()) {
                    throw IllegalArgumentException("No valid credentials found in the CSV file.")
                }

                data class EncryptedImportEntry(
                    val title: String,
                    val username: String,
                    val encPw: String,
                    val iv: String,
                    val url: String,
                    val notes: String,
                    val categoryName: String?,
                    val isFavorite: Boolean,
                    val encTotp: String?,
                    val totpIv: String?
                )

                // Offload Keystore encryption mapping off the main thread
                val encryptedEntries = withContext(Dispatchers.Default) {
                    importEntries.map { entry ->
                        val (encPw, iv) = cryptoManager.encrypt(entry.password)
                        val totpPair = if (!entry.totpSecret.isNullOrBlank()) {
                            cryptoManager.encrypt(entry.totpSecret)
                        } else null
                        EncryptedImportEntry(
                            title = entry.title,
                            username = entry.username,
                            encPw = encPw,
                            iv = iv,
                            url = entry.url,
                            notes = entry.notes,
                            categoryName = entry.categoryName,
                            isFavorite = entry.isFavorite,
                            encTotp = totpPair?.first,
                            totpIv = totpPair?.second
                        )
                    }
                }

                db.withTransaction {
                    val existingCategories = db.categoryDao().getCategoriesByTypeSync(CategoryType.PASSWORD.name)
                    val categoryCache = existingCategories.associateBy { it.name.lowercase() }.toMutableMap()

                    val passwordsToInsert = mutableListOf<PasswordEntry>()

                    for (entry in encryptedEntries) {
                        var categoryId: Int? = null
                        val catName = entry.categoryName?.trim()
                        if (!catName.isNullOrEmpty()) {
                            val cachedCat = categoryCache[catName.lowercase()]
                            if (cachedCat != null) {
                                categoryId = cachedCat.id
                            } else {
                                val newCatId = db.categoryDao().insertCategory(
                                    Category(name = catName, type = CategoryType.PASSWORD.name)
                                ).toInt()
                                val newCat = Category(id = newCatId, name = catName, type = CategoryType.PASSWORD.name)
                                categoryCache[catName.lowercase()] = newCat
                                categoryId = newCatId
                            }
                        }

                        passwordsToInsert.add(
                            PasswordEntry(
                                title = entry.title,
                                username = entry.username,
                                encryptedPassword = entry.encPw,
                                iv = entry.iv,
                                url = entry.url,
                                notes = entry.notes,
                                categoryId = categoryId,
                                isFavorite = entry.isFavorite,
                                encryptedTotpSecret = entry.encTotp,
                                totpIv = entry.totpIv
                            )
                        )
                    }

                    passwordDao.insertPasswords(passwordsToInsert)
                }

                onSuccess(importEntries.size)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to import CSV")
            }
        }
    }

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadPassword(id: Int) {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true)
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
                        decryptedTotpSecret = decryptedTotp,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        selectedPassword = null,
                        decryptedPassword = "",
                        decryptedTotpSecret = "",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun restorePassword(entry: PasswordEntry) {
        restorePasswordById(entry.id)
    }

    fun restorePasswordById(id: Int) {
        viewModelScope.launch {
            try {
                passwordDao.restorePasswordById(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to restore: ${e.message}")
            }
        }
    }

    fun decryptPassword(entry: PasswordEntry): String {
        val cacheKey = "${entry.encryptedPassword}:${entry.iv}"
        val cached = decryptionCache[cacheKey]
        if (cached != null) return cached

        return try {
            val decrypted = cryptoManager.decrypt(entry.encryptedPassword, entry.iv)
            decryptionCache[cacheKey] = decrypted
            decrypted
        } catch (e: Exception) {
            "*** Decryption failed ***"
        }
    }

    fun deletePassword(entry: PasswordEntry) {
        deletePasswordById(entry.id)
    }

    fun deletePasswordById(id: Int) {
        viewModelScope.launch {
            try {
                passwordDao.softDeletePassword(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete: ${e.message}")
            }
        }
    }

    fun permanentlyDeletePassword(id: Int) {
        viewModelScope.launch {
            try {
                passwordDao.permanentlyDeletePassword(id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to permanently delete: ${e.message}")
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            try {
                passwordDao.emptyPasswordTrash()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to empty trash: ${e.message}")
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
        loadJob?.cancel()
        loadJob = null
        _uiState.value = PasswordUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
