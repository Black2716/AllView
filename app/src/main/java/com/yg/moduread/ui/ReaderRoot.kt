package com.yg.moduread.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.plainText

@Composable
fun ReaderRoot(vm: ReaderViewModel) {
    val doc by vm.doc.collectAsStateWithLifecycle()
    val prefs by vm.readerPrefs.collectAsStateWithLifecycle()
    val settingsVisible by vm.showSettings.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var tocVisible by remember { mutableStateOf(false) }
    val currentIndex = remember(doc.docUri) { mutableStateOf(0) }

    val context = LocalContext.current
    val imagePermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // 首帧绘制完成后才做续读，冷启动不等 IO
    LaunchedEffect(Unit) { vm.restoreLastIfNeeded() }

    var askedForImages by rememberSaveable { mutableStateOf(false) }
    val imagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* 拒绝也不影响正文阅读，图片降级为占位提示 */ }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) vm.open(uri) }

    // 图片权限按需申请：只有文档里真的引用了本地图片才打扰用户
    val hasImages = remember(doc.blocks) { doc.blocks.any { it is MdBlock.Image } }
    LaunchedEffect(hasImages, doc.docUri) {
        if (hasImages && !askedForImages) {
            askedForImages = true
            val granted = ContextCompat.checkSelfPermission(context, imagePermission) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) imagePermissionLauncher.launch(imagePermission)
        }
    }

    val openPicker: () -> Unit = {
        // 系统 SAF：优先 text/markdown，部分文件提供方把 .md 归为 text/plain 或 octet-stream，故一并声明
        picker.launch(
            arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        androidx.compose.foundation.layout.Box {
            ReaderScreen(
                doc = doc,
                prefs = prefs,
                onPickFile = openPicker,
                onFontChange = vm::setFontSize,
                onOpenSettings = { vm.setSettingsVisible(true) },
                onSaveProgress = { index, offset ->
                    vm.saveProgress(index, offset)
                    currentIndex.value = index
                },
                onOpenToc = { tocVisible = true },
                // 文档内锚点：#slug -> 块索引 -> 跳转
                onAnchor = { url ->
                    // body = blocks.drop(1) 且列表 0 号是页首标题项，
                    // 两级偏移正好抵消：列表 item 下标 == 块下标
                    vm.resolveAnchor(url)?.let(vm::requestJump)
                },
                jumpTarget = vm.jumpTo.collectAsStateWithLifecycle().value,
                onJumpConsumed = vm::consumeJump,
                recent = recent,
                onOpenRecent = { uri -> runCatching { Uri.parse(uri) }.getOrNull()?.let(vm::open) },
                onForgetRecent = vm::forgetRecent
            )

            if (tocVisible) {
                TocSheet(
                    entries = doc.toc,
                    currentIndex = currentIndex.value,
                    onSelect = { blockIndex ->
                        tocVisible = false
                        vm.requestJump(blockIndex)
                    },
                    onDismiss = { tocVisible = false }
                )
            }

            if (settingsVisible) {
                SettingsSheet(
                    prefs = prefs,
                    onFontChange = vm::setFontSize,
                    onThemeChange = vm::setThemeMode,
                    onDismiss = { vm.setSettingsVisible(false) }
                )
            }
        }
    }
}

/**
 * LazyColumn 的稳定 key 提示：类型 + 正文前 24 字。
 * 只用于 key，不做任何展示；同一位置内容变了 key 才变，滚动时不会误重组。
 */
fun MdBlock.keyHint(): String = when (this) {
    is MdBlock.Heading -> "h" + level + ":" + content.plainText().take(24)
    is MdBlock.Para -> "p:" + content.plainText().take(24)
    is MdBlock.CodeBlock -> "c:" + code.take(24)
    is MdBlock.Math -> "m:" + latex.take(24)
    is MdBlock.Image -> "i:" + url.take(24)
    is MdBlock.Table -> "t:" + (head.firstOrNull()?.inlines?.plainText()?.take(16) ?: "")
    is MdBlock.Quote -> "q"
    is MdBlock.ListBlock -> "l:" + (items.firstOrNull()?.firstOrNull()?.let { b ->
        when (b) {
            is MdBlock.Para -> b.content.plainText().take(16)
            else -> ""
        }
    } ?: "")
    is MdBlock.Rule -> "r"
}

/** 小工具：标题文本（首个一级标题优先，其次文件名）。 */
fun List<MdBlock>.displayTitle(fallback: String): String {
    val heading = firstOrNull { it is MdBlock.Heading && it.level <= 2 } as? MdBlock.Heading
    val text = heading?.let { it.content.plainText() }
    return text?.takeIf { it.isNotBlank() } ?: fallback
}
