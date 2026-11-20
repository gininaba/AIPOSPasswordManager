package com.aipos.aipospm

import com.aipos.aipospm.autofill.PendingAutofillSaveStore
import com.aipos.aipospm.autofill.PendingSavePayload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PendingAutofillSaveStoreTest {

    private var fakeNow = 10_000L

    @Before
    fun setUp() {
        PendingAutofillSaveStore.clearAll()
        PendingAutofillSaveStore.clockMillis = { fakeNow }
    }

    @After
    fun tearDown() {
        PendingAutofillSaveStore.clearAll()
    }

    @Test
    fun testPutPeekAndOneTimeConsume() {
        val payload = PendingSavePayload(
            username = "alice@example.com",
            password = "SecretPassword!42",
            webDomain = "example.com",
            packageName = "com.android.chrome"
        )

        val token = PendingAutofillSaveStore.put(payload)

        // Peek should return payload without deleting it
        val peeked = PendingAutofillSaveStore.peek(token)
        assertNotNull(peeked)
        assertEquals("alice@example.com", peeked!!.username)

        // Consume should return payload once, then wipe it from memory
        val consumed = PendingAutofillSaveStore.consume(token)
        assertNotNull(consumed)
        assertEquals("SecretPassword!42", consumed!!.password)
        assertNull(PendingAutofillSaveStore.peek(token))
        assertNull(PendingAutofillSaveStore.consume(token))
    }

    @Test
    fun testExpiredPayloadIsPurgedAutomatically() {
        val payload = PendingSavePayload(
            username = "bob@example.com",
            password = "TempPassword!99",
            webDomain = "example.org",
            packageName = null
        )

        val token = PendingAutofillSaveStore.put(payload, ttlMillis = 5_000L)

        // Advance clock within TTL
        fakeNow += 4_000L
        assertNotNull(PendingAutofillSaveStore.peek(token))

        // Advance clock past TTL
        fakeNow += 2_000L
        assertNull(PendingAutofillSaveStore.peek(token))
        assertNull(PendingAutofillSaveStore.consume(token))
    }
}

