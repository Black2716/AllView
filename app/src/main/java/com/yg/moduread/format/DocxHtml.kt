package com.yg.moduread.format

import android.util.Base64
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 把 DOCX 转成 HTML，交给 WebView 渲染。
 *
 * 为什么改用 WebView 而不是自己排版：
 * DOCX 的版式语义（表格跨页、图片环绕、列表缩进、字体层级）非常多，
 * 自己用 Compose 复现必然有偏差。浏览器的排版引擎本来就是干这个的，
 * 用它可以把表格、图片、分页都交给成熟的实现。
 *
 * 输出的是自包含 HTML：图片以 base64 内嵌，不依赖任何网络或文件路径，
 * 因此 WebView 可以完全禁止联网。
 */
object DocxHtml {

    /** 单张图片上限。超过就跳过，避免 HTML 字符串过大把内存撑爆。 */
    private const val MAX_IMAGE_BYTES = 3 * 1024 * 1024

    fun from(bytes: ByteArray): String? {
        val zip = ContainerSupport.readZip(bytes)
        val doc = zip.text("word/document.xml") ?: return null
        val root = ContainerSupport.parseXml(doc) ?: return null
        val body = findBody(root) ?: return null

        val rels = readRels(zip)
        val sb = StringBuilder(64 * 1024)
        sb.append(htmlHead())
        sb.append("<div id=\"src\">")
        renderChildren(body, sb, zip, rels)
        sb.append("</div>")
        sb.append(paginateScript())
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun findBody(root: Element): Element? {
        val bodies = root.getElementsByTagName("w:body")
        if (bodies.length > 0) return bodies.item(0) as? Element
        val alt = root.getElementsByTagName("body")
        return if (alt.length > 0) alt.item(0) as? Element else root
    }

    /** 关系 id -> 压缩包内路径。 */
    private fun readRels(zip: ContainerSupport.ZipBundle): Map<String, String> {
        val xml = zip.text("word/_rels/document.xml.rels") ?: return emptyMap()
        val root = ContainerSupport.parseXml(xml) ?: return emptyMap()
        val out = HashMap<String, String>()
        val els = root.getElementsByTagName("Relationship")
        for (i in 0 until els.length) {
            val el = els.item(i) as? Element ?: continue
            val id = el.getAttribute("Id")
            val target = el.getAttribute("Target")
            if (id.isEmpty() || target.isEmpty()) continue
            val path = if (target.startsWith("/")) target.substring(1)
                       else ContainerSupport.normalizePath("word/" + target)
            out[id] = path
        }
        return out
    }

    private fun renderChildren(
        parent: Element,
        sb: StringBuilder,
        zip: ContainerSupport.ZipBundle,
        rels: Map<String, String>
    ) {
        var listBuf: StringBuilder? = null
        var listTag = "ul"
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            when (local(el)) {
                "p" -> {
                    val isList = hasListMarker(el)
                    if (isList) {
                        if (listBuf == null) {
                            listBuf = StringBuilder()
                            listTag = if (isOrdered(el)) "ol" else "ul"
                        }
                        listBuf.append("<li>")
                        appendRuns(el, listBuf, zip, rels)
                        listBuf.append("</li>")
                    } else {
                        if (listBuf != null) {
                            sb.append("<").append(listTag).append(">").append(listBuf).append("</").append(listTag).append(">")
                            listBuf = null
                        }
                        val level = headingLevel(el)
                        val tag = if (level > 0) "h" + level.coerceIn(1, 6) else "p"
                        sb.append("<").append(tag).append(">")
                        appendRuns(el, sb, zip, rels)
                        sb.append("</").append(tag).append(">")
                    }
                }
                "tbl" -> {
                    if (listBuf != null) {
                        sb.append("<").append(listTag).append(">").append(listBuf).append("</").append(listTag).append(">")
                        listBuf = null
                    }
                    renderTable(el, sb, zip, rels)
                }
                else -> renderChildren(el, sb, zip, rels)
            }
        }
        if (listBuf != null) {
            sb.append("<").append(listTag).append(">").append(listBuf).append("</").append(listTag).append(">")
        }
    }

