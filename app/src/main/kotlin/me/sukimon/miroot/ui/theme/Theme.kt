package me.sukimon.miroot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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

@Composable
fun HyperRootTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            try {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            } catch (_: Exception) {
                // Some OEM ROMs have incomplete dynamic color resources
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
