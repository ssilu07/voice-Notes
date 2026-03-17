package com.royals.voicenotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Primary500,
    onPrimary = Color.White,
    primaryContainer = Primary100,
    onPrimaryContainer = Primary900,
    secondary = Secondary500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFFE65100),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = LightTextSecondary,
    error = Red600,
    onError = Color.White,
    outline = LightDivider,
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary400,
    onPrimary = Primary900,
    primaryContainer = Primary700,
    onPrimaryContainer = Primary100,
    secondary = Secondary500,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5C3D00),
    onSecondaryContainer = Color(0xFFFFE0B2),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = DarkTextSecondary,
    error = Red500,
    onError = Color.White,
    outline = DarkDivider,
)

@Composable
fun VoiceNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
