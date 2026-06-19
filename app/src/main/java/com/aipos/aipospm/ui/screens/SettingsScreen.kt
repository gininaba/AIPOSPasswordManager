package com.aipos.aipospm.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aipos.aipospm.ui.viewmodels.AuthViewModel
import com.aipos.aipospm.ui.viewmodels.CategoryViewModel
import com.aipos.aipospm.ui.viewmodels.PasswordViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    passwordViewModel: PasswordViewModel,
    categoryViewModel: CategoryViewModel,
    canUseBiometric: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToManageCategories: () -> Unit
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showChangePassword by rememberSaveable { mutableStateOf(false) }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmNewPassword by rememberSaveable { mutableStateOf("") }
    var currentPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val isBiometricEnabled = authViewModel.isBiometricEnabled()

    // Backup states
    var backupPasswordToSet by remember { mutableStateOf("") }
    var showSetBackupPasswordDialog by remember { mutableStateOf(false) }
    var exportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isBackupInProgress by remember { mutableStateOf(false) }

    var backupPasswordToEnter by remember { mutableStateOf("") }
    var showEnterBackupPasswordDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            // Take persistable permission so the URI stays valid across threads
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* Some providers don't support persistable permissions — that's OK */ }
            exportUri = uri
            showSetBackupPasswordDialog = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Take persistable permission so the URI stays valid across threads
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* Some providers don't support persistable permissions — that's OK */ }
            importUri = uri
            showEnterBackupPasswordDialog = true
        }
    }

    // Set Backup Password Dialog (for export)
    if (showSetBackupPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!isBackupInProgress) showSetBackupPasswordDialog = false },
            title = { Text("Set Backup Password") },
            text = {
                Column {
                    Text(
                        text = "Enter a password to encrypt your backup file. You will need this password to restore your data on any device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = backupPasswordToSet,
                        onValueChange = { backupPasswordToSet = it },
                        label = { Text("Backup Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = exportUri
                        if (uri != null && backupPasswordToSet.isNotEmpty()) {
                            isBackupInProgress = true
                            showSetBackupPasswordDialog = false
                            val pwd = backupPasswordToSet
                            backupPasswordToSet = ""
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
                    },
                    enabled = backupPasswordToSet.isNotEmpty() && !isBackupInProgress
                ) {
                    Text("Export")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSetBackupPasswordDialog = false },
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
                        visualTransformation = PasswordVisualTransformation(),
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
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Database restored successfully")
                                    }
                                },
                                onError = { error ->
                                    isBackupInProgress = false
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
                    onClick = { showEnterBackupPasswordDialog = false },
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
            Text(
                text = "Security",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

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

            // Change password
            Card(
                onClick = { showChangePassword = !showChangePassword },
                modifier = Modifier.fillMaxWidth(),
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

                        Button(
                            onClick = {
                                val success = authViewModel.changeMasterPassword(
                                    currentPassword, newPassword, confirmNewPassword
                                )
                                if (success) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Password changed successfully")
                                    }
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmNewPassword = ""
                                    showChangePassword = false
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Failed to change password")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = currentPassword.isNotBlank() && newPassword.isNotBlank() && confirmNewPassword.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Change Password")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // General Section
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Manage Categories button card
            Card(
                onClick = onNavigateToManageCategories,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Backup & Restore Section
            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                exportLauncher.launch("aipospm_backup.bin")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isBackupInProgress
                        ) {
                            Text(if (isBackupInProgress) "Working..." else "Export Backup")
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isBackupInProgress
                        ) {
                            Text(if (isBackupInProgress) "Working..." else "Import Backup")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    InfoRow("App Name", "AIPOS Password Manager")
                    InfoRow("Version", "1.0.0")
                    InfoRow("Security", "AES-256-GCM Encryption")
                    InfoRow("Storage", "Fully Offline (Local Only)")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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
