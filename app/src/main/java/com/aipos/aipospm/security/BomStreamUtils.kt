package com.aipos.aipospm.security

import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.Reader

private const val UTF8_BOM_CHAR = '\uFEFF'

/**
 * Wraps an [InputStream] and strips a leading UTF-8 Byte Order Mark (0xEF, 0xBB, 0xBF)
 * if present, returning a UTF-8 [Reader]. Safe for streams shorter than 3 bytes.
 */
fun InputStream.stripBom(): Reader {
    val pushback = PushbackInputStream(this, 3)
    val bom = ByteArray(3)
    var totalRead = 0
    while (totalRead < 3) {
        val n = pushback.read(bom, totalRead, 3 - totalRead)
        if (n == -1) break
        totalRead += n
    }
    val hasUtf8Bom = totalRead == 3 &&
        bom[0] == 0xEF.toByte() &&
        bom[1] == 0xBB.toByte() &&
        bom[2] == 0xBF.toByte()
    if (!hasUtf8Bom && totalRead > 0) {
        pushback.unread(bom, 0, totalRead)
    }
    return InputStreamReader(pushback, Charsets.UTF_8)
}

/**
 * Strips a leading UTF-8 BOM character ('\uFEFF') from a string if present.
 */
fun String.stripBom(): String = removePrefix(UTF8_BOM_CHAR.toString())

