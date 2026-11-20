package com.aipos.aipospm.autofill

import android.content.Context
import android.widget.Toast
import com.aipos.aipospm.data.PasswordEntry
import com.aipos.aipospm.security.ClipboardHelper
import com.aipos.aipospm.security.CryptoManager
import com.aipos.aipospm.security.TotpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Automatically copies a 6-digit RFC 6238 TOTP code to the system clipboard
 * (with sensitive marking and 30-second auto-clear) whenever a filled entry
 * has 2FA TOTP configured.
 */
object AutofillTotpNotifier {

    suspend fun copyTotpIfConfigured(
        context: Context,
        entry: PasswordEntry,
        cryptoManager: CryptoManager = CryptoManager.shared
    ): Boolean {
        val encSecret = entry.encryptedTotpSecret
        val totpIv = entry.totpIv
        if (encSecret.isNullOrBlank() || totpIv.isNullOrBlank()) return false

        val code = withContext(Dispatchers.Default) {
            try {
                val secret = cryptoManager.decrypt(encSecret, totpIv)
                TotpHelper.generateCode(secret)
            } catch (_: Exception) {
                null
            }
        } ?: return false

        val appContext = context.applicationContext ?: context
        withContext(Dispatchers.Main) {
            ClipboardHelper.copyAndScheduleClear(
                context = appContext,
                label = "AIPOS 2FA Code",
                text = code
            )
            Toast.makeText(
                appContext,
                "2FA code copied to clipboard",
                Toast.LENGTH_SHORT
            ).show()
        }
        return true
    }
}

