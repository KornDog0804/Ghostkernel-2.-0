package com.github.soundpod.ui.styling

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.core.ui.BuiltInFontFamily.System
import com.github.core.ui.ColorMode
import com.github.core.ui.ColorSource
import com.github.core.ui.Darkness
import com.github.core.ui.LocalAppearance
import com.github.core.ui.appearance

private val GhostKernelColorScheme = darkColorScheme(
    primary = Color(0xFF7FD41A),
    onPrimary = Color(0xFF0a0a0a),
    primaryContainer = Color(0xFF1a0a2e),
    onPrimaryContainer = Color(0xFF7FD41A),
    secondary = Color(0xFF9B59B6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2d1060),
    onSecondaryContainer = Color(0xFFD4A8FF),
    background = Color(0xFF0d0517),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1a0a2e),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2a1040),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF7FD41A),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF1a0a2e),
    inversePrimary = Color(0xFF3a7a00),
)

private val PureBlackColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White
)

private val OffsetWhiteColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color(0xFFF6F6F8),
    onBackground = Color.Black,
    surface = Color(0xFFF6F6F8),
    onSurface = Color.Black
)

private val MaterialDarkScheme = darkColorScheme(
    background = Color(0xFF121212),
    surface = Color(0xFF121212)
)

private val MaterialLightScheme = lightColorScheme(
    background = Color.White,
    surface = Color.White
)

@Composable
fun AppTheme(
    usePureBlack: Boolean = false,
    useMaterialNeutral: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        useMaterialNeutral && darkTheme -> MaterialDarkScheme
        useMaterialNeutral && !darkTheme -> MaterialLightScheme

        usePureBlack && darkTheme -> PureBlackColorScheme

        darkTheme -> GhostKernelColorScheme
        else -> OffsetWhiteColorScheme
    }

    val materialAccentColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicLightColorScheme(context).primary
    } else {
        null
    }

    val appearance = appearance(
        source = if (materialAccentColor != null) ColorSource.MaterialYou else ColorSource.Default,
        mode = if (darkTheme) ColorMode.Dark else ColorMode.Light,
        darkness = if (usePureBlack && darkTheme) Darkness.AMOLED else Darkness.Normal,
        materialAccentColor = materialAccentColor,
        sampleBitmap = null,
        fontFamily = System,
        applyFontPadding = true,
        thumbnailRoundness = 8.dp
    )

    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(
            colorScheme = baseColorScheme,
            typography = Typography,
            content = content
        )
    }

}
