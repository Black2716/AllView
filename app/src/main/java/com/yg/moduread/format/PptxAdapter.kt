package com.yg.moduread.format

import com.yg.moduread.format.Base64Lite as B64
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import org.w3c.dom.Element

/**
 * PPTX 解析。
 *
 * PPTX 是 ZIP + XML 容器：
 *   ppt/slides/slide1.xml, slide2.xml …  每张幻灯片一个文件
 *   ppt/slides/_rels/slideN.xml.rels     幻灯片到图片的关系
 *   ppt/media 下的图片本体
 *
 * 阅读场景只需要「按页序把每张幻灯片的文字与图片列出来」，
 * 不做版式还原 —— 那需要完整的 DrawingML 渲染，成本极高且收益低。
 */
/**
 * 一份 PPTX 的逐页 SVG。
 *
 * 为什么输出 SVG 而不是结构化文本：PPT 是固定版式文档，
 * 文字与形状都带绝对坐标；转成 SVG 才能保住版式，实现「整页」阅读。
 */
data class SlideDeck(
    /** 每页一个完整 HTML（内嵌 SVG）。 */
    val pages: List<String>,
    /** 页面宽高比（宽/高），用于界面按比例显示。 */
    val aspectRatio: Float
)

object PptxAdapter {

    /** 单张图片上限，避免内存被超大插图拖垮。 */
    private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024

    /** 16:9 默认尺寸（EMU）。 */
    private const val DEFAULT_W = 12192000L
    private const val DEFAULT_H = 6858000L

    /**
     * 把 PPTX 转成逐页 SVG。失败返回 null，调用方降级为文本解析。
     *
     * 尺寸取自 ppt/presentation.xml 的 sldSz；缺省按 16:9 的
     * 12192000 x 6858000 EMU（即 1280x720 pt）。
     */
    fun toDeck(bytes: ByteArray): SlideDeck? {
        val zip = ContainerSupport.readZip(bytes)
        val paths = collectSlidePaths(zip)
        if (paths.isEmpty()) return null

        val size = readSlideSize(zip)
        val wEmu = size.first
        val hEmu = size.second

        val pages = ArrayList<String>(paths.size)
        for (path in paths) {
            val xml = zip.text(path) ?: continue
            val rels = readSlideRels(zip, path)
            val html = PptxSvg.toHtml(
                slideXml = xml,
                widthEmu = wEmu,
                heightEmu = hEmu,
                imageResolver = { rid ->
                    val mediaPath = rels[rid] ?: return@toHtml null
                    val data = zip.get(mediaPath) ?: return@toHtml null
                    if (data.size > MAX_IMAGE_BYTES) return@toHtml null
                    val mime = when (mediaPath.substringAfterLast('.', "").lowercase()) {
                        "png" -> "image/png"
                        "jpg", "jpeg" -> "image/jpeg"
                        "gif" -> "image/gif"
                        "bmp" -> "image/bmp"
                        "webp" -> "image/webp"
                        else -> "image/png"
                    }
                    mime to B64.encode(data)
                },
                // 图表不是图片：rels 指向 chartN.xml，取全文交给 ChartSvg 画。
                chartResolver = { rid ->
                    val path2 = rels[rid] ?: return@toHtml null
                    if (path2.contains("chart")) zip.text(path2) else null
                }
            ) ?: continue
            pages.add(html)
        }
        if (pages.isEmpty()) return null
        return SlideDeck(pages, wEmu.toFloat() / hEmu.toFloat())
    }

    /** 读 presentation.xml 的 sldSz；读不到按 16:9 默认值。 */
    private fun readSlideSize(zip: ContainerSupport.ZipBundle): Pair<Long, Long> {
        val xml = zip.text("ppt/presentation.xml") ?: return DEFAULT_W to DEFAULT_H
        val root = ContainerSupport.parseXml(xml) ?: return DEFAULT_W to DEFAULT_H
        val els = root.getElementsByTagName("p:sldSz")
        if (els.length == 0) return DEFAULT_W to DEFAULT_H
        val el = els.item(0) as? Element ?: return DEFAULT_W to DEFAULT_H
        val cx = el.getAttribute("cx").toLongOrNull() ?: DEFAULT_W
        val cy = el.getAttribute("cy").toLongOrNull() ?: DEFAULT_H
        return if (cx > 0 && cy > 0) cx to cy else DEFAULT_W to DEFAULT_H
    }

