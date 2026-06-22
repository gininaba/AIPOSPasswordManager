package com.aipos.aipospm.security

data class PlaintextImportEntry(
    val title: String,
    val username: String,
    val password: String,
    val url: String = "",
    val notes: String = "",
    val categoryName: String? = null,
    val isFavorite: Boolean = false,
    val totpSecret: String? = null
)

object CsvImportMapper {
    /**
     * Maps parsed CSV rows (where the first row is headers) into a list of [PlaintextImportEntry] objects.
     * Uses fuzzy header mapping to support Bitwarden, KeePass, and 1Password formats.
     */
    fun map(rows: List<List<String>>): List<PlaintextImportEntry> {
        if (rows.isEmpty()) return emptyList()

        val headers = rows[0].map { it.trim().lowercase() }
        val entries = mutableListOf<PlaintextImportEntry>()

        // Header mapping aliases
        val titleIdxs = listOf("title", "name", "item title")
        val usernameIdxs = listOf("username", "login_username", "user name", "email", "login")
        val passwordIdxs = listOf("password", "login_password", "code")
        val urlIdxs = listOf("url", "login_uri", "website", "link", "uri")
        val notesIdxs = listOf("notes", "note", "comments")
        val categoryIdxs = listOf("group", "folder", "category")
        val favoriteIdxs = listOf("favorite", "is_favorite", "fav")
        val totpIdxs = listOf("totp", "login_totp", "one-time password", "totp_secret", "otpauth")

        fun findIndex(names: List<String>): Int {
            for (name in names) {
                val idx = headers.indexOf(name)
                if (idx != -1) return idx
            }
            return -1
        }

        val titleCol = findIndex(titleIdxs)
        val usernameCol = findIndex(usernameIdxs)
        val passwordCol = findIndex(passwordIdxs)
        val urlCol = findIndex(urlIdxs)
        val notesCol = findIndex(notesIdxs)
        val categoryCol = findIndex(categoryIdxs)
        val favoriteCol = findIndex(favoriteIdxs)
        val totpCol = findIndex(totpIdxs)

        // If we can't find title and password fields, it's not a recognizable password CSV format.
        if (titleCol == -1 && passwordCol == -1) {
            return emptyList()
        }

        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue

            fun getVal(col: Int): String {
                if (col >= 0 && col < row.size) {
                    return row[col].trim()
                }
                return ""
            }

            val title = getVal(titleCol)
            val username = getVal(usernameCol)
            val password = getVal(passwordCol)
            val url = getVal(urlCol)
            val notes = getVal(notesCol)
            val category = getVal(categoryCol).ifBlank { null }
            val favoriteStr = getVal(favoriteCol).lowercase()
            val isFavorite = favoriteStr == "1" || favoriteStr == "true" || favoriteStr == "yes"

            var totpSecret = getVal(totpCol).ifBlank { null }
            
            // If the TOTP column contains an otpauth URI, extract the secret parameter
            if (totpSecret != null && totpSecret.startsWith("otpauth://", ignoreCase = true)) {
                val secretParam = "secret="
                val idx = totpSecret.indexOf(secretParam, ignoreCase = true)
                if (idx != -1) {
                    val start = idx + secretParam.length
                    val end = totpSecret.indexOf('&', start)
                    totpSecret = if (end != -1) {
                        totpSecret.substring(start, end)
                    } else {
                        totpSecret.substring(start)
                    }
                }
            }

            totpSecret = totpSecret?.replace(" ", "")?.trim()

            // Skip entirely blank rows
            if (title.isBlank() && password.isBlank() && username.isBlank()) continue

            entries.add(
                PlaintextImportEntry(
                    title = title.ifBlank { "Untitled" },
                    username = username,
                    password = password,
                    url = url,
                    notes = notes,
                    categoryName = category,
                    isFavorite = isFavorite,
                    totpSecret = totpSecret
                )
            )
        }

        return entries
    }
}
