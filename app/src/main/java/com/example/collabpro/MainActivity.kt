package com.example.collabpro

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.collabpro.features.identity.presentation.auth.AuthenticationViewModel
import com.example.collabpro.navigation.CollabApp
import com.example.collabpro.ui.theme.CollabProTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authentication: AuthenticationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveResetLink(intent)
        enableEdgeToEdge()
        setContent {
            CollabProTheme { CollabApp(authentication) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveResetLink(intent)
        setIntent(Intent(intent).apply { data = null })
    }

    override fun onResume() {
        super.onResume()
        authentication.checkExpiry()
    }

    private fun receiveResetLink(incoming: Intent?) {
        if (incoming?.action == Intent.ACTION_VIEW) incoming.dataString?.let(authentication::openPasswordReset)
        incoming?.data = null // Do not keep a recovery token in the activity's saved/restored intent.
    }
}

