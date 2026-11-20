package com.aipos.aipospm

import com.aipos.aipospm.security.BitwardenJsonMapper
import com.aipos.aipospm.security.CsvImportMapper
import com.aipos.aipospm.security.CsvParser
import com.aipos.aipospm.security.EncryptedVaultException
import com.aipos.aipospm.security.VaultImportFormat
import com.aipos.aipospm.security.VaultImportParser
import com.aipos.aipospm.security.stripBom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Verification tests covering all milestones in nextplan.md:
 * - Bitwarden unencrypted JSON import (folders, logins, URIs, TOTP, multi-line notes, favorites)
 * - Encrypted Bitwarden JSON rejection
 * - UTF-8 BOM stripping utility (short streams, CSV, JSON)
 * - Resilient & localized CSV header normalization
 * - Explicit empty / corrupt file feedback
 */
class VaultImportTest {

    // ── 1. Bitwarden Unencrypted JSON Import ─────────────────────────

    @Test
    fun testBitwardenUnencryptedJsonImport() {
        val json = """
            {
              "encrypted": false,
              "folders": [
                { "id": "folder-1", "name": "Developer" },
                { "id": "folder-2", "name": "Banking" }
              ],
              "items": [
                {
                  "id": "item-1",
                  "folderId": "folder-1",
                  "type": 1,
                  "name": "GitHub",
                  "notes": "Line 1\nLine 2 with recovery hint",
                  "favorite": true,
                  "login": {
                    "uris": [
                      { "uri": "https://github.com/login" },
                      { "uri": "https://api.github.com" }
                    ],
                    "username": "octocat",
                    "password": "  P@ss\"word,123  ",
                    "totp": "otpauth://totp/GitHub:octocat?secret=JBSW43DPEHPK3PXP&issuer=GitHub"
                  }
                },
                {
                  "id": "item-2",
                  "folderId": null,
                  "type": 1,
                  "name": "Simple Account",
                  "notes": null,
                  "favorite": false,
                  "login": {
                    "uris": [],
                    "username": "alice@example.com",
                    "password": "my-password",
                    "totp": "GEZD GN BV"
                  }
                },
                {
                  "id": "item-3",
                  "folderId": "folder-2",
                  "type": 2,
                  "name": "Recovery Backup Codes",
                  "notes": "CODE-1111\nCODE-2222",
                  "favorite": false
                },
                {
                  "id": "item-4",
                  "type": 3,
                  "name": "Credit Card Should Be Skipped"
                }
              ]
            }
        """.trimIndent()

        val result = BitwardenJsonMapper.parse(json)
        assertTrue(result.isSuccess)
        val entries = result.getOrThrow()
        assertEquals(3, entries.size)

        val github = entries[0]
        assertEquals("GitHub", github.title)
        assertEquals("octocat", github.username)
        assertEquals("  P@ss\"word,123  ", github.password)
        assertEquals("https://github.com/login", github.url)
        assertEquals("Line 1\nLine 2 with recovery hint", github.notes)
        assertEquals("Developer", github.categoryName)
        assertTrue(github.isFavorite)
        assertEquals("JBSW43DPEHPK3PXP", github.totpSecret)

        val simple = entries[1]
        assertEquals("Simple Account", simple.title)
        assertEquals("alice@example.com", simple.username)
        assertEquals("my-password", simple.password)
        assertEquals("", simple.url)
        assertEquals("", simple.notes)
        assertNull(simple.categoryName)
        assertFalse(simple.isFavorite)
        assertEquals("GEZDGNBV", simple.totpSecret)

        val secureNote = entries[2]
        assertEquals("Recovery Backup Codes", secureNote.title)
        assertEquals("", secureNote.username)
        assertEquals("", secureNote.password)
        assertEquals("CODE-1111\nCODE-2222", secureNote.notes)
        assertEquals("Banking", secureNote.categoryName)
        assertFalse(secureNote.isFavorite)
        assertNull(secureNote.totpSecret)
    }

    // ── 2. Encrypted Bitwarden JSON Rejection ────────────────────────

    @Test
    fun testBitwardenEncryptedJsonRejectedGracefully() {
        val encryptedJson = """
            {
              "encrypted": true,
              "encKeyValidation_DO_NOT_EDIT": "2.abc|def",
              "folders": [],
              "items": []
            }
        """.trimIndent()

        val result = BitwardenJsonMapper.parse(encryptedJson)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(
            "Expected EncryptedVaultException but got $error",
            error is EncryptedVaultException
        )
        assertTrue(error!!.message!!.contains("unencrypted", ignoreCase = true))

        // Also verify through unified VaultImportParser
        val stream = ByteArrayInputStream(encryptedJson.toByteArray(Charsets.UTF_8))
        val parserResult = VaultImportParser.parse(stream, "bitwarden_export.json")
        assertTrue(parserResult.isFailure)
        assertTrue(parserResult.exceptionOrNull() is EncryptedVaultException)
    }

    // ── 3. UTF-8 BOM Stripping Utility ───────────────────────────────

