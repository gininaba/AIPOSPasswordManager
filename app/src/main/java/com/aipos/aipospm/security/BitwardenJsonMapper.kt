package com.aipos.aipospm.security

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Thrown when a user attempts to import an encrypted Bitwarden JSON export,
 * which requires Bitwarden's account key and cannot be parsed offline.
 */
class EncryptedVaultException(message: String) : Exception(message)

/**
 * Parses unencrypted Bitwarden JSON vault exports into [PlaintextImportEntry] records.
 * Uses Gson rather than Android's org.json so parsing is 100% JVM unit-testable.
 */
object BitwardenJsonMapper {

    private const val BITWARDEN_TYPE_LOGIN = 1
    private const val BITWARDEN_TYPE_SECURE_NOTE = 2

    fun parse(jsonString: String): Result<List<PlaintextImportEntry>> {
        val cleaned = jsonString.stripBom().trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("The selected JSON file is empty."))
        }

        val rootElement: JsonElement = try {
            JsonParser.parseString(cleaned)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid JSON file format: ${e.message}", e))
        }

        if (!rootElement.isJsonObject) {
            return Result.failure(IllegalArgumentException("Unrecognized JSON structure. Expected a Bitwarden JSON export."))
        }

        val root = rootElement.asJsonObject

        // Safety guard: reject encrypted Bitwarden exports with actionable guidance
        val isEncrypted = root.getBooleanOrNull("encrypted") == true
        if (isEncrypted) {
            return Result.failure(
                EncryptedVaultException(
                    "Encrypted Bitwarden JSON exports cannot be imported without your Bitwarden account key. " +
                        "Please export an unencrypted (.json) vault from Bitwarden and try again."
                )
            )
        }

        val hasItems = root.has("items") && root.get("items").isJsonArray
        val hasFolders = root.has("folders") && root.get("folders").isJsonArray
        if (!hasItems && !hasFolders) {
            return Result.failure(
                IllegalArgumentException(
                    "Unrecognized JSON format. Please select an unencrypted Bitwarden JSON export."
                )
            )
        }

        // Map Bitwarden folder UUIDs -> folder names
        val foldersMap = mutableMapOf<String, String>()
        if (hasFolders) {
            for (folderEl in root.getAsJsonArray("folders")) {
                if (!folderEl.isJsonObject) continue
                val folderObj = folderEl.asJsonObject
                val id = folderObj.getStringOrNull("id")?.trim()
                val name = folderObj.getStringOrNull("name")?.trim()
                if (!id.isNullOrEmpty() && !name.isNullOrEmpty()) {
                    foldersMap[id] = name
                }
            }
        }

        if (!hasItems) {
            return Result.success(emptyList())
        }

        val entries = mutableListOf<PlaintextImportEntry>()
        for (itemEl in root.getAsJsonArray("items")) {
            if (!itemEl.isJsonObject) continue
            val item = itemEl.asJsonObject

            val type = item.getIntOrNull("type") ?: BITWARDEN_TYPE_LOGIN
            if (type != BITWARDEN_TYPE_LOGIN && type != BITWARDEN_TYPE_SECURE_NOTE) {
                continue // Skip cards (3) and identities (4)
            }

            val rawTitle = item.getStringOrNull("name")?.trim() ?: ""
            val notes = item.getStringOrNull("notes") ?: ""
            val isFavorite = item.getBooleanOrNull("favorite") ?: false
            val folderId = item.getStringOrNull("folderId")?.trim()
            val categoryName = folderId?.let { foldersMap[it] }

            if (type == BITWARDEN_TYPE_SECURE_NOTE) {
                if (rawTitle.isBlank() && notes.isBlank()) continue
                entries.add(
                    PlaintextImportEntry(
                        title = rawTitle.ifBlank { "Untitled Note" },
                        username = "",
                        password = "",
                        url = "",
                        notes = notes,
                        categoryName = categoryName,
                        isFavorite = isFavorite,
                        totpSecret = null
                    )
                )
                continue
            }

            val login = item.get("login")?.takeIf { it.isJsonObject }?.asJsonObject
            val username = login?.getStringOrNull("username")?.trim() ?: ""
            // Preserve leading/trailing spaces in passwords verbatim
            val password = login?.getStringOrNull("password") ?: ""
            val totpSecret = CsvImportMapper.extractTotpSecret(login?.getStringOrNull("totp"))

            // Extract first non-blank URI from login.uris[]
            var websiteUrl = ""
            val uris = login?.get("uris")?.takeIf { it.isJsonArray }?.asJsonArray
            if (uris != null) {
                for (uriEl in uris) {
                    if (!uriEl.isJsonObject) continue
                    val candidate = uriEl.asJsonObject.getStringOrNull("uri")?.trim()
                    if (!candidate.isNullOrEmpty()) {
                        websiteUrl = candidate
                        break
                    }
                }
            }

            // Skip entirely blank entries
            if (rawTitle.isBlank() && username.isBlank() && password.isBlank() && notes.isBlank()) continue

            entries.add(
                PlaintextImportEntry(
                    title = rawTitle.ifBlank { "Untitled" },
                    username = username,
                    password = password,
                    url = websiteUrl,
                    notes = notes,
                    categoryName = categoryName,
                    isFavorite = isFavorite,
                    totpSecret = totpSecret
                )
            )
        }

        return Result.success(entries)
    }

    private fun JsonObject.getStringOrNull(memberName: String): String? {
        val el = get(memberName) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return try {
            el.asString
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.getBooleanOrNull(memberName: String): Boolean? {
        val el = get(memberName) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return try {
            el.asBoolean
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.getIntOrNull(memberName: String): Int? {
        val el = get(memberName) ?: return null
        if (el.isJsonNull || !el.isJsonPrimitive) return null
        return try {
            el.asInt
        } catch (_: Exception) {
            null
        }
    }
}

