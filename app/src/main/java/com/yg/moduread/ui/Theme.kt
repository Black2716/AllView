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

/**
 * 纸张色调叠加在深/浅之上：
 *  - PAPER：原本的纸白 / 夜间灰黑
 *  - SEPIA：米黄纸（只影响浅色；深色模式下等同于 NIGHT，因为暖色底配亮字会发脏）
 *  - NIGHT：更深的纯阅读底（浅色模式也压暗，适合暗环境）
 * 只改 background/surface 四个值，其余角色不动，避免整套配色重新校准。
 */
private fun androidx.compose.material3.ColorScheme.withPaper(theme: ReadingTheme, dark: Boolean): androidx.compose.material3.ColorScheme {
    if (theme == ReadingTheme.PAPER) return this
    return when {
        // 浅色 + 米黄
        !dark && theme == ReadingTheme.SEPIA -> copy(
            background = Color(0xFFF6F1E4),
            surface = Color(0xFFF6F1E4),
            surfaceContainerLow = Color(0xFFF1EBDC),
            surfaceContainer = Color(0xFFEBE4D3),
            surfaceContainerHigh = Color(0xFFE4DCC9),
            surfaceContainerHighest = Color(0xFFDDD4C0)
        )
        // 浅色 + 夜间纸：压到接近深色，但保留一点暖
        !dark && theme == ReadingTheme.NIGHT -> copy(
            background = Color(0xFF1B1A18),
            onBackground = Color(0xFFD9D5CE),
            surface = Color(0xFF1B1A18),
            onSurface = Color(0xFFD9D5CE),
            surfaceContainerLow = Color(0xFF211F1D),
            surfaceContainer = Color(0xFF262421),
            surfaceContainerHigh = Color(0xFF2C2A26),
            surfaceContainerHighest = Color(0xFF33302B),
            surfaceVariant = Color(0xFF262421),
            onSurfaceVariant = Color(0xFFA8A39A),
            outline = Color(0xFF3A3733),
            outlineVariant = Color(0xFF2E2B27)
        )
        // 深色 + 米黄：暖调深底
        dark && theme == ReadingTheme.SEPIA -> copy(
            background = Color(0xFF1E1A14),
            onBackground = Color(0xFFE6DCC8),
            surface = Color(0xFF1E1A14),
            onSurface = Color(0xFFE6DCC8),
            surfaceContainerLow = Color(0xFF241F18),
            surfaceContainer = Color(0xFF2A241C),
            surfaceContainerHigh = Color(0xFF312A20),
            surfaceContainerHighest = Color(0xFF383025),
            onSurfaceVariant = Color(0xFFB8AC95)
        )
        else -> this
    }
}

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
    // 深色底从 #111315 降到 #0E1013：纯黑护眼但纯 #000 在 OLED 上会有拖影，
    // #0E1013 是「够暗但不刺激」的折中
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFE4E4E4),
    surface = Color(0xFF0E1013),
    onSurface = Color(0xFFE4E4E4),
    surfaceVariant = Color(0xFF1C1F23),
    onSurfaceVariant = Color(0xFFB4B4B4),
    surfaceContainerLowest = Color(0xFF0A0C0E),
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
fun AppTheme(
    mode: ThemeMode,
    readingTheme: ReadingTheme = ReadingTheme.PAPER,
    content: @Composable () -> Unit
) {
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
    val base = if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = base.withPaper(readingTheme, dark),
        content = content
    )
}
