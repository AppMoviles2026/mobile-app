package com.example.collabpro.identity

import com.example.collabpro.features.identity.application.profile.CreatorProfileValidation
import com.example.collabpro.features.identity.application.social.SocialAuthorizationReturn
import com.example.collabpro.features.identity.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.util.UUID

class ProfileValidationAndSocialLinkTest {
    @Test fun `all backend profile length constraints are enforced`() {
        val input = CreatorProfileUpdate("a".repeat(151), "b".repeat(2001), "n".repeat(151), "a".repeat(2001), "l".repeat(151))
        assertEquals(setOf("displayName", "biography", "niche", "audienceDescription", "location"), CreatorProfileValidation.validate(input)!!.fieldErrors.keys)
    }
    @Test fun `optional empty profile fields and exact limits are valid`() {
        assertNull(CreatorProfileValidation.validate(CreatorProfileUpdate("Nombre")))
        assertNull(CreatorProfileValidation.validate(CreatorProfileUpdate("a".repeat(150), "b".repeat(2000), "n".repeat(150), "a".repeat(2000), "l".repeat(150))))
        assertTrue(CreatorProfileValidation.validate(CreatorProfileUpdate(" "))!!.fieldErrors.containsKey("displayName"))
    }
    @Test fun `only exact opaque UUID return is accepted`() {
        val id = UUID.randomUUID()
        assertEquals(id, SocialAuthorizationReturn.parse("collabpro://social-authorization-completed?authorizationId=$id"))
        assertEquals(id, SocialAuthorizationReturn.parse("collabpro://social-authorization-completed/?authorizationId=$id"))
    }
    @Test fun `foreign extra secret duplicate malformed return links are rejected`() {
        val id = UUID.randomUUID()
        listOf("https://social-authorization-completed?authorizationId=$id", "collabpro://password-reset?authorizationId=$id",
            "collabpro://social-authorization-completed/path?authorizationId=$id", "collabpro://social-authorization-completed?authorizationId=$id&state=secret",
            "collabpro://social-authorization-completed?authorizationId=$id&code=secret", "collabpro://social-authorization-completed?authorizationId=$id#fragment",
            "collabpro://user@social-authorization-completed?authorizationId=$id", "collabpro://social-authorization-completed:12?authorizationId=$id",
            "collabpro://social-authorization-completed?authorizationId=$id&authorizationId=$id", "collabpro://social-authorization-completed?authorizationId=1-1-1-1-1",
            "collabpro://social-authorization-completed?authorizationId=%20$id").forEach { assertNull(it, SocialAuthorizationReturn.parse(it)) }
    }
    @Test fun `browser must use requested official provider with HTTPS`() {
        assertTrue(SocialAuthorizationReturn.trustedBrowserUrl(URI("https://www.instagram.com/oauth/authorize?state=test"), SocialPlatform.INSTAGRAM))
        assertTrue(SocialAuthorizationReturn.trustedBrowserUrl(URI("https://www.tiktok.com/v2/auth/authorize/"), SocialPlatform.TIKTOK))
        listOf("http://www.instagram.com/", "https://www.instagram.com.evil.example/", "https://www.instagram.com:444/", "https://www.tiktok.com/",
            "https://user@www.instagram.com/", "https://www.instagram.com/#fragment").forEach {
            assertFalse(it, SocialAuthorizationReturn.trustedBrowserUrl(URI(it), SocialPlatform.INSTAGRAM))
        }
    }
}
