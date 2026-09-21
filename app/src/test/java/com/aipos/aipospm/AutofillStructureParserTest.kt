package com.aipos.aipospm

import android.text.InputType
import android.view.View
import com.aipos.aipospm.autofill.AutofillStructureParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillStructureParserTest {

    @Test
    fun testPasswordFieldByHint() {
        val hints = arrayOf(View.AUTOFILL_HINT_PASSWORD)
        assertTrue(AutofillStructureParser.isPasswordField(hints, InputType.TYPE_CLASS_TEXT, null, null))
        assertFalse(AutofillStructureParser.isUsernameField(hints, InputType.TYPE_CLASS_TEXT, null, null))
    }

    @Test
    fun testPasswordFieldByInputType() {
        val passwordType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertTrue(AutofillStructureParser.isPasswordField(null, passwordType, null, null))
        assertFalse(AutofillStructureParser.isUsernameField(null, passwordType, null, null))

        val webPasswordType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertTrue(AutofillStructureParser.isPasswordField(null, webPasswordType, null, null))

        val pinPasswordType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertTrue(AutofillStructureParser.isPasswordField(null, pinPasswordType, null, null))
    }

    @Test
    fun testPasswordFieldByIdAndHintText() {
        assertTrue(AutofillStructureParser.isPasswordField(null, InputType.TYPE_CLASS_TEXT, "login_pwd_input", null))
        assertTrue(AutofillStructureParser.isPasswordField(null, InputType.TYPE_CLASS_TEXT, null, "Enter account passcode"))
    }

    @Test
    fun testUsernameFieldByHint() {
        val hints = arrayOf(View.AUTOFILL_HINT_USERNAME)
        assertTrue(AutofillStructureParser.isUsernameField(hints, InputType.TYPE_CLASS_TEXT, null, null))
        assertFalse(AutofillStructureParser.isPasswordField(hints, InputType.TYPE_CLASS_TEXT, null, null))

        val emailHints = arrayOf(View.AUTOFILL_HINT_EMAIL_ADDRESS)
        assertTrue(AutofillStructureParser.isUsernameField(emailHints, InputType.TYPE_CLASS_TEXT, null, null))
    }

    @Test
    fun testUsernameFieldByInputType() {
        val emailType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertTrue(AutofillStructureParser.isUsernameField(null, emailType, null, null))
        assertFalse(AutofillStructureParser.isPasswordField(null, emailType, null, null))
    }

    @Test
    fun testUsernameFieldByIdAndHintText() {
        assertTrue(AutofillStructureParser.isUsernameField(null, InputType.TYPE_CLASS_TEXT, "user_email_address", null))
        assertTrue(AutofillStructureParser.isUsernameField(null, InputType.TYPE_CLASS_TEXT, null, "Phone or Username"))
    }

    @Test
    fun testGenericTextField() {
        assertFalse(AutofillStructureParser.isPasswordField(null, InputType.TYPE_CLASS_TEXT, "notes_content", "Write notes here"))
        assertFalse(AutofillStructureParser.isUsernameField(null, InputType.TYPE_CLASS_TEXT, "notes_content", "Write notes here"))
    }
}