    @Test
    fun testStripBomStreamUtility() {
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csvBody = "folder,name,login_username,login_password\nWork,Jira,dev,secret123".toByteArray(Charsets.UTF_8)

        // Stream WITH UTF-8 BOM
        val withBom = ByteArrayInputStream(bomBytes + csvBody)
        val parsedFromBom = VaultImportParser.parse(withBom, "bitwarden_export.csv")
        assertTrue(parsedFromBom.isSuccess)
        val entries = parsedFromBom.getOrThrow()
        assertEquals(1, entries.size)
        assertEquals("Jira", entries[0].title)
        assertEquals("Work", entries[0].categoryName)

        // Stream WITHOUT BOM and shorter than 3 bytes
        val shortStream = ByteArrayInputStream("hi".toByteArray(Charsets.UTF_8))
        assertEquals("hi", shortStream.stripBom().readText())

        // Empty stream
        val emptyStream = ByteArrayInputStream(ByteArray(0))
        assertEquals("", emptyStream.stripBom().readText())

        // Bitwarden JSON prefixed with UTF-8 BOM
        val jsonBody = """{"encrypted":false,"items":[{"type":1,"name":"BOM Site","login":{"username":"u","password":"p"}}]}"""
            .toByteArray(Charsets.UTF_8)
        val jsonWithBom = ByteArrayInputStream(bomBytes + jsonBody)
        val jsonResult = VaultImportParser.parse(jsonWithBom)
        assertTrue(jsonResult.isSuccess)
        assertEquals(1, jsonResult.getOrThrow().size)
        assertEquals("BOM Site", jsonResult.getOrThrow()[0].title)
    }

    // ── 4. Localized & Aliased CSV Headers ───────────────────────────

    @Test
    fun testAliasedAndLocalizedCsvHeaders() {
        // Alternate English aliases from nextplan.md
        val aliasedCsv = "Service,User,Pass,Link,2FA,Comment,Category\n" +
            "Linear,dev@team.io,pw123,https://linear.app,JBSW43DPEHPK3PXP,Sprint tracker,Work"
        val aliasedRows = CsvParser.parse(ByteArrayInputStream(aliasedCsv.toByteArray(Charsets.UTF_8)))
        val aliasedEntries = CsvImportMapper.map(aliasedRows)
        assertEquals(1, aliasedEntries.size)
        assertEquals("Linear", aliasedEntries[0].title)
        assertEquals("dev@team.io", aliasedEntries[0].username)
        assertEquals("pw123", aliasedEntries[0].password)
        assertEquals("https://linear.app", aliasedEntries[0].url)
        assertEquals("JBSW43DPEHPK3PXP", aliasedEntries[0].totpSecret)
        assertEquals("Sprint tracker", aliasedEntries[0].notes)
        assertEquals("Work", aliasedEntries[0].categoryName)

        // Russian localized headers (e.g. localized Bitwarden / KeePass CSV)
        val russianCsv = "Папка,Название,Логин,Пароль,Сайт,Заметки\n" +
            "Почта,Яндекс,ivan@yandex.ru,parol123,https://mail.yandex.ru,Основной ящик"
        val ruRows = CsvParser.parse(ByteArrayInputStream(russianCsv.toByteArray(Charsets.UTF_8)))
        val ruEntries = CsvImportMapper.map(ruRows)
        assertEquals(1, ruEntries.size)
        assertEquals("Яндекс", ruEntries[0].title)
        assertEquals("ivan@yandex.ru", ruEntries[0].username)
        assertEquals("parol123", ruEntries[0].password)
        assertEquals("Почта", ruEntries[0].categoryName)

        // German localized headers
        val germanCsv = "Ordner,Titel,Benutzername,Passwort,Webseite,Notizen\n" +
            "Arbeit,GitLab,hans,geheim,https://gitlab.com,Team"
        val deRows = CsvParser.parse(ByteArrayInputStream(germanCsv.toByteArray(Charsets.UTF_8)))
        val deEntries = CsvImportMapper.map(deRows)
        assertEquals(1, deEntries.size)
        assertEquals("GitLab", deEntries[0].title)
        assertEquals("hans", deEntries[0].username)
        assertEquals("geheim", deEntries[0].password)
        assertEquals("Arbeit", deEntries[0].categoryName)
    }

    // ── 5. Empty / Corrupt File Feedback & Format Detection ──────────

    @Test
    fun testEmptyAndCorruptFilesReturnExplicitFeedback() {
        // Empty file
        val emptyResult = VaultImportParser.parse(ByteArrayInputStream(ByteArray(0)))
        assertTrue(emptyResult.isFailure)

        // Whitespace-only file
        val blankResult = VaultImportParser.parse(ByteArrayInputStream("   \n\t  ".toByteArray()))
        assertTrue(blankResult.isFailure)

        // Malformed JSON
        val badJson = VaultImportParser.parse(ByteArrayInputStream("{ not valid json".toByteArray()))
        assertTrue(badJson.isFailure)

        // Unrelated JSON object (not a Bitwarden vault)
        val unrelatedJson = VaultImportParser.parse(ByteArrayInputStream("""{"hello":"world"}""".toByteArray()))
        assertTrue(unrelatedJson.isFailure)

        // CSV with unrecognized headers returns empty list (triggers Empty feedback dialog in ViewModel)
        val unknownCsv = VaultImportParser.parse(ByteArrayInputStream("foo,bar\n1,2".toByteArray()))
        assertTrue(unknownCsv.isSuccess)
        assertTrue(unknownCsv.getOrThrow().isEmpty())

        // Format detection works even with no file extension (SAF opaque URI)
        assertEquals(VaultImportFormat.BITWARDEN_JSON, VaultImportParser.detectFormat("  \n {\"items\":[]}", null))
        assertEquals(VaultImportFormat.CSV, VaultImportParser.detectFormat("name,password\na,b", null))
        assertEquals(VaultImportFormat.BITWARDEN_JSON, VaultImportParser.detectFormat("invalid", "export.json"))
    }
}

