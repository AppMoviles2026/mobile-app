package com.example.collabpro.features.identity.infrastructure.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import com.example.collabpro.features.identity.application.social.SocialAuthorizationReturn
import com.example.collabpro.features.identity.domain.model.SocialPlatform
import java.net.URI

/** Only a provider URL goes to the browser. No CollabPro headers, credentials or callback replay. */
object SocialCustomTabs {
    internal fun intent(browserPackage: String): CustomTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .build().apply { intent.setPackage(browserPackage) }

    fun open(activity: Activity, uri: URI, platform: SocialPlatform): Boolean {
        if (!SocialAuthorizationReturn.trustedBrowserUrl(uri, platform)) return false
        return try {
            val browserPackage = CustomTabsClient.getPackageName(activity, emptyList()) ?: return false
            intent(browserPackage).launchUrl(activity, Uri.parse(uri.toString()))
            true
        } catch (_: ActivityNotFoundException) { false }
        catch (_: SecurityException) { false }
    }
}
