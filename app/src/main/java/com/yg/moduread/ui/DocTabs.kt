package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 文档标签条。
 *
 * 为什么是标签而不是分屏：手机上分屏后每栏只剩五六个汉字一行，
 * 长文阅读体验反而变差；标签保持单栏满宽，切换成本也低。
 *
 * 只在打开 2 个以上文档时显示 —— 单文档时不占任何高度。
 */
@Composable
fun DocTabs(
    sessions: List<DocSession>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessions.size < 2) return
    val scroll = rememberScrollState()
    Row(
        modifier
            .fillMaxWidth()
            // 安全区：不加这行标签条会压住状态栏（时间和电量图标）
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(scroll)
            .padding(horizontal = Sp.s, vertical = Sp.xs),
        horizontalArrangement = Arrangement.spacedBy(Sp.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sessions.forEach { session ->
            TabChip(
                label = session.doc.title.ifBlank { "未命名" },
                active = session.id == activeId,
                onClick = { onSelect(session.id) },
                onClose = { onClose(session.id) }
            )
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        Modifier
            .height(30.dp)
            .widthIn(max = 160.dp)
            .clip(RoundedCornerShape(Radius.s))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable(onClick = onClick)
            .padding(start = Sp.s, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = Type.caption,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Box(
            Modifier
                .clip(RoundedCornerShape(Radius.s))
                .clickable(onClick = onClose)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            MiIcons.Close(
                tint = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 12.dp
            )
        }
    }
}
