package me.sukimon.miroot.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Blue500,
    onPrimary = Grey100,
    secondary = Green500,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Grey100,
    onSurface = Grey100,
    error = Red500
)

private val LightColorScheme = lightColorScheme(
    primary = Blue700,
    onPrimary = Grey100,
    secondary = Green700,
    background = Grey100,
    surface = Grey200,
    onBackground = Grey900,
    onSurface = Grey800,
    error = Red700
)

/**
 * Attempt dynamic color outside of @Composable so try-catch is allowed.
 * Some OEM ROMs (e.g. HyperOS) have incomplete dynamic color resources.
 */
private fun safeDynamicColorScheme(context: Context, dark: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return try {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } catch (_: Exception) {
        null
    }
}

@Composable
fun HyperRootTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> safeDynamicColorScheme(context, darkTheme)
        else -> null
    } ?: if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
