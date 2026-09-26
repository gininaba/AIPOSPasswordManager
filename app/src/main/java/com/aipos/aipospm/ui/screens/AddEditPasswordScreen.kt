package com.aipos.aipospm.ui.screens

import com.aipos.aipospm.MainActivity
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import com.aipos.aipospm.data.CategoryPresets
import com.aipos.aipospm.data.CategoryType
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.rememberModalBottomSheetState
import com.aipos.aipospm.ui.components.IconPickerBottomSheet
import com.aipos.aipospm.ui.components.VaultIconRegistry
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
    val categories by categoryViewModel.passwordCategories.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isFavorite by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val isEditing = passwordId != null && passwordId > 0
    var hasLoadedInitialData by rememberSaveable { mutableStateOf(false) }
    var totpSecret by rememberSaveable { mutableStateOf("") }
    var showTotpField by rememberSaveable { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var iconId by rememberSaveable { mutableStateOf<String?>(null) }
    var showIconPicker by rememberSaveable { mutableStateOf(false) }
    val iconPickerSheetState = rememberModalBottomSheetState()

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                showScanner = true
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Camera permission is required to scan QR codes")
                }
            }
        }
    )

    var isPasswordBreached by remember { mutableStateOf(false) }

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
        if (isEditing) {
            passwordViewModel.loadPassword(passwordId)
        }
    }

    // Populate fields once when entry is loaded
    LaunchedEffect(uiState.selectedPassword, uiState.decryptedPassword, hasLoadedInitialData) {
        if (isEditing && !hasLoadedInitialData) {
            val entry = uiState.selectedPassword
            if (entry != null && entry.id == passwordId && !uiState.isLoading) {
                title = entry.title
                username = entry.username
                password = uiState.decryptedPassword
                url = entry.url
                notes = entry.notes
                selectedCategoryId = entry.categoryId
                isFavorite = entry.isFavorite
                iconId = entry.icon
                // Populate TOTP secret if present
                val decryptedTotp = uiState.decryptedTotpSecret
                if (decryptedTotp.isNotEmpty()) {
                    totpSecret = decryptedTotp
                    showTotpField = true
                }
                hasLoadedInitialData = true
            }
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            passwordViewModel.resetSaveSuccess()
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
                            categoryViewModel.addCategory(newCatName, com.aipos.aipospm.data.CategoryType.PASSWORD)
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
                    IconButton(onClick = onNavigateBack) {
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

            if (showIconPicker) {
                IconPickerBottomSheet(
                    sheetState = iconPickerSheetState,
                    currentIconId = iconId,
                    defaultIconVector = Icons.Default.Password,
                    defaultLabel = "Default (Auto / First Letter)",
                    onIconSelected = { iconId = it },
                    onDismissRequest = { showIconPicker = false }
                )
            }

            // Custom Icon Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showIconPicker = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selectedIcon = VaultIconRegistry.getIcon(iconId)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedIcon != null) {
                            Icon(
                                imageVector = selectedIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        } else if (title.isNotBlank()) {
                            Text(
                                text = title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Password,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Icon",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = VaultIconRegistry.getIconItem(iconId)?.label ?: "Default (Auto / First Letter)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = { showIconPicker = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Choose")
                    }

                    if (iconId != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { iconId = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Reset Icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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

            // Smart Category Suggestion Pill
            val suggestedCategoryName = remember(title, selectedCategoryId) {
                if (selectedCategoryId == null && title.isNotBlank()) {
                    CategoryPresets.suggestCategory(title, CategoryType.PASSWORD)
                } else null
            }
            val matchingCat = remember(suggestedCategoryName, categories) {
                if (suggestedCategoryName != null) {
                    categories.find { it.name.equals(suggestedCategoryName, ignoreCase = true) }
                } else null
            }

            AnimatedVisibility(
                visible = suggestedCategoryName != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (suggestedCategoryName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {
                                if (matchingCat != null) {
                                    selectedCategoryId = matchingCat.id
                                } else {
                                    categoryViewModel.addCategory(suggestedCategoryName, CategoryType.PASSWORD) { newId ->
                                        selectedCategoryId = newId
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = if (matchingCat != null) "Suggested: ${matchingCat.name}"
                                    else "Create Folder: $suggestedCategoryName",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        )
                    }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "This password is in the common/breached passwords list. It is highly recommended to choose a stronger password.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            TextButton(
                onClick = onNavigateToGenerator,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Casino,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Password")
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
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Two-Factor Authentication (TOTP)",
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
                                            MainActivity.setExpectingExternalActivity(true)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isValid) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = if (isValid) "Valid" else "Invalid",
                                    tint = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isValid) "Valid Base32 key" else "Invalid Base32 format",
                                    color = if (isValid) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
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
                        totpSecret = totpSecret.trim().ifBlank { null },
                        icon = iconId
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
