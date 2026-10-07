package com.example.collabpro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Coral,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    outline = Border,
    error = Warning
)

@Composable
fun CollabProTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, typography = Typography, content = content)
}
