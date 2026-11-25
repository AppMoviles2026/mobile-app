package com.example.collabpro.features.billing.infrastructure.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import com.example.collabpro.features.billing.application.PayPalSandboxLinks

object PayPalCustomTabs {
    fun open(activity: Activity, raw: String): Boolean {
        val uri = PayPalSandboxLinks.approval(raw) ?: return false
        return try {
            val browser = CustomTabsClient.getPackageName(activity, emptyList()) ?: return false
            val tab = CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build()
            tab.intent.setPackage(browser)
            tab.launchUrl(activity, Uri.parse(uri.toString()))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
