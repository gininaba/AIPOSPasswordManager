package com.aipos.aipospm.security

import java.io.ByteArrayInputStream
import java.io.InputStream

enum class VaultImportFormat {
    BITWARDEN_JSON,
    CSV
}

/**
 * Unified vault file parser supporting both Bitwarden unencrypted JSON (.json)
 * and third-party CSV (.csv) exports with automatic UTF-8 BOM stripping and
 * content-based format detection.
 */
object VaultImportParser {

    fun detectFormat(content: String, fileName: String? = null): VaultImportFormat {
        val trimmed = content.stripBom().trimStart()
        if (trimmed.startsWith("{")) {
            return VaultImportFormat.BITWARDEN_JSON
        }
        if (fileName != null && fileName.endsWith(".json", ignoreCase = true)) {
            return VaultImportFormat.BITWARDEN_JSON
        }
        return VaultImportFormat.CSV
    }

    fun parse(inputStream: InputStream, fileName: String? = null): Result<List<PlaintextImportEntry>> {
        val content = try {
            inputStream.stripBom().use { it.readText() }
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Could not read the selected file: ${e.message}", e))
        }

        if (content.isBlank()) {
            return Result.failure(IllegalArgumentException("The selected file is empty."))
        }

        return when (detectFormat(content, fileName)) {
            VaultImportFormat.BITWARDEN_JSON -> BitwardenJsonMapper.parse(content)
            VaultImportFormat.CSV -> {
                try {
                    val rows = CsvParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
                    Result.success(CsvImportMapper.map(rows))
                } catch (e: Exception) {
                    Result.failure(IllegalArgumentException("Failed to parse CSV file: ${e.message}", e))
                }
            }
        }
    }
}

