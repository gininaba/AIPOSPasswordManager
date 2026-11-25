package com.aipos.aipospm.ui.components

import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Wraps Compose content so every text field requests an incognito keyboard
 * (EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING on Android 8.0+ / API 26+).
 *
 * Ensures keyboards such as HeliBoard, AOSP Keyboard, Gboard, and FlorisBoard
 * do not learn, cache, or suggest master passwords, vault secrets, notes, or TOTP keys.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoInputContainer(
    content: @Composable () -> Unit
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        content()
        return
    }
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val incognitoRequest = PlatformTextInputMethodRequest { outAttrs ->
                val connection = request.createInputConnection(outAttrs)
                outAttrs.imeOptions =
                    outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                connection
            }
            nextHandler.startInputMethod(incognitoRequest)
        },
        content = content
    )
}

