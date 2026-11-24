package com.yg.moduread.format

import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 第二档：ZIP + XML 容器格式（EPUB / DOCX / ODT）与单文件 HTML。
 *
 * 用 DOM（javax.xml.parsers）而不是 XmlPullParser：后者在 JVM 单测里是 Android 桩。
 * 零第三方依赖，java.util.zip 解包，内嵌图片以字节形式交给渲染层（不落盘）。
 */
object ContainerAdapters {

    private const val NL = "\n"

    private fun isHtmlName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
    }

    // ── EPUB ─────────────────────────────────────────────────────

    fun epub(bytes: ByteArray): ParsedDoc {
        val zip = ContainerSupport.readZip(bytes)
        val images = LinkedHashMap<String, ByteArray>()
        val opfEntry = zip.firstMatching(".opf")
        if (opfEntry == null) return degrade(zip, "missing opf")
        val opfPath = opfEntry.first
        val opfText = String(opfEntry.second, Charsets.UTF_8)
        val opfBase = opfPath.substringBeforeLast('/', "")

        val root = ContainerSupport.parseXml(opfText)
        val manifest = LinkedHashMap<String, String>()
        val spine = ArrayList<String>()
        if (root != null) {
            val items = root.getElementsByTagName("item")
            for (i in 0 until items.length) {
                val el = items.item(i) as? Element ?: continue
                val id = el.getAttribute("id")
                val href = el.getAttribute("href")
                if (id.isNotEmpty() && href.isNotEmpty()) manifest[id] = href
            }
            val refs = root.getElementsByTagName("itemref")
            for (i in 0 until refs.length) {
                val el = refs.item(i) as? Element ?: continue
                val idref = el.getAttribute("idref")
                if (idref.isNotEmpty()) spine.add(idref)
            }
        }

        val blocks = ArrayList<MdBlock>()
        val chapters = ArrayList<String>()
        if (spine.isNotEmpty()) {
            for (id in spine) manifest[id]?.let { chapters.add(it) }
        } else {
            for (href in manifest.values) if (isHtmlName(href)) chapters.add(href)
        }

        for (href in chapters) {
            val path = ContainerSupport.normalizePath(if (opfBase.isEmpty()) href else opfBase + "/" + href)
            val content = zip.text(path)
            if (content != null) blocks.addAll(ContainerSupport.htmlToBlocks(content, zip, path, images))
        }

        if (blocks.isEmpty()) return degrade(zip, "empty content")
        val toc = parseNcx(zip)
        val merged = if (toc.isEmpty()) blocks else ensureTocBlocks(blocks, toc)
        return ParsedDoc(merged, plainOf(merged))
    }

    private fun parseNcx(zip: ContainerSupport.ZipBundle): List<Pair<Int, String>> {
        val ncx = zip.firstMatching(".ncx") ?: return emptyList()
        val root = ContainerSupport.parseXml(String(ncx.second, Charsets.UTF_8)) ?: return emptyList()
        val out = ArrayList<Pair<Int, String>>()
        collectNavPoints(root, 1, out)
        return out
    }

    private fun collectNavPoints(node: Node, depth: Int, out: MutableList<Pair<Int, String>>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val el = child as Element
            if (el.tagName.equals("navPoint", ignoreCase = true)) {
                var title = ""
                val labels = el.getElementsByTagName("text")
                if (labels.length > 0) title = (labels.item(0).textContent ?: "").trim()
                if (title.isNotEmpty()) out.add(depth.coerceIn(1, 4) to title)
                collectNavPoints(el, depth + 1, out)
            } else {
                collectNavPoints(el, depth, out)
            }
        }
    }

    private fun ensureTocBlocks(blocks: List<MdBlock>, toc: List<Pair<Int, String>>): List<MdBlock> {
        val headingCount = blocks.count { it is MdBlock.Heading }
        if (headingCount >= toc.size / 2) return blocks
        val inserted = ArrayList<MdBlock>()
        for (pair in toc) inserted.add(MdBlock.Heading(pair.first, listOf(MdInline.Text(pair.second))))
        return inserted + blocks
    }

    private fun degrade(zip: ContainerSupport.ZipBundle, reason: String): ParsedDoc {
        val blocks = ArrayList<MdBlock>()
        for (name in zip.names()) {
            if (!isHtmlName(name)) continue
            val content = zip.text(name) ?: continue
            blocks.addAll(ContainerSupport.fallbackTextBlocks(content))
        }
        if (blocks.isEmpty()) blocks.add(MdBlock.Para(listOf(MdInline.Text("EPUB parse failed: " + reason))))
        return ParsedDoc(blocks, plainOf(blocks))
    }

    // ── DOCX ─────────────────────────────────────────────────────

    fun docx(bytes: ByteArray): ParsedDoc {
        val zip = ContainerSupport.readZip(bytes)
        val images = LinkedHashMap<String, ByteArray>()
        val docText = zip.text("word/document.xml")
        if (docText == null) {
            return ParsedDoc(listOf(MdBlock.Para(listOf(MdInline.Text("DOCX parse failed: no document.xml")))), "")
        }
        val root = ContainerSupport.parseXml(docText)
        if (root == null) {
            return ParsedDoc(listOf(MdBlock.Para(listOf(MdInline.Text("DOCX parse failed: bad xml")))), "")
        }
        val blocks = ArrayList<MdBlock>()
        walkDocx(root, blocks, zip, images)
        if (blocks.isEmpty()) blocks.add(MdBlock.Para(listOf(MdInline.Text("DOCX is empty"))))
        return ParsedDoc(blocks, plainOf(blocks))
    }

    private fun walkDocx(
        node: Node,
        out: MutableList<MdBlock>,
        zip: ContainerSupport.ZipBundle,
        images: MutableMap<String, ByteArray>
    ) {
        val children = node.childNodes
        // 收集连续的列表项：Word 里每个列表项是独立的 w:p（带 w:numPr），
        // 必须先把连续的攒起来才能输出成一个 ListBlock，否则会散成一堆段落。
        val pendingItems = ArrayList<List<MdBlock>>()
        var pendingOrdered = false

        fun flushList() {
            if (pendingItems.isEmpty()) return
            out.add(MdBlock.ListBlock(pendingOrdered, 1, true, ArrayList(pendingItems)))
            pendingItems.clear()
        }

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val el = child as Element
            val tag = el.tagName.lowercase().substringAfter(':')
            when (tag) {
                "p" -> {
                    val text = collectText(el).trim()
                    val listInfo = listInfoOf(el)
                    // 图片在 Word 里是嵌在段落内部的（w:p > w:r > w:drawing > a:blip），
                    // 只在外层 when 里认 "blip" 永远走不到 —— 那些含图的段落会被
                    // 当成纯文本段落，图片直接丢失。
                    // 所以段落里必须主动往下找图片。
                    val paraImages = collectParagraphImages(el, zip, images)
                    if (listInfo != null) {
                        // 列表项：攒进 pendingItems，等连续项结束再一起输出
                        if (pendingItems.isEmpty()) pendingOrdered = listInfo.second
                        if (text.isNotEmpty()) {
                            pendingItems.add(listOf(MdBlock.Para(listOf(MdInline.Text(text)))))
                        }
                    } else {
                        flushList()
                        if (text.isNotEmpty()) {
                            val level = headingLevelOf(styleOf(el))
                            if (level > 0) out.add(MdBlock.Heading(level, listOf(MdInline.Text(text))))
                            else out.add(MdBlock.Para(listOf(MdInline.Text(text))))
                        }
                        // 段落文字之后跟图片，保持原始阅读顺序
                        for (img in paraImages) {
                            out.add(MdBlock.EmbeddedImage(img, ""))
                        }
                    }
                }
                "tbl" -> {
                    flushList()
                    val rows = collectTableRows(el)
                    if (rows.isNotEmpty()) out.add(ContainerSupport.tableFrom(rows))
                }
                "blip" -> {
                    val rid = el.getAttribute("r:embed").ifEmpty { el.getAttribute("embed") }
                    val target = docxImagePath(zip, rid)
                    if (target != null) {
                        val data = zip.get(target)
                        if (data != null) {
                            images[target] = data
                            flushList()
                            out.add(MdBlock.EmbeddedImage(data, ""))
                        }
                    }
                }
                else -> walkDocx(el, out, zip, images)
            }
        }
        flushList()
    }

    /**
     * 判断段落是否是列表项，并返回 (层级, 是否有序)。
     *
     * Word 用 w:numPr/w:numId 表示列表；numId 具体是否有序要看 numbering.xml，
     * 这里不解析那个文件（成本高、收益低），用 w:ilvl + 经验值近似：
     * 绝大多数实际文档里项目符号列表远多于编号列表，因此默认无序。
     */
    private fun listInfoOf(p: Element): Pair<Int, Boolean>? {
        val numPr = p.getElementsByTagName("w:numPr")
        if (numPr.length == 0) return null
        val ilvl = p.getElementsByTagName("w:ilvl")
        val level = if (ilvl.length > 0) {
            val e = ilvl.item(0) as? Element
            (e?.getAttribute("w:val")?.toIntOrNull() ?: 0)
        } else 0
        return level to false
    }
    /**
     * 收集一个段落内的所有内嵌图片，按文档顺序。
     *
     * Word 的图片位置是 w:p → w:r → w:drawing → a:blip（或旧的 v:imagedata）。
     * 这几种都要认，否则「有些 docx 带图片」的图片会静默丢失。
     */
    private fun collectParagraphImages(
        p: Element,
        zip: ContainerSupport.ZipBundle,
        images: MutableMap<String, ByteArray>
    ): List<ByteArray> {
        val result = ArrayList<ByteArray>()
        // a:blip 用 r:embed；老格式 v:imagedata 用 r:id
        val blips = p.getElementsByTagName("a:blip")
        for (i in 0 until blips.length) {
            val el = blips.item(i) as? Element ?: continue
            val rid = el.getAttribute("r:embed").ifEmpty { el.getAttribute("embed") }
            loadImage(rid, zip, images)?.let { result.add(it) }
        }
        val legacy = p.getElementsByTagName("v:imagedata")
        for (i in 0 until legacy.length) {
            val el = legacy.item(i) as? Element ?: continue
            val rid = el.getAttribute("r:id").ifEmpty { el.getAttribute("id") }
            loadImage(rid, zip, images)?.let { result.add(it) }
        }
        return result
    }

    /** 按关系 id 取出图片字节；读不到返回 null。 */
    private fun loadImage(
        rid: String,
        zip: ContainerSupport.ZipBundle,
        images: MutableMap<String, ByteArray>
    ): ByteArray? {
        if (rid.isBlank()) return null
        val target = docxImagePath(zip, rid) ?: return null
        val data = zip.get(target) ?: return null
        images[target] = data
        return data
    }

    private fun collectText(el: Element): String {
        val sb = StringBuilder()
        val texts = el.getElementsByTagName("w:t")
        if (texts.length > 0) {
            for (i in 0 until texts.length) sb.append(texts.item(i).textContent ?: "")
            return sb.toString()
        }
        val plain = el.getElementsByTagName("t")
        for (i in 0 until plain.length) sb.append(plain.item(i).textContent ?: "")
        return sb.toString()
    }

    private fun styleOf(p: Element): String {
        val styles = p.getElementsByTagName("w:pStyle")
        if (styles.length > 0) {
            val el = styles.item(0) as? Element ?: return ""
            val v = el.getAttribute("w:val")
            return if (v.isNotEmpty()) v else el.getAttribute("val")
        }
        return ""
    }

    /**
     * 收集表格行。
     *
     * 三处必须用「直接子元素」而不能用 getElementsByTagName：
     *  - w:tr：嵌套表格的行会被父表重复计入，行数翻倍
     *  - w:tc：嵌套表格的单元格会被算进外层行，列数错乱
     *  - w:t：同理，单元格文字会串
     *
     * 另外处理合并单元格：w:gridSpan 表示横跨 n 列，
     * 必须补 n-1 个空占位，否则该行短一截、后续列整体左移。
     */
    private fun collectTableRows(tbl: Element): List<List<String>> {
        val rows = ArrayList<List<String>>()
        for (tr in directChildren(tbl, "tr")) {
            val cells = ArrayList<String>()
            for (tc in directChildren(tr, "tc")) {
                val text = collectText(tc).trim()
                cells.add(text)
                // 横向合并：补占位保持列数一致
                val span = gridSpanOf(tc)
                for (k in 1 until span) cells.add("")
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }
        // 统一成最大列数，缺失的尾部补空 —— 上下行必须等宽才能对齐
        val width = rows.maxOfOrNull { it.size } ?: 0
        return rows.map { r ->
            if (r.size == width) r else r + List(width - r.size) { "" }
        }
    }

    /** w:gridSpan 的值，默认 1。 */
    private fun gridSpanOf(tc: Element): Int {
        val spans = tc.getElementsByTagName("w:gridSpan")
        if (spans.length == 0) return 1
        val el = spans.item(0) as? Element ?: return 1
        val v = el.getAttribute("w:val").ifEmpty { el.getAttribute("val") }
        return v.toIntOrNull()?.coerceIn(1, 50) ?: 1
    }

    /**
     * 取直接子元素（按本地名，忽略命名空间前缀）。
     */
    private fun directChildren(parent: Element, localName: String): List<Element> {
        val out = ArrayList<Element>()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val e = n as Element
            if (e.tagName.lowercase().substringAfter(':') == localName) out.add(e)
        }
        return out
    }

    fun headingLevelOf(style: String): Int {
        if (style.isBlank()) return 0
        val s = style.lowercase()
        val m = Regex("(?:heading|标题)\\s*([1-6])").find(s) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    private fun docxImagePath(zip: ContainerSupport.ZipBundle, rid: String): String? {
        if (rid.isBlank()) return null
        val rels = zip.text("word/_rels/document.xml.rels") ?: return null
        val root = ContainerSupport.parseXml(rels) ?: return null
        val relsEls = root.getElementsByTagName("Relationship")
        for (i in 0 until relsEls.length) {
            val el = relsEls.item(i) as? Element ?: continue
            if (el.getAttribute("Id") == rid) {
                val target = el.getAttribute("Target")
                if (target.isEmpty()) return null
                return ContainerSupport.normalizePath(if (target.startsWith("/")) target.substring(1) else "word/" + target)
            }
        }
        return null
    }

    // ── ODT ──────────────────────────────────────────────────────

    fun odt(bytes: ByteArray): ParsedDoc {
        val zip = ContainerSupport.readZip(bytes)
        val images = LinkedHashMap<String, ByteArray>()
        val content = zip.text("content.xml")
        if (content == null) {
            return ParsedDoc(listOf(MdBlock.Para(listOf(MdInline.Text("ODT parse failed: no content.xml")))), "")
        }
        val root = ContainerSupport.parseXml(content)
        if (root == null) {
            return ParsedDoc(listOf(MdBlock.Para(listOf(MdInline.Text("ODT parse failed: bad xml")))), "")
        }
        val blocks = ArrayList<MdBlock>()
        walkOdt(root, blocks, zip, images)
        if (blocks.isEmpty()) blocks.add(MdBlock.Para(listOf(MdInline.Text("ODT is empty"))))
        return ParsedDoc(blocks, plainOf(blocks))
    }

    private fun walkOdt(
        node: Node,
        out: MutableList<MdBlock>,
        zip: ContainerSupport.ZipBundle,
        images: MutableMap<String, ByteArray>
    ) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val el = child as Element
            val tag = el.tagName.lowercase().substringAfter(':')
            when (tag) {
                "h" -> {
                    val lvl = el.getAttribute("text:outline-level").ifEmpty { el.getAttribute("outline-level") }
                    val level = lvl.toIntOrNull() ?: 1
                    val text = (el.textContent ?: "").trim()
                    if (text.isNotEmpty()) out.add(MdBlock.Heading(level.coerceIn(1, 6), listOf(MdInline.Text(text))))
                }
                "p" -> {
                    val text = (el.textContent ?: "").trim()
                    if (text.isNotEmpty()) out.add(MdBlock.Para(listOf(MdInline.Text(text))))
                    // 与 DOCX 同理：draw:image 嵌在 draw:frame 里、frame 又在段落内，
                    // 只在顶层认 "image" 永远走不到，含图段落会丢图。
                    for (img in collectOdtImages(el, zip, images)) {
                        out.add(MdBlock.EmbeddedImage(img, ""))
                    }
                }
                "image" -> {
                    val href = el.getAttribute("xlink:href").ifEmpty { el.getAttribute("href") }
                    val path = ContainerSupport.normalizePath(href)
                    val data = if (path.isEmpty()) null else zip.get(path)
                    if (data != null) {
                        images[path] = data
                        out.add(MdBlock.EmbeddedImage(data, ""))
                    }
                }
                else -> walkOdt(el, out, zip, images)
            }
        }
    }

    /** 收集 ODT 段落内所有 draw:image，按文档顺序。 */
    private fun collectOdtImages(
        p: Element,
        zip: ContainerSupport.ZipBundle,
        images: MutableMap<String, ByteArray>
    ): List<ByteArray> {
        val result = ArrayList<ByteArray>()
        val els = p.getElementsByTagName("draw:image")
        for (i in 0 until els.length) {
            val el = els.item(i) as? Element ?: continue
            val href = el.getAttribute("xlink:href").ifEmpty { el.getAttribute("href") }
            val path = ContainerSupport.normalizePath(href)
            if (path.isEmpty()) continue
            val data = zip.get(path) ?: continue
            images[path] = data
            result.add(data)
        }
        return result
    }

    // ── HTML ─────────────────────────────────────────────────────

    fun html(text: String): ParsedDoc {
        val images = LinkedHashMap<String, ByteArray>()
        val blocks = ContainerSupport.htmlToBlocks(text, null, "", images)
        return ParsedDoc(blocks, plainOf(blocks))
    }

    // ── 公用 ─────────────────────────────────────────────────────

    fun plainOf(blocks: List<MdBlock>): String {
        val sb = StringBuilder()
        for (b in blocks) sb.append(plainOfBlock(b)).append(NL)
        return sb.toString()
    }

    private fun plainOfBlock(b: MdBlock): String = when (b) {
        is MdBlock.Heading -> inlineText(b.content)
        is MdBlock.Para -> inlineText(b.content)
        is MdBlock.CodeBlock -> b.code
        is MdBlock.Quote -> plainOf(b.children)
        is MdBlock.ListBlock -> {
            val sb = StringBuilder()
            for (item in b.items) sb.append(plainOf(item)).append(NL)
            sb.toString()
        }
        is MdBlock.Table -> tableText(b)
        is MdBlock.Math -> b.latex
        is MdBlock.Image -> b.alt
        is MdBlock.EmbeddedImage -> b.alt
        is MdBlock.PdfPage -> ""
        is MdBlock.Rule -> ""
    }

    private fun inlineText(items: List<MdInline>): String {
        val sb = StringBuilder()
        for (item in items) {
            when (item) {
                is MdInline.Text -> sb.append(item.text)
                is MdInline.Code -> sb.append(item.text)
                is MdInline.Emph -> sb.append(inlineText(item.children))
                is MdInline.Strong -> sb.append(inlineText(item.children))
                is MdInline.Link -> sb.append(inlineText(item.children))
                is MdInline.InlineImage -> sb.append(item.alt)
                is MdInline.InlineMath -> sb.append(item.latex)
                is MdInline.Break -> sb.append(" ")
            }
            sb.append(" ")
        }
        return sb.toString().trim()
    }

    private fun tableText(t: MdBlock.Table): String {
        val sb = StringBuilder()
        for (c in t.head) sb.append(inlineText(c.inlines)).append(" ")
        sb.append(NL)
        for (row in t.rows) {
            for (c in row) sb.append(inlineText(c.inlines)).append(" ")
            sb.append(NL)
        }
        return sb.toString()
    }
}
