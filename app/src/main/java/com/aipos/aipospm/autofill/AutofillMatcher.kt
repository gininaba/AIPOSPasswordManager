package com.aipos.aipospm.autofill

import com.aipos.aipospm.data.PasswordEntry
import java.net.URI
import java.util.Locale

/**
 * Intelligent credential matching engine for Android Autofill.
 * Matches active vault credentials against web domains (browser contexts)
 * and Android package names (native app contexts).
 */
object AutofillMatcher {

    private val GENERIC_PACKAGE_TOKENS = setOf(
        "com", "org", "net", "io", "co", "app", "apps", "android", "mobile",
        "client", "login", "auth", "corp", "inc", "ltd", "dev", "official",
        "service", "ui", "view", "phone", "tablet", "google", "samsung", "huawei",
        "xiaomi", "oppo", "vivo", "oneplus"
    )

    private val SUBDOMAIN_PREFIXES = setOf(
        "www.", "m.", "mobile.", "login.", "auth.", "accounts.", "account.",
        "identity.", "secure.", "signin.", "app.", "portal.", "my.", "open.",
        "web.", "api.", "id."
    )

    private val PACKAGE_ALIASES = mapOf(
        "com.twitter.android" to listOf("twitter", "x"),
        "com.zhiliaoapp.musically" to listOf("tiktok"),
        "com.ss.android.ugc.trill" to listOf("tiktok"),
        "com.ubercab" to listOf("uber"),
        "com.facebook.katana" to listOf("facebook"),
        "com.google.android.youtube" to listOf("youtube", "google"),
        "com.google.android.gm" to listOf("gmail", "google")
    )

    /**
     * Extracts a normalized, clean host domain from a URL or raw domain string.
     * Examples:
     * - "https://www.github.com/login" -> "github.com"
     * - "accounts.google.com" -> "google.com"
     * - "https://sub.example.co.uk:8080/path" -> "example.co.uk"
     */
    fun extractCleanDomain(rawUrlOrDomain: String): String {
        if (rawUrlOrDomain.isBlank()) return ""

        var candidate = rawUrlOrDomain.trim().lowercase(Locale.ROOT)

        // Ensure scheme is present for URI parsing
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "https://$candidate"
        }

        val host = try {
            URI(candidate).host ?: ""
        } catch (_: Exception) {
            candidate.removePrefix("https://").removePrefix("http://")
                .substringBefore('/').substringBefore(':')
        }

        if (host.isBlank()) return ""

        var cleanHost = host.lowercase(Locale.ROOT).trim()

        // Strip known subdomain prefixes
        for (prefix in SUBDOMAIN_PREFIXES) {
            if (cleanHost.startsWith(prefix) && cleanHost.length > prefix.length) {
                cleanHost = cleanHost.removePrefix(prefix)
                break
            }
        }

        return cleanHost
    }

    /**
     * Extracts significant identifying tokens from an Android package name,
     * including well-known app brand aliases.
     */
    fun extractPackageTokens(packageName: String): List<String> {
        if (packageName.isBlank()) return emptyList()

        val cleanPkg = packageName.lowercase(Locale.ROOT).trim()
        val aliasTokens = PACKAGE_ALIASES[cleanPkg] ?: emptyList()

        val splitTokens = cleanPkg
            .split('.')
            .map { it.trim() }
            .filter { token ->
                token.length > 2 && !GENERIC_PACKAGE_TOKENS.contains(token)
            }

        return (aliasTokens + splitTokens).distinct()
    }

    /**
     * Scores a credential entry against the target context.
     * Returns 0 if no match.
     */
    fun scoreEntry(
        entry: PasswordEntry,
        webDomain: String?,
        packageTokens: List<String>
    ): Int {
        val entryUrlDomain = extractCleanDomain(entry.url)
        val entryTitleClean = entry.title.lowercase(Locale.ROOT).trim()

        var bestScore = 0

        // 1. Web Domain Matching
        if (!webDomain.isNullOrBlank()) {
            val targetCleanDomain = extractCleanDomain(webDomain)

            if (entryUrlDomain.isNotBlank() && targetCleanDomain.isNotBlank()) {
                if (entryUrlDomain == targetCleanDomain) {
                    bestScore = maxOf(bestScore, 100) // Exact domain match
                } else if (entryUrlDomain.endsWith(".$targetCleanDomain") || targetCleanDomain.endsWith(".$entryUrlDomain")) {
                    bestScore = maxOf(bestScore, 85) // Subdomain / related domain match
                }
            }

            // Check if entry title matches web domain base
            val baseDomainName = targetCleanDomain.substringBefore('.')
            if (baseDomainName.length > 2) {
                if (entryTitleClean == baseDomainName) {
                    bestScore = maxOf(bestScore, 75)
                } else if (entryTitleClean.contains(baseDomainName) || baseDomainName.contains(entryTitleClean)) {
                    bestScore = maxOf(bestScore, 50)
                }
            }
        }

        // 2. Android Package Token Matching
        for (token in packageTokens) {
            if (entryTitleClean == token) {
                bestScore = maxOf(bestScore, 80) // Exact title match with app name
            } else if (entryTitleClean.contains(token) || token.contains(entryTitleClean)) {
                bestScore = maxOf(bestScore, 60) // Partial title match
            }

            if (entryUrlDomain.isNotBlank()) {
                val baseEntryDomain = entryUrlDomain.substringBefore('.')
                if (baseEntryDomain == token || entryUrlDomain.contains(token)) {
                    bestScore = maxOf(bestScore, 70) // Entry URL contains app package token
                }
            }
        }

        return bestScore
    }

    /**
     * Finds and ranks matching credentials for an autofill request.
     */
    fun findMatches(
        entries: List<PasswordEntry>,
        webDomain: String?,
        packageName: String?
    ): List<PasswordEntry> {
        val packageTokens = packageName?.let { extractPackageTokens(it) } ?: emptyList()

        if (webDomain.isNullOrBlank() && packageTokens.isEmpty()) {
            return emptyList()
        }

        return entries
            .map { entry -> entry to scoreEntry(entry, webDomain, packageTokens) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<PasswordEntry, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
    }
}
