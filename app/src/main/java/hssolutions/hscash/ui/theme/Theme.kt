package hssolutions.hscash.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import hssolutions.hscash.ui.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF69B4FF),
    onPrimary = Color(0xFF002F55),
    primaryContainer = Color(0xFF0D3F68),
    onPrimaryContainer = Color(0xFFD2E8FF),
    secondary = Color(0xFF55D89B),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF07543A),
    onSecondaryContainer = Color(0xFFB5F4D4),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF07111F),
    surface = Color(0xFF101C2E),
    surfaceVariant = Color(0xFF18263A),
    onBackground = Color(0xFFF1F5FA),
    onSurface = Color(0xFFF1F5FA),
    onSurfaceVariant = Color(0xFFB7C4D5),
    outline = Color(0xFF718096),
    outlineVariant = Color(0xFF2A3A50),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5F0000),
    errorContainer = Color(0xFF5C1B20),
    onErrorContainer = Color(0xFFFFDAD7)
)

private val LightColorScheme = lightColorScheme(
    primary = HsBlue,
    secondary = HsGreen,
    tertiary = HsBlueLight,
    background = HsBackground,
    surface = HsSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = HsText,
    onSurface = HsText,
    surfaceVariant = Color(0xFFEAF0F6),
    onSurfaceVariant = Color(0xFF52677D),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFFDCE4EC),
    error = HsDanger
)

@Composable
fun HsCashTheme(
    themeMode: String = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (darkTheme) DarkColorScheme else LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
