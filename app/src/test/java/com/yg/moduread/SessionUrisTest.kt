package com.yg.moduread

import com.yg.moduread.data.SessionUris
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多开 uri 列表：顺序即标签顺序，空行丢弃。
 * 老版本只存单个 last_uri，新版本存列表，两条路径必须能在同一份偏好上共存。
 */
class SessionUrisTest {

    @Test
    fun encodesWithNewlines() {
        assertEquals("a\nb\nc", SessionUris.encode(listOf("a", "b", "c")))
    }

    @Test
    fun roundTripKeepsOrder() {
        val uris = listOf("content://a", "content://b")
        assertEquals(uris, SessionUris.decode(SessionUris.encode(uris)))
    }

    @Test
    fun nullAndBlankDecodeToEmpty() {
        assertTrue(SessionUris.decode(null).isEmpty())
        assertTrue(SessionUris.decode("").isEmpty())
        assertTrue(SessionUris.decode("\n\n").isEmpty())
    }

    @Test
    fun trailingNewlineDoesNotCreateEmptyTab() {
        assertEquals(listOf("a", "b"), SessionUris.decode("a\nb\n"))
    }

    @Test
    fun keysMatchHistoricalNames() {
        // 键名改动会让老用户的会话列表消失，只能回到单个 last_uri
        assertEquals("session_uris", SessionUris.KEY_SESSION_URIS)
        assertEquals("active_uri", SessionUris.KEY_ACTIVE_URI)
        assertEquals("last_uri", SessionUris.KEY_LAST_URI)
    }
}
