package com.aipos.aipospm.autofill

import android.app.Activity
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.service.autofill.Dataset
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.security.CryptoManager
import com.aipos.aipospm.security.MasterPasswordManager
import com.aipos.aipospm.ui.theme.AIPOSPasswordManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Overlay authentication and credential selection activity for Android Autofill.
 * Authenticates the user via Biometrics or Master Password before releasing
 * decrypted credentials to the calling application.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillAuthActivity : FragmentActivity() {

    companion object {
        const val EXTRA_ENTRY_ID = "com.aipos.aipospm.autofill.ENTRY_ID"
        const val EXTRA_USERNAME_ID = "com.aipos.aipospm.autofill.USERNAME_ID"
        const val EXTRA_PASSWORD_ID = "com.aipos.aipospm.autofill.PASSWORD_ID"

        fun createIntentSender(
            context: Context,
            entryId: Int,
            usernameId: AutofillId?,
            passwordId: AutofillId?,
            requestCode: Int
        ): IntentSender {
            val intent = Intent(context, AutofillAuthActivity::class.java).apply {
                putExtra(EXTRA_ENTRY_ID, entryId)
                putExtra(EXTRA_USERNAME_ID, usernameId)
                putExtra(EXTRA_PASSWORD_ID, passwordId)
            }
            return android.app.PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                android.app.PendingIntent.FLAG_CANCEL_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            ).intentSender
        }
    }

    private val masterPasswordManager by lazy { MasterPasswordManager(this) }
    private val cryptoManager by lazy { CryptoManager() }
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

        var usernameId: AutofillId? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_USERNAME_ID, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_USERNAME_ID)
        }

        var passwordId: AutofillId? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PASSWORD_ID, AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PASSWORD_ID)
        }

        // Fallback: If IDs were lost during pending intent delivery, inspect AssistStructure if provided by OS
        if (usernameId == null && passwordId == null) {
            val structure = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE)
            }
            if (structure != null) {
                val parsed = AutofillStructureParser.parse(structure)
                usernameId = parsed.usernameId
                passwordId = parsed.passwordId
            }
        }

        setContent {
            AIPOSPasswordManagerTheme {
                AutofillAuthScreen(
                    targetEntryId = targetEntryId,
                    usernameId = usernameId,
                    passwordId = passwordId,
                    onDismiss = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onCredentialSelected = { entry ->
                        fulfillAutofill(entry, usernameId, passwordId)
                    }
                )
            }
        }
    }

    @Composable
    private fun AutofillAuthScreen(
        targetEntryId: Int,
        usernameId: AutofillId?,
        passwordId: AutofillId?,
        onDismiss: () -> Unit,
        onCredentialSelected: (PasswordEntry) -> Unit
    ) {
        var isAuthenticated by remember { mutableStateOf(false) }
        var showPasswordPrompt by remember { mutableStateOf(false) }
        var masterPasswordInput by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        var allPasswords by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
        var searchQuery by remember { mutableStateOf("") }

        // Start biometric prompt or fallback to password prompt
        LaunchedEffect(Unit) {
            val canBiometric = canAuthenticateBiometric()
            if (masterPasswordManager.isBiometricEnabled() && canBiometric) {
                promptBiometric(
                    onSuccess = {
                        isAuthenticated = true
                        if (targetEntryId != -1) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val entry = database.passwordDao().getActivePasswordsList()
                                    .firstOrNull { it.id == targetEntryId }
                                if (entry != null) {
                                    fulfillAutofill(entry, usernameId, passwordId)
                                } else {
                                    withContext(Dispatchers.Main) {
                                        onDismiss()
                                    }
                                }
                            }
                        }
                    },
                    onUsePassword = {
                        showPasswordPrompt = true
                    },
                    onCancel = {
                        onDismiss()
                    },
                    onError = { err ->
                        errorMessage = err
                        showPasswordPrompt = true
                    }
                )
            } else {
                showPasswordPrompt = true
            }
        }

        // Load passwords when authenticated in picker mode
        LaunchedEffect(isAuthenticated) {
            if (isAuthenticated && targetEntryId == -1) {
                withContext(Dispatchers.IO) {
                    val list = database.passwordDao().getActivePasswordsList()
                    withContext(Dispatchers.Main) {
                        allPasswords = list
                    }
                }
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

        // Only show dialog if password prompt is requested or if authenticated in picker mode
        if (showPasswordPrompt || (isAuthenticated && targetEntryId == -1)) {
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    if (!isAuthenticated) {
                        // Master Password Prompt
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Unlock AIPOS Autofill",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enter Master Password to autofill credentials",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = masterPasswordInput,
                                onValueChange = {
                                    masterPasswordInput = it
                                    errorMessage = null
                                },
                                label = { Text("Master Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = errorMessage != null,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onDismiss) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val valid = masterPasswordManager.verifyMasterPassword(masterPasswordInput)
                                        if (valid) {
                                            isAuthenticated = true
                                            if (targetEntryId != -1) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    val entry = database.passwordDao().getActivePasswordsList()
                                                        .firstOrNull { it.id == targetEntryId }
                                                    if (entry != null) {
                                                        fulfillAutofill(entry, usernameId, passwordId)
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            onDismiss()
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            errorMessage = "Incorrect master password"
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Unlock & Fill")
                                }
                            }
                        }
                    } else if (targetEntryId == -1) {
                        // Picker Mode: Search & Select Account
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Select Account to Fill",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search vault...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                            ) {
                                items(filteredPasswords, key = { it.id }) { entry ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { onCredentialSelected(entry) },
                                        shape = RoundedCornerShape(12.dp),
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
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = entry.title,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = entry.username,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun canAuthenticateBiometric(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun promptBiometric(
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
            .setSubtitle("Confirm your biometric to fill credentials")
            .setNegativeButtonText("Use Password")
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(promptInfo)
    }

    @Suppress("DEPRECATION")
    private fun fulfillAutofill(
        entry: PasswordEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val decryptedPassword = try {
                cryptoManager.decrypt(entry.encryptedPassword, entry.iv)
            } catch (e: Exception) {
                ""
            }

            // Build pure value dataset for OS injection (no presentations)
            val datasetBuilder = Dataset.Builder()

            if (usernameId != null) {
                datasetBuilder.setValue(usernameId, AutofillValue.forText(entry.username))
            }
            if (passwordId != null) {
                datasetBuilder.setValue(passwordId, AutofillValue.forText(decryptedPassword))
            }

            val dataset = datasetBuilder.build()
            val replyIntent = Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            }

            withContext(Dispatchers.Main) {
                setResult(Activity.RESULT_OK, replyIntent)
                finish()
            }
        }
    }
}
