package com.aipos.aipospm.autofill

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.aipos.aipospm.ui.theme.AIPOSPasswordManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google Password Manager-style Save / Update Password Material 3 Bottom Sheet
 * triggered by AiposAutofillService.onSaveRequest.
 *
 * - Brand-new logins save in 1 tap (encrypted via Android Keystore AES-256-GCM).
 * - Updating/overwriting an existing vault entry's password requires BIOMETRIC_STRONG
 *   or Master Password verification before committing.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AutofillSaveActivity : FragmentActivity() {

    companion object {
        const val EXTRA_SAVE_TOKEN = "com.aipos.aipospm.autofill.SAVE_TOKEN"
    }

    private val masterPasswordManager by lazy { MasterPasswordManager.getInstance(this) }
    private val cryptoManager by lazy { CryptoManager.shared }
    private val database by lazy { AppDatabase.getInstance(this) }

    private var saveToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (masterPasswordManager.isScreenSecurityEnabled()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        saveToken = intent.getStringExtra(EXTRA_SAVE_TOKEN)
        val payload = PendingAutofillSaveStore.peek(saveToken)
        if (payload == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val displayTarget = AutofillMatcher.resolveDisplayTarget(
            context = this,
            webDomain = payload.webDomain,
            packageName = payload.packageName
        )
        val defaultTitle = payload.existingTitle?.takeIf { it.isNotBlank() }
            ?: AutofillMatcher.deriveDefaultTitle(
                webDomain = payload.webDomain,
                packageName = payload.packageName,
                appLabel = AutofillMatcher.resolveAppLabel(this, payload.packageName)
            )

        setContent {
            AIPOSPasswordManagerTheme {
                SavePasswordBottomSheet(
                    payload = payload,
                    defaultTitle = defaultTitle,
                    displayTarget = displayTarget,
                    onDismiss = {
                        PendingAutofillSaveStore.consume(saveToken)
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                    onCommitSave = { title, username, password ->
                        commitCredential(
                            payload = payload,
                            title = title,
                            username = username,
                            password = password
                        )
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            PendingAutofillSaveStore.consume(saveToken)
        }
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SavePasswordBottomSheet(
        payload: PendingSavePayload,
        defaultTitle: String,
        displayTarget: String?,
        onDismiss: () -> Unit,
        onCommitSave: (title: String, username: String, password: String) -> Unit
    ) {
        val isUpdate = payload.existingEntryId != null
        var title by remember { mutableStateOf(defaultTitle) }
        var username by remember { mutableStateOf(payload.username) }
        var password by remember { mutableStateOf(payload.password) }
        var passwordVisible by remember { mutableStateOf(false) }

        var needsMasterPasswordForUpdate by remember { mutableStateOf(false) }
        var masterPasswordInput by remember { mutableStateOf("") }
        var isWorking by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

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
                                text = if (isUpdate) "Update password in AIPOS?" else "Save password to AIPOS?",
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

                    IconButton(onClick = onDismiss, enabled = !isWorking) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!needsMasterPasswordForUpdate) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username / Email") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
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
                        TextButton(onClick = onDismiss, enabled = !isWorking) {
                            Text("Not now")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cleanTitle = title.trim().ifBlank { defaultTitle }
                                val cleanUser = username.trim()
                                val pwd = password
                                if (pwd.isBlank()) {
                                    errorMessage = "Password cannot be empty"
                                    return@Button
                                }
                                if (isUpdate) {
                                    verifyBeforeUpdate(
                                        onVerified = {
                                            isWorking = true
                                            onCommitSave(cleanTitle, cleanUser, pwd)
                                        },
                                        onFallbackPassword = {
                                            needsMasterPasswordForUpdate = true
                                        },
                                        onError = { err ->
                                            errorMessage = err
                                            needsMasterPasswordForUpdate = true
                                        }
                                    )
                                } else {
                                    isWorking = true
                                    onCommitSave(cleanTitle, cleanUser, pwd)
                                }
                            },
                            enabled = !isWorking && password.isNotBlank(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                when {
                                    isWorking -> "Saving..."
                                    isUpdate -> "Update"
                                    else -> "Save"
                                }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Verify your Master Password before overwriting an existing vault entry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = masterPasswordInput,
                        onValueChange = {
                            masterPasswordInput = it
                            errorMessage = null
                        },
                        label = { Text("Master Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = errorMessage != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
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
                        TextButton(
                            onClick = {
                                needsMasterPasswordForUpdate = false
                                masterPasswordInput = ""
                                errorMessage = null
                            },
                            enabled = !isWorking
                        ) {
                            Text("Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (isWorking) return@Button
                                val input = masterPasswordInput
                                isWorking = true
                                errorMessage = null
                                lifecycleScope.launch {
                                    val lockoutSec = masterPasswordManager.getRemainingLockoutSeconds()
                                    if (lockoutSec > 0L) {
                                        isWorking = false
                                        errorMessage = "Too many attempts. Try again in ${lockoutSec}s."
                                        return@launch
                                    }
                                    val valid = withContext(Dispatchers.Default) {
                                        masterPasswordManager.verifyMasterPassword(input)
                                    }
                                    if (valid) {
                                        masterPasswordInput = ""
                                        onCommitSave(
                                            title.trim().ifBlank { defaultTitle },
                                            username.trim(),
                                            password
                                        )
                                    } else {
                                        isWorking = false
                                        val remaining = masterPasswordManager.getRemainingLockoutSeconds()
                                        errorMessage = if (remaining > 0L) {
                                            "Too many attempts. Try again in ${remaining}s."
                                        } else {
                                            "Incorrect master password"
                                        }
                                    }
                                }
                            },
                            enabled = !isWorking && masterPasswordInput.isNotBlank(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(if (isWorking) "Updating..." else "Confirm Update")
                        }
                    }
                }
            }
        }
    }

    private fun verifyBeforeUpdate(
        onVerified: () -> Unit,
        onFallbackPassword: () -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricAvailable = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

        if (masterPasswordManager.isBiometricEnabled() && biometricAvailable) {
            val executor = ContextCompat.getMainExecutor(this)
            val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onVerified()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onFallbackPassword()
                        BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> { /* stay on sheet */ }
                        else -> onError(errString.toString())
                    }
                }
            })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm Password Update")
                .setSubtitle("Verify your identity to overwrite this saved password")
                .setNegativeButtonText("Use Password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()

            prompt.authenticate(promptInfo)
        } else {
            onFallbackPassword()
        }
    }

    private fun commitCredential(
        payload: PendingSavePayload,
        title: String,
        username: String,
        password: String
    ) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val (encrypted, iv) = cryptoManager.encrypt(password)
                    val now = System.currentTimeMillis()
                    val existingId = payload.existingEntryId

                    if (existingId != null) {
                        val existing = database.passwordDao().getActivePasswordByIdSync(existingId)
                        if (existing != null) {
                            database.passwordDao().updatePassword(
                                existing.copy(
                                    title = title,
                                    username = username,
                                    encryptedPassword = encrypted,
                                    iv = iv,
                                    updatedAt = now
                                )
                            )
                        } else {
                            insertNewEntry(title, username, encrypted, iv, payload, now)
                        }
                    } else {
                        insertNewEntry(title, username, encrypted, iv, payload, now)
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            }

            PendingAutofillSaveStore.consume(saveToken)
            withContext(Dispatchers.Main) {
                if (success) {
                    val msg = if (payload.existingEntryId != null) {
                        "Password updated in AIPOS Vault"
                    } else {
                        "Password saved to AIPOS Vault"
                    }
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                } else {
                    Toast.makeText(applicationContext, "Failed to save password", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_CANCELED)
                }
                finish()
            }
        }
    }

    private suspend fun insertNewEntry(
        title: String,
        username: String,
        encryptedPassword: String,
        iv: String,
        payload: PendingSavePayload,
        now: Long
    ) {
        val cleanDomain = payload.webDomain?.let { AutofillMatcher.extractCleanDomain(it) }.orEmpty()
        val urlValue = when {
            cleanDomain.isNotBlank() -> "https://$cleanDomain"
            !payload.packageName.isNullOrBlank() -> "androidapp://${payload.packageName}"
            else -> ""
        }
        database.passwordDao().insertPassword(
            PasswordEntry(
                title = title,
                username = username,
                encryptedPassword = encryptedPassword,
                iv = iv,
                url = urlValue,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}

