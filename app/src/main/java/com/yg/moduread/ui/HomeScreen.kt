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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.yg.moduread.data.RecentFile
import com.yg.moduread.format.DocFormat

/**
 * 首页（主列表）：没有打开文档时的落地页。
 *
 * 改造前这里是「品牌名 + 一句话 + 一个蓝按钮 + 一串光秃秃的文件名」，
 * 名字被截断后完全看不出是什么文件，8 条记录读起来像一段错误日志。
 *
 * 现在的信息层级：
 *   品牌头（AllView + 一句话定位）
 *   → 主操作（打开文件，整宽、48dp 高、带图标）
 *   → 最近打开（每条一张卡片：格式徽标 + 文件名 + 相对时间 + 移除）
 *   → 格式说明（20 种格式不是靠猜的）
 *
 * 行为未变：只有三个既有回调（选文件 / 打开某条 / 忘掉某条），
 * 不新增任何状态或导航；最近文件仍然是最多 8 条、点即打开。
 */
@Composable
internal fun HomeScreen(
    /** 文档打开失败时的原因；正常首页为 null。 */
    error: String?,
    recent: List<RecentFile>,
    onPickFile: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onForgetRecent: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            // 横屏/分屏时视口不足，必须可滚动，否则最近列表底部够不着
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = ReaderGutter)
            .padding(top = Sp.xxl, bottom = Sp.xl)
    ) {
        BrandHeader()
        // 打开失败时把原因放在品牌头下面，而不是替换整个页面 ——
        // 改造前错误文案会把「选择文件」按钮的位置也一起顶走，用户以为页面崩了。
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(Sp.m))
            ErrorBanner(error)
        }
        Spacer(Modifier.height(Sp.xl))
        PrimaryAction(label = "打开文件", onClick = onPickFile, modifier = Modifier.fillMaxWidth())

        if (recent.isEmpty()) {
            Spacer(Modifier.height(Sp.xxl))
            EmptyHint(
                title = "还没有打开过文件",
                detail = "支持 Markdown、纯文本、PDF、Word、Excel、PPTX、EPUB 等格式"
            )
        } else {
            Spacer(Modifier.height(Sp.xxl))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("最近打开")
                Spacer(Modifier.weight(1f))
                Text(
                    text = recent.size.toString() + " 项",
                    fontSize = Type.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Sp.s))
            Column(verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
                recent.forEach { file ->
                    RecentCard(
                        file = file,
                        onOpen = { onOpenRecent(file.uri) },
                        onForget = { onForgetRecent(file.uri) }
                    )
                }
            }
        }

        Spacer(Modifier.height(Sp.xxl))
        FormatSupportNote()
    }
}

/** 错误提示条：左侧主色竖条 + 文案，复用卡片圆角。 */
@Composable
private fun ErrorBanner(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(Sp.m),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(Shapes.pill)
                .background(MaterialTheme.colorScheme.error)
        )
        Spacer(Modifier.width(Sp.m))
        Text(
            text = message,
            fontSize = Type.body,
            lineHeight = (Type.body.value * 1.5f).sp,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

/** 品牌头：大字号 AllView + 一行定位说明。 */
@Composable
private fun BrandHeader() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "AllView",
            fontSize = Type.display,
            lineHeight = (Type.display.value * 1.15f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Sp.xs))
        Text(
            text = "本地文档阅读器 · 无需联网",
            fontSize = Type.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 最近打开的一条：格式徽标 + 文件名 + 相对时间 + 移除键。
 *
 * 改造前每条只有文件名和 11sp 的时间，删掉「是什么文件」这层信息 ——
 * 而阅读器最需要先知道的就是格式（PDF 与 Markdown 的阅读方式完全不同）。
 * 徽标文字由文件名后缀推导（DocFormat.detect），不新增任何存储字段。
 */
@Composable
private fun RecentCard(
    file: RecentFile,
    onOpen: () -> Unit,
    onForget: () -> Unit
) {
    val format = DocFormat.detect(file.name, null)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen)
            .padding(start = Sp.m, top = Sp.s, bottom = Sp.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypeBadge(text = badgeOf(format), tint = tintOf(format), modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(Sp.m))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = file.name,
                fontSize = Type.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatLabel(format) + " · " + relativeTime(file.openedAt),
                fontSize = Type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconAction(
            description = "从最近打开中移除",
            onClick = onForget,
            draw = { tint -> MiIcons.Close(tint, 14.dp) }
        )
    }
}

/**
 * 底部格式说明。
 *
 * 存在的理由：用户不知道这个 App 能开什么，就只能一个个试。
 * 改造前空态页只有一句「打开本地文件开始阅读」，等于什么都没说。
 * 这里按能力分组写死文案（与 DocFormat.detect 支持的集合一致），
 * 不读运行时数据、不发起查询。
 */
@Composable
private fun FormatSupportNote() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Sp.m)
    ) {
        Column {
            Text(
                text = "能打开",
                fontSize = Type.label,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Sp.xs))
            Text(
                text = "Markdown、纯文本、日志、代码、CSV、JSON、XML、YAML、INI、" +
                    "字幕、RTF、HTML、EPUB、Word、Excel、PPTX、PDF",
                fontSize = Type.body,
                lineHeight = (Type.body.value * 1.5f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 徽标上的两三个字符，够认出格式即可。 */
private fun badgeOf(format: DocFormat): String = when (format) {
    DocFormat.MARKDOWN -> "MD"
    DocFormat.TXT -> "TXT"
    DocFormat.LOG -> "LOG"
    DocFormat.CSV -> "CSV"
    DocFormat.JSON -> "{}"
    DocFormat.XML -> "XML"
    DocFormat.YAML -> "YML"
    DocFormat.INI -> "INI"
    DocFormat.SUBTITLE -> "SRT"
    DocFormat.RTF -> "RTF"
    DocFormat.EPUB -> "EPUB"
    DocFormat.DOCX, DocFormat.DOC_LEGACY -> "DOC"
    DocFormat.ODT -> "ODT"
    DocFormat.HTML -> "HTML"
    DocFormat.XLSX -> "XLS"
    DocFormat.PPTX -> "PPT"
    DocFormat.PDF -> "PDF"
    DocFormat.CODE -> "CODE"
    DocFormat.UNSUPPORTED -> "FILE"
}

/** 徽标底色：文档类走主色，表格/幻灯片走次色，其余走第三色。 */
@Composable
private fun tintOf(format: DocFormat) = when (format) {
    DocFormat.XLSX, DocFormat.CSV, DocFormat.PPTX -> MaterialTheme.colorScheme.tertiary
    DocFormat.PDF, DocFormat.DOCX, DocFormat.DOC_LEGACY, DocFormat.ODT, DocFormat.EPUB ->
        MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

/** 最近的相对时间：不引第三方时间库，按粗粒度分档。 */
internal fun relativeTime(epochMillis: Long): String {
    val delta = System.currentTimeMillis() - epochMillis
    if (delta < 0) return "刚刚"
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        delta < minute -> "刚刚"
        delta < hour -> (delta / minute).toString() + " 分钟前"
        delta < day -> (delta / hour).toString() + " 小时前"
        delta < 7 * day -> (delta / day).toString() + " 天前"
        else -> (delta / (7 * day)).toString() + " 周前"
    }
}
