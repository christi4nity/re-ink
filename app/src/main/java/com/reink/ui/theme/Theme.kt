package com.reink.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val ReInkColorScheme = lightColorScheme(
    background = InkWhite,
    surface = InkWhite,
    onBackground = InkBlack,
    onSurface = InkBlack,
    primary = InkTeal,
    onPrimary = InkWhite,
    primaryContainer = InkTealLight,
    onPrimaryContainer = InkTeal,
    secondary = InkOrange,
    onSecondary = InkWhite,
    secondaryContainer = InkOrangeLight,
    onSecondaryContainer = InkOrange,
    tertiary = InkBlue,
    onTertiary = InkWhite,
    tertiaryContainer = InkBlueLight,
    onTertiaryContainer = InkBlue,
    error = InkRed,
    onError = InkWhite,
    errorContainer = InkRedLight,
    onErrorContainer = InkRed,
    outline = InkLightGray,
    outlineVariant = InkLightGray,
    surfaceVariant = InkOffWhite,
    onSurfaceVariant = InkDarkGray,
)

// Transparent ripple — e-ink refreshes make ripples look bad
@OptIn(ExperimentalMaterial3Api::class)
private val NoRipple = RippleConfiguration(color = Color.Transparent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReInkTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides NoRipple) {
        MaterialTheme(
            colorScheme = ReInkColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
