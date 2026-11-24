package com.yg.moduread.format

import com.yg.moduread.md.MdAlign
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.md.MdTableCell
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 容器格式的 ZIP 读取与 HTML/XHTML 转块。
 *
 * 为什么用 DOM 而不是 XmlPullParser：后者在 JVM 单元测试里是 Android 桩，
 * newInstance() 直接抛 "not mocked"，第二档解析器无法被测试覆盖。
 * javax.xml.parsers 是 JDK 与 Android 都自带的标准 API，零依赖且两端行为一致。
 */
object ContainerSupport {

    class ZipBundle(private val entries: Map<String, ByteArray>) {
        fun names(): Set<String> = entries.keys

        fun get(name: String): ByteArray? {
            entries[name]?.let { return it }
            val trimmed = name.trimStart('/')
            entries[trimmed]?.let { return it }
            val lower = trimmed.lowercase()
            return entries.entries.firstOrNull { it.key.lowercase() == lower }?.value
        }

        fun text(name: String): String? = get(name)?.let { String(it, Charsets.UTF_8) }

        fun firstMatching(suffix: String): Pair<String, ByteArray>? {
            val hit = entries.entries.firstOrNull { it.key.lowercase().endsWith(suffix.lowercase()) }
            return hit?.let { it.key to it.value }
        }
    }

    fun readZip(bytes: ByteArray): ZipBundle {
        val map = LinkedHashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                    map[normalizePath(entry.name)] = out.toByteArray()
                }
            }
        }
        return ZipBundle(map)
    }

    fun normalizePath(path: String): String {
        val unified = path.replace(SEP, "/").trimStart('/')
        val parts = ArrayDeque<String>()
        for (seg in unified.split("/")) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

    private const val SEP = "\\"

    fun siblingPath(basePath: String, relative: String): String {
        if (relative.isBlank()) return ""
        if (relative.startsWith("http://") || relative.startsWith("https://") || relative.startsWith("data:")) return ""
        val baseDir = basePath.substringBeforeLast('/', "")
        return normalizePath(if (baseDir.isEmpty()) relative else baseDir + "/" + relative)
    }

    /** 解析 XML。多根节点时包一层 <root> 再试。 */
    fun parseXml(xml: String): Element? = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        val builder = factory.newDocumentBuilder()
        val cleaned = stripDeclarations(xml)
        runCatching { builder.parse(ByteArrayInputStream(cleaned.toByteArray(Charsets.UTF_8))).documentElement }
            .getOrElse {
                runCatching {
                    builder.parse(ByteArrayInputStream(("<root>" + cleaned + "</root>").toByteArray(Charsets.UTF_8))).documentElement
                }.getOrNull()
            }
    }.getOrNull()

    private fun stripDeclarations(xml: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < xml.length) {
            if (xml.startsWith("<!DOCTYPE", i, ignoreCase = true) || xml.startsWith("<?xml", i, ignoreCase = true)) {
                val close = xml.indexOf('>', i)
                if (close < 0) break
                i = close + 1
                continue
            }
            sb.append(xml[i])
            i++
        }
        return sb.toString()
    }

    // ── HTML / XHTML 转块 ──────────────────────────────────────

    fun htmlToBlocks(
        html: String,
        bundle: ZipBundle?,
        basePath: String,
        collector: MutableMap<String, ByteArray>
    ): List<MdBlock> {
        val root = parseXml(html)
        if (root == null) return fallbackTextBlocks(html)
        val out = ArrayList<MdBlock>()
        walk(root, out, bundle, basePath, collector, 0)
        return if (out.isEmpty()) fallbackTextBlocks(html) else out
    }

    private fun walk(
        node: Node,
        out: MutableList<MdBlock>,
        bundle: ZipBundle?,
        basePath: String,
        collector: MutableMap<String, ByteArray>,
        headingLevel: Int
    ) {
        when (node.nodeType) {
            Node.TEXT_NODE -> {
                val t = node.nodeValue ?: return
                if (t.isBlank()) return
                val text = decodeEntities(t).trim()
                if (text.isEmpty()) return
                if (headingLevel > 0) out.add(MdBlock.Heading(headingLevel.coerceIn(1, 6), listOf(MdInline.Text(text))))
                else out.add(MdBlock.Para(listOf(MdInline.Text(text))))
            }
            Node.ELEMENT_NODE -> {
                val el = node as Element
                val tag = el.tagName.lowercase().substringAfter(':')
                if (tag == "script" || tag == "style" || tag == "head") return

                val hLevel = when (tag) {
                    "h1" -> 1; "h2" -> 2; "h3" -> 3; "h4" -> 4; "h5" -> 5; "h6" -> 6
                    else -> 0
                }
                if (hLevel > 0) {
                    val text = decodeEntities(el.textContent ?: "").trim()
                    if (text.isNotEmpty()) out.add(MdBlock.Heading(hLevel, listOf(MdInline.Text(text))))
                    return
                }
                if (tag == "img") {
                    val src = el.getAttribute("src")
                    val alt = el.getAttribute("alt")
                    val path = siblingPath(basePath, src)
                    val data = if (path.isEmpty()) null else bundle?.get(path)
                    if (data != null) {
                        collector[path] = data
                        out.add(MdBlock.EmbeddedImage(data, alt))
                    }
                    return
                }
                if (tag == "li") {
                    val text = decodeEntities(el.textContent ?: "").trim()
                    if (text.isNotEmpty()) out.add(MdBlock.ListBlock(false, 1, true, listOf(listOf(MdBlock.Para(listOf(MdInline.Text(text)))))))
                    return
                }

                val children = el.childNodes
                for (i in 0 until children.length) walk(children.item(i), out, bundle, basePath, collector, hLevel)
            }
        }
    }

    fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        return s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
    }

    fun fallbackTextBlocks(html: String): List<MdBlock> {
        val sb = StringBuilder()
        var i = 0
        var inTag = false
        while (i < html.length) {
            val c = html[i]
            if (c == '<') { inTag = true; i++; continue }
            if (c == '>') { inTag = false; sb.append(' '); i++; continue }
            if (!inTag) sb.append(c)
            i++
        }
        val blocks = ArrayList<MdBlock>()
        for (line in decodeEntities(sb.toString()).split('\n')) {
            val t = line.trim()
            if (t.isNotEmpty()) blocks.add(MdBlock.Para(listOf(MdInline.Text(t))))
        }
        return blocks
    }

    fun tableFrom(rows: List<List<String>>): MdBlock.Table {
        val width = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        fun cells(row: List<String>): List<MdTableCell> = (0 until width).map { i ->
            MdTableCell(listOf(MdInline.Text(row.getOrElse(i) { "" })), MdAlign.NONE)
        }
        return MdBlock.Table(cells(rows.firstOrNull() ?: emptyList()), rows.drop(1).map { cells(it) })
    }
}
