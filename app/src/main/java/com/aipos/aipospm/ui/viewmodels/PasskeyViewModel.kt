package com.aipos.aipospm.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.data.PasskeyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing locally stored offline WebAuthn / FIDO2 Passkeys.
 */
class PasskeyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val passkeyDao = db.passkeyDao()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            passkeyDao.purgeOldDeletedPasskeys(thirtyDaysAgo)
        }
    }

    val passkeys: StateFlow<List<PasskeyEntry>> = passkeyDao.getAllPasskeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val passkeyCount: StateFlow<Int> = passkeyDao.getPasskeyCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deletedPasskeys: StateFlow<List<PasskeyEntry>> = passkeyDao.getDeletedPasskeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deletedPasskeyCount: StateFlow<Int> = passkeyDao.getDeletedPasskeyCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun deletePasskey(entry: PasskeyEntry) {
        viewModelScope.launch {
            passkeyDao.softDeletePasskey(entry.id)
        }
    }

    fun restorePasskey(entry: PasskeyEntry) {
        viewModelScope.launch {
            passkeyDao.restorePasskeyById(entry.id)
        }
    }

    fun permanentlyDeletePasskey(id: Int) {
        viewModelScope.launch {
            passkeyDao.permanentlyDeletePasskey(id)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            passkeyDao.emptyPasskeyTrash()
        }
    }
}

