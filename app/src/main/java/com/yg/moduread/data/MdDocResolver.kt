package com.yg.moduread.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * 文档间相对路径解析。
 *
 * 存在的理由：技术书常按章节拆成多个 .md（你的书稿就有 chapters/ 目录），
 * 正文里写 `[下一章](./ch2.md)` 是自然写法。commonmark 会把它当普通链接，
 * 而阅读器此前只处理 `#锚点` 和 http(s)，相对路径被静默丢弃。
 *
 * 解析顺序与 [MdImageResolver] 一致（同一套 SAF 路径经验）：
 *   1. 与当前文档同目录的兄弟节点
 *   2. Downloads 提供方的 raw: 型 id 转绝对路径
 *   3. 应用私有目录（便于用户把书放进来）
 *   4. 公共 Download 目录
 */
object MdDocResolver {

    /** 是否是「文档间相对路径」候选：非 http(s)、非 #锚点、非 mailto 等。 */
    fun isRelativeDocLink(url: String): Boolean {
        val u = url.trim()
        if (u.isEmpty()) return false
        if (u.startsWith("#")) return false
        if (u.startsWith("http://") || u.startsWith("https://")) return false
        if (u.startsWith("mailto:") || u.startsWith("tel:")) return false
        // 明确是别的 content/file 授权也算外部，不在这里处理
        if (u.startsWith("content://") || u.startsWith("file://")) return false
        return true
    }

    /** 只接受 .md / .markdown，避免把图片、压缩包当文档打开。 */
    fun isMarkdownPath(path: String): Boolean {
        val lower = path.substringBefore('#').substringBefore('?').lowercase()
        return lower.endsWith(".md") || lower.endsWith(".markdown")
    }

    /**
     * 规范化相对路径：去掉 `./`，折叠 `a/../b`，并丢弃 `?query` 与 `#fragment`。
     * 纯字符串运算，可单测。
     */
    fun normalize(relative: String): String {
        val clean = relative.trim().substringBefore('#').substringBefore('?')
        val parts = ArrayDeque<String>()
        for (seg in clean.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(seg)
            }
        }
        return parts.joinToString("/")
    }

    /**
     * 把相对路径解析成可打开的 Uri；找不到返回 null。
     * 这是唯一需要 Context 的入口，其余逻辑都可脱离 Android 测试。
     */
    fun resolve(context: Context, docUri: String?, relative: String): Uri? {
        val rel = normalize(relative)
        if (rel.isEmpty()) return null

        val docId = docIdOf(docUri)
        val parent = docId?.substringBeforeLast('/', "")

        // 1) SAF 兄弟节点
        if (!parent.isNullOrEmpty()) {
            val siblingId = parent.trimEnd('/') + "/" + rel
            siblingUri(docUri, siblingId)?.let { uri ->
                if (canRead(context, uri)) return uri
            }
            // 2) raw: 型 id 直接转绝对路径
            if (parent.startsWith("raw:")) {
                val f = File(parent.removePrefix("raw:") + "/" + rel)
                if (f.isFile) return Uri.fromFile(f)
            }
        }

        // 3) 私有目录与公共 Download
        val candidates = listOfNotNull(
            File(context.filesDir, rel),
            context.getExternalFilesDir(null)?.let { File(it, rel) },
            File("/sdcard/Download", rel),
            File("/sdcard/Download", rel.substringAfterLast('/'))
        )
        candidates.firstOrNull { it.isFile }?.let { return Uri.fromFile(it) }

        // 4) 分区存储下 /sdcard 直读会被拒，改用 Downloads 提供方的 document 树查询
        downloadsUri(context, rel.substringAfterLast('/'))?.let { uri ->
            if (canRead(context, uri)) return uri
        }
        return null
    }

    private fun canRead(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    /**
     * 通过 Downloads 提供方按文件名找 Uri（Android 10+ 分区存储的正规路径）。
     * 只解决「同目录/Download 下的兄弟文档」，不递归子目录 —— 递归查询代价高，
     * 且书稿类文档通常平铺或按固定子目录组织。
     */
    private fun downloadsUri(context: Context, fileName: String): Uri? = runCatching {
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            arrayOf(android.provider.MediaStore.Downloads._ID),
            android.provider.MediaStore.Downloads.DISPLAY_NAME + " = ?",
            arrayOf(fileName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
            } else null
        }
    }.getOrNull()

    private fun docIdOf(docUri: String?): String? {
        if (docUri == null) return null
        val uri = runCatching { Uri.parse(docUri) }.getOrNull() ?: return null
        if (uri.scheme != "content") return null
        return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: uri.pathSegments.lastOrNull()
    }

    private fun siblingUri(docUri: String?, siblingId: String): Uri? {
        val uri = runCatching { Uri.parse(docUri ?: return null) }.getOrNull() ?: return null
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val path = "/" + segments.dropLast(1).joinToString("/") + "/" + Uri.encode(siblingId)
        return runCatching { uri.buildUpon().encodedPath(path).build() }.getOrNull()
    }
}
