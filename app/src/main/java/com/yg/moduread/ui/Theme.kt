package com.yg.moduread.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 纸张色调叠加在深/浅之上：
 *  - PAPER：原本的纸白 / 夜间灰黑
 *  - SEPIA：米黄纸（只影响浅色；深色模式下等同于 NIGHT，因为暖色底配亮字会发脏）
 *  - NIGHT：更深的纯阅读底（浅色模式也压暗，适合暗环境）
 *
 * 只改 background/surface 系列与 onSurface/outline，不重算强调色 ——
 * 理由：强调色（primary）是全局品牌色，纸张只是「纸张」，换纸不该换墨水。
 * 但 primaryContainer 等容器色必须跟着底色走，否则米黄纸上会出现冷蓝卡片
 * （改造前就有这个问题，本轮一并修掉）。
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
            surfaceContainerHighest = Color(0xFFDDD4C0),
            // 米黄纸上用暖灰做容器/描边，冷蓝会显得脏
            primaryContainer = Color(0xFFE7DCC2),
            onPrimaryContainer = Color(0xFF3E3220),
            outline = Color(0xFFD3C8B0),
            outlineVariant = Color(0xFFE2D8C2)
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
            // 深底上 primary 必须提亮，否则 0xFF2F6FEB 在 #1B1A18 上对比度不足
            primary = Color(0xFF8FB0F0),
            onPrimary = Color(0xFF0B1220),
            primaryContainer = Color(0xFF2B3A55),
            onPrimaryContainer = Color(0xFFD6E2FA),
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
            onSurfaceVariant = Color(0xFFB8AC95),
            primary = Color(0xFFE6C58F),
            onPrimary = Color(0xFF2A1F0C),
            primaryContainer = Color(0xFF3E3220),
            onPrimaryContainer = Color(0xFFF3E2C4),
            outline = Color(0xFF463D2F),
            outlineVariant = Color(0xFF352E24)
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
    secondaryContainer = Color(0xFFE2EAF4),
    onSecondaryContainer = Color(0xFF1C2C42),
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
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFDADAD2),
    outlineVariant = Color(0xFFE7E7E0),
    // 统一遮罩浓度：所有面板共用，避免有的 0.35 有的默认值
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
    secondaryContainer = Color(0xFF25303D),
    onSecondaryContainer = Color(0xFFCFE0F5),
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
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF2C3035),
    outlineVariant = Color(0xFF23272C),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE4E4E4),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF2F6FEB)
)

/**
 * M3 字阶 ← 令牌。
 *
 * 改造前 MaterialTheme 用的是 M3 默认 Typography（Roboto + 默认字重与行高），
 * 而自绘组件用 Design.kt 的 Type（11~28sp）—— 同一个界面里两套字阶。
 * 这里把 Type 抬进 MaterialTheme，M3 的 Text/Button/TextField 与自绘组件
 * 从此共用一套字号与行高。
 *
 * 行高按 1.45~1.5 倍字号给，中文方块字按 1.35 倍会挤。
 */
val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = Type.display,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontSize = Type.heading,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = TextStyle(
        fontSize = Type.heading,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontSize = Type.title,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = Type.bodyLarge,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    titleSmall = TextStyle(
        fontSize = Type.body,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = Type.bodyLarge,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = Type.body,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = Type.caption,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = Type.body,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontSize = Type.label,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = Type.label,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium
    )
)

/**
 * M3 形状 ← 令牌。
 *
 * 改造前 M3 组件（ModalBottomSheet 28dp、TextField 4dp、Card 12dp）
 * 用的是 M3 默认 Shapes，与 Design.kt 的 Shapes（6/12dp）两套。
 * 现在 extraSmall/small 统一 6dp、medium/large 12dp、extraLarge 28dp，
 * 输入框、卡片、面板、胶囊全部落在同一组圆角上。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.s),
    small = RoundedCornerShape(Radius.s),
    medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.m),
    extraLarge = RoundedCornerShape(Radius.l)
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
    val scheme = (if (dark) DarkColors else LightColors).withPaper(readingTheme, dark)
    // 系统栏图标亮暗必须跟着「最终底色」走，不能跟着 mode 走。
    // 改造前用 !dark 判断：浅色模式 + 夜间纸时底色被压到 #1B1A18，
    // 图标却仍是深色，状态栏文字在深底上几乎看不见。
    val lightBars = scheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
