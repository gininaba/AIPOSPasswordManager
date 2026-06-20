package com.aipos.aipospm.security

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates RFC 6238 TOTP codes locally using HMAC-SHA1.
 *
 * This implementation is entirely offline — it uses the device clock
 * and a user-provided Base32 secret. No network calls are made.
 */
object TotpHelper {

    private const val TOTP_PERIOD_SECONDS = 30
    private const val TOTP_DIGITS = 6
    private const val HMAC_ALGORITHM = "HmacSHA1"

    // Base32 alphabet (RFC 4648)
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Generates the current 6-digit TOTP code for the given Base32 secret.
     *
     * @param base32Secret The Base32-encoded secret key (spaces/hyphens are stripped).
     * @param timeMillis  The current time in milliseconds (defaults to system clock).
     * @return The 6-digit OTP code as a zero-padded string, or null if the secret is invalid.
     */
    fun generateCode(base32Secret: String, timeMillis: Long = System.currentTimeMillis()): String? {
        val keyBytes = decodeBase32(base32Secret) ?: return null
        if (keyBytes.isEmpty()) return null

        val counter = timeMillis / 1000 / TOTP_PERIOD_SECONDS
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()

        val hash = hmacSha1(keyBytes, counterBytes) ?: return null
        val code = dynamicTruncate(hash)

        return code.toString().padStart(TOTP_DIGITS, '0')
    }

    /**
     * Returns the number of seconds remaining in the current TOTP period.
     */
    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis()): Int {
        val elapsed = (timeMillis / 1000) % TOTP_PERIOD_SECONDS
        return (TOTP_PERIOD_SECONDS - elapsed).toInt()
    }

    /**
     * Returns the TOTP period in seconds (always 30).
     */
    fun periodSeconds(): Int = TOTP_PERIOD_SECONDS

    /**
     * Validates whether a string is a well-formed Base32 secret.
     *
     * @return true if the sanitized input contains only valid Base32 characters
     *         and decodes to at least 1 byte.
     */
    fun isValidBase32(input: String): Boolean {
        val sanitized = sanitize(input)
        if (sanitized.isEmpty()) return false
        return sanitized.all { it in BASE32_CHARS } && (decodeBase32(input)?.isNotEmpty() == true)
    }

    // ── Internal ─────────────────────────────────────────────────────

    /**
     * Decodes a Base32-encoded string into raw bytes.
     * Strips whitespace, hyphens, and '=' padding. Case-insensitive.
     */
    internal fun decodeBase32(input: String): ByteArray? {
        val sanitized = sanitize(input)
        if (sanitized.isEmpty()) return null

        var bits = 0
        var value = 0
        val output = mutableListOf<Byte>()

        for (char in sanitized) {
            val index = BASE32_CHARS.indexOf(char)
            if (index == -1) return null // Invalid character

            value = (value shl 5) or index
            bits += 5

            if (bits >= 8) {
                bits -= 8
                output.add(((value shr bits) and 0xFF).toByte())
            }
        }

        return output.toByteArray()
    }

    private fun sanitize(input: String): String {
        return input
            .uppercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("=", "")
            .trim()
    }

    private fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray? {
        return try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
            mac.doFinal(data)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Performs RFC 4226 dynamic truncation on the HMAC hash
     * to produce a 6-digit integer code.
     */
    private fun dynamicTruncate(hash: ByteArray): Int {
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulus = Math.pow(10.0, TOTP_DIGITS.toDouble()).toInt()
        return binary % modulus
    }
}
