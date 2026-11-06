package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * «Наследие Nerpa» — the brand palette of the consumer client: graphite base, one cyan accent,
 * warm paper in light mode. Values are the design tokens, not Material defaults; a role kept at the
 * Material default would show upstream blue next to the cyan status colour.
 */
private val LightColor = lightColorScheme(
    primary = Color(0xFF0E7490), // Cyan 700
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFCDEEF7), // Pale Cyan
    onPrimaryContainer = Color(0xFF00343F), // Deep Teal
    secondary = Color(0xFF0B5E75), // Cyan 800
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFD3EFF7), // Pale Cyan
    onSecondaryContainer = Color(0xFF00343F), // Deep Teal
    tertiary = Color(0xFF78716C), // Stone 500
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFE7E5E4), // Light Stone
    onTertiaryContainer = Color(0xFF1C1917), // Stone 900
    error = Color(0xFFB91C1C), // Red 700
    errorContainer = Color(0xFFFCD9D9), // Pale Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF4A0B0B), // Deep Red
    background = Color(0xFFFAF8F5), // Paper
    onBackground = Color(0xFF1C1917), // Stone 900
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF1C1917), // Stone 900
    surfaceVariant = Color(0xFFF2EFE9), // Warm Paper Gray
    onSurfaceVariant = Color(0xFF57534E), // Stone 600
    outline = Color(0xFFA8A29E), // Stone 400
    outlineVariant = Color(0xFFE0DBD1), // Sand Border
    inverseSurface = Color(0xFF1C1917), // Stone 900
    inverseOnSurface = Color(0xFFFAF8F5), // Paper
    inversePrimary = Color(0xFF67E8F9), // Cyan 300
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF0E7490), // Cyan 700
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFFCFAF7), // Off-white Paper
    surfaceContainer = Color(0xFFF7F4EF), // Paper
    surfaceContainerHigh = Color(0xFFF2EFE9), // Warm Paper Gray
    surfaceContainerHighest = Color(0xFFEAE6DE), // Deep Paper Gray
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF06B6D4), // Cyan 500
    onPrimary = Color(0xFF06171B), // Near-black Teal
    primaryContainer = Color(0xFF0B3A46), // Dark Cyan
    onPrimaryContainer = Color(0xFF67E8F9), // Cyan 300
    secondary = Color(0xFF22D3EE), // Cyan 400
    onSecondary = Color(0xFF00363F), // Dark Cyan
    secondaryContainer = Color(0xFF004E5C), // Deep Cyan
    onSecondaryContainer = Color(0xFFA6F2FF), // Light Cyan
    tertiary = Color(0xFF94A3B8), // Slate 400
    onTertiary = Color(0xFF0F172A), // Slate 900
    tertiaryContainer = Color(0xFF1E293B), // Slate 800
    onTertiaryContainer = Color(0xFFCBD5E1), // Slate 300
    error = Color(0xFFF87171), // Red 400
    errorContainer = Color(0xFF5A1E22), // Dark Red
    onError = Color(0xFF300C0E), // Near-black Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Color(0xFF09090B), // Ink
    onBackground = Color(0xFFE4E4E7), // Zinc 200
    surface = Color(0xFF0B0B0D), // Graphite
    onSurface = Color(0xFFE4E4E7), // Zinc 200
    surfaceVariant = Color(0xFF232328), // Raised Graphite
    onSurfaceVariant = Color(0xFFA1A1AA), // Zinc 400
    outline = Color(0xFF3F3F46), // Zinc 700
    outlineVariant = Color(0xFF27272A), // Zinc 800
    inverseSurface = Color(0xFFE4E4E7), // Zinc 200
    inverseOnSurface = Color(0xFF18181B), // Card Graphite
    inversePrimary = Color(0xFF0E7490), // Cyan 700
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF06B6D4), // Cyan 500
    surfaceContainerLowest = Color(0xFF060608), // Deepest Ink
    surfaceContainerLow = Color(0xFF0B0B0D), // Graphite
    surfaceContainer = Color(0xFF111114), // Shell Graphite
    surfaceContainerHigh = Color(0xFF18181B), // Card Graphite
    surfaceContainerHighest = Color(0xFF232328), // Raised Graphite
)

// Semantic colors. Text roles are theme-aware: one mid-cyan is legible on graphite but not on paper.
val colorPingLight = Color(0xFF047857) // Emerald 700
val colorPingDark = Color(0xFF34D399) // Emerald 400
val colorPingRedLight = Color(0xFFB91C1C) // Red 700
val colorPingRedDark = Color(0xFFF87171) // Red 400
val colorConfigTypeLight = Color(0xFF0E7490) // Cyan 700
val colorConfigTypeDark = Color(0xFF22D3EE) // Cyan 400
val colorFabActive = Color(0xFF06B6D4) // Cyan 500
val colorFabInactiveLight = Color(0xFFA8A29E) // Stone 400
val colorFabInactiveDark = Color(0xFF3F3F46) // Zinc 700
val dividerColorLight = Color(0xFFE0DBD1) // Sand Border
val dividerColorDark = Color(0xFF27272A) // Zinc 800

// Toast Colors 85%
val toastNormalBgLight = Color(0xD957534E) // Stone 600
val toastNormalBgDark = Color(0xD9232328) // Raised Graphite
val toastSuccessBg = Color(0xD9047857) // Emerald 700
val toastErrorBg = Color(0xD9B91C1C) // Red 700
val toastInfoBg = Color(0xD90E7490) // Cyan 700
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Material You replaces the whole brand palette, so the brand ships with it off; the setting
    // stays available and is still honoured when the user turns it on.
    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
