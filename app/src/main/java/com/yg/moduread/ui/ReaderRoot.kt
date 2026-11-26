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
import com.yg.moduread.format.DocFormat
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.displayTitle
import com.yg.moduread.md.keyHint

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
    // PDF 全屏查看：状态提到界面根部，用普通 Box 叠加而不是 Popup/Dialog
    var pdfViewer by remember { mutableStateOf<Pair<android.graphics.Bitmap, String>?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var bookmarksVisible by remember { mutableStateOf(false) }
    // 相对路径打不开时记下想去的文档，等用户在 SAF 里选完再核对
    val pendingDocLink = remember { mutableStateOf<String?>(null) }
    // 初值直接取该会话记下的可见块，而不是 0。
    // 改造前是 remember(activeId) { mutableStateOf(0) }：切换文档时先回落成 0，
    // 下一帧 LaunchedEffect 才补上真实位置，目录面板若正开着会看到高亮闪一下顶部。
    // 取值与 LaunchedEffect 里的表达式完全一致，只是把「先错一帧再改」变成「一开始就对」。
    val currentIndex = remember(activeId) {
        mutableStateOf(sessions.firstOrNull { it.id == activeId }?.visibleBlock ?: 0)
    }

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
        // 必须用 OpenDocument 而不是 GetContent：只有前者能拿到「持久授权」，
        // GetContent 只给一次性读权限，重启后已打开的文档会变成「未授权」。
        // MIME 传 */* 放开过滤，否则系统识别不出 MIME 的文件（CSV/.c/.cpp）会被置灰。
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
        // OpenDocument 收的是 MIME 数组。用 */* + 各具体类型，兼顾「不过滤」与
        // 让系统把已知类型排在前面；实际格式仍由扩展名判定（DocFormat.detect）。
        picker.launch(arrayOf("*/*"))
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
                onOpenPdfViewer = { bitmap, pageLabel -> pdfViewer = bitmap to pageLabel },
                // 三条分支（锚点 / 兄弟文档 / 外部链接）的判定顺序集中在 dispatchLink 里
                onLink = { url ->
                    dispatchLink(
                        url = url,
                        context = context,
                        docUri = doc.docUri,
                        onAnchor = { anchor -> vm.resolveAnchor(anchor)?.let(vm::requestJump) },
                        onOpenDocument = vm::open,
                        onNeedsPicker = { link ->
                            pendingDocLink.value = link
                            picker.launch(arrayOf("*/*"))
                        }
                    )
                },
                jumpTarget = vm.jumpTo.collectAsStateWithLifecycle().value,
                onJumpConsumed = vm::consumeJump,
                recent = recent,
                onOpenRecent = { uri -> runCatching { Uri.parse(uri) }.getOrNull()?.let(vm::open) },
                onForgetRecent = vm::forgetRecent
            )
        }

        // PDF 全屏查看器：普通 Box 叠加在主界面之上，尺寸天然铺满，
        // 不涉及 Popup/Dialog 的窗口尺寸与事件分发问题。
        pdfViewer?.let { (bmp, pageLabel) ->
            PdfFullscreenOverlay(
                bitmap = bmp,
                label = pageLabel,
                onDismiss = { pdfViewer = null }
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
