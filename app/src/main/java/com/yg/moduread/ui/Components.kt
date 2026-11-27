package com.yg.moduread.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 跨界面复用的基础部件。
 *
 * 存在的理由：改造前「一张卡片」在设置页、搜索页、书签页各写了一遍
 * （同样的 clip + background + padding 三件套），「一个胶囊按钮」在
 * 外观/纸张两组选项里各写了一遍且都不足 48dp。重复的定义意味着
 * 圆角、内边距、触摸高度会各自漂移 —— 重新设计一次就要翻五个文件。
 *
 * 这里只收敛真正重复且容易漂移的东西：卡片外壳、选择胶囊、面板头、
 * 主按钮、空态提示、发丝线。业务语义（点哪个回调）留在调用点。
 */

// ─────────────────────────── 面板外壳 ───────────────────────────

/**
 * 面板顶部的拖拽手柄。
 *
 * 改造前四个面板都用 M3 默认手柄（4dp 宽、32dp 高、带 22dp 上下留白的
 * 独立区域），在 28dp 圆角的面板上显得又小又孤单。这里给一个
 * 36×4dp 的胶囊，留白与控制区一并收紧，面板顶边看起来更完整。
 */
@Composable
internal fun SheetDragHandle() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = Sp.m, bottom = Sp.s),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(Shapes.pill)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.faint))
        )
    }
}

/**
 * 面板标题行：标题 + 可选计数 + 右侧关闭。
 *
 * 改造前目录/书签两个面板各写了一遍标题 Row，搜索面板干脆没有标题
 * （只有一个输入框，用户不确定自己在哪个面板里）。统一后：
 * 每个面板都有一个 48dp 高的关闭按钮，标题与计数用同一组字阶。
 */
@Composable
internal fun SheetHeader(
    title: String,
    badge: String? = null,
    onClose: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Sp.l, end = Sp.xs, top = Sp.xs, bottom = Sp.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = Type.title,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!badge.isNullOrBlank()) {
            Spacer(Modifier.width(Sp.s))
            Text(
                text = badge,
                fontSize = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        if (onClose != null) {
            IconAction(
                description = "关闭" + title,
                onClick = onClose,
                draw = { tint -> MiIcons.Close(tint, 16.dp) }
            )
        }
    }
}

/** 48dp 的正方形图标按钮：所有「×」「返回」这类小图标都走它。 */
@Composable
internal fun IconAction(
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    draw: @Composable (Color) -> Unit
) {
    Box(
        Modifier
            .size(MinTouchTarget)
            .clip(Shapes.pill)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        draw(tint)
    }
}

// ─────────────────────────── 容器 ───────────────────────────

/**
 * 设置项卡片：一个标题 + 一块内容，外用浅描边区分层次。
 *
 * 用描边而不是阴影：纸上加阴影会显得脏，1dp 的 outlineVariant 已经
 * 足够把卡片从面板底色里分出来，且在深色与米黄纸上都成立。
 */
@Composable
internal fun SettingCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surface)
            .border(Hairline, MaterialTheme.colorScheme.outlineVariant, Shapes.card)
            .padding(horizontal = Sp.m, vertical = Sp.m)
    ) {
        Text(
            text = title,
            fontSize = Type.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Sp.s))
        content()
    }
}

/** 面板内的分组小标题。 */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = Type.label,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Sp.xs, bottom = Sp.xs)
    )
}

/** 1dp 发丝线。散布在各处的 Box+height(1.dp)+background 统一走这里。 */
@Composable
internal fun HairlineDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Box(modifier.fillMaxWidth().height(Hairline).background(color))
}

// ─────────────────────────── 交互件 ───────────────────────────

/**
 * 选择胶囊：外观三选一、纸张三选一、以及后续任何「互斥多选」都用它。
 *
 * 改造前 ThemeChip / PaperChip 是两份几乎相同的实现，
 * 高度约 36dp（低于 48dp 触摸标准），选中态只换底色、读屏也读不出选中。
 * 现在：最小高度 48dp、选中态带描边 + 对勾、语义里带 selected + role。
 */
