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
    background = Color(0xFFFCFCFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFCFCFA),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF2F2ED),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFFDADAD2)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF0B1220),
    background = Color(0xFF111315),
    onBackground = Color(0xFFE4E4E4),
    surface = Color(0xFF111315),
    onSurface = Color(0xFFE4E4E4),
    surfaceVariant = Color(0xFF1C1F23),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF2C3035)
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
