package com.yg.moduread.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// 纸感浅色 / 夜间深色，两组配色都保证正文对比度 > 12:1
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FEB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FB),
    onPrimaryContainer = Color(0xFF12325F),
    // 次级强调：分类、行内标签。去饱和的主色，不抢正文注意力
    secondary = Color(0xFF5B7CA8),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF8A6A3B),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFCFCFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFCFCFA),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF2F2ED),
    onSurfaceVariant = Color(0xFF4A4A4A),
    // M3 3.x 的面板/卡片底：比 surfaceVariant 更贴近纸面层次
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F3),
    surfaceContainer = Color(0xFFF2F2ED),
    surfaceContainerHigh = Color(0xFFECECE6),
    surfaceContainerHighest = Color(0xFFE6E6E0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFDADAD2),
    outlineVariant = Color(0xFFE7E7E0),
    // 统一遮罩浓度：三个面板共用，避免有的 0.35 有的默认值
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C2C2C),
    inverseOnSurface = Color(0xFFF2F2ED),
    inversePrimary = Color(0xFF9DBFF7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF1B355E),
    onPrimaryContainer = Color(0xFFCFE0FF),
    secondary = Color(0xFF93B4DC),
    onSecondary = Color(0xFF0B1220),
    tertiary = Color(0xFFD9B98A),
    onTertiary = Color(0xFF231A0A),
    background = Color(0xFF111315),
    onBackground = Color(0xFFE4E4E4),
    surface = Color(0xFF111315),
    onSurface = Color(0xFFE4E4E4),
    surfaceVariant = Color(0xFF1C1F23),
    onSurfaceVariant = Color(0xFFB4B4B4),
    surfaceContainerLowest = Color(0xFF0C0E10),
    surfaceContainerLow = Color(0xFF15181B),
    surfaceContainer = Color(0xFF1C1F23),
    surfaceContainerHigh = Color(0xFF23272C),
    surfaceContainerHighest = Color(0xFF2A2F35),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    outline = Color(0xFF2C3035),
    outlineVariant = Color(0xFF23272C),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE4E4E4),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF2F6FEB)
)

/** Compose 的 LocalView 上下文可能被主题包装过，逐层解包拿到真正承载窗口的 Activity。 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? android.app.Activity
}

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
