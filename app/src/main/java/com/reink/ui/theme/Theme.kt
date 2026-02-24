package com.reink.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val EInkColorScheme = lightColorScheme(
    background = EInkWhite,
    surface = EInkWhite,
    onBackground = EInkBlack,
    onSurface = EInkBlack,
    primary = EInkBlack,
    onPrimary = EInkWhite,
    secondary = EInkDarkGray,
    onSecondary = EInkWhite,
    outline = EInkMediumGray,
    surfaceVariant = EInkOffWhite,
    onSurfaceVariant = EInkDarkGray,
)

// Transparent ripple — no visual ripple on e-ink
@OptIn(ExperimentalMaterial3Api::class)
private val NoRipple = RippleConfiguration(color = Color.Transparent)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReInkTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides NoRipple) {
        MaterialTheme(
            colorScheme = EInkColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}
