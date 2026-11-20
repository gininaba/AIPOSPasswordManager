package com.aipos.aipospm.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.service.autofill.FillContext
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Result data class containing target AutofillIds, current text values,
 * sign-up heuristics, and context identifiers parsed from an Android AssistStructure.
 */
data class ParsedAutofillStructure(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val confirmPasswordId: AutofillId? = null,
    val webDomain: String?,
    val packageName: String?,
    val isNewPasswordFlow: Boolean = false,
    val focusedId: AutofillId? = null,
    val usernameText: String? = null,
    val passwordText: String? = null
) {
    val hasTargetFields: Boolean
        get() = usernameId != null || passwordId != null
}

/**
 * Aggregated credentials and origin context extracted across one or more
 * FillContexts (supports both single-screen and two-step login flows).
 */
data class ParsedSaveContext(
    val username: String,
    val password: String,
    val webDomain: String?,
    val packageName: String?
)

/**
 * Heuristic parser for Android AssistStructure hierarchies.
 * Detects username, email, password, and confirm-password fields across native apps
 * and WebViews, taking into account focused fields, proximity, HTML attributes,
 * new-password/sign-up hints, and multi-step login histories.
 */
@RequiresApi(Build.VERSION_CODES.O)
object AutofillStructureParser {

    val TRUSTED_BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "org.mozilla.klar",
        "io.github.forkmaintainers.iceraven",
        "us.spotco.fennec_dos",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.vivaldi.browser",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser",
        "com.kiwibrowser.browser",
        "org.cromite.cromite",
        "app.vanadium.browser",
        "org.torproject.torbrowser"
    )

    private data class CandidateNode(
        val autofillId: AutofillId,
        val isFocused: Boolean,
        val isPassword: Boolean,
        val isNewPassword: Boolean,
        val isConfirmPassword: Boolean,
        val isUsername: Boolean,
        val isEditableText: Boolean,
        val currentText: String?
    )

    fun parse(structure: AssistStructure): ParsedAutofillStructure {
        var foundWebDomain: String? = null
        val packageName = structure.activityComponent?.packageName
        val allowWebDomain = packageName == null || packageName in TRUSTED_BROWSER_PACKAGES
        val candidates = mutableListOf<CandidateNode>()

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val root = windowNode.rootViewNode ?: continue

            traverseNode(root) { node ->
                if (allowWebDomain && foundWebDomain.isNullOrBlank() && !node.webDomain.isNullOrBlank()) {
                    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        node.webScheme?.lowercase(Locale.ROOT)
                    } else null
                    if (scheme == null || scheme == "https" || scheme == "http") {
                        foundWebDomain = node.webDomain
                    }
                }

                val autofillId = node.autofillId ?: return@traverseNode
                val isPwd = isPasswordField(node)
                val isNewPwd = if (isPwd) isNewPasswordField(node) else false
                val isConfirmPwd = if (isPwd) isConfirmPasswordField(node) else false
                val isUser = if (isPwd) false else isUsernameField(node)
                val isEditable = isEditableTextField(node)

                if (isPwd || isUser || isEditable) {
                    candidates.add(
                        CandidateNode(
                            autofillId = autofillId,
                            isFocused = node.isFocused,
                            isPassword = isPwd,
                            isNewPassword = isNewPwd,
                            isConfirmPassword = isConfirmPwd,
                            isUsername = isUser,
                            isEditableText = isEditable,
                            currentText = extractNodeText(node)
                        )
                    )
                }
            }
        }

        var targetUsername: CandidateNode? = null
        var targetPassword: CandidateNode? = null
        var confirmPassword: CandidateNode? = null

        val focusedCandidate = candidates.firstOrNull { it.isFocused }
        val passwordCandidates = candidates.filter { it.isPassword }

        if (focusedCandidate != null) {
            if (focusedCandidate.isPassword) {
                targetPassword = focusedCandidate
                val idx = candidates.indexOf(focusedCandidate)
                targetUsername = candidates.take(idx).lastOrNull { it.isUsername }
                    ?: candidates.take(idx).lastOrNull { it.isEditableText && !it.isPassword }

                // Pair with a sibling confirmation password field if present
                confirmPassword = candidates.drop(idx + 1).firstOrNull { it.isPassword }
                    ?: if (focusedCandidate.isConfirmPassword) {
                        candidates.take(idx).lastOrNull { it.isPassword }
                    } else null
            } else if (focusedCandidate.isUsername || focusedCandidate.isEditableText) {
                targetUsername = focusedCandidate
                val idx = candidates.indexOf(focusedCandidate)
                val followingPasswords = candidates.drop(idx + 1).filter { it.isPassword }
                targetPassword = followingPasswords.firstOrNull() ?: passwordCandidates.firstOrNull()
                if (followingPasswords.size >= 2) {
                    confirmPassword = followingPasswords[1]
                } else if (passwordCandidates.size >= 2) {
                    confirmPassword = passwordCandidates.firstOrNull { it.autofillId != targetPassword?.autofillId }
                }
            }
        } else {
            targetPassword = passwordCandidates.firstOrNull()
            if (passwordCandidates.size >= 2) {
                // On change-password forms (current + new + confirm = 3 fields), prefer the new + confirm pair
                if (passwordCandidates.size >= 3) {
                    targetPassword = passwordCandidates[1]
                    confirmPassword = passwordCandidates[2]
                } else {
                    confirmPassword = passwordCandidates[1]
                }
            }
            targetUsername = candidates.firstOrNull { it.isUsername }

            if (targetPassword != null && targetUsername == null) {
                val pwdIdx = candidates.indexOf(targetPassword)
                if (pwdIdx > 0) {
                    targetUsername = candidates.take(pwdIdx).lastOrNull { it.isEditableText && !it.isPassword }
                }
            }
        }

        val isNewPasswordFlow =
            (targetPassword?.isNewPassword == true) ||
                (confirmPassword != null) ||
                passwordCandidates.any { it.isNewPassword || it.isConfirmPassword }

        return ParsedAutofillStructure(
            usernameId = targetUsername?.autofillId,
            passwordId = targetPassword?.autofillId,
            confirmPasswordId = confirmPassword?.autofillId?.takeIf { it != targetPassword?.autofillId },
            webDomain = foundWebDomain,
            packageName = packageName,
            isNewPasswordFlow = isNewPasswordFlow,
            focusedId = focusedCandidate?.autofillId,
            usernameText = targetUsername?.currentText?.trim()?.takeIf { it.isNotEmpty() },
            passwordText = targetPassword?.currentText?.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Merges one or more FillContexts (newest to oldest) to extract the submitted
     * username, password, webDomain, and packageName during an onSaveRequest.
     * Supports two-step login flows where username was entered on step 1 and password on step 2.
     */
    fun parseSaveContext(fillContexts: List<FillContext>): ParsedSaveContext? {
        if (fillContexts.isEmpty()) return null
        val parsedSteps = fillContexts.asReversed().map { parse(it.structure) }
        return mergeSaveSteps(parsedSteps)
    }

    /**
     * Pure Kotlin merge helper (unit-testable without framework AssistStructure instances).
     * Expects steps ordered newest-first.
     */
    internal fun mergeSaveSteps(newestFirstSteps: List<ParsedAutofillStructure>): ParsedSaveContext? {
        if (newestFirstSteps.isEmpty()) return null

        val password = newestFirstSteps.firstNotNullOfOrNull { it.passwordText?.takeIf { p -> p.isNotEmpty() } }
            ?: return null
        val username = newestFirstSteps.firstNotNullOfOrNull { it.usernameText?.trim()?.takeIf { u -> u.isNotEmpty() } }
            .orEmpty()
        val webDomain = newestFirstSteps.firstNotNullOfOrNull { it.webDomain?.takeIf { d -> d.isNotBlank() } }
        val packageName = newestFirstSteps.firstNotNullOfOrNull { it.packageName?.takeIf { p -> p.isNotBlank() } }

        return ParsedSaveContext(
            username = username,
            password = password,
            webDomain = webDomain,
            packageName = packageName
        )
    }

    private fun extractNodeText(node: AssistStructure.ViewNode): String? {
        val autofillText = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
        if (!autofillText.isNullOrEmpty()) return autofillText
        return node.text?.toString()?.takeIf { it.isNotEmpty() }
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
        if (hints != null) {
            for (hint in hints) {
                if (hint.equals(View.AUTOFILL_HINT_PASSWORD, ignoreCase = true) ||
                    hint.contains("password", ignoreCase = true)
                ) {
                    return true
                }
            }
        }

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordVariation = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        if (isPasswordVariation) return true

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

    fun isNewPasswordField(node: AssistStructure.ViewNode): Boolean {
        val htmlInfo = node.htmlInfo
        if (htmlInfo != null) {
            val tag = htmlInfo.tag?.lowercase(Locale.ROOT) ?: ""
            if (tag == "input") {
                val attributes = htmlInfo.attributes ?: emptyList()
                for (attr in attributes) {
                    val key = attr.first.lowercase(Locale.ROOT)
                    val value = attr.second.lowercase(Locale.ROOT)
                    if (key == "autocomplete" && value.contains("new-password")) return true
                    if ((key == "name" || key == "id") && isNewPasswordKeyword(value)) return true
                }
            }
        }

        return isNewPasswordField(
            hints = node.autofillHints,
            idEntry = node.idEntry,
            hintText = node.hint?.toString()
        )
    }

    fun isNewPasswordField(
        hints: Array<String>?,
        idEntry: String?,
        hintText: String?
    ): Boolean {
        if (hints != null) {
            for (hint in hints) {
                val normalized = hint.lowercase(Locale.ROOT)
                if (normalized == "newpassword" || normalized == "new-password" || normalized.contains("new_password")) {
                    return true
                }
            }
        }
        val cleanId = idEntry?.lowercase(Locale.ROOT) ?: ""
        val cleanHint = hintText?.lowercase(Locale.ROOT) ?: ""
        return isNewPasswordKeyword(cleanId) || isNewPasswordKeyword(cleanHint)
    }

    fun isConfirmPasswordField(node: AssistStructure.ViewNode): Boolean {
        val htmlInfo = node.htmlInfo
        if (htmlInfo != null) {
            val tag = htmlInfo.tag?.lowercase(Locale.ROOT) ?: ""
            if (tag == "input") {
                val attributes = htmlInfo.attributes ?: emptyList()
                for (attr in attributes) {
                    val key = attr.first.lowercase(Locale.ROOT)
                    val value = attr.second.lowercase(Locale.ROOT)
                    if ((key == "name" || key == "id" || key == "placeholder") && isConfirmPasswordKeyword(value)) {
                        return true
                    }
                }
            }
        }
        return isConfirmPasswordField(node.idEntry, node.hint?.toString())
    }

    fun isConfirmPasswordField(idEntry: String?, hintText: String?): Boolean {
        val cleanId = idEntry?.lowercase(Locale.ROOT) ?: ""
        val cleanHint = hintText?.lowercase(Locale.ROOT) ?: ""
        return isConfirmPasswordKeyword(cleanId) || isConfirmPasswordKeyword(cleanHint)
    }

    private fun isNewPasswordKeyword(value: String): Boolean {
        if (value.isBlank()) return false
        return value.contains("new_password") ||
            value.contains("new-password") ||
            value.contains("newpassword") ||
            value.contains("new password") ||
            value.contains("create_password") ||
            value.contains("create password") ||
            value.contains("choose password") ||
            value.contains("signup_password") ||
            value.contains("register_password")
    }

    private fun isConfirmPasswordKeyword(value: String): Boolean {
        if (value.isBlank()) return false
        return value.contains("confirm_password") ||
            value.contains("confirm-password") ||
            value.contains("confirmpassword") ||
            value.contains("confirm password") ||
            value.contains("repeat_password") ||
            value.contains("repeat password") ||
            value.contains("retype") ||
            value.contains("verify password")
    }

    fun isUsernameField(node: AssistStructure.ViewNode): Boolean {
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
        if (isPasswordField(hints, inputType, idEntry, hintText)) return false

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

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isEmailVariation = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        if (isEmailVariation) return true

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
