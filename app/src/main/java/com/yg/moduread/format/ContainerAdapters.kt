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
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val el = child as Element
            val tag = el.tagName.lowercase().substringAfter(':')
            when (tag) {
                "p" -> {
                    val text = collectText(el).trim()
                    if (text.isNotEmpty()) {
                        val style = styleOf(el)
                        val level = headingLevelOf(style)
                        if (level > 0) out.add(MdBlock.Heading(level, listOf(MdInline.Text(text))))
                        else out.add(MdBlock.Para(listOf(MdInline.Text(text))))
                    }
                }
                "tbl" -> {
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
                            out.add(MdBlock.EmbeddedImage(data, ""))
                        }
                    }
                }
                else -> walkDocx(el, out, zip, images)
            }
        }
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

    private fun collectTableRows(tbl: Element): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val trs = tbl.getElementsByTagName("w:tr")
        for (i in 0 until trs.length) {
            val tr = trs.item(i) as? Element ?: continue
            val cells = ArrayList<String>()
            val tcs = tr.getElementsByTagName("w:tc")
            for (j in 0 until tcs.length) {
                val tc = tcs.item(j) as? Element ?: continue
                cells.add(collectText(tc).trim())
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }
        return rows
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
