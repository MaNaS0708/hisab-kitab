package com.example.hisabkitab

import com.example.hisabkitab.util.UsernameUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameUtilTest {

    @Test
    fun testNormalization() {
        assertEquals("john_doe", UsernameUtil.normalize("  John_Doe  "))
        assertEquals("user123", UsernameUtil.normalize("USER123"))
    }

    @Test
    fun testValidationValidUsernames() {
        assertTrue(UsernameUtil.isValid("john_doe"))
        assertTrue(UsernameUtil.isValid("abc"))
        assertTrue(UsernameUtil.isValid("user_123_test_name"))
    }

    @Test
    fun testValidationInvalidUsernames() {
        // Too short (< 3)
        assertFalse(UsernameUtil.isValid("ab"))
        // Too long (> 20)
        assertFalse(UsernameUtil.isValid("a_very_long_username_that_exceeds"))
        // Invalid characters (spaces, special chars)
        assertFalse(UsernameUtil.isValid("john doe"))
        assertFalse(UsernameUtil.isValid("john@doe"))
        assertFalse(UsernameUtil.isValid("john-doe"))
    }
}
