package com.aipos.aipospm.passkey

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.aipos.aipospm.autofill.AutofillMatcher
import com.aipos.aipospm.autofill.AutofillTotpNotifier
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.data.PasskeyEntry
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.security.CryptoManager
import com.aipos.aipospm.security.MasterPasswordManager
import com.aipos.aipospm.ui.theme.AIPOSPasswordManagerTheme
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Overlay authentication activity invoked by Android 14+ CredentialManager when creating
 * or signing in with an AIPOS Passkey or Saved Password. Enforces FLAG_SECURE and
 * BIOMETRIC_STRONG / Master Password verification before generating or decrypting credentials.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyAuthActivity : FragmentActivity() {

    companion object {
        const val ACTION_CREATE_PASSKEY = "com.aipos.aipospm.passkey.CREATE_PASSKEY"
        const val ACTION_GET_PASSKEY = "com.aipos.aipospm.passkey.GET_PASSKEY"
        const val ACTION_CREATE_PASSWORD = "com.aipos.aipospm.passkey.CREATE_PASSWORD"
        const val ACTION_GET_PASSWORD = "com.aipos.aipospm.passkey.GET_PASSWORD"
        const val EXTRA_PASSKEY_ID = "com.aipos.aipospm.passkey.PASSKEY_ID"
        const val EXTRA_PASSWORD_ENTRY_ID = "com.aipos.aipospm.passkey.PASSWORD_ENTRY_ID"
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

        val action = intent.action
        val isPasswordFlow = action == ACTION_GET_PASSWORD || action == ACTION_CREATE_PASSWORD ||
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)?.callingRequest is CreatePasswordRequest
        val isCreateFlow = action == ACTION_CREATE_PASSKEY || action == ACTION_CREATE_PASSWORD ||
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent) != null

        val passkeyId = intent.getIntExtra(EXTRA_PASSKEY_ID, -1)
        val passwordEntryId = intent.getIntExtra(EXTRA_PASSWORD_ENTRY_ID, -1)

        setContent {
            AIPOSPasswordManagerTheme {
                CredentialAuthSheet(
                    isCreateFlow = isCreateFlow,
                    isPasswordFlow = isPasswordFlow,
                    onAuthenticated = {
                        lifecycleScope.launch {
                            when {
                                isPasswordFlow && isCreateFlow -> handleCreatePassword()
                                isPasswordFlow && !isCreateFlow -> handleGetPassword(passwordEntryId)
                                isCreateFlow -> handleCreatePasskey()
                                else -> handleGetPasskey(passkeyId)
                            }
                        }
                    },
                    onCancel = {
                        finishWithCancellation(isCreateFlow)
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CredentialAuthSheet(
        isCreateFlow: Boolean,
        isPasswordFlow: Boolean,
        onAuthenticated: () -> Unit,
        onCancel: () -> Unit
    ) {
        var showPasswordPrompt by remember { mutableStateOf(false) }
        var masterPasswordInput by remember { mutableStateOf("") }
        var masterPasswordVisible by remember { mutableStateOf(false) }
        var isVerifying by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val promptTitle = when {
            isPasswordFlow && isCreateFlow -> "Save Password to AIPOS"
            isPasswordFlow -> "Sign in with AIPOS Password"
            isCreateFlow -> "Create AIPOS Passkey"
            else -> "Sign in with AIPOS Passkey"
        }

        val promptSubtitle = when {
            isPasswordFlow && isCreateFlow -> "Verify your identity to save this password"
            isPasswordFlow -> "Verify your identity to fill saved password"
            else -> "Verify your identity to authorize AIPOS Passkey"
        }

        LaunchedEffect(Unit) {
            val biometricAvailable = BiometricManager.from(this@PasskeyAuthActivity)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            if (masterPasswordManager.isBiometricEnabled() && biometricAvailable) {
                promptBiometric(
                    title = promptTitle,
                    subtitle = promptSubtitle,
                    onSuccess = onAuthenticated,
                    onUsePassword = { showPasswordPrompt = true },
                    onCancel = onCancel,
                    onError = { err ->
                        errorMessage = err
                        showPasswordPrompt = true
                    }
                )
            } else {
                showPasswordPrompt = true
            }
        }

        if (showPasswordPrompt) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onCancel,
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
                                    imageVector = if (isPasswordFlow) Icons.Default.Key else Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = promptTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "AIPOS Password Manager",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = onCancel, enabled = !isVerifying) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = promptSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = masterPasswordInput,
                        onValueChange = {
                            masterPasswordInput = it
                            errorMessage = null
                        },
                        label = { Text("Master Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { masterPasswordVisible = !masterPasswordVisible }) {
                                Icon(
                                    imageVector = if (masterPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (masterPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (masterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = errorMessage != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
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
                        TextButton(onClick = onCancel, enabled = !isVerifying) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (isVerifying) return@Button
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
                                        onAuthenticated()
                                    } else {
                                        val remaining = masterPasswordManager.getRemainingLockoutSeconds()
                                        errorMessage = if (remaining > 0L) {
                                            "Too many attempts. Try again in ${remaining}s."
                                        } else {
                                            "Incorrect master password"
                                        }
                                    }
                                }
                            },
                            enabled = !isVerifying && masterPasswordInput.isNotBlank(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                when {
                                    isVerifying -> "Verifying..."
                                    isPasswordFlow && isCreateFlow -> "Save Password"
                                    isPasswordFlow -> "Sign In"
                                    isCreateFlow -> "Create Passkey"
                                    else -> "Sign In"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleCreatePassword() {
        val providerRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val createRequest = providerRequest?.callingRequest as? CreatePasswordRequest
        if (providerRequest == null || createRequest == null) {
            finishWithCreateError("Missing password creation request")
            return
        }

        try {
            withContext(Dispatchers.IO) {
                val targetPackage = providerRequest.callingAppInfo.packageName
                val appLabel = AutofillMatcher.resolveAppLabel(this@PasskeyAuthActivity, targetPackage)
                val title = AutofillMatcher.deriveDefaultTitle(
                    webDomain = null,
                    packageName = targetPackage,
                    appLabel = appLabel
                )
                val (encrypted, iv) = cryptoManager.encrypt(createRequest.password)
                val now = System.currentTimeMillis()

                val activePasswords = database.passwordDao().getActivePasswordsList()
                val matches = AutofillMatcher.findMatches(activePasswords, null, targetPackage)
                val existing = matches.firstOrNull { it.username.equals(createRequest.id, ignoreCase = true) }

                if (existing != null) {
                    database.passwordDao().updatePassword(
                        existing.copy(
                            encryptedPassword = encrypted,
                            iv = iv,
                            updatedAt = now
                        )
                    )
                } else {
                    database.passwordDao().insertPassword(
                        PasswordEntry(
                            title = title,
                            username = createRequest.id,
                            encryptedPassword = encrypted,
                            iv = iv,
                            url = "androidapp://$targetPackage",
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val replyIntent = Intent()
            PendingIntentHandler.setCreateCredentialResponse(replyIntent, CreatePasswordResponse())
            setResult(Activity.RESULT_OK, replyIntent)
            finish()
        } catch (e: Exception) {
            finishWithCreateError(e.message ?: "Failed to save password")
        }
    }

    private suspend fun handleGetPassword(passwordEntryId: Int) {
        if (passwordEntryId <= 0) {
            finishWithGetError("Missing selected password entry")
            return
        }

        try {
            val entry = withContext(Dispatchers.IO) {
                database.passwordDao().getActivePasswordByIdSync(passwordEntryId)
            } ?: throw IllegalStateException("Selected password is no longer available")

            val decryptedPassword = withContext(Dispatchers.Default) {
                cryptoManager.decrypt(entry.encryptedPassword, entry.iv)
            }

            // Copy 6-digit 2FA TOTP code automatically if configured
            AutofillTotpNotifier.copyTotpIfConfigured(
                context = applicationContext,
                entry = entry,
                cryptoManager = cryptoManager
            )

            val replyIntent = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                replyIntent,
                GetCredentialResponse(PasswordCredential(entry.username, decryptedPassword))
            )
            setResult(Activity.RESULT_OK, replyIntent)
            finish()
        } catch (e: Exception) {
            finishWithGetError(e.message ?: "Failed to retrieve saved password")
        }
    }

    private suspend fun handleCreatePasskey() {
        val providerRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val createRequest = providerRequest?.callingRequest as? CreatePublicKeyCredentialRequest
        if (providerRequest == null || createRequest == null) {
            finishWithCreateError("Missing passkey creation request")
            return
        }

        try {
            val registrationJson = withContext(Dispatchers.Default) {
                val requestObj = JsonParser.parseString(createRequest.requestJson).asJsonObject
                val rpObj = requestObj.getAsJsonObject("rp")
                val userObj = requestObj.getAsJsonObject("user")

                val rpId = rpObj?.getStringOrNull("id")?.trim()
                    ?: throw IllegalArgumentException("Missing rp.id in WebAuthn request")
                val rpName = rpObj.getStringOrNull("name")?.trim().orEmpty().ifBlank { rpId }
                val userId = userObj?.getStringOrNull("id")?.trim()
                    ?: throw IllegalArgumentException("Missing user.id in WebAuthn request")
                val userName = userObj.getStringOrNull("name")?.trim().orEmpty()
                val userDisplayName = userObj.getStringOrNull("displayName")?.trim().orEmpty()
                val challenge = requestObj.getStringOrNull("challenge")?.trim()
                    ?: throw IllegalArgumentException("Missing challenge in WebAuthn request")

                val generated = WebAuthnCrypto.generatePasskeyKeyPair()
                val (encryptedKey, iv) = cryptoManager.encrypt(generated.pkcs8PrivateKeyBase64)

                val origin = resolveOrigin(providerRequest.callingAppInfo, rpId, createRequest.clientDataHash)
                val responseJson = WebAuthnCrypto.createRegistrationResponseJson(
                    generated = generated,
                    rpId = rpId,
                    challengeBase64Url = challenge,
                    origin = origin,
                    packageName = providerRequest.callingAppInfo.packageName,
                    providedClientDataHash = createRequest.clientDataHash
                )

                val now = System.currentTimeMillis()
                val entry = PasskeyEntry(
                    credentialId = generated.credentialIdBase64Url,
                    rpId = rpId,
                    rpName = rpName,
                    userHandle = userId,
                    userName = userName,
                    userDisplayName = userDisplayName,
                    encryptedPrivateKey = encryptedKey,
                    privateKeyIv = iv,
                    publicKeyCose = generated.cosePublicKeyBase64Url,
                    signCount = 0L,
                    createdAt = now,
                    lastUsedAt = now
                )
                database.passkeyDao().insertPasskey(entry)
                responseJson
            }

            val replyIntent = Intent()
            PendingIntentHandler.setCreateCredentialResponse(
                replyIntent,
                CreatePublicKeyCredentialResponse(registrationJson)
            )
            setResult(Activity.RESULT_OK, replyIntent)
            finish()
        } catch (e: Exception) {
            finishWithCreateError(e.message ?: "Failed to create passkey")
        }
    }

    private suspend fun handleGetPasskey(passkeyId: Int) {
        val providerRequest = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val getOption = providerRequest?.credentialOptions
            ?.filterIsInstance<GetPublicKeyCredentialOption>()
            ?.firstOrNull()

        if (providerRequest == null || getOption == null || passkeyId <= 0) {
            finishWithGetError("Missing passkey sign-in request")
            return
        }

        try {
            val assertionJson = withContext(Dispatchers.Default) {
                val entry = database.passkeyDao().getPasskeyByIdSync(passkeyId)
                    ?.takeIf { !it.isDeleted }
                    ?: throw IllegalStateException("Selected passkey is no longer available")

                val requestObj = JsonParser.parseString(getOption.requestJson).asJsonObject
                val rpId = requestObj.getStringOrNull("rpId")?.trim().orEmpty().ifBlank { entry.rpId }
                val challenge = requestObj.getStringOrNull("challenge")?.trim()
                    ?: throw IllegalArgumentException("Missing challenge in WebAuthn request")

                val pkcs8PrivateKey = cryptoManager.decrypt(entry.encryptedPrivateKey, entry.privateKeyIv)
                val nextSignCount = entry.signCount + 1L
                val origin = resolveOrigin(providerRequest.callingAppInfo, rpId, getOption.clientDataHash)

                val responseJson = WebAuthnCrypto.createAssertionResponseJson(
                    credentialIdBase64Url = entry.credentialId,
                    userHandleBase64Url = entry.userHandle,
                    pkcs8PrivateKeyBase64 = pkcs8PrivateKey,
                    rpId = rpId,
                    signCount = nextSignCount,
                    challengeBase64Url = challenge,
                    origin = origin,
                    packageName = providerRequest.callingAppInfo.packageName,
                    providedClientDataHash = getOption.clientDataHash
                )

                database.passkeyDao().recordPasskeyUsage(entry.id)
                responseJson
            }

            val replyIntent = Intent()
            PendingIntentHandler.setGetCredentialResponse(
                replyIntent,
                GetCredentialResponse(PublicKeyCredential(assertionJson))
            )
            setResult(Activity.RESULT_OK, replyIntent)
            finish()
        } catch (e: Exception) {
            finishWithGetError(e.message ?: "Failed to sign in with passkey")
        }
    }

    private fun resolveOrigin(
        callingAppInfo: CallingAppInfo,
        rpId: String,
        clientDataHash: ByteArray?
    ): String {
        if (clientDataHash != null) {
            return "https://$rpId"
        }
        val signers = callingAppInfo.signingInfo.apkContentsSigners
        val firstCert = signers?.firstOrNull()?.toByteArray()
        return if (firstCert != null) {
            WebAuthnCrypto.computeAndroidApkOrigin(firstCert)
        } else {
            "https://$rpId"
        }
    }

    private fun promptBiometric(
        title: String,
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
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use Password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun finishWithCancellation(isCreateFlow: Boolean) {
        val replyIntent = Intent()
        if (isCreateFlow) {
            PendingIntentHandler.setCreateCredentialException(
                replyIntent,
                CreateCredentialCancellationException("User cancelled credential creation")
            )
        } else {
            PendingIntentHandler.setGetCredentialException(
                replyIntent,
                GetCredentialCancellationException("User cancelled sign-in")
            )
        }
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    private fun finishWithCreateError(message: String) {
        val replyIntent = Intent()
        PendingIntentHandler.setCreateCredentialException(
            replyIntent,
            CreateCredentialUnknownException(message)
        )
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    private fun finishWithGetError(message: String) {
        val replyIntent = Intent()
        PendingIntentHandler.setGetCredentialException(
            replyIntent,
            GetCredentialUnknownException(message)
        )
        setResult(Activity.RESULT_OK, replyIntent)
        finish()
    }

    private fun JsonObject.getStringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return try {
            el.asString
        } catch (_: Exception) {
            null
        }
    }
}
