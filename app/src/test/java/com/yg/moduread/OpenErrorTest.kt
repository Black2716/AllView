package com.yg.moduread

import com.yg.moduread.data.friendlyOpenError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 打开失败文案：这些字符串直接显示在空态页上，属于用户可见的对外行为。
 * 判定顺序也有意义（SecurityException 的 message 里也可能带 ENOENT），
 * 所以逐条钉住。
 */
class OpenErrorTest {

    @Test
    fun securityExceptionGetsPermissionCopy() {
        val msg = friendlyOpenError(SecurityException("denied"))
        assertTrue(msg.startsWith("这个文件没有被授权访问。"))
    }

    @Test
    fun permissionDenialMessageIsRecognised() {
        val msg = friendlyOpenError(IllegalStateException("Permission Denial: opening provider"))
        assertTrue(msg.startsWith("这个文件没有被授权访问。"))
    }

    @Test
    fun missingFileGetsMovedCopy() {
        assertEquals("文件找不到了，可能已被移动或删除。", friendlyOpenError(java.io.FileNotFoundException("x")))
        assertEquals(
            "文件找不到了，可能已被移动或删除。",
            friendlyOpenError(RuntimeException("open failed: ENOENT (No such file or directory)"))
        )
    }

    @Test
    fun eaccesGetsReadPermissionCopy() {
        assertEquals("没有读取该文件的权限。", friendlyOpenError(RuntimeException("open failed: EACCES")))
    }

    @Test
    fun blankMessageGetsGenericCopy() {
        assertEquals("打开失败，请重试。", friendlyOpenError(RuntimeException()))
        assertEquals("打开失败，请重试。", friendlyOpenError(RuntimeException("   ")))
    }

    @Test
    fun longMessageIsTruncatedToFirstLine() {
        val msg = friendlyOpenError(RuntimeException("第一行\n第二行"))
        assertEquals("第一行", msg)
    }

    @Test
    fun overlyLongLineIsCutAt120Chars() {
        val msg = friendlyOpenError(RuntimeException("x".repeat(300)))
        assertEquals(120, msg.length)
    }
}
