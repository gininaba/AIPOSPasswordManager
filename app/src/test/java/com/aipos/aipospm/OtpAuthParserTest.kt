package com.aipos.aipospm

import com.aipos.aipospm.ui.screens.parseOtpAuthUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpAuthParserTest {

    @Test
    fun testValidOtpAuthUri() {
        val uri = "otpauth://totp/GitHub:octocat?secret=JBSW43DPEHPK3PXP&issuer=GitHub"
        val parsed = parseOtpAuthUri(uri)
        
        org.junit.Assert.assertNotNull(parsed)
        val (secret, issuer, label) = parsed!!
        assertEquals("JBSW43DPEHPK3PXP", secret)
        assertEquals("GitHub", issuer)
        assertEquals("octocat", label)
    }

    @Test
    fun testValidOtpAuthUriWithoutIssuerInPath() {
        val uri = "otpauth://totp/octocat?secret=JBSW43DPEHPK3PXP&issuer=GitHub"
        val parsed = parseOtpAuthUri(uri)
        
        org.junit.Assert.assertNotNull(parsed)
        val (secret, issuer, label) = parsed!!
        assertEquals("JBSW43DPEHPK3PXP", secret)
        assertEquals("GitHub", issuer)
        assertEquals("octocat", label)
    }

    @Test
    fun testValidOtpAuthUriWithUrlEncoding() {
        val uri = "otpauth://totp/Google%3Atest%40gmail.com?secret=JBSW43DPEHPK3PXP&issuer=Google"
        val parsed = parseOtpAuthUri(uri)
        
        org.junit.Assert.assertNotNull(parsed)
        val (secret, issuer, label) = parsed!!
        assertEquals("JBSW43DPEHPK3PXP", secret)
        assertEquals("Google", issuer)
        assertEquals("test@gmail.com", label)
    }

    @Test
    fun testInvalidOtpAuthUri() {
        val uri1 = "http://google.com"
        assertNull(parseOtpAuthUri(uri1))

        val uri2 = "otpauth://hotp/test?secret=123" // we only support totp
        assertNull(parseOtpAuthUri(uri2))
    }
}
