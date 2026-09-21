package com.aipos.aipospm.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Result data class containing target AutofillIds and context identifiers
 * parsed from an Android AssistStructure.
 */
data class ParsedAutofillStructure(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val webDomain: String?,
    val packageName: String?
) {
    val hasTargetFields: Boolean
        get() = usernameId != null || passwordId != null
}

/**
 * Heuristic parser for Android AssistStructure hierarchies.
 * Detects username, email, and password fields across native apps and WebViews,
 * taking into account focused fields, proximity, HTML attributes, and input variations.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AutofillStructureParser {

    private data class CandidateNode(
        val autofillId: AutofillId,
        val isFocused: Boolean,
        val isPassword: Boolean,
        val isUsername: Boolean,
        val isEditableText: Boolean
    )

    fun parse(structure: AssistStructure): ParsedAutofillStructure {
        var foundWebDomain: String? = null
        val packageName = structure.activityComponent?.packageName
        val candidates = mutableListOf<CandidateNode>()

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val root = windowNode.rootViewNode ?: continue

            traverseNode(root) { node ->
                if (foundWebDomain.isNullOrBlank() && !node.webDomain.isNullOrBlank()) {
                    foundWebDomain = node.webDomain
                }

                val autofillId = node.autofillId ?: return@traverseNode
                val isPwd = isPasswordField(node)
                val isUser = if (isPwd) false else isUsernameField(node)
                val isEditable = isEditableTextField(node)

                if (isPwd || isUser || isEditable) {
                    candidates.add(
                        CandidateNode(
                            autofillId = autofillId,
                            isFocused = node.isFocused,
                            isPassword = isPwd,
                            isUsername = isUser,
                            isEditableText = isEditable
                        )
                    )
                }
            }
        }

        var targetUsernameId: AutofillId? = null
        var targetPasswordId: AutofillId? = null

        val focusedCandidate = candidates.firstOrNull { it.isFocused }

        if (focusedCandidate != null) {
            if (focusedCandidate.isPassword) {
                // User focused a password field
                targetPasswordId = focusedCandidate.autofillId
                val idx = candidates.indexOf(focusedCandidate)
                // Look for preceding username or editable field
                targetUsernameId = candidates.take(idx).lastOrNull { it.isUsername }?.autofillId
                    ?: candidates.take(idx).lastOrNull { it.isEditableText && !it.isPassword }?.autofillId
            } else if (focusedCandidate.isUsername || focusedCandidate.isEditableText) {
                // User focused a username or text field
                targetUsernameId = focusedCandidate.autofillId
                val idx = candidates.indexOf(focusedCandidate)
                // Look for following password field, or any password field
                targetPasswordId = candidates.drop(idx + 1).firstOrNull { it.isPassword }?.autofillId
                    ?: candidates.firstOrNull { it.isPassword }?.autofillId
            }
        } else {
            // No node explicitly has isFocused=true (e.g. initial window fill)
            targetPasswordId = candidates.firstOrNull { it.isPassword }?.autofillId
            targetUsernameId = candidates.firstOrNull { it.isUsername }?.autofillId

            if (targetPasswordId != null && targetUsernameId == null) {
                // If password exists but no explicit username, pick closest preceding editable text
                val pwdIdx = candidates.indexOfFirst { it.isPassword }
                targetUsernameId = candidates.take(pwdIdx).lastOrNull { it.isEditableText && !it.isPassword }?.autofillId
            }
        }

        return ParsedAutofillStructure(
            usernameId = targetUsernameId,
            passwordId = targetPasswordId,
            webDomain = foundWebDomain,
            packageName = packageName
        )
    }

    private fun traverseNode(node: AssistStructure.ViewNode, action: (AssistStructure.ViewNode) -> Unit) {
        action(node)
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) ?: continue
            traverseNode(child, action)
        }
    }

    fun isEditableTextField(node: AssistStructure.ViewNode): Boolean {
        val className = node.className?.lowercase(Locale.ROOT) ?: ""
        if (className.contains("edittext") || className.contains("textinputedittext")) {
            return true
        }

        val inputType = node.inputType
        if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
            return true
        }

        val htmlInfo = node.htmlInfo
        if (htmlInfo != null) {
            val tag = htmlInfo.tag?.lowercase(Locale.ROOT) ?: ""
            if (tag == "input") {
                val typeAttr = htmlInfo.attributes?.firstOrNull { it.first.equals("type", ignoreCase = true) }?.second?.lowercase(Locale.ROOT) ?: "text"
                if (typeAttr != "hidden" && typeAttr != "submit" && typeAttr != "button" && typeAttr != "checkbox" && typeAttr != "radio") {
                    return true
                }
            }
        }

        return false
    }

    fun isPasswordField(node: AssistStructure.ViewNode): Boolean {
        // Check HTML attributes for WebViews / Chrome
        val htmlInfo = node.htmlInfo
        if (htmlInfo != null) {
            val tag = htmlInfo.tag?.lowercase(Locale.ROOT) ?: ""
            if (tag == "input") {
                val attributes = htmlInfo.attributes ?: emptyList()
                for (attr in attributes) {
                    val key = attr.first.lowercase(Locale.ROOT)
                    val value = attr.second.lowercase(Locale.ROOT)

                    if (key == "type" && value == "password") return true
                    if (key == "autocomplete" && (value.contains("password") || value == "current-password" || value == "new-password")) return true
                    if ((key == "name" || key == "id") && (value.contains("password") || value.contains("passwd") || value == "pwd")) return true
                }
            }
        }

        return isPasswordField(
            hints = node.autofillHints,
            inputType = node.inputType,
            idEntry = node.idEntry,
            hintText = node.hint?.toString()
        )
    }

    fun isPasswordField(
        hints: Array<String>?,
        inputType: Int,
        idEntry: String?,
        hintText: String?
    ): Boolean {
        // 1. Check autofill hints
        if (hints != null) {
            for (hint in hints) {
                if (hint.equals(View.AUTOFILL_HINT_PASSWORD, ignoreCase = true) ||
                    hint.contains("password", ignoreCase = true)
                ) {
                    return true
                }
            }
        }

        // 2. Check input type
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordVariation = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        if (isPasswordVariation) return true

        // 3. Check ID and hint heuristics
        val cleanId = idEntry?.lowercase(Locale.ROOT) ?: ""
        val cleanHint = hintText?.lowercase(Locale.ROOT) ?: ""

        if (cleanId.contains("password") || cleanId.contains("passwd") || cleanId.contains("pwd") || cleanId.contains("passcode")) {
            return true
        }

        if (cleanHint.contains("password") || cleanHint.contains("passcode") || cleanHint.contains("pwd") || cleanHint.contains("contraseña") || cleanHint.contains("passwort")) {
            return true
        }

        return false
    }

    fun isUsernameField(node: AssistStructure.ViewNode): Boolean {
        // Check HTML attributes for WebViews / Chrome
        val htmlInfo = node.htmlInfo
        if (htmlInfo != null) {
            val tag = htmlInfo.tag?.lowercase(Locale.ROOT) ?: ""
            if (tag == "input") {
                val attributes = htmlInfo.attributes ?: emptyList()
                for (attr in attributes) {
                    val key = attr.first.lowercase(Locale.ROOT)
                    val value = attr.second.lowercase(Locale.ROOT)

                    if (key == "type" && value == "email") return true
                    if (key == "autocomplete" && (value == "username" || value == "email" || value.contains("username") || value.contains("email"))) return true
                    if ((key == "name" || key == "id") && (value.contains("user") || value.contains("email") || value.contains("login") || value.contains("account"))) {
                        if (!value.contains("pass") && !value.contains("pwd")) return true
                    }
                }
            }
        }

        return isUsernameField(
            hints = node.autofillHints,
            inputType = node.inputType,
            idEntry = node.idEntry,
            hintText = node.hint?.toString()
        )
    }

    fun isUsernameField(
        hints: Array<String>?,
        inputType: Int,
        idEntry: String?,
        hintText: String?
    ): Boolean {
        // If it's a password field, it cannot be a username field
        if (isPasswordField(hints, inputType, idEntry, hintText)) return false

        // 1. Check autofill hints
        if (hints != null) {
            for (hint in hints) {
                if (hint.equals(View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                    hint.equals(View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true) ||
                    hint.equals(View.AUTOFILL_HINT_PHONE, ignoreCase = true)
                ) {
                    return true
                }
            }
        }

        // 2. Check input type
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isEmailVariation = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        if (isEmailVariation) return true

        // 3. Check ID and hint heuristics
        val cleanId = idEntry?.lowercase(Locale.ROOT) ?: ""
        val cleanHint = hintText?.lowercase(Locale.ROOT) ?: ""

        val usernameKeywords = listOf("username", "user", "email", "login", "account", "identifier", "handle", "phone", "usuario", "nutzer", "identifiant")
        if (usernameKeywords.any { cleanId.contains(it) }) {
            return true
        }

        if (usernameKeywords.any { cleanHint.contains(it) }) {
            return true
        }

        return false
    }
}
