package com.aipos.aipospm.autofill

import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Ephemeral payload handed off from AiposAutofillService.onSaveRequest to AutofillSaveActivity.
 * Kept strictly in process memory with automatic expiration so plaintext passwords are never
 * serialized into Intent extras, Bundles, or disk storage.
 */
data class PendingSavePayload(
    val username: String,
    val password: String,
    val webDomain: String?,
    val packageName: String?,
    val existingEntryId: Int? = null,
    val existingTitle: String? = null
)

object PendingAutofillSaveStore {

    internal const val DEFAULT_TTL_MILLIS = 120_000L // 2 minutes

    private data class StoredItem(
        val payload: PendingSavePayload,
        val expiresAtMillis: Long
    )

    private val entries = ConcurrentHashMap<String, StoredItem>()

    internal var clockMillis: () -> Long = { SystemClock.elapsedRealtime() }

    fun put(payload: PendingSavePayload, ttlMillis: Long = DEFAULT_TTL_MILLIS): String {
        val now = clockMillis()
        purgeExpired(now)
        val token = UUID.randomUUID().toString()
        entries[token] = StoredItem(
            payload = payload,
            expiresAtMillis = now + ttlMillis
        )
        return token
    }

    fun peek(token: String?): PendingSavePayload? {
        if (token.isNullOrBlank()) return null
        val now = clockMillis()
        purgeExpired(now)
        val item = entries[token] ?: return null
        return if (item.expiresAtMillis >= now) item.payload else {
            entries.remove(token)
            null
        }
    }

    fun consume(token: String?): PendingSavePayload? {
        if (token.isNullOrBlank()) return null
        val now = clockMillis()
        purgeExpired(now)
        val item = entries.remove(token) ?: return null
        return if (item.expiresAtMillis >= now) item.payload else null
    }

    fun clearAll() {
        entries.clear()
    }

    private fun purgeExpired(now: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next.value.expiresAtMillis < now) {
                iterator.remove()
            }
        }
    }
}

