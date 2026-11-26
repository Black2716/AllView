package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.md.displayTitle

/**
 * 阅读界面的「外壳」部件：页首大标题、顶栏、底栏、字号浮层、加载态。
 *
 * 从 ReaderScreen.kt 拆出的理由：ReaderScreen 的主体职责是**滚动编排**
 * （恢复位置、跳转、可见块上报、进度落盘节流），但这些只占它一半篇幅，
 * 另一半是「长什么样」。两部分改动频率与原因完全不同 ——
 * 调整图标间距不该动到 scrollToItem 的 +1 偏移计算，反之亦然。
 * 拆开后 ReaderScreen 只剩编排逻辑，本文件只描述外观与交互。
 *
 * 行为完全未变：组件签名、可见性（原 private，现 internal 以便跨文件引用）、
 * 尺寸令牌与文案都保持原样。
 */

/**
 * 底栏总高度（含 1dp 发丝线与内边距）。
 *
 * 存在的理由：进度线、字号浮层都要「贴在底栏正上方」，
 * 改造前三处各写了一个 52.dp —— 它其实是从 MinTouchTarget(48) 手算出来的，
 * 谁调底栏高度就会漏改另一处，进度线立刻压到按钮上。
 * 现在底栏自身与两个浮层引用同一个值，改这里即全站一致。
 */
internal val BottomBarHeight = 52.dp

/** 正文滚动区底部留白：底栏高度再加一档，保证最后一行不被浮层遮住。 */
internal val ReaderBottomContentInset = 96.dp

/** 页首大标题：文档标题 + 一条短下划线。 */
@Composable
internal fun DocTitle(doc: DocumentState, fontSize: Float) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(
            text = doc.blocks.displayTitle(doc.title),
            fontSize = (fontSize * 1.85f).sp,
            lineHeight = (fontSize * 1.85f * 1.3f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(44.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/** 顶部栏：当前章节 + 阅读进度百分比。 */
@Composable
internal fun TopBar(title: String, progress: Float, modifier: Modifier) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = (progress * 100).toInt().toString() + "%",
                fontSize = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = Alpha.soft))
        )
    }
}

/**
 * 底部栏：四个等距图标 + 一个低频项收进「⋯」。
 *
 * 改造前这里是六个同款文字胶囊（A- A+ 目录 搜索 设置 ... 换文件），
 * 没有主次也没有图标 —— 密集却没有信息量。现在：
 *  - 图标 24dp、触摸区 48dp，四个按钮视觉重量一致
 *  - 字号（低频）改成点「Aa」弹浮层，腾出两个按钮位
 *  - 「换文件」收进「⋯」，主栏从 6 项降到 4 项
 */
@Composable
internal fun ReaderBottomBar(
    fontPanelOpen: Boolean,
    onToggleFontPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    searchEnabled: Boolean = true,
    onOpenBookmarks: () -> Unit,
    onPickFile: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier
) {
    var moreOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().height(BottomBarHeight + bottomPadding)) {
        // 发丝线：比改造前的 1dp outline(0.6f) 更淡，只做分隔不抢视线
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = Sp.s, vertical = Sp.xs)
                .padding(bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarIcon(
                label = "目录",
                onClick = onOpenToc,
                modifier = Modifier.weight(1f),
                draw = { tint -> MiIcons.Toc(tint) }
            )
            // PDF 与无文字层的扫描件没有搜索索引，入口置灰而不是静默失效
            BarIcon(
                label = "搜索",
                onClick = { if (searchEnabled) onOpenSearch() },
                modifier = Modifier.weight(1f),
                dimmed = !searchEnabled,
                draw = { tint -> MiIcons.Search(tint) }
            )
            BarIcon(
                label = "字号",
                onClick = onToggleFontPanel,
                active = fontPanelOpen,
                modifier = Modifier.weight(1f),
                draw = { tint -> FontGlyph(tint) }
            )
            // 「⋯」下拉必须锚定到这个按钮本身。
            // 直接放在 Column 里的话，锚点是整个 Column 的左上角，菜单会贴屏幕最左边。
            Box(Modifier.weight(1f)) {
                BarIcon(
                    label = "更多",
                    onClick = { moreOpen = true },
                    active = moreOpen,
                    modifier = Modifier.fillMaxWidth(),
                    draw = { tint -> MiIcons.More(tint) }
                )

                // 低频操作都放这儿，不占用主栏
                DropdownMenu(
                    expanded = moreOpen,
                    onDismissRequest = { moreOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("换一个文件", fontSize = Type.bodyLarge) },
                        onClick = { moreOpen = false; onPickFile() }
                    )
                    DropdownMenuItem(
                        text = { Text("书签", fontSize = Type.bodyLarge) },
                        onClick = { moreOpen = false; onOpenBookmarks() }
                    )
                    DropdownMenuItem(
                        text = { Text("阅读设置", fontSize = Type.bodyLarge) },
                        onClick = { moreOpen = false; onOpenSettings() }
                    )
                }
            }
        }
    }
}

/** 底部栏图标按钮：图标 24dp，触摸区撑到 48dp 高，点击标题可无障碍朗读。 */
@Composable
private fun BarIcon(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    /** 置灰：功能当前不可用（如 PDF 无搜索索引）。 */
    dimmed: Boolean = false,
    draw: @Composable (Color) -> Unit
) {
    val tint = when {
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.faint)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .height(MinTouchTarget)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        draw(tint)
    }
}

/** 字号图标用「Aa」字样，比画两个三角形更像约定的 UI 语言。 */
@Composable
private fun FontGlyph(tint: Color) {
    Text(
        text = MiIcons.FONT_LABEL,
        fontSize = Type.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = tint
    )
}

/**
 * 字号浮层。改造前是两个常驻按钮 A-/A+；
 * 调字号是低频操作（一本书 1~2 次），不值得占两个主栏位置。
 */
@Composable
internal fun FontPanel(
    fontSize: Float,
    onFontChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    // fillMaxWidth 之后 Box 的 align 不再居中生效，内容会贴在左侧 padding 边界上。
    // 用 wrapContentWidth + 水平居中的 Row 让胶囊整体居中。
    Row(
        Modifier
            .wrapContentWidth()
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Sp.m, vertical = Sp.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "A",
            fontSize = Type.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = fontSize,
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
        Spacer(Modifier.width(Sp.s))
        Text(
            text = fontSize.toInt().toString(),
            fontSize = Type.caption,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp)
        )
        // 【已标注的尺寸改动】关闭按钮命中区从 32dp 撑到 MinTouchTarget(48dp)。
        // 同排的 M3 Slider 本身就有 48dp 高，面板总高因此不变（不会把胶囊撑胖），
        // 只横向宽了 16dp；图标仍 14dp 居中，观感一致。
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(Shapes.pill)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭字号面板" },
            contentAlignment = Alignment.Center
        ) {
            MiIcons.Close(MaterialTheme.colorScheme.onSurfaceVariant, 14.dp)
        }
    }
}

/** 首帧骨架：文档还在解析时的居中转圈。 */
@Composable
internal fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}
