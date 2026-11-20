package com.aipos.aipospm.passkey

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.aipos.aipospm.R
import com.aipos.aipospm.autofill.AutofillMatcher
import com.aipos.aipospm.data.AppDatabase
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * 100% offline Android 14+ CredentialProviderService supporting both:
 * - WebAuthn / FIDO2 Passkeys (PublicKeyCredential)
 * - Saved Passwords (PasswordCredential) rendered natively in Android's Google-style bottom sheet
 *
 * Requires zero INTERNET or BLUETOOTH permissions.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class AiposCredentialProviderService : CredentialProviderService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database by lazy { AppDatabase.getInstance(this) }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        val (action, requestCode, description) = when (request) {
            is BeginCreatePublicKeyCredentialRequest -> Triple(
                PasskeyAuthActivity.ACTION_CREATE_PASSKEY,
                REQUEST_CODE_CREATE_PASSKEY,
                "Save passkey offline in AIPOS Vault"
            )
            is BeginCreatePasswordCredentialRequest -> Triple(
                PasskeyAuthActivity.ACTION_CREATE_PASSWORD,
                REQUEST_CODE_CREATE_PASSWORD,
                "Save password offline in AIPOS Vault"
            )
            else -> {
                callback.onError(
                    CreateCredentialUnsupportedException("AIPOS supports Passkeys and Password credentials.")
                )
                return
            }
        }

        val job = serviceScope.launch {
            try {
                val createIntent = Intent(this@AiposCredentialProviderService, PasskeyAuthActivity::class.java).apply {
                    this.action = action
                }
                val pendingIntent = PendingIntent.getActivity(
                    this@AiposCredentialProviderService,
                    requestCode,
                    createIntent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val createEntry = CreateEntry.Builder(
                    accountName = "AIPOS Vault",
                    pendingIntent = pendingIntent
                )
                    .setDescription(description)
                    .build()

                val response = BeginCreateCredentialResponse.Builder()
                    .setCreateEntries(listOf(createEntry))
                    .build()

                if (!cancellationSignal.isCanceled) {
                    callback.onResult(response)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!cancellationSignal.isCanceled) {
                    callback.onError(CreateCredentialUnknownException(e.message ?: "Failed to prepare credential creation"))
                }
            }
        }

        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        val job = serviceScope.launch {
            try {
                val responseBuilder = BeginGetCredentialResponse.Builder()
                val passkeyDao = database.passkeyDao()
                val passwordDao = database.passwordDao()

                for (option in request.beginGetCredentialOptions) {
                    when (option) {
                        is BeginGetPublicKeyCredentialOption -> {
                            val (rpId, allowedCredentialIds) = parseGetRequestJson(option.requestJson)
                            if (rpId.isNullOrBlank()) continue

                            val candidates = passkeyDao.getActivePasskeysByRpId(rpId).filter { entry ->
                                allowedCredentialIds.isEmpty() || entry.credentialId in allowedCredentialIds
                            }

                            for (entry in candidates) {
                                val getIntent = Intent(this@AiposCredentialProviderService, PasskeyAuthActivity::class.java).apply {
                                    action = PasskeyAuthActivity.ACTION_GET_PASSKEY
                                    putExtra(PasskeyAuthActivity.EXTRA_PASSKEY_ID, entry.id)
                                }
                                val pendingIntent = PendingIntent.getActivity(
                                    this@AiposCredentialProviderService,
                                    REQUEST_CODE_GET_PASSKEY_BASE + entry.id,
                                    getIntent,
                                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )

                                val username = entry.userName.ifBlank { entry.userDisplayName.ifBlank { entry.rpId } }
                                val displayName = entry.userDisplayName.ifBlank { entry.rpName.ifBlank { entry.rpId } }

                                val credentialEntry = PublicKeyCredentialEntry.Builder(
                                    context = this@AiposCredentialProviderService,
                                    username = username,
                                    pendingIntent = pendingIntent,
                                    beginGetPublicKeyCredentialOption = option
                                )
                                    .setDisplayName(displayName)
                                    .setLastUsedTime(Instant.ofEpochMilli(entry.lastUsedAt))
                                    .build()

                                responseBuilder.addCredentialEntry(credentialEntry)
                            }
                        }

                        is BeginGetPasswordOption -> {
                            val callingApp = request.callingAppInfo ?: continue
                            // For native Android apps (non-origin browser calls), match by packageName
                            if (callingApp.isOriginPopulated()) continue

                            val targetPackage = callingApp.packageName
                            val allPasswords = passwordDao.getActivePasswordsList()
                            val matches = AutofillMatcher.findMatches(
                                entries = allPasswords,
                                webDomain = null,
                                packageName = targetPackage
                            ).filter { entry ->
                                entry.encryptedPassword.isNotBlank() &&
                                    (option.allowedUserIds.isEmpty() || entry.username in option.allowedUserIds)
                            }

                            val keyIcon = Icon.createWithResource(
                                this@AiposCredentialProviderService,
                                R.drawable.ic_autofill_key
                            )

                            for (entry in matches.take(5)) {
                                val getIntent = Intent(this@AiposCredentialProviderService, PasskeyAuthActivity::class.java).apply {
                                    action = PasskeyAuthActivity.ACTION_GET_PASSWORD
                                    putExtra(PasskeyAuthActivity.EXTRA_PASSWORD_ENTRY_ID, entry.id)
                                }
                                val pendingIntent = PendingIntent.getActivity(
                                    this@AiposCredentialProviderService,
                                    REQUEST_CODE_GET_PASSWORD_BASE + entry.id,
                                    getIntent,
                                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                )

                                val username = entry.username.ifBlank { entry.title }
                                val passwordEntry = PasswordCredentialEntry.Builder(
                                    context = this@AiposCredentialProviderService,
                                    username = username,
                                    pendingIntent = pendingIntent,
                                    beginGetPasswordOption = option
                                )
                                    .setDisplayName(entry.title)
                                    .setIcon(keyIcon)
                                    .setLastUsedTime(Instant.ofEpochMilli(entry.updatedAt))
                                    .build()

                                responseBuilder.addCredentialEntry(passwordEntry)
                            }
                        }
                    }
                }

                if (!cancellationSignal.isCanceled) {
                    callback.onResult(responseBuilder.build())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!cancellationSignal.isCanceled) {
                    callback.onError(GetCredentialUnknownException(e.message ?: "Failed to query credentials"))
                }
            }
        }

        cancellationSignal.setOnCancelListener { job.cancel() }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun parseGetRequestJson(requestJson: String): Pair<String?, Set<String>> {
        return try {
            val obj = JsonParser.parseString(requestJson).asJsonObject
            val rpId = obj.get("rpId")?.takeIf { !it.isJsonNull }?.asString?.trim()
            val allowed = mutableSetOf<String>()
            val allowArray = obj.get("allowCredentials")?.takeIf { it.isJsonArray }?.asJsonArray
            if (allowArray != null) {
                for (el in allowArray) {
                    if (!el.isJsonObject) continue
                    val id = el.asJsonObject.get("id")?.takeIf { !it.isJsonNull }?.asString?.trim()
                    if (!id.isNullOrEmpty()) allowed.add(id)
                }
            }
            rpId to allowed
        } catch (_: Exception) {
            null to emptySet()
        }
    }

    companion object {
        private const val REQUEST_CODE_CREATE_PASSKEY = 40001
        private const val REQUEST_CODE_CREATE_PASSWORD = 40002
        private const val REQUEST_CODE_GET_PASSKEY_BASE = 50000
        private const val REQUEST_CODE_GET_PASSWORD_BASE = 60000
    }
}