    /** 段落里的文字与图片，同时处理粗体/斜体/下划线。 */
    private fun appendRuns(
        p: Element,
        sb: StringBuilder,
        zip: ContainerSupport.ZipBundle,
        rels: Map<String, String>
    ) {
        val kids = p.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            when (local(el)) {
                "r" -> appendRun(el, sb, zip, rels)
                // 超链接里的 run 也要输出
                "hyperlink", "smartTag", "ins" -> appendRuns(el, sb, zip, rels)
                else -> {}
            }
        }
        // 段内直接挂的图片（少见，但有的文档会这样）
        val blips = p.getElementsByTagName("a:blip")
        for (i in 0 until blips.length) {
            val el = blips.item(i) as? Element ?: continue
            appendImage(el, sb, zip, rels)
        }
    }

    private fun appendRun(
        run: Element,
        sb: StringBuilder,
        zip: ContainerSupport.ZipBundle,
        rels: Map<String, String>
    ) {
        val rPr = firstChild(run, "rPr")
        val bold = rPr?.let { hasChild(it, "b") } == true
        val italic = rPr?.let { hasChild(it, "i") } == true
        val underline = rPr?.let { hasChild(it, "u") } == true

        val textEls = run.getElementsByTagName("w:t")
        if (textEls.length > 0) {
            var open = ""
            var close = ""
            if (bold) { open += "<strong>"; close = "</strong>" + close }
            if (italic) { open += "<em>"; close = "</em>" + close }
            if (underline) { open += "<u>"; close = "</u>" + close }
            sb.append(open)
            for (i in 0 until textEls.length) {
                sb.append(escape((textEls.item(i) as? Element)?.textContent.orEmpty()))
            }
            sb.append(close)
        }

        val blips = run.getElementsByTagName("a:blip")
        for (i in 0 until blips.length) {
            val el = blips.item(i) as? Element ?: continue
            appendImage(el, sb, zip, rels)
        }
        // 老格式图片
        val legacy = run.getElementsByTagName("v:imagedata")
        for (i in 0 until legacy.length) {
            val el = legacy.item(i) as? Element ?: continue
            val rid = el.getAttribute("r:id").ifEmpty { el.getAttribute("id") }
            appendImageByRid(rid, sb, zip, rels)
        }
    }

    private fun appendImage(
        blip: Element,
        sb: StringBuilder,
        zip: ContainerSupport.ZipBundle,
        rels: Map<String, String>
    ) {
        val rid = blip.getAttribute("r:embed").ifEmpty { blip.getAttribute("embed") }
        appendImageByRid(rid, sb, zip, rels)
    }

    private fun appendImageByRid(
        rid: String,
        sb: StringBuilder,
        zip: ContainerSupport.ZipBundle,
        rels: Map<String, String>
    ) {
        if (rid.isEmpty()) return
        val path = rels[rid] ?: return
        val data = zip.get(path) ?: return
        if (data.size > MAX_IMAGE_BYTES) return
        val mime = when (path.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        val b64 = Base64.encodeToString(data, Base64.NO_WRAP)
        sb.append("<img src=\"data:").append(mime).append(";base64,").append(b64).append("\"/>")
    }

    private fun renderTable(
        tbl: Element,
        sb: StringBuilder,
        zip: ContainerSupport.ZipBundle,
        rels: Map<String, String>
    ) {
        sb.append("<table>")
        for (tr in directChildren(tbl, "tr")) {
            sb.append("<tr>")
            for (tc in directChildren(tr, "tc")) {
                val span = gridSpan(tc)
                sb.append("<td")
                if (span > 1) sb.append(" colspan=\"").append(span).append("\"")
                sb.append(">")
                renderChildren(tc, sb, zip, rels)
                sb.append("</td>")
            }
            sb.append("</tr>")
        }
        sb.append("</table>")
    }

    // ── 样式判定 ─────────────────────────────────────────────

    private fun headingLevel(p: Element): Int {
        val pPr = firstChild(p, "pPr") ?: return 0
        val styleEl = firstChild(pPr, "pStyle") ?: return 0
        val v = styleEl.getAttribute("w:val").ifEmpty { styleEl.getAttribute("val") }
        return ContainerAdapters.headingLevelOf(v)
    }

    private fun hasListMarker(p: Element): Boolean {
        val pPr = firstChild(p, "pPr") ?: return false
        return firstChild(pPr, "numPr") != null
    }

    /** 编号列表 vs 项目符号：只认 numbering 里明说是 decimal 的，其余按无序处理。 */
    private fun isOrdered(p: Element): Boolean = false

    private fun gridSpan(tc: Element): Int {
        val els = tc.getElementsByTagName("w:gridSpan")
        if (els.length == 0) return 1
        val el = els.item(0) as? Element ?: return 1
        val v = el.getAttribute("w:val").ifEmpty { el.getAttribute("val") }
        return v.toIntOrNull()?.coerceIn(1, 50) ?: 1
    }

    // ── DOM 小工具 ───────────────────────────────────────────

    private fun local(el: Element): String = el.tagName.lowercase().substringAfter(':')

    private fun firstChild(parent: Element, name: String): Element? {
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val e = n as Element
            val ln = e.tagName.lowercase().substringAfter(':')
            if (ln == name.substringAfter(':')) return e
        }
        return null
    }

    private fun hasChild(parent: Element, name: String): Boolean = firstChild(parent, name) != null

    private fun directChildren(parent: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val e = n as Element
            if (local(e) == name) out.add(e)
        }
        return out
    }

    private fun escape(s: String): String = buildString(s.length + 16) {
        for (c in s) {
            when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '\u0022' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    // ── HTML 骨架 ────────────────────────────────────────────

    private fun htmlHead(): String = "<!DOCTYPE html><html><head>" +
        "<meta charset=\"utf-8\"/>" +
        "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>" +
        "<style>" +
        "*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}" +
        "body{margin:0;padding:0;background:#e9e9ee;font-family:-apple-system,'Noto Sans CJK SC',sans-serif;" +
        "color:#1b1b1f;font-size:16px;line-height:1.75;-webkit-text-size-adjust:100%}" +
        "#src{display:none}" +
        ".page{background:#fff;width:100%;margin:14px 0;padding:18mm 16mm;" +
        "box-shadow:0 1px 4px rgba(0,0,0,.16);position:relative;overflow:hidden}" +
        ".page .pno{position:absolute;right:12px;bottom:8px;font-size:11px;color:#9a9aa2}" +
        "h1,h2,h3,h4,h5,h6{margin:1.1em 0 .5em;line-height:1.35;font-weight:600;break-after:avoid}" +
        "h1{font-size:1.55em}h2{font-size:1.32em}h3{font-size:1.16em}h4{font-size:1.04em}" +
        "p{margin:.55em 0;text-align:justify}" +
        "ul,ol{margin:.55em 0;padding-left:1.5em}li{margin:.25em 0}" +
        "table{border-collapse:collapse;width:100%;margin:.8em 0;font-size:.94em;break-inside:avoid}" +
        "td,th{border:1px solid #c9c9d1;padding:6px 9px;vertical-align:top;word-break:break-word}" +
        "th{background:#f2f2f6;font-weight:600}" +
        "img{max-width:100%;height:auto;display:block;margin:.7em 0}" +
        "strong{font-weight:600}u{text-decoration:underline}" +
        "</style></head><body>"

    /**
     * 分页脚本。
     *
     * WebView 本身只做连续滚动，要「整页」必须自己切：
     * 把 #src 里的块级元素按页高搬进 .page 容器，装不下就换下一页。
     * 单块高过整页时（超长表格/大图）限制高度，避免溢出页外看不见。
     */
    private fun paginateScript(): String = """
<script>
(function(){
  var src = document.getElementById('src');
  if(!src) return;
  // 页高按 A4 比例：内容宽 210mm 对应高 297mm
  function pageHeight(){
    var w = document.documentElement.clientWidth;
    return Math.round(w * 297 / 210);
  }
  var H = pageHeight();
  var nodes = Array.prototype.slice.call(src.childNodes);
  var pages = [];
  var cur = null, used = 0;

  function newPage(){
    cur = document.createElement('div');
    cur.className = 'page';
    // 记录可用高度：页高减去上下内边距
    cur._limit = H - Math.round(H * 0.12);
    used = 0;
    document.body.appendChild(cur);
    pages.push(cur);
  }

  newPage();
  for (var i = 0; i < nodes.length; i++){
    var n = nodes[i];
    if (n.nodeType === 3 && !n.textContent.trim()) continue;
    cur.appendChild(n);
    var h = n.offsetHeight || 0;
    if (used + h > cur._limit && cur.childNodes.length > 1){
      // 放不下：把这块挪到新页
      cur.removeChild(n);
      newPage();
      cur.appendChild(n);
      used = n.offsetHeight || 0;
    } else {
      used += h;
    }
  }

  // 页码
  for (var p = 0; p < pages.length; p++){
    var tag = document.createElement('div');
    tag.className = 'pno';
    tag.textContent = (p+1) + ' / ' + pages.length;
    pages[p].appendChild(tag);
  }
  // 通知原生层页数，便于顶部显示
  try { window.__pageCount = pages.length; } catch(e){}
})();
</script>
</body></html>"""
}
