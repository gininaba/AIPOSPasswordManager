package com.aipos.aipospm.autofill

import android.app.Activity
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.security.CryptoManager
import com.aipos.aipospm.security.MasterPasswordManager
import com.aipos.aipospm.ui.components.VaultIconRegistry
import com.aipos.aipospm.ui.theme.AIPOSPasswordManagerTheme
import com.aipos.aipospm.ui.viewmodels.PasswordGeneratorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google Password Manager-style overlay authentication, credential picker,
 * and strong password generator bottom-sheet activity for Android Autofill.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : FragmentActivity() {

    companion object {
        const val MODE_FILL_ENTRY = "fill_entry"
        const val MODE_PICKER = "picker"
        const val MODE_SUGGEST_PASSWORD = "suggest_password"

        const val EXTRA_MODE = "com.aipos.aipospm.autofill.MODE"
        const val EXTRA_ENTRY_ID = "com.aipos.aipospm.autofill.ENTRY_ID"
        const val EXTRA_USERNAME_ID = "com.aipos.aipospm.autofill.USERNAME_ID"
        const val EXTRA_PASSWORD_ID = "com.aipos.aipospm.autofill.PASSWORD_ID"
        const val EXTRA_CONFIRM_PASSWORD_ID = "com.aipos.aipospm.autofill.CONFIRM_PASSWORD_ID"
        const val EXTRA_WEB_DOMAIN = "com.aipos.aipospm.autofill.WEB_DOMAIN"
        const val EXTRA_TARGET_PACKAGE = "com.aipos.aipospm.autofill.TARGET_PACKAGE"
        const val EXTRA_PREFILL_USERNAME = "com.aipos.aipospm.autofill.PREFILL_USERNAME"
        const val EXTRA_ENTRY_TITLE = "com.aipos.aipospm.autofill.ENTRY_TITLE"
        const val EXTRA_ENTRY_USERNAME = "com.aipos.aipospm.autofill.ENTRY_USERNAME"

        fun createIntentSender(
            context: Context,
            mode: String = MODE_FILL_ENTRY,
            entryId: Int,
            usernameId: AutofillId?,
            passwordId: AutofillId?,
            confirmPasswordId: AutofillId? = null,
            webDomain: String? = null,
            targetPackageName: String? = null,
            prefillUsername: String? = null,
            entryTitle: String? = null,
            entryUsername: String? = null,
            requestCode: Int
        ): IntentSender {
            val intent = Intent(context, AutofillAuthActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_ENTRY_ID, entryId)
                putExtra(EXTRA_USERNAME_ID, usernameId)
                putExtra(EXTRA_PASSWORD_ID, passwordId)
                putExtra(EXTRA_CONFIRM_PASSWORD_ID, confirmPasswordId)
                putExtra(EXTRA_WEB_DOMAIN, webDomain)
                putExtra(EXTRA_TARGET_PACKAGE, targetPackageName)
                putExtra(EXTRA_PREFILL_USERNAME, prefillUsername)
                putExtra(EXTRA_ENTRY_TITLE, entryTitle)
                putExtra(EXTRA_ENTRY_USERNAME, entryUsername)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
            ).intentSender
        }
    }

    private val masterPasswordManager by lazy { MasterPasswordManager.getInstance(this) }
    private val cryptoManager by lazy { CryptoManager.shared }
    private val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (masterPasswordManager.isScreenSecurityEnabled()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        val targetEntryId = intent.getIntExtra(EXTRA_ENTRY_ID, -1)
        val initialMode = intent.getStringExtra(EXTRA_MODE) ?: if (targetEntryId > 0) MODE_FILL_ENTRY else MODE_PICKER
        var webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        var targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
        val prefillUsername = intent.getStringExtra(EXTRA_PREFILL_USERNAME).orEmpty()
        val entryTitleHint = intent.getStringExtra(EXTRA_ENTRY_TITLE)
        val entryUsernameHint = intent.getStringExtra(EXTRA_ENTRY_USERNAME)

        var usernameId: AutofillId? = getParcelableCompat(EXTRA_USERNAME_ID)
        var passwordId: AutofillId? = getParcelableCompat(EXTRA_PASSWORD_ID)
        var confirmPasswordId: AutofillId? = getParcelableCompat(EXTRA_CONFIRM_PASSWORD_ID)

        if (usernameId == null && passwordId == null) {
            val structure: AssistStructure? = getParcelableCompat(AutofillManager.EXTRA_ASSIST_STRUCTURE)
            if (structure != null) {
                val parsed = AutofillStructureParser.parse(structure)
                usernameId = parsed.usernameId
                passwordId = parsed.passwordId
                confirmPasswordId = parsed.confirmPasswordId
                if (webDomain.isNullOrBlank()) webDomain = parsed.webDomain
                if (targetPackage.isNullOrBlank()) targetPackage = parsed.packageName
            }
        }

        val displayTarget = AutofillMatcher.resolveDisplayTarget(this, webDomain, targetPackage)
        val defaultSaveTitle = AutofillMatcher.deriveDefaultTitle(
            webDomain = webDomain,
            packageName = targetPackage,
            appLabel = AutofillMatcher.resolveAppLabel(this, targetPackage)
        )

        setContent {
            AIPOSPasswordManagerTheme {
                AutofillBottomSheetHost(
                    initialMode = initialMode,
                    targetEntryId = targetEntryId,
                    entryTitleHint = entryTitleHint,
                    entryUsernameHint = entryUsernameHint,
                    displayTarget = displayTarget,
                    webDomain = webDomain,
                    targetPackage = targetPackage,
                    defaultSaveTitle = defaultSaveTitle,
                    prefillUsername = prefillUsername,
                    canFillPassword = passwordId != null,
                    usernameId = usernameId,
                    passwordId = passwordId,
                    confirmPasswordId = confirmPasswordId,
                    onDismiss = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onCredentialSelected = { entry ->
                        fulfillAutofill(entry, usernameId, passwordId)
                    },
                    onUseGeneratedPassword = { title, username, password ->
                        saveAndFillGeneratedPassword(
                            title = title,
                            username = username,
                            password = password,
                            webDomain = webDomain,
                            targetPackage = targetPackage,
                            usernameId = usernameId,
                            passwordId = passwordId,
                            confirmPasswordId = confirmPasswordId
                        )
                    }
                )
            }
        }
    }

    private inline fun <reified T> getParcelableCompat(key: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AutofillBottomSheetHost(
        initialMode: String,
        targetEntryId: Int,
        entryTitleHint: String?,
        entryUsernameHint: String?,
        displayTarget: String?,
        webDomain: String?,
        targetPackage: String?,
        defaultSaveTitle: String,
        prefillUsername: String,
        canFillPassword: Boolean,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        confirmPasswordId: AutofillId?,
        onDismiss: () -> Unit,
        onCredentialSelected: (PasswordEntry) -> Unit,
        onUseGeneratedPassword: (title: String, username: String, password: String) -> Unit
    ) {
        var currentMode by remember { mutableStateOf(initialMode) }
        var isAuthenticated by remember { mutableStateOf(false) }
        var showPasswordSheet by remember { mutableStateOf(false) }
        var masterPasswordInput by remember { mutableStateOf("") }
        var masterPasswordVisible by remember { mutableStateOf(false) }
        var isVerifying by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        var allPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
        var suggestedPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
        var searchQuery by remember { mutableStateOf("") }

        // Strong password generator state
        var genLength by remember { mutableIntStateOf(16) }
        var genSymbols by remember { mutableStateOf(true) }
        var generatedPassword by remember {
            mutableStateOf(
                PasswordGeneratorViewModel.generateRandomPassword(length = 16, useSymbols = true)
            )
        }
        var genTitle by remember { mutableStateOf(defaultSaveTitle) }
        var genUsername by remember { mutableStateOf(prefillUsername) }
        var isSavingGenerated by remember { mutableStateOf(false) }

        // Launch biometric immediately on open
        LaunchedEffect(Unit) {
            val canBiometric = canAuthenticateBiometric()
            if (masterPasswordManager.isBiometricEnabled() && canBiometric) {
                val subtitle = when {
                    initialMode == MODE_FILL_ENTRY && !entryTitleHint.isNullOrBlank() -> {
                        if (!entryUsernameHint.isNullOrBlank()) "Fill $entryTitleHint ($entryUsernameHint)"
                        else "Fill $entryTitleHint"
                    }
                    initialMode == MODE_SUGGEST_PASSWORD -> "Authenticate to generate & save password"
                    else -> "Unlock AIPOS Password Manager"
                }
                promptBiometric(
                    subtitle = subtitle,
                    onSuccess = {
                        isAuthenticated = true
                        if (initialMode == MODE_FILL_ENTRY && targetEntryId > 0) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val entry = database.passwordDao().getActivePasswordByIdSync(targetEntryId)
                                if (entry != null) {
                                    fulfillAutofillSuspend(entry, usernameId, passwordId)
                                } else {
                                    withContext(Dispatchers.Main) { onDismiss() }
                                }
                            }
                        }
                    },
                    onUsePassword = { showPasswordSheet = true },
                    onCancel = { onDismiss() },
                    onError = { err ->
                        errorMessage = err
                        showPasswordSheet = true
                    }
                )
            } else {
                showPasswordSheet = true
            }
        }

        // Load vault entries once authenticated for picker mode
        LaunchedEffect(isAuthenticated, currentMode) {
            if (isAuthenticated && currentMode == MODE_PICKER && allPasswords.isEmpty()) {
                val loaded = withContext(Dispatchers.IO) {
                    database.passwordDao().getActivePasswordsList()
                        .filter { AutofillMatcher.isFillableCredential(it) }
                }
                val matched = AutofillMatcher.findMatches(loaded, webDomain, targetPackage)
                allPasswords = loaded
                suggestedPasswords = matched
            }
        }

        val filteredPasswords = remember(allPasswords, searchQuery) {
            if (searchQuery.isBlank()) allPasswords
            else allPasswords.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.username.contains(searchQuery, ignoreCase = true) ||
                    it.url.contains(searchQuery, ignoreCase = true)
            }
        }

        val shouldShowSheet = showPasswordSheet ||
            (isAuthenticated && (currentMode == MODE_PICKER || currentMode == MODE_SUGGEST_PASSWORD))

        if (shouldShowSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                scrimColor = Color.Transparent,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp)
                ) {
                    // Google Password Manager-style branded sheet header
                    GoogleStyleSheetHeader(
                        title = when {
                            !isAuthenticated -> "Unlock AIPOS Autofill"
                            currentMode == MODE_SUGGEST_PASSWORD -> "Suggest strong password"
                            else -> "AIPOS Password Manager"
                        },
                        displayTarget = displayTarget,
                        onClose = onDismiss
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    when {
                        !isAuthenticated -> {
                            MasterPasswordUnlockSection(
                                password = masterPasswordInput,
                                onPasswordChange = {
                                    masterPasswordInput = it
                                    errorMessage = null
                                },
                                passwordVisible = masterPasswordVisible,
                                onToggleVisible = { masterPasswordVisible = !masterPasswordVisible },
                                errorMessage = errorMessage,
                                isVerifying = isVerifying,
                                onCancel = onDismiss,
                                onUnlock = {
                                    if (isVerifying) return@MasterPasswordUnlockSection
                                    val input = masterPasswordInput
                                    isVerifying = true
                                    errorMessage = null
                                    lifecycleScope.launch {
                                        val lockoutSec = masterPasswordManager.getRemainingLockoutSeconds()
                                        if (lockoutSec > 0L) {
                                            isVerifying = false
                                            errorMessage = "Too many attempts. Try again in ${lockoutSec}s."
                                            return@launch
                                        }
                                        val valid = withContext(Dispatchers.Default) {
                                            masterPasswordManager.verifyMasterPassword(input)
                                        }
                                        isVerifying = false
                                        if (valid) {
                                            masterPasswordInput = ""
                                            isAuthenticated = true
                                            showPasswordSheet = false
                                            if (currentMode == MODE_FILL_ENTRY && targetEntryId > 0) {
                                                val entry = withContext(Dispatchers.IO) {
                                                    database.passwordDao().getActivePasswordByIdSync(targetEntryId)
                                                }
                                                if (entry != null) {
                                                    fulfillAutofillSuspend(entry, usernameId, passwordId)
                                                } else {
                                                    onDismiss()
                                                }
                                            }
                                        } else {
                                            val remaining = masterPasswordManager.getRemainingLockoutSeconds()
                                            errorMessage = if (remaining > 0L) {
                                                "Too many attempts. Try again in ${remaining}s."
                                            } else {
                                                "Incorrect master password"
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        currentMode == MODE_SUGGEST_PASSWORD -> {
                            StrongPasswordSuggestionSection(
                                generatedPassword = generatedPassword,
                                length = genLength,
                                useSymbols = genSymbols,
                                title = genTitle,
                                username = genUsername,
                                isSaving = isSavingGenerated,
                                hasConfirmField = confirmPasswordId != null,
                                onTitleChange = { genTitle = it },
                                onUsernameChange = { genUsername = it },
                                onLengthChange = { newLen ->
                                    genLength = newLen
                                    generatedPassword = PasswordGeneratorViewModel.generateRandomPassword(
                                        length = newLen,
                                        useSymbols = genSymbols
                                    )
                                },
                                onToggleSymbols = {
                                    genSymbols = !genSymbols
                                    generatedPassword = PasswordGeneratorViewModel.generateRandomPassword(
                                        length = genLength,
                                        useSymbols = genSymbols
                                    )
                                },
                                onRegenerate = {
                                    generatedPassword = PasswordGeneratorViewModel.generateRandomPassword(
                                        length = genLength,
                                        useSymbols = genSymbols
                                    )
                                },
                                onSwitchToPicker = { currentMode = MODE_PICKER },
                                onUseAndSave = {
                                    if (!isSavingGenerated) {
                                        isSavingGenerated = true
                                        onUseGeneratedPassword(
                                            genTitle.trim().ifBlank { defaultSaveTitle },
                                            genUsername.trim(),
                                            generatedPassword
                                        )
                                    }
                                }
                            )
                        }

                        else -> {
                            VaultPickerSection(
                                displayTarget = displayTarget,
                                suggestedPasswords = suggestedPasswords,
                                filteredPasswords = filteredPasswords,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                canSuggestPassword = canFillPassword,
                                onSuggestPasswordClick = { currentMode = MODE_SUGGEST_PASSWORD },
                                onCredentialSelected = onCredentialSelected
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun GoogleStyleSheetHeader(
        title: String,
        displayTarget: String?,
        onClose: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!displayTarget.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = displayTarget,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }
    }

    @Composable
    private fun MasterPasswordUnlockSection(
        password: String,
        onPasswordChange: (String) -> Unit,
        passwordVisible: Boolean,
        onToggleVisible: () -> Unit,
        errorMessage: String?,
        isVerifying: Boolean,
        onCancel: () -> Unit,
        onUnlock: () -> Unit
    ) {
        Text(
            text = "Enter your Master Password to unlock autofill",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Master Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onToggleVisible) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false
            ),
            isError = errorMessage != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel, enabled = !isVerifying) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onUnlock,
                enabled = !isVerifying && password.isNotBlank(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(if (isVerifying) "Verifying..." else "Unlock")
            }
        }
    }

    @Composable
    private fun VaultPickerSection(
        displayTarget: String?,
        suggestedPasswords: List<PasswordEntry>,
        filteredPasswords: List<PasswordEntry>,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        canSuggestPassword: Boolean,
        onSuggestPasswordClick: () -> Unit,
        onCredentialSelected: (PasswordEntry) -> Unit
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search saved passwords...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            singleLine = true
        )

        if (canSuggestPassword && searchQuery.isBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onSuggestPasswordClick),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Suggest strong password",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Generate a unique password and save it to AIPOS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchQuery.isBlank() && suggestedPasswords.isNotEmpty()) {
                item(key = "header_suggested") {
                    Text(
                        text = if (!displayTarget.isNullOrBlank()) "Suggested for $displayTarget" else "Suggested",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }
                items(suggestedPasswords, key = { "suggested_${it.id}" }) { entry ->
                    CredentialSheetRow(
                        entry = entry,
                        highlighted = true,
                        onClick = { onCredentialSelected(entry) }
                    )
                }
                item(key = "divider_all") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = "All saved passwords",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            if (filteredPasswords.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No fillable passwords in your vault" else "No matching passwords found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val suggestedIds = if (searchQuery.isBlank()) suggestedPasswords.map { it.id }.toSet() else emptySet()
                val remaining = filteredPasswords.filterNot { it.id in suggestedIds }
                items(remaining, key = { "all_${it.id}" }) { entry ->
                    CredentialSheetRow(
                        entry = entry,
                        highlighted = false,
                        onClick = { onCredentialSelected(entry) }
                    )
                }
            }
        }
    }

    @Composable
    private fun CredentialSheetRow(
        entry: PasswordEntry,
        highlighted: Boolean,
        onClick: () -> Unit
    ) {
        val customIcon = remember(entry.icon) { VaultIconRegistry.getIcon(entry.icon) }
        val hasTotp = !entry.encryptedTotpSecret.isNullOrBlank() && !entry.totpIv.isNullOrBlank()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (highlighted) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = customIcon ?: Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.username.ifBlank { entry.title },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(entry.title)
                            if (entry.encryptedPassword.isNotBlank()) {
                                append(" • ••••••••")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasTotp) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "2FA TOTP",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "2FA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StrongPasswordSuggestionSection(
        generatedPassword: String,
        length: Int,
        useSymbols: Boolean,
        title: String,
        username: String,
        isSaving: Boolean,
        hasConfirmField: Boolean,
        onTitleChange: (String) -> Unit,
        onUsernameChange: (String) -> Unit,
        onLengthChange: (Int) -> Unit,
        onToggleSymbols: () -> Unit,
        onRegenerate: () -> Unit,
        onSwitchToPicker: () -> Unit,
        onUseAndSave: () -> Unit
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Generated strong password",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = generatedPassword,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onRegenerate, enabled = !isSaving) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate password",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(16, 20, 24).forEach { len ->
                FilterChip(
                    selected = length == len,
                    onClick = { onLengthChange(len) },
                    label = { Text("$len chars") }
                )
            }
            FilterChip(
                selected = useSymbols,
                onClick = onToggleSymbols,
                label = { Text("Symbols") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Save as") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username / Email (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        if (hasConfirmField) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Both password and confirmation fields will be filled automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSwitchToPicker, enabled = !isSaving) {
                Text("Choose saved")
            }
            Button(
                onClick = onUseAndSave,
                enabled = !isSaving && generatedPassword.isNotEmpty(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(if (isSaving) "Saving..." else "Use & Save Password")
            }
        }
    }

    private fun canAuthenticateBiometric(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun promptBiometric(
        subtitle: String,
        onSuccess: () -> Unit,
        onUsePassword: () -> Unit,
        onCancel: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onUsePassword()
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> onCancel()
                    else -> onError(errString.toString())
                }
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock AIPOS Autofill")
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use Password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun fulfillAutofill(
        entry: PasswordEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ) {
        lifecycleScope.launch {
            fulfillAutofillSuspend(entry, usernameId, passwordId)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun fulfillAutofillSuspend(
        entry: PasswordEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ) {
        if (usernameId == null && passwordId == null) {
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            return
        }

        val decryptedPassword = if (entry.encryptedPassword.isNotBlank() && entry.iv.isNotBlank()) {
            withContext(Dispatchers.Default) {
                try {
                    cryptoManager.decrypt(entry.encryptedPassword, entry.iv)
                } catch (_: Exception) {
                    null
                }
            }
        } else ""

        if (passwordId != null && entry.encryptedPassword.isNotBlank() && decryptedPassword == null) {
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            return
        }

        val datasetBuilder = Dataset.Builder()
        var hasAnyValue = false

        if (usernameId != null && entry.username.isNotBlank()) {
            datasetBuilder.setValue(usernameId, AutofillValue.forText(entry.username))
            hasAnyValue = true
        }
        if (passwordId != null && !decryptedPassword.isNullOrEmpty()) {
            datasetBuilder.setValue(passwordId, AutofillValue.forText(decryptedPassword))
            hasAnyValue = true
        }

        if (!hasAnyValue) {
            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            return
        }

        // Automatically copy 6-digit TOTP code with 30s auto-clear if entry has 2FA configured
        AutofillTotpNotifier.copyTotpIfConfigured(
            context = applicationContext,
            entry = entry,
            cryptoManager = cryptoManager
        )

        val replyIntent = Intent().apply {
            putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
        }

        withContext(Dispatchers.Main) {
            setResult(Activity.RESULT_OK, replyIntent)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun saveAndFillGeneratedPassword(
        title: String,
        username: String,
        password: String,
        webDomain: String?,
        targetPackage: String?,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        confirmPasswordId: AutofillId?
    ) {
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val (encrypted, iv) = cryptoManager.encrypt(password)
                    val cleanDomain = webDomain?.let { AutofillMatcher.extractCleanDomain(it) }.orEmpty()
                    val urlValue = when {
                        cleanDomain.isNotBlank() -> "https://$cleanDomain"
                        !targetPackage.isNullOrBlank() -> "androidapp://$targetPackage"
                        else -> ""
                    }
                    val now = System.currentTimeMillis()
                    database.passwordDao().insertPassword(
                        PasswordEntry(
                            title = title,
                            username = username,
                            encryptedPassword = encrypted,
                            iv = iv,
                            url = urlValue,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    true
                } catch (_: Exception) {
                    false
                }
            }

            val datasetBuilder = Dataset.Builder()
            if (usernameId != null && username.isNotBlank()) {
                datasetBuilder.setValue(usernameId, AutofillValue.forText(username))
            }
            if (passwordId != null) {
                datasetBuilder.setValue(passwordId, AutofillValue.forText(password))
            }
            if (confirmPasswordId != null) {
                datasetBuilder.setValue(confirmPasswordId, AutofillValue.forText(password))
            }

            val replyIntent = Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build())
            }

            withContext(Dispatchers.Main) {
                if (saved) {
                    Toast.makeText(applicationContext, "Strong password saved to AIPOS Vault", Toast.LENGTH_SHORT).show()
                }
                setResult(Activity.RESULT_OK, replyIntent)
                finish()
            }
        }
    }
}
