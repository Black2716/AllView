package com.yg.moduread.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 阅读设置。
 *
 * 改造前是手写 Box + Color.Black(0.35f) 遮罩自制的底部面板，
 * 和目录/搜索用的 ModalBottomSheet 是两套观感（无拖拽手柄、圆角动画、遮罩浓度都不同）。
 * 现在统一成 ModalBottomSheet，三个面板同一套动效与遮罩。
 *
 * 第二轮外观改造：
 *  - 三张卡片原本各写一遍 clip+background+padding，改用 SettingCard（带 1dp 描边分层）；
 *  - 外观/纸张的六个胶囊原本是两份几乎相同的 ThemeChip / PaperChip，
 *    高度约 36dp、选中态只换底色、读屏读不出选中 —— 统一换 ChoiceChip；
 *  - 面板原先没有标题栏和关闭键，只能下滑收起，补 SheetHeader。
 */
@Composable
fun SettingsSheet(
    prefs: ReaderPrefs,
    onFontChange: (Float) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onReadingThemeChange: (ReadingTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AppSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            SheetHeader(title = "阅读设置", onClose = onDismiss)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Sp.l)
                    .padding(bottom = Sp.xl)
            ) {
                SettingCard(title = "字号") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prefs.fontSizeSp.toInt().toString() + " sp",
                            fontSize = Type.body,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "预览文字大小",
                            fontSize = Type.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 两端放 A 图标，比光秃秃一条轨道更直观
                        Text(
                            text = "A",
                            fontSize = Type.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = prefs.fontSizeSp,
                            onValueChange = onFontChange,
                            valueRange = ReaderPrefs.MIN_FONT..ReaderPrefs.MAX_FONT,
                            steps = (ReaderPrefs.MAX_FONT - ReaderPrefs.MIN_FONT).toInt() - 1,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Sp.s)
                        )
                        Text(
                            text = "A",
                            fontSize = Type.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(Sp.m))

                SettingCard(title = "外观") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                        ThemeChip("跟随系统", ThemeMode.SYSTEM, prefs.themeMode, onThemeChange, Modifier.weight(1f))
                        ThemeChip("浅色", ThemeMode.LIGHT, prefs.themeMode, onThemeChange, Modifier.weight(1f))
                        ThemeChip("深色", ThemeMode.DARK, prefs.themeMode, onThemeChange, Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(Sp.m))

                SettingCard(title = "纸张") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                        PaperChip("纸白", ReadingTheme.PAPER, prefs.readingTheme, onReadingThemeChange, Modifier.weight(1f))
                        PaperChip("米黄", ReadingTheme.SEPIA, prefs.readingTheme, onReadingThemeChange, Modifier.weight(1f))
                        PaperChip("夜间", ReadingTheme.NIGHT, prefs.readingTheme, onReadingThemeChange, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 纸张色调选项。选中态同时画出该纸张的底色和描边，比纯文字更直观。 */
@Composable
private fun PaperChip(
    label: String,
    theme: ReadingTheme,
    current: ReadingTheme,
    onSelect: (ReadingTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val swatch = when (theme) {
        ReadingTheme.PAPER -> Color(0xFFFCFCFA)
        ReadingTheme.SEPIA -> Color(0xFFF6F1E4)
        ReadingTheme.NIGHT -> Color(0xFF1B1A18)
    }
    ChoiceChip(
        label = label,
        selected = theme == current,
        onClick = { onSelect(theme) },
        modifier = modifier,
        swatch = swatch
    )
}

@Composable
private fun ThemeChip(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    ChoiceChip(
        label = label,
        selected = mode == current,
        onClick = { onSelect(mode) },
        modifier = modifier
    )
}
