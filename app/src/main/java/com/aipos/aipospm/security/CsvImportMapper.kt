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
     * Extracts a clean Base32 TOTP secret from either a raw secret string or an `otpauth://` URI.
     */
    internal fun extractTotpSecret(raw: String?): String? {
        var totpSecret = raw?.trim()?.ifBlank { null } ?: return null
        if (totpSecret.startsWith("otpauth://", ignoreCase = true)) {
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
        return totpSecret.replace(" ", "").trim().ifBlank { null }
    }

    /**
     * Maps parsed CSV rows (where the first row is headers) into a list of [PlaintextImportEntry] objects.
     * Uses resilient header normalization (BOM-stripped, trimmed, lowercase) and aliases to support
     * Bitwarden, KeePass, 1Password, and localized CSV exports.
     */
    fun map(rows: List<List<String>>): List<PlaintextImportEntry> {
        if (rows.isEmpty()) return emptyList()

        val headers = rows[0].map { it.replace("\uFEFF", "").trim().lowercase() }
        val entries = mutableListOf<PlaintextImportEntry>()

        // Header mapping aliases (English + common localized exports: RU, DE, FR, ES)
        val titleIdxs = listOf(
            "title", "name", "item title", "service", "account",
            "название", "имя", "titel", "nom", "título", "nombre"
        )
        val usernameIdxs = listOf(
            "username", "login_username", "user name", "user", "email", "login",
            "логин", "имя пользователя", "benutzername", "nom d'utilisateur", "usuario"
        )
        val passwordIdxs = listOf(
            "password", "login_password", "pass", "code",
            "пароль", "passwort", "mot de passe", "contraseña"
        )
        val urlIdxs = listOf(
            "url", "login_uri", "website", "link", "uri",
            "сайт", "адрес", "webseite", "site web", "sitio web"
        )
        val notesIdxs = listOf(
            "notes", "note", "comment", "comments",
            "заметки", "примечания", "notizen", "remarques", "notas"
        )
        val categoryIdxs = listOf(
            "group", "folder", "category",
            "папка", "группа", "категория", "ordner", "kategorie", "dossier", "catégorie", "carpeta", "categoría"
        )
        val favoriteIdxs = listOf(
            "favorite", "is_favorite", "fav",
            "избранное", "favorit", "favori", "favorito"
        )
        val totpIdxs = listOf(
            "totp", "login_totp", "one-time password", "totp_secret", "otpauth", "2fa", "otp"
        )

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

            fun getVal(col: Int, trim: Boolean = true): String {
                if (col >= 0 && col < row.size) {
                    return if (trim) row[col].trim() else row[col]
                }
                return ""
            }

            val title = getVal(titleCol)
            val username = getVal(usernameCol)
            // Preserve leading/trailing spaces in passwords verbatim
            val password = getVal(passwordCol, trim = false)
            val url = getVal(urlCol)
            val notes = getVal(notesCol)
            val category = getVal(categoryCol).ifBlank { null }
            val favoriteStr = getVal(favoriteCol).lowercase()
            val isFavorite = favoriteStr == "1" || favoriteStr == "true" || favoriteStr == "yes"

            val totpSecret = extractTotpSecret(getVal(totpCol))

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
