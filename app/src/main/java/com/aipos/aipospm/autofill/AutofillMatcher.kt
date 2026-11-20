package com.aipos.aipospm.autofill

import android.content.Context
import com.aipos.aipospm.data.PasswordEntry
import java.net.URI
import java.util.Locale

/**
 * Intelligent credential matching engine for Android Autofill and Credential Manager.
 * Matches active fillable vault credentials against web domains (browser contexts)
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
     * Returns true when an entry contains at least a username or an encrypted password
     * (filters out Note-only vault entries from autofill suggestions).
     */
    fun isFillableCredential(entry: PasswordEntry): Boolean {
        return entry.username.isNotBlank() || entry.encryptedPassword.isNotBlank()
    }

    /**
     * Extracts a normalized, clean host domain from a URL or raw domain string.
     */
    fun extractCleanDomain(rawUrlOrDomain: String): String {
        if (rawUrlOrDomain.isBlank()) return ""

        var candidate = rawUrlOrDomain.trim().lowercase(Locale.ROOT)

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
     * Derives a clean human-readable entry title from a web domain, app label, or package name.
     * Example: "accounts.github.com" -> "Github", "com.spotify.music" -> "Spotify".
     */
    fun deriveDefaultTitle(
        webDomain: String?,
        packageName: String?,
        appLabel: String? = null
    ): String {
        if (!webDomain.isNullOrBlank()) {
            val clean = extractCleanDomain(webDomain)
            val base = clean.substringBefore('.').trim()
            if (base.isNotEmpty()) {
                return base.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
            return clean
        }

        if (!appLabel.isNullOrBlank()) {
            return appLabel.trim()
        }

        if (!packageName.isNullOrBlank()) {
            val firstToken = extractPackageTokens(packageName).firstOrNull()
            if (!firstToken.isNullOrBlank()) {
                return firstToken.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
            return packageName.substringAfterLast('.').replaceFirstChar { it.titlecase(Locale.ROOT) }
        }

        return "Saved Login"
    }

    /**
     * Resolves a human-readable context label (clean domain or installed app label)
     * for display in Google-style autofill bottom sheets.
     */
    fun resolveDisplayTarget(
        context: Context,
        webDomain: String?,
        packageName: String?
    ): String? {
        if (!webDomain.isNullOrBlank()) {
            return extractCleanDomain(webDomain).ifBlank { webDomain }
        }
        if (!packageName.isNullOrBlank()) {
            val label = resolveAppLabel(context, packageName)
            if (!label.isNullOrBlank()) return label
            return packageName
        }
        return null
    }

    fun resolveAppLabel(context: Context, packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Scores a credential entry against the target context.
     * Returns 0 if no match or if the entry is not a fillable credential.
     */
    fun scoreEntry(
        entry: PasswordEntry,
        webDomain: String?,
        packageTokens: List<String>
    ): Int {
        if (!isFillableCredential(entry)) return 0

        val entryUrlDomain = extractCleanDomain(entry.url)
        val entryTitleClean = entry.title.lowercase(Locale.ROOT).trim()

        var bestScore = 0

        // 1. Web Domain Matching
        if (!webDomain.isNullOrBlank()) {
            val targetCleanDomain = extractCleanDomain(webDomain)

            if (entryUrlDomain.isNotBlank() && targetCleanDomain.isNotBlank()) {
                if (entryUrlDomain == targetCleanDomain) {
                    bestScore = maxOf(bestScore, 100)
                } else if (entryUrlDomain.endsWith(".$targetCleanDomain") || targetCleanDomain.endsWith(".$entryUrlDomain")) {
                    bestScore = maxOf(bestScore, 85)
                }
            }

            val baseDomainName = targetCleanDomain.substringBefore('.')
            if (baseDomainName.length > 2 && entryTitleClean.isNotEmpty()) {
                if (entryTitleClean == baseDomainName) {
                    bestScore = maxOf(bestScore, 75)
                } else if (entryTitleClean.contains(baseDomainName) ||
                    (entryTitleClean.length >= 4 && baseDomainName.contains(entryTitleClean))
                ) {
                    bestScore = maxOf(bestScore, 50)
                }
            }
        }

        // 2. Android Package Token Matching
        for (token in packageTokens) {
            if (entryTitleClean.isNotEmpty()) {
                if (entryTitleClean == token) {
                    bestScore = maxOf(bestScore, 80)
                } else if (entryTitleClean.contains(token) ||
                    (entryTitleClean.length >= 4 && token.contains(entryTitleClean))
                ) {
                    bestScore = maxOf(bestScore, 60)
                }
            }

            if (entryUrlDomain.isNotBlank()) {
                val baseEntryDomain = entryUrlDomain.substringBefore('.')
                if (baseEntryDomain == token || entryUrlDomain.contains(token)) {
                    bestScore = maxOf(bestScore, 70)
                }
            }
        }

        return bestScore
    }

    /**
     * Finds and ranks matching fillable credentials for an autofill or Credential Manager request.
     * Avoids matching against raw browser package tokens when no webDomain was provided.
     */
    fun findMatches(
        entries: List<PasswordEntry>,
        webDomain: String?,
        packageName: String?
    ): List<PasswordEntry> {
        val isTrustedBrowserWithoutDomain =
            webDomain.isNullOrBlank() &&
                !packageName.isNullOrBlank() &&
                packageName in AutofillStructureParser.TRUSTED_BROWSER_PACKAGES

        val packageTokens = if (isTrustedBrowserWithoutDomain) {
            emptyList()
        } else {
            packageName?.let { extractPackageTokens(it) } ?: emptyList()
        }

        if (webDomain.isNullOrBlank() && packageTokens.isEmpty()) {
            return emptyList()
        }

        return entries
            .asSequence()
            .filter { isFillableCredential(it) }
            .map { entry -> entry to scoreEntry(entry, webDomain, packageTokens) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<PasswordEntry, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt }
            )
            .map { it.first }
            .toList()
    }
}
