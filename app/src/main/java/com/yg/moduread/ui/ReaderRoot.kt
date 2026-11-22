package com.yg.moduread.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yg.moduread.data.MdDocResolver
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.plainText

@Composable
fun ReaderRoot(vm: ReaderViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val doc by vm.doc.collectAsStateWithLifecycle()
    val prefs by vm.readerPrefs.collectAsStateWithLifecycle()
    val settingsVisible by vm.showSettings.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    var tocVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var bookmarksVisible by remember { mutableStateOf(false) }
    // 相对路径打不开时记下想去的文档，等用户在 SAF 里选完再核对
    val pendingDocLink = remember { mutableStateOf<String?>(null) }
    val currentIndex = remember(activeId) { mutableStateOf(0) }

    val context = LocalContext.current
    val imagePermission = if (android.os.Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // 首帧绘制完成后才做续读，冷启动不等 IO
    LaunchedEffect(Unit) { vm.restoreLastIfNeeded() }

    // 切到另一份文档时，目录高亮的起点同步为该会话记录的可见块
    LaunchedEffect(activeId) {
        currentIndex.value = sessions.firstOrNull { it.id == activeId }?.visibleBlock ?: 0
    }

    var askedForImages by rememberSaveable { mutableStateOf(false) }
    val imagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.open(uri)
        pendingDocLink.value = null
    }

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
        picker.launch(
            arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // 标签条：只在多开时出现，单文档不占高度
            DocTabs(
                sessions = sessions,
                activeId = activeId,
                onSelect = { id ->
                    // 切走前先把当前会话的滚动位置回写，切回时才能原样恢复
                    activeId?.let { current -> vm.saveSessionScroll(current, currentIndex.value, 0) }
                    vm.switchTo(id)
                },
                onClose = { id -> vm.closeSession(id) }
            )

            ReaderScreen(
                // 每份文档的滚动位置完全独立：doc.docUri 变化会让 ReaderScreen
                // 重建自己的 LazyListState，不会串到别的文档
                doc = doc,
                prefs = prefs,
                onPickFile = openPicker,
                onFontChange = vm::setFontSize,
                onOpenSettings = { vm.setSettingsVisible(true) },
                onSaveProgress = { index, offset ->
                    vm.saveProgress(index, offset)
                    currentIndex.value = index
                    activeId?.let { vm.saveSessionScroll(it, index, offset) }
                },
                onVisibleBlock = { index ->
                    currentIndex.value = index
                    activeId?.let { vm.saveSessionVisibleBlock(it, index) }
                },
                onOpenToc = { tocVisible = true },
                onOpenSearch = { searchVisible = true },
                onOpenBookmarks = { bookmarksVisible = true },
                onLongPressBlock = { blockIndex -> vm.addBookmark(blockIndex) },
                onLink = { url ->
                    when {
                        // 1) 文档内锚点
                        url.startsWith("#") -> vm.resolveAnchor(url)?.let(vm::requestJump)
                        // 2) 兄弟文档（.md 相对路径）—— B1 行为保持不变
                        MdDocResolver.isRelativeDocLink(url) && MdDocResolver.isMarkdownPath(url) -> {
                            val target = MdDocResolver.resolve(context, doc.docUri, url)
                            if (target != null) {
                                vm.open(target)
                            } else {
                                // 分区存储下未被授权过的兄弟文档读不到（平台限制）。
                                // 回退到 SAF：用户选一次即拿到持久授权，之后再点就直接打开。
                                pendingDocLink.value = url
                                picker.launch(
                                    arrayOf("text/markdown", "text/x-markdown", "text/plain", "application/octet-stream")
                                )
                            }
                        }
                        // 3) 其余交给系统浏览器
                        else -> runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(url)
                                )
                            )
                        }
                    }
                },
                jumpTarget = vm.jumpTo.collectAsStateWithLifecycle().value,
                onJumpConsumed = vm::consumeJump,
                recent = recent,
                onOpenRecent = { uri -> runCatching { Uri.parse(uri) }.getOrNull()?.let(vm::open) },
                onForgetRecent = vm::forgetRecent
            )
        }

        if (bookmarksVisible) {
            BookmarkSheet(
                bookmarks = bookmarks,
                onOpen = { mark ->
                    // 文档改版后摘要对不上就不跳，避免落到错误位置
                    if (vm.jumpToBookmark(mark)) bookmarksVisible = false
                },
                onDelete = { mark -> vm.removeBookmark(mark.blockIndex) },
                onDismiss = { bookmarksVisible = false }
            )
        }

        if (searchVisible) {
            SearchSheet(
                index = doc.searchIndex,
                onSelect = { hit ->
                    searchVisible = false
                    vm.requestJump(hit.blockIndex)
                },
                onDismiss = { searchVisible = false }
            )
        }

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
                onReadingThemeChange = vm::setReadingTheme,
                onDismiss = { vm.setSettingsVisible(false) }
            )
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
