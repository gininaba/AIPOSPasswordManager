package com.aipos.aipospm.security

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {
    /**
     * Parses a CSV input stream into a list of rows, where each row is a list of field values.
     * Correctly handles quoted fields, commas inside quotes, escaped double quotes (""), and multi-line values.
     */
    fun parse(inputStream: InputStream): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        
        val currentField = StringBuilder()
        var inQuotes = false
        var currentRecord = mutableListOf<String>()
        
        var charCode: Int
        while (reader.read().also { charCode = it } != -1) {
            val c = charCode.toChar()
            
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
                    } else {
                        // Start of quoted field
                        inQuotes = true
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
