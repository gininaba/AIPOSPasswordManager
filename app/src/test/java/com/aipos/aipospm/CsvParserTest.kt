package com.aipos.aipospm

import com.aipos.aipospm.security.CsvParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class CsvParserTest {

    @Test
    fun testSimpleCsv() {
        val csvData = "header1,header2\nvalue1,value2"
        val stream = ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8))
        val parsed = CsvParser.parse(stream)
        
        assertEquals(2, parsed.size)
        assertEquals(listOf("header1", "header2"), parsed[0])
        assertEquals(listOf("value1", "value2"), parsed[1])
    }

    @Test
    fun testQuotedFields() {
        val csvData = "\"header,one\",header2\n\"value,one\",value2"
        val stream = ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8))
        val parsed = CsvParser.parse(stream)
        
        assertEquals(2, parsed.size)
        assertEquals(listOf("header,one", "header2"), parsed[0])
        assertEquals(listOf("value,one", "value2"), parsed[1])
    }

    @Test
    fun testEscapedQuotes() {
        val csvData = "\"header \"\"one\"\"\",header2\n\"value \"\"one\"\"\",value2"
        val stream = ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8))
        val parsed = CsvParser.parse(stream)
        
        assertEquals(2, parsed.size)
        assertEquals(listOf("header \"one\"", "header2"), parsed[0])
        assertEquals(listOf("value \"one\"", "value2"), parsed[1])
    }

    @Test
    fun testMultilineField() {
        val csvData = "\"header\nwith\nnewline\",header2\nvalue1,value2"
        val stream = ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8))
        val parsed = CsvParser.parse(stream)
        
        assertEquals(2, parsed.size)
        assertEquals(listOf("header\nwith\nnewline", "header2"), parsed[0])
        assertEquals(listOf("value1", "value2"), parsed[1])
    }
}
