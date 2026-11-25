package com.aipos.aipospm

import com.aipos.aipospm.passkey.CborEncoder
import com.aipos.aipospm.passkey.WebAuthnCrypto
import com.aipos.aipospm.ui.viewmodels.BackupPayload
import com.aipos.aipospm.ui.viewmodels.PasskeyBackup
import com.aipos.aipospm.ui.viewmodels.PasswordBackup
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Unit tests verifying 100% offline WebAuthn / FIDO2 Passkey cryptography (Issue #10),
 * FIDO:/ QR URI detection (Issue #13), and Backup v7 Passkey + excludeFromAudit serialization.
 */
class WebAuthnPasskeyTest {

    @Test
    fun testCborCoseEc2PublicKeyEncoding() {
        val x = ByteArray(32) { (it + 1).toByte() }
        val y = ByteArray(32) { (it + 33).toByte() }
        val encoded = CborEncoder.encodeCoseEc2PublicKey(x, y)

        // Map of 5 entries (0xA5):
        // 1 (0x01): 2 (0x02), 3 (0x03): -7 (0x26), -1 (0x20): 1 (0x01),
        // -2 (0x21): bstr(32) (0x58 0x20 + x), -3 (0x22): bstr(32) (0x58 0x20 + y)
        assertEquals(0xA5.toByte(), encoded[0])
        assertEquals(0x01.toByte(), encoded[1])
        assertEquals(0x02.toByte(), encoded[2])
        assertEquals(0x03.toByte(), encoded[3])
        assertEquals(0x26.toByte(), encoded[4]) // -7 = major 1, value 6 => 0x26
        assertEquals(0x20.toByte(), encoded[5]) // -1 => 0x20
        assertEquals(0x01.toByte(), encoded[6])
        assertEquals(0x21.toByte(), encoded[7]) // -2 => 0x21
        assertEquals(0x58.toByte(), encoded[8]) // bstr len in 1 byte
        assertEquals(0x20.toByte(), encoded[9]) // 32 bytes
        assertArrayEquals(x, encoded.copyOfRange(10, 42))
        assertEquals(0x22.toByte(), encoded[42]) // -3 => 0x22
        assertEquals(0x58.toByte(), encoded[43])
        assertEquals(0x20.toByte(), encoded[44])
        assertArrayEquals(y, encoded.copyOfRange(45, 77))
        assertEquals(77, encoded.size)
    }

    @Test
    fun testEndToEndPasskeyRegistrationAndAssertionSignatureVerification() {
        val rpId = "login.microsoftonline.com"
        val userHandle = WebAuthnCrypto.base64UrlEncode("user-uuid-1234".toByteArray())
        val challenge = WebAuthnCrypto.base64UrlEncode(ByteArray(32) { it.toByte() })
        val origin = "https://login.microsoftonline.com"

        // 1. Register (Create Passkey)
        val generated = WebAuthnCrypto.generatePasskeyKeyPair()
        val regJsonStr = WebAuthnCrypto.createRegistrationResponseJson(
            generated = generated,
            rpId = rpId,
            challengeBase64Url = challenge,
            origin = origin
        )
        val regJson = JsonParser.parseString(regJsonStr).asJsonObject
        assertEquals(generated.credentialIdBase64Url, regJson.get("id").asString)
        assertEquals("public-key", regJson.get("type").asString)

        val regResp = regJson.getAsJsonObject("response")
        assertEquals(WebAuthnCrypto.COSE_ALG_ES256, regResp.get("publicKeyAlgorithm").asInt)
        val authData = WebAuthnCrypto.base64UrlDecode(regResp.get("authenticatorData").asString)
        // Flags byte at index 32: UP(0x01) | UV(0x04) | BE(0x08) | BS(0x10) | AT(0x40) = 0x5D
        assertEquals(0x5D.toByte(), authData[32])
        assertArrayEquals(WebAuthnCrypto.AIPOS_AAGUID, authData.copyOfRange(37, 53))

        // 2. Authenticate (Get Passkey Assertion)
        val assertJsonStr = WebAuthnCrypto.createAssertionResponseJson(
            credentialIdBase64Url = generated.credentialIdBase64Url,
            userHandleBase64Url = userHandle,
            pkcs8PrivateKeyBase64 = generated.pkcs8PrivateKeyBase64,
            rpId = rpId,
            signCount = 1L,
            challengeBase64Url = challenge,
            origin = origin
        )
        val assertJson = JsonParser.parseString(assertJsonStr).asJsonObject
        val assertResp = assertJson.getAsJsonObject("response")
        assertEquals(userHandle, assertResp.get("userHandle").asString)

        // 3. Cryptographically verify ES256 DER signature using the exported SPKI public key
        val spkiBytes = WebAuthnCrypto.base64UrlDecode(generated.spkiPublicKeyBase64Url)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spkiBytes))
        val assertAuthData = WebAuthnCrypto.base64UrlDecode(assertResp.get("authenticatorData").asString)
        val clientDataJson = WebAuthnCrypto.base64UrlDecode(assertResp.get("clientDataJSON").asString)
        val signatureBytes = WebAuthnCrypto.base64UrlDecode(assertResp.get("signature").asString)

        // Assertion flags byte at index 32: UP | UV | BE | BS = 0x1D
        assertEquals(0x1D.toByte(), assertAuthData[32])

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(assertAuthData)
        verifier.update(WebAuthnCrypto.sha256(clientDataJson))
        assertTrue("ES256 assertion signature must verify against registered public key", verifier.verify(signatureBytes))
    }

    @Test
    fun testFidoQrUriDetection() {
        assertTrue(WebAuthnCrypto.isFidoQrUri("FIDO:/025431838472910472910472"))
        assertTrue(WebAuthnCrypto.isFidoQrUri("  fido:/1234567890  "))
        assertFalse(WebAuthnCrypto.isFidoQrUri("otpauth://totp/GitHub:octocat?secret=JBSW43DPEHPK3PXP"))
        assertFalse(WebAuthnCrypto.isFidoQrUri("https://example.com"))
    }

    @Test
    fun testBackupV7PasskeyAndExcludeFromAuditSerialization() {
        val gson = Gson()
        val payload = BackupPayload(
            version = 7,
            categories = emptyList(),
            passwords = listOf(
                PasswordBackup(
                    id = 1,
                    title = "SIM PIN",
                    username = "",
                    plaintext = "7392",
                    url = "",
                    notes = "",
                    categoryId = null,
                    isFavorite = false,
                    createdAt = 1000L,
                    updatedAt = 1000L,
                    excludeFromAudit = true
                )
            ),
            apiKeys = emptyList(),
            passkeys = listOf(
                PasskeyBackup(
                    credentialId = "cred-id-b64url",
                    rpId = "github.com",
                    rpName = "GitHub",
                    userHandle = "user-handle",
                    userName = "octocat",
                    userDisplayName = "The Octocat",
                    plaintextPrivateKey = "pkcs8-b64url",
                    publicKeyCose = "cose-b64url",
                    signCount = 3L,
                    createdAt = 1000L,
                    lastUsedAt = 2000L
                )
            )
        )

        val json = gson.toJson(payload)
        val restored = gson.fromJson(json, BackupPayload::class.java)
        assertEquals(7, restored.version)
        assertEquals(true, restored.passwords?.firstOrNull()?.excludeFromAudit)
        assertEquals(1, restored.passkeys?.size)
        assertEquals("github.com", restored.passkeys?.firstOrNull()?.rpId)
        assertEquals(3L, restored.passkeys?.firstOrNull()?.signCount)
    }

    @Test
    fun testAaguidUuidMatchesEmbeddedRegistrationBytes() {
        assertEquals("6169706f-732d-706d-4a10-8f3c52e79b01", WebAuthnCrypto.AIPOS_AAGUID_UUID)
        assertEquals(WebAuthnCrypto.AIPOS_AAGUID_UUID, WebAuthnCrypto.formatAaguidUuid(WebAuthnCrypto.AIPOS_AAGUID))

        val generated = WebAuthnCrypto.generatePasskeyKeyPair()
        val authData = WebAuthnCrypto.buildRegistrationAuthData(
            rpId = "login.microsoftonline.com",
            credentialId = generated.credentialIdBytes,
            cosePublicKey = generated.cosePublicKeyBytes,
            signCount = 0L
        )
        // WebAuthn authData layout: rpIdHash (0..31), flags (32), signCount (33..36), aaguid (37..52)
        val embeddedAaguid = authData.copyOfRange(37, 53)
        assertEquals(WebAuthnCrypto.AIPOS_AAGUID_UUID, WebAuthnCrypto.formatAaguidUuid(embeddedAaguid))
    }
}

