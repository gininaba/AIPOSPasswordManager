package com.aipos.aipospm.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.QrCodeScanner
import com.aipos.aipospm.security.TotpHelper
import com.aipos.aipospm.ui.components.CameraPreview
import com.aipos.aipospm.ui.viewmodels.CategoryViewModel
import com.aipos.aipospm.ui.viewmodels.PasswordViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPasswordScreen(
    passwordViewModel: PasswordViewModel,
    categoryViewModel: CategoryViewModel,
    passwordId: Int?,
    onNavigateBack: () -> Unit,
    onNavigateToGenerator: () -> Unit
) {
    val uiState by passwordViewModel.uiState.collectAsStateWithLifecycle()
    val categories by categoryViewModel.categories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var title by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var totpSecret by rememberSaveable { mutableStateOf("") }
    var showTotpField by rememberSaveable { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                showScanner = true
            }
        }
    )

    var isPasswordBreached by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            passwordViewModel.clearSelection()
        }
    }

    LaunchedEffect(password) {
        if (password.isNotEmpty()) {
            kotlinx.coroutines.delay(250)
            isPasswordBreached = passwordViewModel.isPasswordBreached(password)
        } else {
            isPasswordBreached = false
        }
    }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }

    // Load existing entry for editing
    LaunchedEffect(passwordId) {
        if (passwordId != null && passwordId > 0) {
            isEditing = true
            passwordViewModel.loadPassword(passwordId)
        }
    }

    // Populate fields when entry is loaded
    LaunchedEffect(uiState.selectedPassword) {
        uiState.selectedPassword?.let { entry ->
            if (isEditing) {
                title = entry.title
                username = entry.username
                password = uiState.decryptedPassword
                url = entry.url
                notes = entry.notes
                selectedCategoryId = entry.categoryId
                isFavorite = entry.isFavorite
                // Populate TOTP secret if present
                val decryptedTotp = uiState.decryptedTotpSecret
                if (decryptedTotp.isNotEmpty()) {
                    totpSecret = decryptedTotp
                    showTotpField = true
                }
            }
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            passwordViewModel.resetSaveSuccess()
            passwordViewModel.clearSelection()
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            passwordViewModel.clearError()
        }
    }

    // Dialog to create a quick new category
    if (showCreateCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            title = { Text("New Category") },
            text = {
                OutlinedTextField(
                    value = newCatName,
                    onValueChange = { newCatName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCatName.trim().isNotEmpty()) {
                            categoryViewModel.addCategory(newCatName)
                            newCatName = ""
                        }
                        showCreateCategoryDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Password" else "Add Password",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        passwordViewModel.clearSelection()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star
                            else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title *") },
                placeholder = { Text("e.g., Google, GitHub") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Selection Dropdown
            val selectedCategoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "None"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Category: $selectedCategoryName")
                    }
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedCategoryId = null
                                dropdownExpanded = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showCreateCategoryDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Category",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username / Email *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide" else "Show"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            // Password breach checker alert
            if (password.isNotEmpty() && isPasswordBreached) {
                Text(
                    text = "⚠️ This password is in the common/breached passwords list. It is highly recommended to choose a stronger password.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            TextButton(
                onClick = onNavigateToGenerator,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text("🎲 Generate Password")
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Website URL") },
                placeholder = { Text("https://example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(12.dp))

            // TOTP Section
            Card(
                onClick = { showTotpField = !showTotpField },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔐 Two-Factor Authentication (TOTP)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (showTotpField) "Hide" else if (totpSecret.isNotEmpty()) "Edit" else "Add",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showTotpField) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = totpSecret,
                            onValueChange = { totpSecret = it },
                            label = { Text("TOTP Secret Key (Base32)") },
                            placeholder = { Text("e.g., JBSW43DPEHPK3PXP") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val permissionCheck = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA
                                        )
                                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                            showScanner = true
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan QR Code"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (totpSecret.isNotBlank()) {
                            val isValid = TotpHelper.isValidBase32(totpSecret)
                            Text(
                                text = if (isValid) "✅ Valid Base32 key" else "❌ Invalid Base32 format",
                                color = if (isValid) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (title.isBlank() || username.isBlank() || password.isBlank()) {
                        return@Button
                    }
                    passwordViewModel.savePassword(
                        id = if (isEditing) passwordId else null,
                        title = title.trim(),
                        username = username.trim(),
                        password = password,
                        url = url.trim(),
                        notes = notes.trim(),
                        categoryId = selectedCategoryId,
                        isFavorite = isFavorite,
                        totpSecret = totpSecret.trim().ifBlank { null }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = title.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isEditing) "Update Password" else "Save Password",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showScanner) {
        AlertDialog(
            onDismissRequest = { showScanner = false },
            title = { Text("Scan TOTP QR Code") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    CameraPreview(
                        onQrCodeScanned = { qrResult ->
                            val parsed = parseOtpAuthUri(qrResult)
                            if (parsed != null) {
                                val (secret, issuer, label) = parsed
                                if (secret.isNotEmpty()) {
                                    totpSecret = secret
                                    showTotpField = true
                                    if (title.isBlank() && issuer.isNotEmpty()) {
                                        title = issuer
                                    }
                                    if (username.isBlank() && label.isNotEmpty()) {
                                        username = label
                                    }
                                }
                                showScanner = false
                            } else {
                                if (TotpHelper.isValidBase32(qrResult)) {
                                    totpSecret = qrResult
                                    showTotpField = true
                                    showScanner = false
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showScanner = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun parseOtpAuthUri(uriString: String): Triple<String, String, String>? {
    if (!uriString.startsWith("otpauth://totp/", ignoreCase = true)) return null
    try {
        val remaining = uriString.substring("otpauth://totp/".length)
        val queryStartIndex = remaining.indexOf('?')
        val pathPart = if (queryStartIndex != -1) remaining.substring(0, queryStartIndex) else remaining
        val queryPart = if (queryStartIndex != -1) remaining.substring(queryStartIndex + 1) else ""

        // Parse path: could be Issuer:Label or just Label
        val decodedPath = java.net.URLDecoder.decode(pathPart, "UTF-8")
        var label = ""
        var pathIssuer = ""
        if (decodedPath.contains(":")) {
            val parts = decodedPath.split(":", limit = 2)
            pathIssuer = parts[0].trim()
            label = parts[1].trim()
        } else {
            label = decodedPath.trim()
        }

        // Parse query params
        val queryParams = mutableMapOf<String, String>()
        if (queryPart.isNotEmpty()) {
            val pairs = queryPart.split('&')
            for (pair in pairs) {
                val idx = pair.indexOf('=')
                if (idx != -1) {
                    val key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8").lowercase()
                    val value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    queryParams[key] = value
                }
            }
        }

        val secret = queryParams["secret"] ?: ""
        val queryIssuer = queryParams["issuer"] ?: ""
        val issuer = queryIssuer.ifEmpty { pathIssuer }

        return Triple(secret, issuer, label)
    } catch (e: Exception) {
        return null
    }
}
