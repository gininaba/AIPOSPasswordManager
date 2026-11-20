package com.aipos.aipospm.autofill

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.InlinePresentation
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import com.aipos.aipospm.MainActivity
import com.aipos.aipospm.R

/**
 * Builds Android 11+ (API 30+) IME inline suggestion strip chips (Gboard, Samsung Keyboard,
 * SwiftKey, HeliBoard, etc.) styled after Google Password Manager.
 */
@RequiresApi(Build.VERSION_CODES.R)
object InlinePresentationHelper {

    private const val ATTRIBUTION_REQUEST_CODE_BASE = 71000

    /**
     * Returns specs that support InlineSuggestionUi v1, or empty list if the keyboard
     * does not request/support inline suggestions.
     */
    fun getSupportedSpecs(request: InlineSuggestionsRequest?): List<InlinePresentationSpec> {
        if (request == null || request.maxSuggestionCount <= 0) return emptyList()
        return request.inlinePresentationSpecs.filter { spec ->
            try {
                UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)
            } catch (_: Exception) {
                false
            }
        }
    }

    @SuppressLint("RestrictedApi")
    fun createCredentialChip(
        context: Context,
        spec: InlinePresentationSpec,
        title: String,
        subtitle: String?,
        requestCodeOffset: Int
    ): InlinePresentation? {
        return buildChip(
            context = context,
            spec = spec,
            title = title,
            subtitle = subtitle,
            iconRes = R.drawable.ic_autofill_key,
            contentDescription = if (!subtitle.isNullOrBlank()) "$title, $subtitle" else title,
            isPinned = false,
            requestCodeOffset = requestCodeOffset
        )
    }

    @SuppressLint("RestrictedApi")
    fun createSuggestPasswordChip(
        context: Context,
        spec: InlinePresentationSpec,
        requestCodeOffset: Int
    ): InlinePresentation? {
        return buildChip(
            context = context,
            spec = spec,
            title = "Suggest strong password",
            subtitle = null,
            iconRes = R.drawable.ic_autofill_sparkle,
            contentDescription = "Suggest strong password with AIPOS",
            isPinned = false,
            requestCodeOffset = requestCodeOffset
        )
    }

    @SuppressLint("RestrictedApi")
    fun createVaultActionChip(
        context: Context,
        spec: InlinePresentationSpec,
        pinned: Boolean,
        showLabel: Boolean,
        requestCodeOffset: Int
    ): InlinePresentation? {
        return buildChip(
            context = context,
            spec = spec,
            title = if (showLabel) "AIPOS Vault" else null,
            subtitle = null,
            iconRes = R.drawable.ic_autofill_shield,
            contentDescription = "Open AIPOS Password Manager",
            isPinned = pinned,
            requestCodeOffset = requestCodeOffset
        )
    }

    @SuppressLint("RestrictedApi")
    private fun buildChip(
        context: Context,
        spec: InlinePresentationSpec,
        title: String?,
        subtitle: String?,
        @DrawableRes iconRes: Int,
        contentDescription: String,
        isPinned: Boolean,
        requestCodeOffset: Int
    ): InlinePresentation? {
        return try {
            val attributionIntent = PendingIntent.getActivity(
                context,
                ATTRIBUTION_REQUEST_CODE_BASE + requestCodeOffset,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val contentBuilder = InlineSuggestionUi.newContentBuilder(attributionIntent)
                .setStartIcon(Icon.createWithResource(context, iconRes))
                .setContentDescription(contentDescription)

            if (!title.isNullOrBlank()) {
                contentBuilder.setTitle(title)
            }
            if (!subtitle.isNullOrBlank() && subtitle != title) {
                contentBuilder.setSubtitle(subtitle)
            }

            InlinePresentation(contentBuilder.build().slice, spec, isPinned)
        } catch (_: Exception) {
            null
        }
    }
}

