package com.aipos.aipospm

import com.aipos.aipospm.security.CsvImportMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvImportMapperTest {

    @Test
    fun testBitwardenMapping() {
        val rows = listOf(
            listOf("folder", "favorite", "type", "name", "notes", "fields", "reprompt", "login_uri", "login_username", "login_password", "login_totp"),
            listOf("Social", "1", "login", "GitHub", "My notes", "", "0", "https://github.com", "octocat", "superpassword", "JBSW43DPEHPK3PXP")
        )

        val mapped = CsvImportMapper.map(rows)
        assertEquals(1, mapped.size)
        val entry = mapped[0]
        assertEquals("GitHub", entry.title)
        assertEquals("octocat", entry.username)
        assertEquals("superpassword", entry.password)
        assertEquals("https://github.com", entry.url)
        assertEquals("My notes", entry.notes)
        assertEquals("Social", entry.categoryName)
        assertEquals(true, entry.isFavorite)
        assertEquals("JBSW43DPEHPK3PXP", entry.totpSecret)
    }

    @Test
    fun testKeePassMapping() {
        val rows = listOf(
            listOf("Group", "Title", "Username", "Password", "URL", "Notes", "Created", "Last Modified"),
            listOf("Email", "Gmail", "octo@gmail.com", "pass123", "https://gmail.com", "Gmail notes", "2026-01-01", "2026-02-02")
        )

        val mapped = CsvImportMapper.map(rows)
        assertEquals(1, mapped.size)
        val entry = mapped[0]
        assertEquals("Gmail", entry.title)
        assertEquals("octo@gmail.com", entry.username)
        assertEquals("pass123", entry.password)
        assertEquals("https://gmail.com", entry.url)
        assertEquals("Gmail notes", entry.notes)
        assertEquals("Email", entry.categoryName)
        assertEquals(false, entry.isFavorite)
    }

    @Test
    fun test1PasswordMapping() {
        val rows = listOf(
            listOf("Title", "Username", "Password", "Website", "Notes"),
            listOf("Slack", "workspace", "slackpass", "https://slack.com", "Slack note")
        )

        val mapped = CsvImportMapper.map(rows)
        assertEquals(1, mapped.size)
        val entry = mapped[0]
        assertEquals("Slack", entry.title)
        assertEquals("workspace", entry.username)
        assertEquals("slackpass", entry.password)
        assertEquals("https://slack.com", entry.url)
        assertEquals("Slack note", entry.notes)
        assertEquals(null, entry.categoryName)
        assertEquals(false, entry.isFavorite)
    }
}
