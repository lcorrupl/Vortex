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

private val LightColorScheme =
  lightColorScheme(
    primary = VortexPrimary,
    secondary = VortexSecondary,
    tertiary = VortexTertiary,
    background = VortexBackground,
    surface = VortexSurface,
    onPrimary = Color.White,
    onSecondary = VortexTextPrimary,
    onTertiary = Color.White,
    onBackground = VortexTextPrimary,
    onSurface = VortexTextPrimary,
    surfaceVariant = VortexSurfaceVariant,
    onSurfaceVariant = VortexTextSecondary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Set to false to default to High Density Premium Light View
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
