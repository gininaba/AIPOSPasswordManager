package com.aipos.aipospm

import com.aipos.aipospm.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalActivityAutoLockSuppressionTest {

    @Test
    fun testDefaultStateDoesNotSuppressAutoLock() {
        // When no external activity was requested, auto-lock is not suppressed
        MainActivity.setExpectingExternalActivity(false)
        assertFalse(MainActivity.consumeExternalActivitySuppression())
    }

    @Test
    fun testExternalActivitySuppressionIsConsumedOnce() {
        // Requesting an external activity should suppress auto-lock exactly once
        MainActivity.setExpectingExternalActivity(true)
        assertTrue(MainActivity.consumeExternalActivitySuppression())

        // Consecutive call should return false because it was already consumed
        assertFalse(MainActivity.consumeExternalActivitySuppression())
    }

    @Test
    fun testExternalActivitySuppressionCanBeExplicitlyCancelled() {
        MainActivity.setExpectingExternalActivity(true)
        MainActivity.setExpectingExternalActivity(false)
        assertFalse(MainActivity.consumeExternalActivitySuppression())
    }

    @Test
    fun testLastBackgroundTimeTrackingAndClearing() {
        val now = System.currentTimeMillis()
        MainActivity.setLastBackgroundTime(now)
        org.junit.Assert.assertEquals(now, MainActivity.getLastBackgroundTime())

        MainActivity.setLastBackgroundTime(0L)
        org.junit.Assert.assertEquals(0L, MainActivity.getLastBackgroundTime())
    }
}
