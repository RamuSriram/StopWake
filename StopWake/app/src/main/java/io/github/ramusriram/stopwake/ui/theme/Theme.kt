package io.github.ramusriram.stopwake.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = StopWakeBlueBright,
    onPrimary = Color.White,
    primaryContainer = StopWakeBlueContainerDark,
    onPrimaryContainer = StopWakeBlueOnContainerDark,
    secondary = StopWakeBlueBright,
    onSecondary = Color.White,
    secondaryContainer = StopWakeBlueContainerDark,
    onSecondaryContainer = StopWakeBlueOnContainerDark,
    tertiary = WarningAmber,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = WarningAmberDark,
    onTertiaryContainer = WarningAmberLight,
    error = Color(0xFFF87171),
    onError = Color.White,
    errorContainer = DangerCrimsonDark,
    onErrorContainer = DangerCrimsonLight,
    background = SlateBackgroundDark,
    onBackground = SlateTextPrimaryDark,
    surface = SlateSurfaceDark,
    onSurface = SlateTextPrimaryDark,
    surfaceVariant = SlateSurfaceVariantDark,
    onSurfaceVariant = SlateTextSecondaryDark,
    outline = Color(0xFF334155)
)

private val LightColorScheme = lightColorScheme(
    primary = StopWakeBlue,
    onPrimary = Color.White,
    primaryContainer = StopWakeBlueContainerLight,
    onPrimaryContainer = StopWakeBlueOnContainerLight,
    secondary = StopWakeBlue,
    onSecondary = Color.White,
    secondaryContainer = StopWakeBlueContainerLight,
    onSecondaryContainer = StopWakeBlueOnContainerLight,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    tertiaryContainer = WarningAmberLight,
    onTertiaryContainer = WarningAmberDark,
    error = DangerCrimson,
    onError = Color.White,
    errorContainer = DangerCrimsonLight,
    onErrorContainer = DangerCrimsonDark,
    background = SlateBackgroundLight,
    onBackground = SlateTextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = SlateTextPrimaryLight,
    surfaceVariant = SlateSurfaceVariantLight,
    onSurfaceVariant = SlateTextSecondaryLight,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun StopWakeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.surface.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}