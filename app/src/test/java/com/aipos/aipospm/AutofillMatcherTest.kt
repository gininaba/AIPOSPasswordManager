package com.aipos.aipospm

import com.aipos.aipospm.autofill.AutofillMatcher
import com.aipos.aipospm.data.PasswordEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillMatcherTest {

    @Test
    fun testExtractCleanDomain() {
        assertEquals("github.com", AutofillMatcher.extractCleanDomain("https://www.github.com/login"))
        assertEquals("google.com", AutofillMatcher.extractCleanDomain("accounts.google.com"))
        assertEquals("live.com", AutofillMatcher.extractCleanDomain("https://login.live.com/oauth2"))
        assertEquals("spotify.com", AutofillMatcher.extractCleanDomain("https://open.spotify.com/"))
        assertEquals("netflix.com", AutofillMatcher.extractCleanDomain("http://netflix.com:443/browse"))
        assertEquals("", AutofillMatcher.extractCleanDomain(""))
    }

    @Test
    fun testExtractPackageTokens() {
        val twitterTokens = AutofillMatcher.extractPackageTokens("com.twitter.android")
        assertEquals(listOf("twitter", "x"), twitterTokens)

        val spotifyTokens = AutofillMatcher.extractPackageTokens("com.spotify.music")
        assertEquals(listOf("spotify", "music"), spotifyTokens)

        val telegramTokens = AutofillMatcher.extractPackageTokens("org.telegram.messenger")
        assertEquals(listOf("telegram", "messenger"), telegramTokens)

        val genericTokens = AutofillMatcher.extractPackageTokens("com.android.app")
        assertTrue(genericTokens.isEmpty())
    }

    @Test
    fun testFindMatchesWebDomain() {
        val entryGithub = PasswordEntry(
            id = 1,
            title = "GitHub",
            username = "octocat",
            encryptedPassword = "enc",
            iv = "iv",
            url = "https://github.com/login"
        )
        val entryGoogle = PasswordEntry(
            id = 2,
            title = "Google Personal",
            username = "user@gmail.com",
            encryptedPassword = "enc",
            iv = "iv",
            url = "https://accounts.google.com"
        )
        val entryTwitter = PasswordEntry(
            id = 3,
            title = "Twitter / X",
            username = "coder",
            encryptedPassword = "enc",
            iv = "iv",
            url = "https://x.com"
        )

        val allEntries = listOf(entryTwitter, entryGoogle, entryGithub)

        // Web match for github
        val matches = AutofillMatcher.findMatches(allEntries, "github.com", null)
        assertEquals(1, matches.size)
        assertEquals(1, matches[0].id)

        // Web match for google with subdomain
        val googleMatches = AutofillMatcher.findMatches(allEntries, "accounts.google.com", null)
        assertEquals(1, googleMatches.size)
        assertEquals(2, googleMatches[0].id)
    }

    @Test
    fun testFindMatchesNativeApp() {
        val entrySpotify = PasswordEntry(
            id = 10,
            title = "Spotify Premium",
            username = "music_lover",
            encryptedPassword = "enc",
            iv = "iv",
            url = "https://spotify.com"
        )
        val entryReddit = PasswordEntry(
            id = 11,
            title = "Reddit",
            username = "redditor",
            encryptedPassword = "enc",
            iv = "iv",
            url = "https://reddit.com"
        )

        val allEntries = listOf(entrySpotify, entryReddit)

        val matches = AutofillMatcher.findMatches(allEntries, null, "com.spotify.music")
        assertEquals(1, matches.size)
        assertEquals(10, matches[0].id)
    }

    @Test
    fun testRankingPriority() {
        val exactDomainMatch = PasswordEntry(
            id = 1,
            title = "My Company Login",
            username = "corp_user",
            encryptedPassword = "enc",
            iv = "iv",
            url = "https://auth0.com"
        )
        val titleOnlyMatch = PasswordEntry(
            id = 2,
            title = "Auth0 Developer Portal",
            username = "dev_user",
            encryptedPassword = "enc",
            iv = "iv",
            url = "https://some-other-portal.org"
        )

        val entries = listOf(titleOnlyMatch, exactDomainMatch)
        val matches = AutofillMatcher.findMatches(entries, "auth0.com", null)

        assertEquals(2, matches.size)
        // Exact URL match (score 100) must rank ahead of title-only match (score 75/50)
        assertEquals(1, matches[0].id)
        assertEquals(2, matches[1].id)
    }
}
