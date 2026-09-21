package com.aipos.aipospm.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.aipos.aipospm.R
import com.aipos.aipospm.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android AutofillService implementation for AIPOS Password Manager.
 * Handles system fill requests, matches active credentials against target
 * domains and packages, and constructs secure authenticated datasets.
 */
@RequiresApi(Build.VERSION_CODES.O)
class AiposAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database by lazy { AppDatabase.getInstance(this) }

    @Suppress("DEPRECATION")
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

        serviceScope.launch {
            try {
                val passwords = database.passwordDao().getActivePasswordsList()
                val matches = AutofillMatcher.findMatches(passwords, parsed.webDomain, parsed.packageName)

                val responseBuilder = FillResponse.Builder()
                var datasetCount = 0

                // 1. Add matching credential datasets (up to top 3)
                for (match in matches.take(3)) {
                    val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                        setTextViewText(R.id.autofill_title, match.title)
                        setTextViewText(R.id.autofill_subtitle, match.username)
                    }

                    val intentSender = AutofillAuthActivity.createIntentSender(
                        context = this@AiposAutofillService,
                        entryId = match.id,
                        usernameId = parsed.usernameId,
                        passwordId = parsed.passwordId,
                        requestCode = match.id
                    )

                    val datasetBuilder = Dataset.Builder(presentation)
                    datasetBuilder.setAuthentication(intentSender)

                    if (parsed.usernameId != null) {
                        datasetBuilder.setValue(parsed.usernameId, null, presentation)
                    }
                    if (parsed.passwordId != null) {
                        datasetBuilder.setValue(parsed.passwordId, null, presentation)
                    }

                    responseBuilder.addDataset(datasetBuilder.build())
                    datasetCount++
                }

                // 2. Add "Search in AIPOS Vault..." option
                val searchPresentation = RemoteViews(packageName, R.layout.autofill_search_item)
                val searchIntentSender = AutofillAuthActivity.createIntentSender(
                    context = this@AiposAutofillService,
                    entryId = -1,
                    usernameId = parsed.usernameId,
                    passwordId = parsed.passwordId,
                    requestCode = 99999
                )

                val searchDatasetBuilder = Dataset.Builder(searchPresentation)
                searchDatasetBuilder.setAuthentication(searchIntentSender)

                if (parsed.usernameId != null) {
                    searchDatasetBuilder.setValue(parsed.usernameId, null, searchPresentation)
                }
                if (parsed.passwordId != null) {
                    searchDatasetBuilder.setValue(parsed.passwordId, null, searchPresentation)
                }

                responseBuilder.addDataset(searchDatasetBuilder.build())
                datasetCount++

                callback.onSuccess(responseBuilder.build())
            } catch (e: Exception) {
                callback.onFailure(e.message)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
