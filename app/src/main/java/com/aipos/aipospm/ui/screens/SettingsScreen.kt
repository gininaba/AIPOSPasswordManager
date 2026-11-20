package com.aipos.aipospm.ui.screens

import android.content.Intent
import com.aipos.aipospm.MainActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.aipos.aipospm.ui.components.bounceClick
import com.aipos.aipospm.ui.components.pressScale
import android.os.Build
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aipos.aipospm.security.ClipboardHelper
import com.aipos.aipospm.ui.theme.DangerRed
import com.aipos.aipospm.ui.theme.SecurityGreen
import com.aipos.aipospm.ui.theme.WarningAmber
import com.aipos.aipospm.ui.viewmodels.ApiKeyViewModel
import com.aipos.aipospm.ui.viewmodels.AuthViewModel
import com.aipos.aipospm.ui.viewmodels.CategoryViewModel
import com.aipos.aipospm.ui.viewmodels.PasskeyViewModel
import com.aipos.aipospm.ui.viewmodels.PasswordViewModel
import com.aipos.aipospm.ui.viewmodels.VaultImportResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    passwordViewModel: PasswordViewModel,
    apiKeyViewModel: ApiKeyViewModel? = null,
    categoryViewModel: CategoryViewModel,
    passkeyViewModel: PasskeyViewModel? = null,
    canUseBiometric: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToManageCategories: () -> Unit,
    onNavigateToTrash: () -> Unit = {}
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val deletedPasswordCount by passwordViewModel.deletedPasswordCount.collectAsStateWithLifecycle()
    val deletedApiKeyCount by (apiKeyViewModel?.deletedApiKeyCount ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsStateWithLifecycle()
    val deletedPasskeyCount by (passkeyViewModel?.deletedPasskeyCount ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsStateWithLifecycle()
    val totalDeletedCount = deletedPasswordCount + deletedApiKeyCount + deletedPasskeyCount
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkPasskeyProviderEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val cm = context.getSystemService(android.credentials.CredentialManager::class.java)
                val component = android.content.ComponentName(
                    context,
                    com.aipos.aipospm.passkey.AiposCredentialProviderService::class.java
                )
                cm != null && cm.isEnabledCredentialProviderService(component)
            } catch (_: Exception) {
                false
            }
        } else false
    }

    var isAutofillEnabled by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val am = context.getSystemService(android.view.autofill.AutofillManager::class.java)
                am != null && am.isAutofillSupported && am.hasEnabledAutofillServices()
            } else false
        )
    }

    var isPasskeyProviderEnabled by remember {
        mutableStateOf(checkPasskeyProviderEnabled())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val am = context.getSystemService(android.view.autofill.AutofillManager::class.java)
                    isAutofillEnabled = am != null && am.isAutofillSupported && am.hasEnabledAutofillServices()
                }
                isPasskeyProviderEnabled = checkPasskeyProviderEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(authState.error) {
        authState.error?.let {
            snackbarHostState.showSnackbar(it)
            authViewModel.clearError()
        }
    }

    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }

    val isBiometricEnabled = authState.isBiometricEnabled

    // Backup states (kept in memory only — never serialized to instance state Bundle)
    var backupPasswordToSet by remember { mutableStateOf("") }
    var confirmBackupPasswordToSet by remember { mutableStateOf("") }
    var backupPasswordVisible by remember { mutableStateOf(false) }
    var confirmBackupPasswordVisible by remember { mutableStateOf(false) }
    var showSetBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf("") }
    var isBackupInProgress by remember { mutableStateOf(false) }

    var backupPasswordToEnter by remember { mutableStateOf("") }
    var backupPasswordToEnterVisible by remember { mutableStateOf(false) }
    var showEnterBackupPasswordDialog by remember { mutableStateOf(false) }
    var importUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val importUri = remember(importUriString) { importUriString?.let { android.net.Uri.parse(it) } }

    // Recovery Key states (kept in memory only)
    var showViewKeyPasswordDialog by remember { mutableStateOf(false) }
    var showRecoveryKeyDialog by remember { mutableStateOf(false) }
    var decryptedRecoveryKey by remember { mutableStateOf("") }

    // Third-party vault import feedback dialog state
    var isVaultImportInProgress by remember { mutableStateOf(false) }
    var vaultImportAlertTitle by remember { mutableStateOf<String?>(null) }
    var vaultImportAlertMessage by remember { mutableStateOf<String?>(null) }

    val vaultImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}

            isVaultImportInProgress = true
            passwordViewModel.importVaultFile(uri = uri) { result ->
                isVaultImportInProgress = false
                when (result) {
                    is VaultImportResult.Success -> {
                        val label = if (result.count == 1) "credential" else "credentials"
                        scope.launch {
                            snackbarHostState.showSnackbar("Successfully imported ${result.count} $label")
                        }
                    }
                    is VaultImportResult.EncryptedExport -> {
                        vaultImportAlertTitle = "Encrypted Export Detected"
                        vaultImportAlertMessage = result.message
                    }
                    is VaultImportResult.Empty -> {
                        vaultImportAlertTitle = "No Credentials Imported"
                        vaultImportAlertMessage = result.message
                    }
                    is VaultImportResult.Error -> {
                        vaultImportAlertTitle = "Import Failed"
                        vaultImportAlertMessage = result.message
                    }
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pwd = pendingExportPassword
        pendingExportPassword = ""
        backupPasswordToSet = ""
        confirmBackupPasswordToSet = ""
        if (uri != null && pwd.isNotEmpty()) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* Some providers don't support persistable permissions — that's OK */ }

            isBackupInProgress = true
            passwordViewModel.exportBackup(
                uri = uri,
                password = pwd,
                onSuccess = {
                    isBackupInProgress = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Backup exported successfully")
                    }
                },
                onError = { error ->
                    isBackupInProgress = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Export failed: $error")
                    }
                }
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* Some providers don't support persistable permissions — that's OK */ }
            importUriString = uri.toString()
            backupPasswordToEnter = ""
            backupPasswordToEnterVisible = false
            showEnterBackupPasswordDialog = true
        }
    }

    // Set Backup Password Dialog (for export)
    if (showSetBackupPasswordDialog) {
        val meetsMinLength = backupPasswordToSet.length >= 8
        val passwordsMatch = meetsMinLength && backupPasswordToSet == confirmBackupPasswordToSet
        AlertDialog(
            onDismissRequest = {
                if (!isBackupInProgress) {
                    showSetBackupPasswordDialog = false
                    backupPasswordToSet = ""
                    confirmBackupPasswordToSet = ""
                }
            },
            title = { Text("Set Backup Password") },
            text = {
                Column {
                    Text(
                        text = "Enter a password (at least 8 characters) to encrypt your backup file. You will need this password to restore your vault data on any device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = backupPasswordToSet,
                        onValueChange = { backupPasswordToSet = it },
                        label = { Text("Backup Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (backupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { backupPasswordVisible = !backupPasswordVisible }) {
                                Icon(
                                    imageVector = if (backupPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (backupPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        isError = backupPasswordToSet.isNotEmpty() && !meetsMinLength,
                        supportingText = {
                            if (backupPasswordToSet.isNotEmpty() && !meetsMinLength) {
                                Text("At least 8 characters required", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmBackupPasswordToSet,
                        onValueChange = { confirmBackupPasswordToSet = it },
                        label = { Text("Confirm Backup Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (confirmBackupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmBackupPasswordVisible = !confirmBackupPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmBackupPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmBackupPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        isError = confirmBackupPasswordToSet.isNotEmpty() && !passwordsMatch,
                        supportingText = {
                            if (confirmBackupPasswordToSet.isNotEmpty() && !passwordsMatch) {
                                Text("Passwords do not match", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (passwordsMatch) {
                            pendingExportPassword = backupPasswordToSet
                            showSetBackupPasswordDialog = false
                            MainActivity.setExpectingExternalActivity(true)
                            exportLauncher.launch("aipospm_backup.bin")
                        }
                    },
                    enabled = passwordsMatch && !isBackupInProgress
                ) {
                    Text("Save to File")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSetBackupPasswordDialog = false
                        backupPasswordToSet = ""
                        confirmBackupPasswordToSet = ""
                    },
                    enabled = !isBackupInProgress
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Enter Backup Password Dialog (for import)
    if (showEnterBackupPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBackupInProgress) showEnterBackupPasswordDialog = false },
            title = { Text("Enter Backup Password") },
            text = {
                Column {
                    Text(
                        text = "Enter the password that was used to encrypt this backup file to restore your database.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = backupPasswordToEnter,
                        onValueChange = { backupPasswordToEnter = it },
                        label = { Text("Backup Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (backupPasswordToEnterVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { backupPasswordToEnterVisible = !backupPasswordToEnterVisible }) {
                                Icon(
                                    imageVector = if (backupPasswordToEnterVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (backupPasswordToEnterVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = importUri
                        if (uri != null && backupPasswordToEnter.isNotEmpty()) {
                            isBackupInProgress = true
                            showEnterBackupPasswordDialog = false
                            val pwd = backupPasswordToEnter
                            backupPasswordToEnter = ""
                            passwordViewModel.importBackup(
                                uri = uri,
                                password = pwd,
                                onSuccess = {
                                    isBackupInProgress = false
                                    importUriString = null
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Database restored successfully")
                                    }
                                },
                                onError = { error ->
                                    isBackupInProgress = false
                                    importUriString = null
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Restore failed: $error")
                                    }
                                }
                            )
                        }
                    },
                    enabled = backupPasswordToEnter.isNotEmpty() && !isBackupInProgress
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEnterBackupPasswordDialog = false
                        importUriString = null
                        backupPasswordToEnter = ""
                    },
                    enabled = !isBackupInProgress
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Security Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (canUseBiometric) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric Unlock",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Use fingerprint or face to unlock the app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { authViewModel.setBiometricEnabled(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Auto lock timeout
            var timeoutMenuExpanded by remember { mutableStateOf(false) }
            val timeoutOptions = listOf(
                Pair("Immediately", 0),
                Pair("1 Minute", 1),
                Pair("5 Minutes", 5),
                Pair("10 Minutes", 10),
                Pair("Never", -1)
            )
            val currentTimeoutLabel = timeoutOptions.firstOrNull { it.second == authState.autoLockTimeout }?.first ?: "Never"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Lock Timeout",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Lock app after backgrounding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        TextButton(onClick = { timeoutMenuExpanded = true }) {
                            Text(currentTimeoutLabel)
                        }
                        DropdownMenu(
                            expanded = timeoutMenuExpanded,
                            onDismissRequest = { timeoutMenuExpanded = false }
                        ) {
                            timeoutOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.first) },
                                    onClick = {
                                        authViewModel.setAutoLockTimeout(option.second)
                                        timeoutMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Screen Privacy (FLAG_SECURE)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screen Privacy",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Block screenshots and hide content in recent apps switcher",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = authState.isScreenSecurityEnabled,
                        onCheckedChange = { authViewModel.setScreenSecurity(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Android Autofill Service
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                    data = android.net.Uri.parse("package:com.aipos.aipospm")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallback = Intent(android.provider.Settings.ACTION_SETTINGS)
                                    context.startActivity(fallback)
                                } catch (_: Exception) {}
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Autofill requires Android 8.0 or higher")
                            }
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Autofill Service",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isAutofillEnabled) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SecurityGreen.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SecurityGreen
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WarningAmber.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Disabled",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = WarningAmber
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isAutofillEnabled)
                                "Inline keyboard chips, auto-save prompts & 2FA auto-copy active"
                            else
                                "Tap to enable AIPOS as your device's Autofill Provider",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure Autofill",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Android 14+ Passkey Provider Service
            val supportsPasskeys = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick {
                        if (supportsPasskeys) {
                            MainActivity.setExpectingExternalActivity(true)
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_CREDENTIAL_PROVIDER).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                } catch (_: Exception) {}
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("System Passkey Provider requires Android 14 (API 34) or higher")
                            }
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Passkey Provider",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            !supportsPasskeys -> WarningAmber.copy(alpha = 0.12f)
                                            isPasskeyProviderEnabled -> SecurityGreen.copy(alpha = 0.12f)
                                            else -> WarningAmber.copy(alpha = 0.12f)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = when {
                                        !supportsPasskeys -> "Requires Android 14"
                                        isPasskeyProviderEnabled -> "Active"
                                        else -> "Disabled"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        !supportsPasskeys -> WarningAmber
                                        isPasskeyProviderEnabled -> SecurityGreen
                                        else -> WarningAmber
                                    }
                                )
                            }
                        }
                        Text(
                            text = when {
                                !supportsPasskeys -> "Upgrade to Android 14+ to enable system-wide offline Passwords & Passkeys"
                                isPasskeyProviderEnabled -> "AIPOS is active as an offline Passwords & Passkeys provider"
                                else -> "Tap to enable AIPOS under Passwords, passkeys & accounts"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure Passkey Provider",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Change password
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { showChangePassword = !showChangePassword },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Change Master Password",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Update your master password",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (showChangePassword) {
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Current Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (currentPasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    currentPasswordVisible = !currentPasswordVisible
                                }) {
                                    Icon(
                                        imageVector = if (currentPasswordVisible) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (newPasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    newPasswordVisible = !newPasswordVisible
                                }) {
                                    Icon(
                                        imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = confirmNewPassword,
                            onValueChange = { confirmNewPassword = it },
                            label = { Text("Confirm New Password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val changePasswordBtnInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                authViewModel.changeMasterPassword(
                                    currentPassword, newPassword, confirmNewPassword
                                ) { success ->
                                    if (success) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Password changed successfully")
                                        }
                                        currentPassword = ""
                                        newPassword = ""
                                        confirmNewPassword = ""
                                        showChangePassword = false
                                    }
                                    // Specific error is surfaced automatically via authState.error
                                }
                            },
                            interactionSource = changePasswordBtnInteractionSource,
                            modifier = Modifier.fillMaxWidth().pressScale(changePasswordBtnInteractionSource),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !authState.isLoading && currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmNewPassword.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (authState.isLoading) "Updating..." else "Change Password")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { showViewKeyPasswordDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Emergency Recovery Key",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "View or copy your emergency recovery key",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // General Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "General",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Manage Categories button card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(onNavigateToManageCategories),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Manage Categories",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Create and organize custom folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Trash / Recently Deleted button card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(onNavigateToTrash),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Trash",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (totalDeletedCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DangerRed.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$totalDeletedCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DangerRed
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Restore or permanently delete items (30-day auto purge)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backup & Restore Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Encrypted Local Backups",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Export your database to a password-encrypted file, or restore from a previously exported backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val exportBtnInteractionSource = remember { MutableInteractionSource() }
                    val importBtnInteractionSource = remember { MutableInteractionSource() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                backupPasswordToSet = ""
                                confirmBackupPasswordToSet = ""
                                backupPasswordVisible = false
                                confirmBackupPasswordVisible = false
                                showSetBackupPasswordDialog = true
                            },
                            interactionSource = exportBtnInteractionSource,
                            modifier = Modifier.weight(1f).pressScale(exportBtnInteractionSource),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isBackupInProgress
                        ) {
                            Text(if (isBackupInProgress) "Working..." else "Export Backup")
                        }
                        OutlinedButton(
                            onClick = {
                                MainActivity.setExpectingExternalActivity(true)
                                importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                            },
                            interactionSource = importBtnInteractionSource,
                            modifier = Modifier.weight(1f).pressScale(importBtnInteractionSource),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isBackupInProgress
                        ) {
                            Text(if (isBackupInProgress) "Working..." else "Import Backup")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Import Third-Party Vaults",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Import credentials from Bitwarden (unencrypted JSON or CSV), KeePass, or 1Password CSV exports. Files are processed completely offline and encrypted locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val vaultImportBtnInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            MainActivity.setExpectingExternalActivity(true)
                            vaultImportLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "application/json",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        interactionSource = vaultImportBtnInteractionSource,
                        modifier = Modifier.fillMaxWidth().pressScale(vaultImportBtnInteractionSource),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isVaultImportInProgress && !isBackupInProgress
                    ) {
                        Text(if (isVaultImportInProgress) "Importing..." else "Import CSV or JSON Vault")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val appVersion = remember(context) {
                try {
                    val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    "v${pkgInfo.versionName ?: "1.7.0-beta.1"}"
                } catch (_: Exception) {
                    "v1.7.0-beta.1"
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("App Name", "AIPOS Password Manager")
                    InfoRow("Version", appVersion)
                    InfoRow("Developed by", "gininaba")
                    InfoRow("Security", "AES-256-GCM Encryption")
                    InfoRow("Storage", "Fully Offline (Local Only)")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showViewKeyPasswordDialog) {
        var verifyPasswordInput by remember { mutableStateOf("") }
        var verifyPasswordVisible by remember { mutableStateOf(false) }
        var verifyError by remember { mutableStateOf<String?>(null) }
        var isVerifyingKey by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { 
                if (!isVerifyingKey) {
                    showViewKeyPasswordDialog = false 
                    verifyPasswordInput = ""
                    verifyError = null
                }
            },
            title = { Text("Confirm Master Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "To view your emergency recovery key, please verify your master password.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = verifyPasswordInput,
                        onValueChange = { verifyPasswordInput = it },
                        label = { Text("Master Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (verifyPasswordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { verifyPasswordVisible = !verifyPasswordVisible }) {
                                Icon(
                                    imageVector = if (verifyPasswordVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (verifyError != null) {
                        Text(
                            text = verifyError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isVerifyingKey) return@TextButton
                        isVerifyingKey = true
                        verifyError = null
                        authViewModel.verifyMasterPasswordForAction(verifyPasswordInput) { isCorrect, errMsg ->
                            if (isCorrect) {
                                authViewModel.getDecryptedRecoveryKeyAsync { key ->
                                    isVerifyingKey = false
                                    if (key != null) {
                                        decryptedRecoveryKey = key
                                        showRecoveryKeyDialog = true
                                        showViewKeyPasswordDialog = false
                                        verifyPasswordInput = ""
                                        verifyError = null
                                    } else {
                                        verifyError = "Recovery key not found. Please set a new password to generate one."
                                    }
                                }
                            } else {
                                isVerifyingKey = false
                                verifyError = errMsg ?: "Incorrect master password"
                            }
                        }
                    },
                    enabled = !isVerifyingKey && verifyPasswordInput.isNotBlank()
                ) {
                    Text(if (isVerifyingKey) "Verifying..." else "Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showViewKeyPasswordDialog = false
                        verifyPasswordInput = ""
                        verifyError = null
                    },
                    enabled = !isVerifyingKey
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRecoveryKeyDialog) {
        AlertDialog(
            onDismissRequest = {
                showRecoveryKeyDialog = false
                decryptedRecoveryKey = ""
            },
            title = { Text("Emergency Recovery Key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Write down this key or save it in a secure offline location.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = decryptedRecoveryKey,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ClipboardHelper.copyAndScheduleClear(context, "Recovery Key", decryptedRecoveryKey)
                    }
                ) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRecoveryKeyDialog = false
                    decryptedRecoveryKey = ""
                }) {
                    Text("Close")
                }
            }
        )
    }

    if (vaultImportAlertTitle != null && vaultImportAlertMessage != null) {
        AlertDialog(
            onDismissRequest = {
                vaultImportAlertTitle = null
                vaultImportAlertMessage = null
            },
            title = { Text(vaultImportAlertTitle!!) },
            text = {
                Text(
                    text = vaultImportAlertMessage!!,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vaultImportAlertTitle = null
                        vaultImportAlertMessage = null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
