package com.aipos.aipospm.security

import java.io.BufferedReader
import java.io.InputStream

object CsvParser {
    /**
     * Parses a CSV input stream into a list of rows, where each row is a list of field values.
     * Automatically strips UTF-8 BOM bytes and handles quoted fields, commas inside quotes,
     * escaped double quotes (""), literal quotes in unquoted fields, and multi-line values.
     */
    fun parse(inputStream: InputStream): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val reader = BufferedReader(inputStream.stripBom())
        
        val currentField = StringBuilder()
        var inQuotes = false
        var currentRecord = mutableListOf<String>()
        
        var isFirstChar = true
        var charCode: Int
        while (reader.read().also { charCode = it } != -1) {
            val c = charCode.toChar()

            // Strip UTF-8 BOM if present at the very start of the stream
            if (isFirstChar) {
                isFirstChar = false
                if (c == '\uFEFF') continue
            }
            
            when {
                c == '"' -> {
                    if (inQuotes) {
                        reader.mark(1)
                        val nextCharCode = reader.read()
                        if (nextCharCode != -1 && nextCharCode.toChar() == '"') {
                            // Escaped double quote inside quotes
                            currentField.append('"')
                        } else {
                            // End of quoted field
                            inQuotes = false
                            if (nextCharCode != -1) {
                                reader.reset()
                            }
                        }
                    } else if (currentField.isEmpty()) {
                        // Start of quoted field (RFC 4180: quotes only open at the start of a field)
                        inQuotes = true
                    } else {
                        // Literal quote inside an unquoted field (e.g., password containing '"')
                        currentField.append(c)
                    }
                }
                c == ',' -> {
                    if (inQuotes) {
                        currentField.append(c)
                    } else {
                        currentRecord.add(currentField.toString())
                        currentField.setLength(0)
                    }
                }
                c == '\r' -> {
                    if (inQuotes) {
                        currentField.append(c)
                    } else {
                        // Support both CRLF (\r\n) and standalone CR (\r) line endings
                        reader.mark(1)
                        val nextCharCode = reader.read()
                        if (nextCharCode != -1 && nextCharCode.toChar() != '\n') {
                            reader.reset()
                        }
                        currentRecord.add(currentField.toString())
                        currentField.setLength(0)
                        result.add(currentRecord)
                        currentRecord = mutableListOf()
                    }
                }
                c == '\n' -> {
                    if (inQuotes) {
                        currentField.append(c)
                    } else {
                        currentRecord.add(currentField.toString())
                        currentField.setLength(0)
                        result.add(currentRecord)
                        currentRecord = mutableListOf()
                    }
                }
                else -> {
                    currentField.append(c)
                }
            }
        }
        
        // Handle last record if not followed by a newline
        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(currentField.toString())
            result.add(currentRecord)
        }
        
        return result
    }
}
