package com.example.collabpro.auth

import com.example.collabpro.features.identity.application.auth.*
import com.example.collabpro.features.identity.domain.model.AccountType
import org.junit.Assert.*
import org.junit.Test

class AuthInputAndLinkTest {
    @Test fun `registration requires backend compatible password length and name`() {
        assertNotNull(AuthInputValidation.registration(AccountType.CREATOR, "Ana", "ana@example.test", "1234567"))
        assertNull(AuthInputValidation.registration(AccountType.CREATOR, "Ana", "ana@example.test", "12345678"))
        assertNotNull(AuthInputValidation.registration(AccountType.BRAND, " ", "brand@example.test", "a".repeat(129)))
        assertNotNull(AuthInputValidation.registration(AccountType.BRAND, "x".repeat(151), "brand@example.test", "password123"))
    }
    @Test fun `login does not enforce a different minimum than the backend`() {
        assertNull(AuthInputValidation.login("ana@example.test", "short"))
        assertNotNull(AuthInputValidation.login("not-an-email", "password123"))
        assertNotNull(AuthInputValidation.login("ana@example.test", " "))
    }
    @Test fun `reset requires matching passwords and correct field names`() {
        assertNull(AuthInputValidation.reset("password123", "password123"))
        assertTrue(AuthInputValidation.reset("short", "different")!!.fieldErrors.keys.containsAll(listOf("newPassword", "confirmation")))
    }
    @Test fun `fixed recovery URL is parsed without exposing the token`() {
        val link = PasswordResetLink.parse("collabpro://password-reset?token=opaque_test-token") as PasswordResetLink.Valid
        assertEquals("opaque_test-token", link.token)
        assertFalse(link.toString().contains(link.token))
    }
    @Test fun `incomplete duplicate external or oversized recovery links are rejected`() {
        for (url in listOf("collabpro://password-reset", "collabpro://password-reset?token=", "collabpro://password-reset?token=a&token=b",
            "https://example.test/password-reset?token=a", "collabpro://wrong-host?token=a", "collabpro://password-reset/path?token=a",
            "collabpro://user@password-reset?token=a", "collabpro://password-reset?token=a#fragment",
            "collabpro://password-reset?token=" + "a".repeat(129), "collabpro://password-reset?token=%0Asecret", "collabpro://password-reset?token=one+two")) {
            assertEquals(PasswordResetLink.Invalid, PasswordResetLink.parse(url))
        }
    }
}
