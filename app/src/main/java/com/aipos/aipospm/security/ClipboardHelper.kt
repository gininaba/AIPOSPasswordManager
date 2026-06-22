package com.aipos.aipospm.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface ClipboardDelegate {
    fun setPrimaryClip(label: String, text: String)
    fun getPrimaryClipText(): String?
    fun clearPrimaryClip()
}

class SystemClipboardDelegate(private val context: Context) : ClipboardDelegate {
    private val clipboard: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun setPrimaryClip(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    override fun getPrimaryClipText(): String? {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).text?.toString()
        }
        return null
    }

    override fun clearPrimaryClip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

object ClipboardHelper {
    // Thread safety is managed using Dispatchers.Main
    private var scope = CoroutineScope(Dispatchers.Main)
    private var currentJob: kotlinx.coroutines.Job? = null
    
    // Exposed setter to override the coroutine scope in unit tests
    fun setCoroutineScope(newScope: CoroutineScope) {
        scope = newScope
    }

    // Settable delegate for testing
    var testDelegate: ClipboardDelegate? = null

    // Settable delay for testing
    var clearDelayMillis: Long = 30_000

    private fun getDelegate(context: Context): ClipboardDelegate {
        return testDelegate ?: SystemClipboardDelegate(context)
    }

    fun copyAndScheduleClear(context: Context, label: String, text: String, onCleared: () -> Unit = {}) {
        val delegate = getDelegate(context)
        delegate.setPrimaryClip(label, text)

        currentJob?.cancel()
        currentJob = scope.launch {
            delay(clearDelayMillis)
            val currentText = delegate.getPrimaryClipText()
            if (currentText == text) {
                delegate.clearPrimaryClip()
                onCleared()
            }
        }
    }
}
