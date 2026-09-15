package hssolutions.hscash.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = HsGreenLight,
    secondary = HsBlueLight,
    tertiary = HsGreen,
    background = HsBlue,
    surface = Color(0xFF102A43),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
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
    onSurface = HsText
)

@Composable
fun HsCashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> LightColorScheme
        // As telas atuais usam superfícies claras fixas; manter a paleta clara evita texto branco em fundo claro.
        darkTheme -> LightColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
