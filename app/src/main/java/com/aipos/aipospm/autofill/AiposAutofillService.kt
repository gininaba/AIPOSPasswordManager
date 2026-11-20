package com.aipos.aipospm.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.aipos.aipospm.R
import com.aipos.aipospm.data.AppDatabase
import com.aipos.aipospm.security.CryptoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Google Password Manager-style Android AutofillService for AIPOS Password Manager.
 *
 * Features:
 * - Android 11+ (API 30+) IME inline keyboard strip chips (matched credentials,
 *   strong password suggestion on sign-up forms, and a pinned AIPOS Vault action chip)
 * - Adaptive Day/Night Material 3 fallback dropdown cards for older keyboards/OS versions
 * - Automatic SaveInfo registration + onSaveRequest bottom-sheet flow (Save / Update)
 */
@RequiresApi(Build.VERSION_CODES.O)
class AiposAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database by lazy { AppDatabase.getInstance(this) }
    private val cryptoManager by lazy { CryptoManager.shared }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val parsed = AutofillStructureParser.parse(structure)
        if (!parsed.hasTargetFields) {
            callback.onSuccess(null)
            return
        }

        val fillJob = serviceScope.launch {
            try {
                val passwords = database.passwordDao().getActivePasswordsList()
                val matches = AutofillMatcher.findMatches(passwords, parsed.webDomain, parsed.packageName)

                val responseBuilder = FillResponse.Builder()

                val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    InlinePresentationHelper.getSupportedSpecs(request.inlineSuggestionsRequest)
                } else emptyList()

                val maxInlineSlots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    request.inlineSuggestionsRequest?.maxSuggestionCount ?: 0
                } else 0

                val shouldOfferSuggestPassword =
                    parsed.passwordId != null && (parsed.isNewPasswordFlow || matches.isEmpty())

                // Reserve 1 inline slot for the trailing Vault chip, and 1 for Suggest Password if active
                val reservedSlots = 1 + if (shouldOfferSuggestPassword) 1 else 0
                val maxMatchChips = if (inlineSpecs.isNotEmpty() && maxInlineSlots > 0) {
                    maxOf(0, minOf(3, maxInlineSlots - reservedSlots))
                } else {
                    3
                }

                var inlineSlotIndex = 0

                // Helper to build a suggest-strong-password dataset
                fun addSuggestPasswordDataset() {
                    val menuPresentation = RemoteViews(packageName, R.layout.autofill_generate_item)
                    val inlinePresentation = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        inlineSpecs.isNotEmpty() &&
                        inlineSlotIndex < maxInlineSlots
                    ) {
                        val spec = inlineSpecs[minOf(inlineSlotIndex, inlineSpecs.lastIndex)]
                        inlineSlotIndex++
                        InlinePresentationHelper.createSuggestPasswordChip(
                            context = this@AiposAutofillService,
                            spec = spec,
                            requestCodeOffset = 500
                        )
                    } else null

                    val intentSender = AutofillAuthActivity.createIntentSender(
                        context = this@AiposAutofillService,
                        mode = AutofillAuthActivity.MODE_SUGGEST_PASSWORD,
                        entryId = -2,
                        usernameId = parsed.usernameId,
                        passwordId = parsed.passwordId,
                        confirmPasswordId = parsed.confirmPasswordId,
                        webDomain = parsed.webDomain,
                        targetPackageName = parsed.packageName,
                        prefillUsername = parsed.usernameText,
                        requestCode = REQUEST_CODE_SUGGEST
                    )

                    responseBuilder.addDataset(
                        buildAuthenticatedDataset(
                            parsed = parsed,
                            menuPresentation = menuPresentation,
                            inlinePresentation = inlinePresentation,
                            intentSender = intentSender
                        )
                    )
                }

                // On sign-up / new-password forms, surface "Suggest strong password" FIRST (Google style)
                if (shouldOfferSuggestPassword && parsed.isNewPasswordFlow) {
                    addSuggestPasswordDataset()
                }

                // 1. Add matching credential datasets
                for ((idx, match) in matches.take(maxMatchChips).withIndex()) {
                    val primaryLabel = match.username.ifBlank { match.title }
                    val secondaryLabel = if (match.username.isNotBlank()) {
                        match.title
                    } else {
                        AutofillMatcher.extractCleanDomain(match.url).ifBlank { "AIPOS Vault" }
                    }

                    val menuPresentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                        setTextViewText(R.id.autofill_title, primaryLabel)
                        setTextViewText(R.id.autofill_subtitle, secondaryLabel)
                    }

                    val inlinePresentation = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        inlineSpecs.isNotEmpty() &&
                        inlineSlotIndex < maxInlineSlots
                    ) {
                        val spec = inlineSpecs[minOf(inlineSlotIndex, inlineSpecs.lastIndex)]
                        inlineSlotIndex++
                        InlinePresentationHelper.createCredentialChip(
                            context = this@AiposAutofillService,
                            spec = spec,
                            title = primaryLabel,
                            subtitle = secondaryLabel,
                            requestCodeOffset = idx + 1
                        )
                    } else null

                    val intentSender = AutofillAuthActivity.createIntentSender(
                        context = this@AiposAutofillService,
                        mode = AutofillAuthActivity.MODE_FILL_ENTRY,
                        entryId = match.id,
                        usernameId = parsed.usernameId,
                        passwordId = parsed.passwordId,
                        confirmPasswordId = parsed.confirmPasswordId,
                        webDomain = parsed.webDomain,
                        targetPackageName = parsed.packageName,
                        entryTitle = match.title,
                        entryUsername = match.username,
                        requestCode = match.id
                    )

                    responseBuilder.addDataset(
                        buildAuthenticatedDataset(
                            parsed = parsed,
                            menuPresentation = menuPresentation,
                            inlinePresentation = inlinePresentation,
                            intentSender = intentSender
                        )
                    )
                }

                // When no matches exist on a non-signup password form, offer Suggest Strong Password after matches
                if (shouldOfferSuggestPassword && !parsed.isNewPasswordFlow) {
                    addSuggestPasswordDataset()
                }

                // 2. Add trailing pinned "AIPOS Password Manager" Vault picker dataset
                val vaultMenuPresentation = RemoteViews(packageName, R.layout.autofill_search_item)
                val vaultInlinePresentation = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    inlineSpecs.isNotEmpty() &&
                    inlineSlotIndex < maxInlineSlots
                ) {
                    val spec = inlineSpecs[minOf(inlineSlotIndex, inlineSpecs.lastIndex)]
                    val hasOtherChips = inlineSlotIndex > 0
                    InlinePresentationHelper.createVaultActionChip(
                        context = this@AiposAutofillService,
                        spec = spec,
                        pinned = hasOtherChips && maxInlineSlots >= 2,
                        showLabel = !hasOtherChips,
                        requestCodeOffset = 900
                    )
                } else null

                val searchIntentSender = AutofillAuthActivity.createIntentSender(
                    context = this@AiposAutofillService,
                    mode = AutofillAuthActivity.MODE_PICKER,
                    entryId = -1,
                    usernameId = parsed.usernameId,
                    passwordId = parsed.passwordId,
                    confirmPasswordId = parsed.confirmPasswordId,
                    webDomain = parsed.webDomain,
                    targetPackageName = parsed.packageName,
                    prefillUsername = parsed.usernameText,
                    requestCode = REQUEST_CODE_PICKER
                )

                responseBuilder.addDataset(
                    buildAuthenticatedDataset(
                        parsed = parsed,
                        menuPresentation = vaultMenuPresentation,
                        inlinePresentation = vaultInlinePresentation,
                        intentSender = searchIntentSender
                    )
                )

                // 3. Attach SaveInfo so Android invokes onSaveRequest when a password is submitted
                buildSaveInfo(parsed)?.let { responseBuilder.setSaveInfo(it) }

                if (!cancellationSignal.isCanceled) {
                    callback.onSuccess(responseBuilder.build())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!cancellationSignal.isCanceled) {
                    callback.onFailure(e.message)
                }
            }
        }

        cancellationSignal.setOnCancelListener {
            fillJob.cancel()
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        serviceScope.launch {
            try {
                val saveContext = AutofillStructureParser.parseSaveContext(request.fillContexts)
                if (saveContext == null || saveContext.password.isBlank()) {
                    callback.onSuccess()
                    return@launch
                }

                val activePasswords = database.passwordDao().getActivePasswordsList()
                val matches = AutofillMatcher.findMatches(
                    entries = activePasswords,
                    webDomain = saveContext.webDomain,
                    packageName = saveContext.packageName
                )

                // Check if an account with the same username already exists for this domain/app
                val matchingUserEntry = if (saveContext.username.isNotBlank()) {
                    matches.firstOrNull { it.username.equals(saveContext.username, ignoreCase = true) }
                } else null

                if (matchingUserEntry != null) {
                    val existingDecrypted = try {
                        if (matchingUserEntry.encryptedPassword.isNotBlank() && matchingUserEntry.iv.isNotBlank()) {
                            cryptoManager.decrypt(matchingUserEntry.encryptedPassword, matchingUserEntry.iv)
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                    // Identical credential already stored — silently succeed without prompting
                    if (existingDecrypted == saveContext.password) {
                        callback.onSuccess()
                        return@launch
                    }
                }

                val token = PendingAutofillSaveStore.put(
                    PendingSavePayload(
                        username = saveContext.username,
                        password = saveContext.password,
                        webDomain = saveContext.webDomain,
                        packageName = saveContext.packageName,
                        existingEntryId = matchingUserEntry?.id,
                        existingTitle = matchingUserEntry?.title
                    )
                )

                val saveIntent = Intent(this@AiposAutofillService, AutofillSaveActivity::class.java).apply {
                    putExtra(AutofillSaveActivity.EXTRA_SAVE_TOKEN, token)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val pendingIntent = PendingIntent.getActivity(
                        this@AiposAutofillService,
                        REQUEST_CODE_SAVE,
                        saveIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    callback.onSuccess(pendingIntent.intentSender)
                } else {
                    saveIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(saveIntent)
                    callback.onSuccess()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                callback.onSuccess()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    @Suppress("DEPRECATION")
    private fun buildAuthenticatedDataset(
        parsed: ParsedAutofillStructure,
        menuPresentation: RemoteViews,
        inlinePresentation: InlinePresentation?,
        intentSender: android.content.IntentSender
    ): Dataset {
        val targetIds = buildList<AutofillId> {
            parsed.usernameId?.let { add(it) }
            parsed.passwordId?.let { add(it) }
            parsed.confirmPasswordId?.let { add(it) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val presentationsBuilder = Presentations.Builder()
                .setMenuPresentation(menuPresentation)
            if (inlinePresentation != null) {
                presentationsBuilder.setInlinePresentation(inlinePresentation)
            }
            val builder = Dataset.Builder(presentationsBuilder.build())
                .setAuthentication(intentSender)
            val emptyField = Field.Builder().build()
            for (id in targetIds) {
                builder.setField(id, emptyField)
            }
            return builder.build()
        }

        val builder = Dataset.Builder(menuPresentation)
            .setAuthentication(intentSender)
        for (id in targetIds) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                builder.setValue(id, null, menuPresentation, inlinePresentation)
            } else {
                builder.setValue(id, null, menuPresentation)
            }
        }
        return builder.build()
    }

    private fun buildSaveInfo(parsed: ParsedAutofillStructure): SaveInfo? {
        val passwordId = parsed.passwordId ?: return null
        val saveType = if (parsed.usernameId != null) {
            SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        } else {
            SaveInfo.SAVE_DATA_TYPE_PASSWORD
        }

        val builder = SaveInfo.Builder(saveType, arrayOf(passwordId))
        val optionalIds = buildList {
            parsed.usernameId?.let { add(it) }
            parsed.confirmPasswordId?.let { add(it) }
        }
        if (optionalIds.isNotEmpty()) {
            builder.setOptionalIds(optionalIds.toTypedArray())
        }

        // Enable SPA / browser save detection when all login fields disappear after submit
        if (!parsed.webDomain.isNullOrBlank()) {
            builder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        }
        return builder.build()
    }

    companion object {
        private const val REQUEST_CODE_PICKER = -1001
        private const val REQUEST_CODE_SUGGEST = -1002
        private const val REQUEST_CODE_SAVE = -1003
    }
}
