package com.aipos.aipospm.passkey

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Pure JVM/Android WebAuthn ES256 (ECDSA P-256 / COSE alg -7) cryptographic helper
 * for 100% offline Android 14+ Passkey creation and assertion signing.
 */
object WebAuthnCrypto {

    const val COSE_ALG_ES256 = -7

    // AIPOS Password Manager deterministic AAGUID (16 bytes)
    // RFC 4122 lowercase UUID representation: 6169706f-732d-706d-4a10-8f3c52e79b01
    const val AIPOS_AAGUID_UUID = "6169706f-732d-706d-4a10-8f3c52e79b01"

    val AIPOS_AAGUID: ByteArray = byteArrayOf(
        0x61.toByte(), 0x69.toByte(), 0x70.toByte(), 0x6F.toByte(),
        0x73.toByte(), 0x2D.toByte(), 0x70.toByte(), 0x6D.toByte(),
        0x4A.toByte(), 0x10.toByte(), 0x8F.toByte(), 0x3C.toByte(),
        0x52.toByte(), 0xE7.toByte(), 0x9B.toByte(), 0x01.toByte()
    )

    fun formatAaguidUuid(aaguidBytes: ByteArray = AIPOS_AAGUID): String {
        require(aaguidBytes.size == 16) { "AAGUID must be 16 bytes" }
        val hex = aaguidBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    // Authenticator data flags (WebAuthn Level 3 §6.1)
    private const val FLAG_UP = 0x01 // User Present
    private const val FLAG_UV = 0x04 // User Verified (biometric / master password)
    private const val FLAG_BE = 0x08 // Backup Eligible
    private const val FLAG_BS = 0x10 // Backup State
    private const val FLAG_AT = 0x40 // Attested Credential Data included

    private val SECURE_RANDOM = SecureRandom()

    data class GeneratedPasskey(
        val credentialIdBytes: ByteArray,
        val credentialIdBase64Url: String,
        val pkcs8PrivateKeyBase64: String,
        val cosePublicKeyBytes: ByteArray,
        val cosePublicKeyBase64Url: String,
        val spkiPublicKeyBase64Url: String
    )

    /**
     * Generates a fresh ECDSA P-256 (secp256r1) keypair and random 32-byte credential ID.
     */
    fun generatePasskeyKeyPair(): GeneratedPasskey {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"), SECURE_RANDOM)
        val keyPair = keyPairGenerator.generateKeyPair()

        val ecPublicKey = keyPair.public as ECPublicKey
        val x = bigIntegerToFixed32Bytes(ecPublicKey.w.affineX)
        val y = bigIntegerToFixed32Bytes(ecPublicKey.w.affineY)
        val cosePublicKey = CborEncoder.encodeCoseEc2PublicKey(x, y)

        val credId = ByteArray(32).also { SECURE_RANDOM.nextBytes(it) }

        return GeneratedPasskey(
            credentialIdBytes = credId,
            credentialIdBase64Url = base64UrlEncode(credId),
            pkcs8PrivateKeyBase64 = base64UrlEncode(keyPair.private.encoded),
            cosePublicKeyBytes = cosePublicKey,
            cosePublicKeyBase64Url = base64UrlEncode(cosePublicKey),
            spkiPublicKeyBase64Url = base64UrlEncode(ecPublicKey.encoded)
        )
    }

    /**
     * Builds WebAuthn authenticatorData for credential creation (registration).
     */
    fun buildRegistrationAuthData(
        rpId: String,
        credentialId: ByteArray,
        cosePublicKey: ByteArray,
        signCount: Long = 0L
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(sha256(rpId.toByteArray(Charsets.UTF_8)))
        out.write(FLAG_UP or FLAG_UV or FLAG_BE or FLAG_BS or FLAG_AT)
        out.write(ByteBuffer.allocate(4).putInt(signCount.toInt()).array())
        out.write(AIPOS_AAGUID)
        out.write(ByteBuffer.allocate(2).putShort(credentialId.size.toShort()).array())
        out.write(credentialId)
        out.write(cosePublicKey)
        return out.toByteArray()
    }

    /**
     * Builds WebAuthn authenticatorData for credential assertion (sign-in).
     */
    fun buildAssertionAuthData(rpId: String, signCount: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(sha256(rpId.toByteArray(Charsets.UTF_8)))
        out.write(FLAG_UP or FLAG_UV or FLAG_BE or FLAG_BS)
        out.write(ByteBuffer.allocate(4).putInt(signCount.toInt()).array())
        return out.toByteArray()
    }

    /**
     * Builds WebAuthn clientDataJSON string for registration ("webauthn.create")
     * or authentication ("webauthn.get").
     */
    fun buildClientDataJson(
        type: String,
        challengeBase64Url: String,
        origin: String,
        packageName: String? = null
    ): ByteArray {
        val obj = JsonObject().apply {
            addProperty("type", type)
            addProperty("challenge", challengeBase64Url)
            addProperty("origin", origin)
            if (!packageName.isNullOrBlank()) {
                addProperty("androidPackageName", packageName)
            }
        }
        return obj.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Builds the full W3C WebAuthn PublicKeyCredential registration response JSON string.
     */
    fun createRegistrationResponseJson(
        generated: GeneratedPasskey,
        rpId: String,
        challengeBase64Url: String,
        origin: String,
        packageName: String? = null,
        providedClientDataHash: ByteArray? = null
    ): String {
        val authData = buildRegistrationAuthData(
            rpId = rpId,
            credentialId = generated.credentialIdBytes,
            cosePublicKey = generated.cosePublicKeyBytes,
            signCount = 0L
        )
        val attestationObject = CborEncoder.encodeNoneAttestationObject(authData)

        // When a privileged browser provides clientDataHash, clientDataJSON may still be populated
        // with standard fields for API completeness; RPs verify using the browser's clientDataJSON.
        val clientDataJson = buildClientDataJson(
            type = "webauthn.create",
            challengeBase64Url = challengeBase64Url,
            origin = origin,
            packageName = if (providedClientDataHash == null) packageName else null
        )

        val transports = JsonArray().apply {
            add("internal")
            add("hybrid")
        }

        val responseObj = JsonObject().apply {
            addProperty("clientDataJSON", base64UrlEncode(clientDataJson))
            addProperty("attestationObject", base64UrlEncode(attestationObject))
            add("transports", transports)
            addProperty("authenticatorData", base64UrlEncode(authData))
            addProperty("publicKey", generated.spkiPublicKeyBase64Url)
            addProperty("publicKeyAlgorithm", COSE_ALG_ES256)
        }

        val root = JsonObject().apply {
            addProperty("id", generated.credentialIdBase64Url)
            addProperty("rawId", generated.credentialIdBase64Url)
            addProperty("type", "public-key")
            addProperty("authenticatorAttachment", "platform")
            add("response", responseObj)
            add("clientExtensionResults", JsonObject())
        }
        return root.toString()
    }

    /**
     * Signs a WebAuthn assertion (authenticatorData || clientDataHash) using SHA256withECDSA
     * (ASN.1 DER signature per W3C WebAuthn §6.5.5) and returns the authentication response JSON.
     */
    fun createAssertionResponseJson(
        credentialIdBase64Url: String,
        userHandleBase64Url: String,
        pkcs8PrivateKeyBase64: String,
        rpId: String,
        signCount: Long,
        challengeBase64Url: String,
        origin: String,
        packageName: String? = null,
        providedClientDataHash: ByteArray? = null
    ): String {
        val authData = buildAssertionAuthData(rpId = rpId, signCount = signCount)
        val clientDataJson = buildClientDataJson(
            type = "webauthn.get",
            challengeBase64Url = challengeBase64Url,
            origin = origin,
            packageName = if (providedClientDataHash == null) packageName else null
        )
        val clientDataHash = providedClientDataHash ?: sha256(clientDataJson)

        val signedPayload = ByteArray(authData.size + clientDataHash.size)
        System.arraycopy(authData, 0, signedPayload, 0, authData.size)
        System.arraycopy(clientDataHash, 0, signedPayload, authData.size, clientDataHash.size)

        val privateKey = decodePrivateKey(pkcs8PrivateKeyBase64)
        val signatureBytes = signEs256(privateKey, signedPayload)

        val responseObj = JsonObject().apply {
            addProperty("clientDataJSON", base64UrlEncode(clientDataJson))
            addProperty("authenticatorData", base64UrlEncode(authData))
            addProperty("signature", base64UrlEncode(signatureBytes))
            addProperty("userHandle", userHandleBase64Url)
        }

        val root = JsonObject().apply {
            addProperty("id", credentialIdBase64Url)
            addProperty("rawId", credentialIdBase64Url)
            addProperty("type", "public-key")
            addProperty("authenticatorAttachment", "platform")
            add("response", responseObj)
            add("clientExtensionResults", JsonObject())
        }
        return root.toString()
    }

    /**
     * Computes Android APK origin format: "android:apk-key-hash:<base64url(sha256(cert))>"
     */
    fun computeAndroidApkOrigin(signingCertBytes: ByteArray): String {
        return "android:apk-key-hash:${base64UrlEncode(sha256(signingCertBytes))}"
    }

    /**
     * Detects cross-device FIDO2/Passkey hybrid QR code URIs ("FIDO:/...").
     */
    fun isFidoQrUri(rawQr: String): Boolean {
        return rawQr.trim().startsWith("FIDO:/", ignoreCase = true)
    }

    internal fun decodePrivateKey(pkcs8Base64Url: String): PrivateKey {
        val keyBytes = base64UrlDecode(pkcs8Base64Url)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("EC").generatePrivate(spec)
    }

    internal fun signEs256(privateKey: PrivateKey, payload: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey, SECURE_RANDOM)
        sig.update(payload)
        return sig.sign()
    }

    internal fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    internal fun bigIntegerToFixed32Bytes(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val result = ByteArray(32)
        if (raw.size == 32) {
            System.arraycopy(raw, 0, result, 0, 32)
        } else if (raw.size > 32) {
            // Strip leading sign byte(s)
            System.arraycopy(raw, raw.size - 32, result, 0, 32)
        } else {
            // Left-pad with zeroes
            System.arraycopy(raw, 0, result, 32 - raw.size, raw.size)
        }
        return result
    }

    // Portable RFC 4648 Base64Url (no padding) working on API 24+ and JVM unit tests
    private const val B64URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun base64UrlEncode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder((input.size * 4 + 2) / 3)
        var i = 0
        while (i < input.size) {
            val b0 = input[i++].toInt() and 0xFF
            val hasB1 = i < input.size
            val b1 = if (hasB1) input[i++].toInt() and 0xFF else 0
            val hasB2 = i < input.size
            val b2 = if (hasB2) input[i++].toInt() and 0xFF else 0

            sb.append(B64URL_ALPHABET[(b0 ushr 2) and 0x3F])
            sb.append(B64URL_ALPHABET[((b0 shl 4) or (b1 ushr 4)) and 0x3F])
            if (hasB1) {
                sb.append(B64URL_ALPHABET[((b1 shl 2) or (b2 ushr 6)) and 0x3F])
            }
            if (hasB2) {
                sb.append(B64URL_ALPHABET[b2 and 0x3F])
            }
        }
        return sb.toString()
    }

    fun base64UrlDecode(input: String): ByteArray {
        val clean = input.trim().trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream((clean.length * 3) / 4)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = when (c) {
                in 'A'..'Z' -> c - 'A'
                in 'a'..'z' -> c - 'a' + 26
                in '0'..'9' -> c - '0' + 52
                '-', '+' -> 62
                '_', '/' -> 63
                else -> continue
            }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}

