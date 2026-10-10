package com.tvabierta.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Background = Color(0xFF0B0E13)
    val Surface = Color(0xFF151A21)
    val SurfaceHigh = Color(0xFF202731)
    val Focus = Color(0xFF2C3542)
    /** VLC's cone orange: focus rings and highlights. */
    val Accent = Color(0xFFFF8800)
    /** Logo purple (Twitch-like #9146FF), same as the launcher icon and banner. */
    val Brand = Color(0xFF9146FF)
    val TextPrimary = Color(0xFFF1F3F6)
    val TextSecondary = Color(0xFF9AA4B1)
    val LogoPlate = Color(0xFFE4E7EB)
    val Danger = Color(0xFFFF6B6B)
}

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.Black,
            secondary = Palette.Accent,
            background = Palette.Background,
            onBackground = Palette.TextPrimary,
            surface = Palette.Surface,
            onSurface = Palette.TextPrimary,
            surfaceVariant = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.TextSecondary,
            error = Palette.Danger,
        ),
        content = content,
    )
}
