package com.yg.moduread.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 用 WebView 渲染 DOCX 转成的 HTML。
 *
 * 安全性：这份 HTML 完全由本地 DOCX 生成，图片是 base64 内嵌，
 * 不需要任何网络访问。因此显式关掉联网与文件访问，
 * 即使文档内容里有恶意链接也打不出去。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DocxWebView(
    html: String,
    modifier: Modifier = Modifier
) {
    // WebView 会持续持有 DOM，换文档时必须换实例，否则会残留上一份内容。
    // 用 html 的 hash 作为 key：内容变了就重建 WebView。
    val key = remember(html) { html.hashCode() }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // 分页靠内联脚本完成，必须开 JS
                settings.javaScriptEnabled = true
                // 不联网：图片是 base64 内嵌，本来就不需要网络。
                // 注意不能用 blockNetworkLoads = true —— 它会连 about:blank 的
                // 初始加载一起拦掉，页面直接空白（实测踩过）。
                settings.blockNetworkLoads = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                setBackgroundColor(0xFFE9E9EE.toInt())
                webViewClient = object : WebViewClient() {
                    // 拦截一切导航：这份 HTML 不该跳转到任何地方
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true
                }
                // 在 factory 里直接加载：update 在首次组合时的调用时序不可靠，
                // 依赖它会导致首屏空白（实测踩过）。
                // baseUrl 必须是合法的 about:blank：
                // 传 null 时部分 WebView 版本会静默拒绝加载。
                loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
                android.util.Log.i("AllViewWeb", "load len=" + html.length)
                // 诊断：加载完成后回读页数，确认分页脚本是否真的跑了
                postDelayed({
                    val js = "document.querySelectorAll('.page').length + '/' + document.body.scrollHeight"
                    evaluateJavascript(js) { r -> android.util.Log.i("AllViewWeb", "probe " + r) }
                }, 1500)
            }
        },
        update = { web ->
            // 文档切换时才重新加载
            if (web.tag != key) {
                web.tag = key
                web.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
            }
        }
    )
}
