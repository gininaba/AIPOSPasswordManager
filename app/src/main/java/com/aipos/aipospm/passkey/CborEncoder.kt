package com.aipos.aipospm.passkey

import java.io.ByteArrayOutputStream

/**
 * Deterministic RFC 8949 CBOR encoder tailored for WebAuthn COSE_Key (ES256 P-256)
 * and "none" attestationObject serialization. Zero third-party dependencies.
 */
object CborEncoder {

    private const val MAJOR_UNSIGNED_INT = 0
    private const val MAJOR_NEGATIVE_INT = 1
    private const val MAJOR_BYTE_STRING = 2
    private const val MAJOR_TEXT_STRING = 3
    private const val MAJOR_MAP = 5

    /**
     * Encodes an EC2 P-256 public key as a canonical COSE_Key map (RFC 8152 / CTAP2):
     * { 1: 2 (EC2), 3: -7 (ES256), -1: 1 (P-256), -2: x (32 bytes), -3: y (32 bytes) }
     */
    fun encodeCoseEc2PublicKey(x: ByteArray, y: ByteArray): ByteArray {
        require(x.size == 32 && y.size == 32) { "P-256 coordinates must be exactly 32 bytes each" }
        val out = ByteArrayOutputStream()
        writeHeader(out, MAJOR_MAP, 5L)

        // 1: 2 (kty = EC2)
        writeInt(out, 1L)
        writeInt(out, 2L)

        // 3: -7 (alg = ES256)
        writeInt(out, 3L)
        writeInt(out, -7L)

        // -1: 1 (crv = P-256)
        writeInt(out, -1L)
        writeInt(out, 1L)

        // -2: x (32-byte coordinate)
        writeInt(out, -2L)
        writeByteString(out, x)

        // -3: y (32-byte coordinate)
        writeInt(out, -3L)
        writeByteString(out, y)

        return out.toByteArray()
    }

    /**
     * Encodes a WebAuthn "none" attestationObject:
     * { "fmt": "none", "attStmt": {}, "authData": <bytes> }
     * Keys follow canonical CBOR ordering by encoded byte length then lexicographical order:
     * "fmt" (3), "attStmt" (7), "authData" (8).
     */
    fun encodeNoneAttestationObject(authData: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        writeHeader(out, MAJOR_MAP, 3L)

        writeTextString(out, "fmt")
        writeTextString(out, "none")

        writeTextString(out, "attStmt")
        writeHeader(out, MAJOR_MAP, 0L)

        writeTextString(out, "authData")
        writeByteString(out, authData)

        return out.toByteArray()
    }

    internal fun writeInt(out: ByteArrayOutputStream, value: Long) {
        if (value >= 0L) {
            writeHeader(out, MAJOR_UNSIGNED_INT, value)
        } else {
            writeHeader(out, MAJOR_NEGATIVE_INT, -1L - value)
        }
    }

    internal fun writeByteString(out: ByteArrayOutputStream, bytes: ByteArray) {
        writeHeader(out, MAJOR_BYTE_STRING, bytes.size.toLong())
        out.write(bytes)
    }

    internal fun writeTextString(out: ByteArrayOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeHeader(out, MAJOR_TEXT_STRING, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeHeader(out: ByteArrayOutputStream, majorType: Int, value: Long) {
        val prefix = majorType shl 5
        when {
            value < 24L -> out.write(prefix or value.toInt())
            value <= 0xFFL -> {
                out.write(prefix or 24)
                out.write(value.toInt() and 0xFF)
            }
            value <= 0xFFFFL -> {
                out.write(prefix or 25)
                out.write(((value shr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
            value <= 0xFFFF_FFFFL -> {
                out.write(prefix or 26)
                out.write(((value shr 24) and 0xFF).toInt())
                out.write(((value shr 16) and 0xFF).toInt())
                out.write(((value shr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
            else -> {
                out.write(prefix or 27)
                for (shift in 56 downTo 0 step 8) {
                    out.write(((value shr shift) and 0xFF).toInt())
                }
            }
        }
    }
}