    fun parse(bytes: ByteArray): ParsedDoc {
        val zip = ContainerSupport.readZip(bytes)
        val slidePaths = collectSlidePaths(zip)
        if (slidePaths.isEmpty()) {
            return ParsedDoc(
                listOf(MdBlock.Para(listOf(MdInline.Text("这份 PPTX 里没有找到幻灯片内容。")))),
                ""
            )
        }

        val blocks = ArrayList<MdBlock>()
        val plain = StringBuilder()

        slidePaths.forEachIndexed { index, path ->
            val xml = zip.text(path) ?: return@forEachIndexed
            val root = ContainerSupport.parseXml(xml) ?: return@forEachIndexed

            // 每张幻灯片一个二级标题，目录与进度都能据此定位
            val slideTitle = "第 " + (index + 1) + " 页"
            blocks.add(MdBlock.Heading(2, listOf(MdInline.Text(slideTitle))))
            plain.append(slideTitle).append('\n')

            // 文本：a:t 是 DrawingML 里所有可见文字的节点
            for (t in collectTexts(root)) {
                blocks.add(MdBlock.Para(listOf(MdInline.Text(t))))
                plain.append(t).append('\n')
            }

            // 图片：通过 slideN.xml.rels 找到 media 路径
            for (mediaPath in readSlideRels(zip, path).values) {
                if (!isImage(mediaPath)) continue
                val data = zip.get(mediaPath) ?: continue
                if (data.size > MAX_IMAGE_BYTES) continue
                blocks.add(MdBlock.EmbeddedImage(data, ""))
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(MdBlock.Para(listOf(MdInline.Text("这份 PPTX 没有可显示的文本或图片。"))))
        }
        return ParsedDoc(blocks, plain.toString())
    }

    /**
     * 收集幻灯片路径并按页码排序。
     *
     * 不能字典序遍历：slide10 会排在 slide2 前面，页序全乱。
     * 必须按文件名里的数字比较。
     */
    private fun collectSlidePaths(zip: ContainerSupport.ZipBundle): List<String> {
        return zip.names()
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .sortedBy { path ->
                path.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull()
                    ?: Int.MAX_VALUE
            }
    }

    /** 逐个 a:t 取文字，跳过空白。 */
    private fun collectTexts(root: Element): List<String> {
        val out = ArrayList<String>()
        val els = root.getElementsByTagName("a:t")
        for (i in 0 until els.length) {
            val el = els.item(i) as? Element ?: continue
            val t = (el.textContent ?: "").trim()
            if (t.isNotEmpty()) out.add(t)
        }
        return out
    }

    /** slideN.xml.rels：rId -> media 路径。 */
    private fun readSlideRels(
        zip: ContainerSupport.ZipBundle,
        slidePath: String
    ): Map<String, String> {
        val dir = slidePath.substringBeforeLast('/', "")
        val fileName = slidePath.substringAfterLast('/')
        val relsPath = dir + "/_rels/" + fileName + ".rels"
        val xml = zip.text(relsPath) ?: return emptyMap()
        val root = ContainerSupport.parseXml(xml) ?: return emptyMap()
        val out = HashMap<String, String>()
        val els = root.getElementsByTagName("Relationship")
        for (i in 0 until els.length) {
            val el = els.item(i) as? Element ?: continue
            val id = el.getAttribute("Id")
            val target = el.getAttribute("Target")
            if (id.isEmpty() || target.isEmpty()) continue
            // Target 形如 ../media/image1.png，相对 slides/ 目录
            out[id] = ContainerSupport.normalizePath(dir + "/" + target)
        }
        return out
    }

    private fun isImage(path: String): Boolean =
        when (path.substringAfterLast('.', "").lowercase()) {
            "png", "jpg", "jpeg", "gif", "bmp", "webp" -> true
            else -> false
        }
}
