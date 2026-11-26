package com.yg.moduread.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yg.moduread.data.MdDocResolver

/**
 * 正文里的链接点击分派。
 *
 * 从 ReaderRoot 的 onLink 闭包里抽出来的理由：那是个嵌套三层的 when，
 * 里面还夹着 SAF 回退与 startActivity，ReaderRoot 因此承担了路由策略。
 * 抽出后 ReaderRoot 只声明「锚点去哪、文档去哪、需要选文件时做什么」，
 * 三条分支的判定顺序集中在这里一处。
 *
 * 三条分支（顺序有语义，不要重排）：
 *  1. `#anchor` —— 文档内锚点，交给上层解析成块索引；
 *  2. .md 相对路径 —— 先按同目录解析；取不到说明分区存储未授权，
 *     回退到 SAF 让用户选一次（拿到持久授权后即可直接打开）；
 *  3. 其余 —— 交给系统浏览器/其他 App。
 */
internal fun dispatchLink(
    url: String,
    context: Context,
    docUri: String?,
    onAnchor: (String) -> Unit,
    onOpenDocument: (Uri) -> Unit,
    onNeedsPicker: (String) -> Unit
) {
    when {
        url.startsWith("#") -> onAnchor(url)

        MdDocResolver.isRelativeDocLink(url) && MdDocResolver.isMarkdownPath(url) -> {
            val target = MdDocResolver.resolve(context, docUri, url)
            if (target != null) {
                onOpenDocument(target)
            } else {
                // 分区存储下未被授权过的兄弟文档读不到（平台限制）。
                // 回退到 SAF：用户选一次即拿到持久授权，之后再点就直接打开。
                onNeedsPicker(url)
            }
        }

        else -> runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
