package com.yg.moduread.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

/**
 * 本地 Markdown 的图片路径解析。
 *
 * 纯离线阅读器：http(s) 一律返回 null，界面显示占位提示，不做任何网络抓取。
 * 相对路径按顺序尝试，命中即止：
 *   1. 与 .md 同目录的兄弟节点（SAF documentId 拼路径；Downloads 的 raw: 型 id 转绝对路径）
 *   2. 应用私有目录 filesDir / externalFilesDir
 *   3. 公共 Download 目录直读
 *   4. MediaStore 同名图片（Android 11+ 分区存储兜底）
 */
object MdImageResolver {

    fun open(context: Context, docUri: String?, raw: String): InputStream? {
        val url = raw.trim()
        if (url.isEmpty()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return null

        val parsed = Uri.parse(url)
        when (parsed.scheme) {
            "content" -> return runCatching {
                context.contentResolver.openInputStream(parsed)
            }.getOrNull()

            "file" -> return parsed.path?.let(::openFile)
        }
        if (File(url).isAbsolute) openFile(url)?.let { return it }

        val relative = url.removePrefix("./")
        val parentDoc = docIdOf(docUri)?.substringBeforeLast('/', "")

        // 1) 与文档同级
        if (parentDoc != null && parentDoc.isNotEmpty()) {
            val siblingId = parentDoc.trimEnd('/') + "/" + relative
            siblingUri(docUri, siblingId)?.let { uri ->
                runCatching { context.contentResolver.openInputStream(uri) }
                    .getOrNull()?.let { return it }
            }
            if (parentDoc.startsWith("raw:")) {
                openFile(parentDoc.removePrefix("raw:") + "/" + relative)?.let { return it }
            }
        }

        // 2) 私有目录
        listOfNotNull(
            File(context.filesDir, relative),
            context.getExternalFilesDir(null)?.let { File(it, relative) }
        ).forEach { file -> if (file.isFile) return openFile(file.absolutePath) }

        // 3) 公共 Download 直读（Android 10 及以下 / 已授予存储权限时可读）
        val fileName = relative.substringAfterLast('/')
        listOf(File("/sdcard/Download", relative), File("/sdcard/Download", fileName))
            .forEach { file -> if (file.isFile) return openFile(file.absolutePath) }

        // 4) MediaStore 兜底
        mediaStoreUri(context, fileName)?.let { uri ->
            runCatching { context.contentResolver.openInputStream(uri) }
                .getOrNull()?.let { return it }
        }
        return null
    }

    private fun mediaStoreUri(context: Context, fileName: String): Uri? = runCatching {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = MediaStore.Images.Media.DISPLAY_NAME + " = ?"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf(fileName), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
            } else {
                null
            }
        }
    }.getOrNull()

    private fun openFile(path: String): InputStream? =
        runCatching { File(path).inputStream() }.getOrNull()

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
