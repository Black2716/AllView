package com.yg.moduread

import com.yg.moduread.data.MdDocResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 针对一个真实缺陷的回归测试。
 *
 * 缺陷经过：`resolve` 对 `raw:` 与 `/sdcard` 候选直接返回 `Uri.fromFile(...)`，
 * 没有校验可读性。分区存储下该 file:// 应用根本读不到，
 * 但上层看到「非 null」就以为解析成功，于是既打不开、也不触发 SAF 回退，
 * 表现为「点链接毫无反应」。
 *
 * 修复方式是所有文件候选都必须过 canRead。这里锁定判定层的契约：
 * 只有真正是 Markdown 且有明确相对形态的链接才进解析分支，
 * 其余一律不吞掉、不误判。
 */
class MdDocResolverRegressionTest {

    @Test
    fun directoryLinkToMarkdownIsAccepted() {
        assertTrue(decide("./ch1.md"))
        assertTrue(decide("chapters/../ch2.md"))
        assertTrue(decide("sub/dir/a.markdown"))
    }

    @Test
    fun nonMarkdownRelativeIsNotTreatedAsDocument() {
        // 图片/压缩包等相对路径不该进文档解析分支（应交给图片解析或忽略）
        assertFalse(decide("./pic.png"))
        assertFalse(decide("./data.zip"))
        assertFalse(decide("notes.txt"))
    }

    @Test
    fun externalAndAnchorLinksAreNotDocumentLinks() {
        assertFalse(decide("https://example.com/a.md"))
        assertFalse(decide("#section"))
        assertFalse(decide("mailto:x@y.z"))
    }

    @Test
    fun normalizedPathIsWhatGetsResolved() {
        // 解析用的是规范化后的路径：`..` 必须被折叠，否则 SAF 拼出的 id 是错的
        assertEquals("ch1.md", MdDocResolver.normalize("./sub/../ch1.md"))
        assertEquals("a/b.md", MdDocResolver.normalize("a/./b.md"))
    }

    @Test
    fun emptyOrFragmentOnlyProducesNothingToResolve() {
        assertEquals("", MdDocResolver.normalize("#only-fragment"))
        assertEquals("", MdDocResolver.normalize("./"))
    }

    /** 与 ReaderRoot 里的分派条件保持一致。 */
    private fun decide(url: String): Boolean =
        MdDocResolver.isRelativeDocLink(url) && MdDocResolver.isMarkdownPath(url)
}
