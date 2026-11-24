package com.yg.moduread

import com.yg.moduread.md.CodeHighlighter
import com.yg.moduread.md.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    @Test
    fun supportsKnownLanguagesAndAliases() {
        assertTrue(CodeHighlighter.supports("kotlin"))
        assertTrue(CodeHighlighter.supports("kt"))
        assertTrue(CodeHighlighter.supports("py"))
        assertTrue(CodeHighlighter.supports("sh"))
        assertFalse(CodeHighlighter.supports("rust"))
        assertFalse(CodeHighlighter.supports(null))
    }

    @Test
    fun unknownLanguageReturnsSinglePlainToken() {
        val code = "let x = 1;"
        val tokens = CodeHighlighter.tokenize(code, "rust")
        assertEquals(1, tokens.size)
        assertEquals(TokenKind.PLAIN, tokens[0].kind)
        assertEquals(code, tokens[0].text)
    }

    @Test
    fun tokensReassembleToOriginalExactly() {
        // 最重要的一条：高亮不能丢字或改字
        val code = "fun add(a: Int): Int {\n    // 求和\n    return a + 1\n}"
        val joined = CodeHighlighter.tokenize(code, "kotlin").joinToString("") { it.text }
        assertEquals(code, joined)
    }

    @Test
    fun kotlinKeywordAndStringAndNumber() {
        val tokens = CodeHighlighter.tokenize("val s = \"hi\" 42", "kotlin")
        assertTrue(tokens.any { it.kind == TokenKind.KEYWORD && it.text == "val" })
        assertTrue(tokens.any { it.kind == TokenKind.STRING && it.text == "\"hi\"" })
        assertTrue(tokens.any { it.kind == TokenKind.NUMBER && it.text == "42" })
    }

    @Test
    fun lineCommentSwallowsRestOfLine() {
        val tokens = CodeHighlighter.tokenize("x = 1  # 注释里有 val", "python")
        val comment = tokens.single { it.kind == TokenKind.COMMENT }
        assertTrue(comment.text.contains("val"))
        // 注释里的 val 不能被当成关键字
        assertFalse(tokens.any { it.kind == TokenKind.KEYWORD && it.text == "val" })
    }

    @Test
    fun functionCallDetected() {
        val tokens = CodeHighlighter.tokenize("print(x)", "python")
        assertTrue(tokens.any { it.kind == TokenKind.FUNCTION && it.text == "print" })
    }

    @Test
    fun unterminatedStringDoesNotCrash() {
        val code = "s = \"没闭合"
        assertEquals(code, CodeHighlighter.tokenize(code, "python").joinToString("") { it.text })
    }

    @Test
    fun emptyCodeProducesNothing() {
        assertTrue(CodeHighlighter.tokenize("", "kotlin").isEmpty())
    }

    @Test
    fun supportsMainstreamSourceLanguages() {
        assertTrue(CodeHighlighter.supports("java"))
        assertTrue(CodeHighlighter.supports("js"))
        assertTrue(CodeHighlighter.supports("go"))
        assertTrue(CodeHighlighter.supports("rs"))
        assertTrue(CodeHighlighter.supports("cpp"))
        assertTrue(CodeHighlighter.supports("sql"))
        assertTrue(CodeHighlighter.supports("css"))
        assertTrue(CodeHighlighter.supports("yaml"))
    }

    @Test
    fun javaCodeIsHighlightedAndLossless() {
        val code = "public class A { // note\u000A    int x = 1;\u000A}"
        val tokens = CodeHighlighter.tokenize(code, "java")
        assertEquals(code, tokens.joinToString("") { it.text })
        assertTrue(tokens.any { it.kind == TokenKind.KEYWORD })
        assertTrue(tokens.any { it.kind == TokenKind.COMMENT })
    }

    @Test
    fun goCodeIsHighlightedAndLossless() {
        val code = "func main() {\u000A\tfmt.Println(\"hi\")\u000A}"
        val tokens = CodeHighlighter.tokenize(code, "go")
        assertEquals(code, tokens.joinToString("") { it.text })
        assertTrue(tokens.any { it.kind == TokenKind.KEYWORD })
        assertTrue(tokens.any { it.kind == TokenKind.STRING })
    }

    @Test
    fun unknownLanguageStillLossless() {
        val code = "whatever ~~~ text"
        val tokens = CodeHighlighter.tokenize(code, "brainfuck")
        assertEquals(code, tokens.joinToString("") { it.text })
    }
}
