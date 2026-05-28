package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val ProfessionalDarkColorScheme = darkColorScheme(
    primary = Blue400,
    onPrimary = Slate900,
    secondary = Blue500,
    onSecondary = Color.White,
    tertiary = TextGray,
    background = Slate900,
    onBackground = Color.White,
    surface = Slate800,
    onSurface = Color.White,
    surfaceVariant = Slate700,
    onSurfaceVariant = TextGrayLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors for custom tech theme
    content: @Composable () -> Unit,
) {
    val colorScheme = ProfessionalDarkColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
