package com.example.collabpro

import android.os.Bundle
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.collabpro.features.identity.presentation.auth.AuthenticationViewModel
import com.example.collabpro.features.identity.presentation.profile.CreatorIdentityViewModel
import com.example.collabpro.features.campaign.presentation.manage.BrandCampaignViewModel
import com.example.collabpro.features.campaign.presentation.discovery.CampaignDiscoveryViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.collabpro.navigation.CollabApp
import com.example.collabpro.ui.theme.CollabProTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authentication: AuthenticationViewModel by viewModels()
    private val creatorIdentity: CreatorIdentityViewModel by viewModels()
    private val brandCampaigns: BrandCampaignViewModel by viewModels()
    private val campaignDiscovery: CampaignDiscoveryViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveIdentityLink(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                creatorIdentity.browserEvents.collect { event ->
                    if (creatorIdentity.canOpenBrowser(event)) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.uri.toString())).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch (_: ActivityNotFoundException) { creatorIdentity.browserUnavailable(event) }
                        catch (_: SecurityException) { creatorIdentity.browserUnavailable(event) }
                    }
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            CollabProTheme { CollabApp(authentication, creatorIdentity, brandCampaigns, campaignDiscovery) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveIdentityLink(intent)
        setIntent(Intent(intent).apply { data = null })
    }

    override fun onResume() {
        super.onResume()
        authentication.checkExpiry()
        creatorIdentity.onResume()
        campaignDiscovery.onResume()
    }

    override fun onStop() {
        campaignDiscovery.onBackground()
        super.onStop()
    }

    private fun receiveIdentityLink(incoming: Intent?) {
        if (incoming?.action == Intent.ACTION_VIEW) {
            val raw = incoming.dataString
            if (raw != null && incoming.data?.scheme == "collabpro") when (incoming.data?.host) {
                "password-reset" -> authentication.openPasswordReset(raw)
                "social-authorization-completed" -> creatorIdentity.receiveAuthorizationReturn(raw)
            }
        }
        incoming?.data = null // Do not keep a recovery token in the activity's saved/restored intent.
    }
}

