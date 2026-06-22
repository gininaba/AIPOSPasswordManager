package com.aipos.aipospm

import android.content.Context
import android.content.ContextWrapper
import com.aipos.aipospm.security.ClipboardDelegate
import com.aipos.aipospm.security.ClipboardHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClipboardHelperTest {

    private class MockClipboardDelegate : ClipboardDelegate {
        var label: String? = null
        var text: String? = null

        override fun setPrimaryClip(label: String, text: String) {
            this.label = label
            this.text = text
        }

        override fun getPrimaryClipText(): String? {
            return text
        }

        override fun clearPrimaryClip() {
            text = null
            label = null
        }
    }

    private lateinit var mockDelegate: MockClipboardDelegate
    private lateinit var dummyContext: Context

    @Before
    fun setUp() {
        mockDelegate = MockClipboardDelegate()
        // Instantiate concrete ContextWrapper without base context to avoid Mockito dependence
        dummyContext = ContextWrapper(null)
        ClipboardHelper.testDelegate = mockDelegate
        ClipboardHelper.clearDelayMillis = 50 // 50ms for fast testing
        ClipboardHelper.setCoroutineScope(CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun testCopySetsText() {
        ClipboardHelper.copyAndScheduleClear(dummyContext, "Label", "MyPassword")
        assertEquals("MyPassword", mockDelegate.getPrimaryClipText())
        assertEquals("Label", mockDelegate.label)
    }

    @Test
    fun testScheduledClearWipesPasswordAfterDelay() = runBlocking {
        var clearedCalled = false
        ClipboardHelper.copyAndScheduleClear(dummyContext, "Label", "MyPassword", onCleared = {
            clearedCalled = true
        })

        assertEquals("MyPassword", mockDelegate.getPrimaryClipText())

        // Wait for delay to complete
        delay(100)

        assertNull(mockDelegate.getPrimaryClipText())
        assertTrue(clearedCalled)
    }

    @Test
    fun testScheduledClearDoesNotWipeIfTextChanged() = runBlocking {
        var clearedCalled = false
        ClipboardHelper.copyAndScheduleClear(dummyContext, "Label", "MyPassword", onCleared = {
            clearedCalled = true
        })

        assertEquals("MyPassword", mockDelegate.getPrimaryClipText())

        // Simulate user copying another password in the meantime
        mockDelegate.text = "NewCopiedPassword"

        // Wait for delay to complete
        delay(100)

        // Clipboard should still contain the new password, not be cleared!
        assertEquals("NewCopiedPassword", mockDelegate.getPrimaryClipText())
        assertTrue(!clearedCalled)
    }
}
