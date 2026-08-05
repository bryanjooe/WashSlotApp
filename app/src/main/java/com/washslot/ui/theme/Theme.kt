package com.washslot.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val BitStakColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextWhite,
    secondary = LightBlueAccent,
    onSecondary = DarkBlueBg,
    tertiary = AmberAccent,
    background = DarkBlueBg,
    onBackground = TextWhite,
    surface = CardNavy,
    onSurface = TextWhite,
    surfaceVariant = CardNavy,
    onSurfaceVariant = TextGrey,
    outline = TextGrey
)

@Composable
fun WashSlotTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> BitStakColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
