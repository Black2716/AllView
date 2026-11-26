package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yg.moduread.R

/**
 * 阅读设置。
 *
 * 改造前是手写 Box + Color.Black(0.35f) 遮罩自制的底部面板，
 * 和目录/搜索用的 ModalBottomSheet 是两套观感（无拖拽手柄、圆角动画、遮罩浓度都不同）。
 * 现在统一成 ModalBottomSheet，三个面板同一套动效与遮罩。
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Sp.l)
                .padding(bottom = Sp.xl)
        ) {
            SectionTitle("显示")
            Spacer(Modifier.height(Sp.s))

            // 字号卡片
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.card)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = Sp.m, vertical = Sp.m)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "字号",
                        fontSize = Type.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = prefs.fontSizeSp.toInt().toString() + " sp",
                        fontSize = Type.body,
                        color = MaterialTheme.colorScheme.primary
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
                        modifier = Modifier.weight(1f).padding(horizontal = Sp.s)
                    )
                    Text(
                        text = "A",
                        fontSize = Type.title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Sp.m))

            // 外观卡片
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.card)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = Sp.m, vertical = Sp.m)
            ) {
                Text(
                    text = "外观",
                    fontSize = Type.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Sp.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                    ThemeChip("跟随系统", ThemeMode.SYSTEM, prefs.themeMode, onThemeChange, Modifier.weight(1f))
                    ThemeChip("浅色", ThemeMode.LIGHT, prefs.themeMode, onThemeChange, Modifier.weight(1f))
                    ThemeChip("深色", ThemeMode.DARK, prefs.themeMode, onThemeChange, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(Sp.m))

            // 纸张色调：底色微调，与深/浅正交
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.card)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = Sp.m, vertical = Sp.m)
            ) {
                Text(
                    text = "纸张",
                    fontSize = Type.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Sp.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.s)) {
                    PaperChip("纸白", ReadingTheme.PAPER, prefs.readingTheme, onReadingThemeChange, Modifier.weight(1f))
                    PaperChip("米黄", ReadingTheme.SEPIA, prefs.readingTheme, onReadingThemeChange, Modifier.weight(1f))
                    PaperChip("夜间", ReadingTheme.NIGHT, prefs.readingTheme, onReadingThemeChange, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = Type.caption,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 纸张色调选项。选中态直接画出该纸张的底色，比纯文字更直观。 */
@Composable
private fun PaperChip(
    label: String,
    theme: ReadingTheme,
    current: ReadingTheme,
    onSelect: (ReadingTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = theme == current
    val swatch = when (theme) {
        ReadingTheme.PAPER -> Color(0xFFFCFCFA)
        ReadingTheme.SEPIA -> Color(0xFFF6F1E4)
        ReadingTheme.NIGHT -> Color(0xFF1B1A18)
    }
    Row(
        modifier
            .clip(Shapes.chip)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable { onSelect(theme) }
            .padding(vertical = Sp.s + 2.dp, horizontal = Sp.s),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(Shapes.pill)
                .background(swatch)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = Type.body,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeChip(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = mode == current
    Box(
        modifier
            .clip(Shapes.chip)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable { onSelect(mode) }
            .padding(vertical = Sp.s + 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = Type.body,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
