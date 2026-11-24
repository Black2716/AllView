package com.yg.moduread.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.yg.moduread.md.MdBlock

/**
 * PDF 页。
 *
 * 三次迭代的结论，记下来避免再走弯路：
 *
 *  v1（缩放做在列表内的页上）：transformable 抢走所有手势，列表滚不动；
 *     缩放状态按页记录，各页大小不一致。
 *  v2（Dialog + graphicsLayer）：全屏对话框里图片始终白屏。
 *     uiautomator 甚至拿不到界面树 —— Dialog 的 window 与主界面嵌套有问题。
 *  v3（当前）：Popup 全屏承载查看器。Popup 是独立 window 但没有 Dialog 的
 *     装饰开销，实测能正常渲染，且不影响主列表的滚动与布局。
 *
 * 列表里的页永远等宽、可滚动；点击打开 Popup 查看器，缩放只作用其中。
 */
@Composable
fun PdfPageSlot(
    block: MdBlock.PdfPage,
    docUri: String?,
    modifier: Modifier = Modifier,
    /** 点击页面时把位图交给上层，由上层在全屏叠加层里展示。 */
    onOpenViewer: (Bitmap, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val cache = remember(docUri) { PdfBitmapCache(context, docUri) }

    // 渲染必须放到 IO 线程：Bitmap.createBitmap + page.render 在大页面上
    // 要几百毫秒，同步执行会把主线程卡住甚至 ANR，用户看到的就是「很慢」。
    // 同时把异常暴露出来（之前的 runCatching 吞掉后只显示「渲染失败」，无法定位）。
    var bitmap by remember(docUri, block.pageIndex) { mutableStateOf<Bitmap?>(null) }
    var failure by remember(docUri, block.pageIndex) { mutableStateOf<String?>(null) }
    // key 必须包含页面尺寸：LazyColumn 复用 item 时只换 index 不会换 key 之外的状态，
    // 而 block 对象本身是新的（不同页的 widthPoints 可能不同），所以把尺寸并入 key。
    LaunchedEffect(docUri, block.pageIndex, block.widthPoints, block.heightPoints) {
        // 必须先查缓存再决定要不要渲染：滚动时 item 会反复进出组合，
        // 已经渲染过的页直接取缓存，避免重复渲染（这是「加载慢」的主因之一）。
        cache.peek(block.pageIndex)?.let {
            bitmap = it
            return@LaunchedEffect
        }

        // NonCancellable 很关键：列表快速滚动时 LaunchedEffect 会被取消，
        // 若渲染中途被打断，PdfRenderer 的 openPage 状态会残留，
        // 结果是既没进缓存也没显示 —— 用户看到的就是「空白页」。
        // 让渲染一次跑完并落进 LRU，下次进出组合就能直接命中。
        val result = withContext(Dispatchers.IO + NonCancellable) { cache.load(block.pageIndex) }
        result.onSuccess { bitmap = it }
            .onFailure { failure = it.message ?: it::class.java.simpleName }
    }


    val ratio = if (block.heightPoints > 0) {
        block.widthPoints.toFloat() / block.heightPoints.toFloat()
    } else 0.707f

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = bitmap != null) {
                bitmap?.let { onOpenViewer(it, "第 " + (block.pageIndex + 1) + " 页") }
            }
    ) {
        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = "第 " + (block.pageIndex + 1) + " 页",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 失败时显示真实原因，而不是笼统的「渲染失败」——
            // 否则无法判断是内存不足、文件损坏还是渲染器初始化失败。
            Text(
                text = "第 " + (block.pageIndex + 1) + " 页" +
                    (failure?.let { "：" + it } ?: "（渲染中…）"),
                fontSize = Type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(Sp.m)
            )
        }
    }

}

/** 缩放上限。位图按 2 倍渲染，4 倍视觉放大已接近可辨认极限。 */
private const val MAX_ZOOM = 4f

/**
 * PDF 页位图缓存。
 *
 * PdfRenderer 非线程安全且必须 close；只保留最近几页避免整本入内存。
 * A4@2x 约 8 MB/页，4 页约 32 MB。
 */
private class PdfBitmapCache(
    private val context: android.content.Context,
    private val docUri: String?
) {
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private val lru = object : LinkedHashMap<Int, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean {
            val evict = size > maxPages()
            if (evict) eldest?.value?.recycle()
            return evict
        }
    }

    /** 只查缓存，不触发渲染。用于组合时先看有没有现成的。 */
    fun peek(pageIndex: Int): Bitmap? = lru[pageIndex]?.takeIf { !it.isRecycled }

    /**
     * 加载某页位图。返回 Result 而不是可空值：
     * 失败原因要能显示出来，否则只有一句「渲染失败」无法定位。
     */
    fun load(pageIndex: Int): Result<Bitmap> {
        lru[pageIndex]?.let { if (!it.isRecycled) return Result.success(it) }
        val r = ensureRenderer()
            ?: return Result.failure(IllegalStateException("无法打开 PDF（PdfRenderer 初始化失败）"))
        // PdfRenderer 不是线程安全的，且同一时刻只允许一个 Page 打开。
        // 列表里多个页面会并发加载，不加锁时 openPage 互相干扰，
        // 表现就是部分页永远空白（这是实测到的真实故障）。
        return synchronized(renderLock) {
        runCatching {
            r.openPage(pageIndex).use { page ->
                // 关键：倍率按「屏幕宽度能容下的像素数」算，而不是固定 2 倍。
                // 大页面（A3/A1 图纸）固定 2 倍会产生 3000x4200 的位图，
                // 单页就占 50 MB，列表里几页同时渲染必然 OOM。
                val size = PdfRenderSize.compute(
                    page.width,
                    page.height,
                    context.resources.displayMetrics.widthPixels
                )
                val w = size[0]
                val h = size[1]

                // 必须是 ARGB_8888：PdfRenderer.Page.render() 只支持这个格式，
                // 换成 RGB_565 会抛 IllegalArgumentException("unsupported pixel format")。
                // 内存靠像素上限控制（见 PdfRenderSize.MAX_PIXELS），不是靠降位深。
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                lru[pageIndex] = bmp
                bmp
            }
        }
        }
    }

    /** 保护 PdfRenderer 的互斥锁（跨页面共享）。 */
    private val renderLock = Any()

    private fun ensureRenderer(): PdfRenderer? {
        renderer?.let { return it }
        return runCatching {
            val uri = android.net.Uri.parse(docUri ?: return null)
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            pfd = descriptor
            PdfRenderer(descriptor).also { renderer = it }
        }.getOrNull()
    }

    companion object {
        /** 单页像素上限。1600 万像素 @RGB_565 约 32 MB，是安全上限。 */
        private const val MAX_PIXELS = 16_000_000L
        /** 最小倍率：太小的页面（如小幅插图）不至于放大到模糊。 */
        private const val MIN_SCALE = 0.5f
        /** 最大倍率：屏幕通常 1080 宽，2 倍已足够，超过只浪费内存。 */
        private const val MAX_SCALE = 2.0f
        /**
         * 缓存页数。按「运行时可用堆的 1/8」估算能放几页，
         * 至少 1 页、至多 4 页 —— 小内存设备只留 1 页也不会崩。
         */
        private fun maxPages(): Int {
            val heapMb = (Runtime.getRuntime().maxMemory() / (1024L * 1024L)).toInt()
            val budgetMb = (heapMb / 8).coerceAtLeast(16)
            val perPageMb = 8                       // RGB_565 @1600 万像素约 8~32 MB
            return (budgetMb / perPageMb).coerceIn(1, 4)
        }
    }
}
