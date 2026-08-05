package com.mobileclaude.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

val AppleBlue = Color(0xFF0A84FF)
val Mint = Color(0xFF2ED4A7)
val WarmOrange = Color(0xFFFF9F0A)

private val LightColors = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEFF),
    onPrimaryContainer = Color(0xFF003A69),
    secondary = Mint,
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE9E9ED),
    onSurfaceVariant = Color(0xFF636366),
    outline = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64AFFF),
    onPrimary = Color(0xFF002E50),
    secondary = Mint,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outline = Color(0xFF48484A),
    error = Color(0xFFFF6961),
)

@Composable
fun ClaudeLinkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.systemUiVisibility = if (dark) 0 else 8192
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
