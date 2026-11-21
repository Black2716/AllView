package com.yg.moduread.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LoadedDoc(
    val title: String,
    val blocks: List<MdBlock>,
    val charCount: Int,
    val baseDir: String?,
    val docUri: String?
)

/** 只做两件事：按 UTF-8 读出文本、把文本转成 AST。全部在 IO 线程完成。 */
object DocRepository {

    suspend fun load(context: Context, uri: Uri): LoadedDoc = withContext(Dispatchers.IO) {
        val name = queryName(context, uri) ?: uri.lastPathSegment ?: "未命名.md"
        val raw = context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
            ?: throw IllegalStateException("无法打开该文件")

        val text = String(raw, Charsets.UTF_8).removePrefix("\uFEFF")
        val blocks = MdParser.shared.parse(text)
        LoadedDoc(
            title = name.substringAfterLast('/'),
            blocks = blocks,
            charCount = text.length,
            baseDir = if (uri.scheme == "file") uri.path?.substringBeforeLast('/') else null,
            docUri = uri.toString()
        )
    }

    private fun queryName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}
