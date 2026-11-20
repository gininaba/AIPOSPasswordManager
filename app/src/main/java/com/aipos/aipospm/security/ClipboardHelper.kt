package com.aipos.aipospm.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface ClipboardDelegate {
    fun setPrimaryClip(label: String, text: String)
    fun getPrimaryClipText(): String?
    fun clearPrimaryClip()
    fun getPrimaryClipTimestamp(): Long? = null
    fun isBackgroundReadRestricted(): Boolean = false
}

class SystemClipboardDelegate(context: Context) : ClipboardDelegate {
    private val appContext = context.applicationContext ?: context
    private val clipboard: ClipboardManager? by lazy {
        try {
            appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        } catch (_: Exception) {
            null
        }
    }

    override fun setPrimaryClip(label: String, text: String) {
        try {
            val clip = ClipData.newPlainText(label, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard?.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.w("ClipboardHelper", "Failed to set primary clip", e)
        }
    }

    override fun getPrimaryClipText(): String? {
        return try {
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    override fun getPrimaryClipTimestamp(): Long? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                clipboard?.primaryClipDescription?.timestamp
            } else null
        } catch (_: Exception) {
            null
        }
    }

    override fun isBackgroundReadRestricted(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    override fun clearPrimaryClip() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard?.clearPrimaryClip()
            } else {
                clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (e: Exception) {
            Log.w("ClipboardHelper", "Failed to clear primary clip", e)
        }
    }
}

object ClipboardHelper {
    // Thread safety is managed using Dispatchers.Main with SupervisorJob so one failure cannot cancel future clears
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
        return testDelegate ?: SystemClipboardDelegate(context.applicationContext ?: context)
    }

    fun copyAndScheduleClear(context: Context, label: String, text: String, onCleared: () -> Unit = {}) {
        val delegate = getDelegate(context)
        delegate.setPrimaryClip(label, text)
        val copiedTimestamp = delegate.getPrimaryClipTimestamp()

        currentJob?.cancel()
        currentJob = scope.launch {
            delay(clearDelayMillis)
            val currentText = delegate.getPrimaryClipText()
            val shouldClear = when {
                currentText != null -> currentText == text
                delegate.isBackgroundReadRestricted() -> {
                    // On Android 10+, background apps receive null from primaryClip.
                    // Clear unless the OS timestamp proves the clipboard was overwritten.
                    val currentTimestamp = delegate.getPrimaryClipTimestamp()
                    currentTimestamp == null || copiedTimestamp == null || currentTimestamp == copiedTimestamp
                }
                else -> false
            }
            if (shouldClear) {
                delegate.clearPrimaryClip()
                onCleared()
            }
        }
    }
}