@Composable
internal fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 可选的色块（纸张色调用），传 null 则不画。 */
    swatch: Color? = null,
    /** 是否在文字前显示对勾。三选项并排时宽度吃紧，可关掉。 */
    showCheck: Boolean = false
) {
    val shape = Shapes.chip
    Row(
        modifier
            .heightIn(min = MinTouchTarget)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .border(
                width = if (selected) 1.5.dp else Hairline,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = if (selected) label + "，已选中" else label
            }
            .padding(horizontal = Sp.s, vertical = Sp.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheck) {
            val tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.faint)
            MiIcons.Check(tint, 14.dp)
            Spacer(Modifier.width(Sp.xs))
        }
        if (swatch != null) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(Shapes.pill)
                    .background(swatch)
                    .border(Hairline, MaterialTheme.colorScheme.outline, Shapes.pill)
            )
            Spacer(Modifier.width(Sp.xs + 2.dp))
        }
        Text(
            text = label,
            fontSize = Type.body,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * 主行动按钮（「选择文件」「打开本地文件」）。
 *
 * 改造前是手写 Box + clickable + 10dp 圆角 + 22/12dp 内边距，
 * 高度约 44dp 且没有按压反馈之外的任何状态。现在：
 * 48dp 最小高度、令牌圆角、按下时轻微缩放、语义上是 Button。
 */
@Composable
internal fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(Motion.fast),
        label = "primaryActionScale"
    )
    Row(
        modifier
            .heightIn(min = MinTouchTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = Sp.xl, vertical = Sp.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiIcons.Folder(MaterialTheme.colorScheme.onPrimary, 18.dp)
        Spacer(Modifier.width(Sp.s))
        Text(
            text = label,
            fontSize = Type.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1
        )
    }
}

/** 次级按钮：描边、透明底，用于「换一个文件」这类不该抢主动作的操作。 */
@Composable
internal fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .heightIn(min = MinTouchTarget)
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = Sp.l, vertical = Sp.m),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = Type.body,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

// ─────────────────────────── 空态 ───────────────────────────

/**
 * 面板内空态：图标 + 一行主文案 + 一行说明。
 *
 * 改造前三个面板各写一个 Box + Text，内边距分别是 26dp / Sp.xl / Sp.xl，
 * 文本对齐与行距也不一致；搜索面板的空态甚至只是一句灰字，
 * 没有「为什么没有结果」的提示。
 */
@Composable
internal fun EmptyHint(
    title: String,
    detail: String? = null,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Sp.xxl, horizontal = Sp.l),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.height(Sp.m))
        }
        Text(
            text = title,
            fontSize = Type.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(Sp.xs))
            Text(
                text = detail,
                fontSize = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.soft),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 行内元数据小标签（「12 个标题」「PDF · 38 页」）。 */
@Composable
internal fun MetaBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = Type.label,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(Shapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = Sp.s, vertical = 2.dp)
    )
}

/**
 * 搜索/过滤输入框。
 *
 * 改造前目录与搜索两个面板都用 M3 的 OutlinedTextField：默认 4dp 圆角描边框、
 * 默认内边距、默认的字号，与全站「填充卡片 + 12dp 圆角」的语言完全不是一套，
 * 在底部面板里显得像另一个 App 的控件。这里用 BasicTextField 自绘：
 * 填充底色 + 发丝描边 + 左图标 + 右清除键，与卡片同一套形状令牌。
 */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Search,
    onSearch: () -> Unit = {}
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(Hairline, MaterialTheme.colorScheme.outlineVariant, Shapes.card)
            .padding(start = Sp.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiIcons.Search(MaterialTheme.colorScheme.onSurfaceVariant, 18.dp)
        Spacer(Modifier.width(Sp.s))
        Box(
            Modifier
                .weight(1f)
                .padding(vertical = Sp.m)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = Type.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = Type.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            )
        }
        // 有内容时才出现清除键：空框上放一个 × 只会让人以为能点
        if (value.isNotEmpty()) {
            IconAction(
                description = "清除输入",
                onClick = { onValueChange("") },
                draw = { tint -> MiIcons.Close(tint, 14.dp) }
            )
        } else {
            Spacer(Modifier.width(Sp.s))
        }
    }
}

/** 圆角小方块（文件类型徽标底色用）。 */
@Composable
internal fun TypeBadge(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 40.dp, height = 40.dp)
            .clip(RoundedCornerShape(Radius.m - 2.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = Type.label,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}
