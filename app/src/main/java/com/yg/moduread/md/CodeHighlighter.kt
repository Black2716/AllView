package com.yg.moduread.md

/** 高亮的词法类别。渲染层决定每种类别用什么颜色，解析层只给语义。 */
enum class TokenKind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, FUNCTION }

data class CodeToken(val text: String, val kind: TokenKind)

/**
 * 手写的轻量代码高亮。
 *
 * 为什么不用现成的高亮库：SyntaxHighlighter / Prism 这类要么带几十 MB 语法定义，
 * 要么依赖 JS 引擎，与「1 MB 阅读器」的定位冲突。技术书里的代码 90% 是
 * kotlin/python/bash/json，四种手写词法足够，关键词表合计不到 100 条。
 *
 * 未知语言原样返回一个 PLAIN token，绝不吞内容、不改排版。
 */
object CodeHighlighter {

    /** 支持的语言别名 -> 规范名。 */
    private val ALIASES = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "kotlin" to "kotlin",
        "py" to "python", "python3" to "python", "python" to "python",
        "sh" to "bash", "shell" to "bash", "zsh" to "bash", "console" to "bash",
        "bash" to "bash", "json" to "json", "jsonc" to "json",
        "java" to "java", "js" to "javascript", "jsx" to "javascript",
        "ts" to "typescript", "tsx" to "typescript", "javascript" to "javascript",
        "go" to "go", "rs" to "rust", "c" to "c", "h" to "c",
        "cpp" to "cpp", "cc" to "cpp", "hpp" to "cpp", "csharp" to "csharp", "cs" to "csharp",
        "rb" to "ruby", "php" to "php", "swift" to "swift", "sql" to "sql",
        "css" to "css", "scss" to "css", "less" to "css", "yaml" to "yaml", "yml" to "yaml",
        "xml" to "xml", "html" to "xml", "htm" to "xml", "toml" to "toml"
    )

    private val KEYWORDS = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "class", "object", "interface", "data", "sealed",
            "enum", "when", "if", "else", "for", "while", "do", "return", "null",
            "true", "false", "is", "as", "in", "out", "by", "lazy", "override",
            "private", "public", "internal", "protected", "companion", "init",
            "suspend", "import", "package", "try", "catch", "finally", "throw", "this"
        ),
        "python" to setOf(
            "def", "class", "return", "if", "elif", "else", "for", "while", "in",
            "not", "and", "or", "import", "from", "as", "with", "try", "except",
            "finally", "raise", "pass", "break", "continue", "lambda", "yield",
            "None", "True", "False", "self", "global", "nonlocal", "assert", "del"
        ),
        "bash" to setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done",
            "case", "esac", "function", "return", "export", "local", "echo", "cd",
            "source", "sudo", "python", "pip", "git", "set", "unset"
        ),
        // JSON 没有关键字，用 true/false/null 充数
        "json" to setOf("true", "false", "null"),
        "java" to setOf(
            "public", "private", "protected", "class", "interface", "enum", "extends",
            "implements", "static", "final", "void", "int", "long", "double", "float",
            "boolean", "char", "byte", "short", "new", "return", "if", "else", "for",
            "while", "do", "switch", "case", "break", "continue", "try", "catch",
            "finally", "throw", "throws", "import", "package", "this", "super", "null",
            "true", "false", "abstract", "synchronized", "volatile", "transient"
        ),
        "javascript" to setOf(
            "function", "const", "let", "var", "class", "extends", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "try", "catch",
            "finally", "throw", "new", "this", "super", "import", "export", "default",
            "async", "await", "yield", "typeof", "instanceof", "null", "undefined",
            "true", "false", "of", "in", "from"
        ),
        "typescript" to setOf(
            "function", "const", "let", "var", "class", "interface", "type", "enum",
            "extends", "implements", "return", "if", "else", "for", "while", "switch",
            "case", "break", "continue", "try", "catch", "finally", "throw", "new",
            "this", "import", "export", "default", "async", "await", "null", "undefined",
            "true", "false", "readonly", "public", "private", "protected", "as", "of"
        ),
        "go" to setOf(
            "func", "package", "import", "type", "struct", "interface", "var", "const",
            "return", "if", "else", "for", "range", "switch", "case", "default", "break",
            "continue", "go", "defer", "chan", "select", "map", "nil", "true", "false"
        ),
        "rust" to setOf(
            "fn", "let", "mut", "const", "struct", "enum", "impl", "trait", "pub", "use",
            "mod", "return", "if", "else", "for", "while", "loop", "match", "break",
            "continue", "self", "Self", "true", "false", "Some", "None", "Ok", "Err"
        ),
        "c" to setOf(
            "int", "char", "float", "double", "void", "long", "short", "unsigned", "signed",
            "struct", "union", "enum", "typedef", "static", "extern", "const", "return",
            "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
            "sizeof", "NULL", "include", "define"
        ),
        "cpp" to setOf(
            "int", "char", "float", "double", "void", "long", "short", "unsigned", "bool",
            "class", "struct", "namespace", "template", "typename", "public", "private",
            "protected", "virtual", "override", "const", "static", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "new", "delete",
            "nullptr", "true", "false", "using", "auto"
        ),
        "csharp" to setOf(
            "public", "private", "protected", "internal", "class", "struct", "interface",
            "enum", "namespace", "using", "static", "readonly", "const", "void", "int",
            "string", "bool", "double", "var", "new", "return", "if", "else", "for",
            "foreach", "while", "switch", "case", "break", "continue", "try", "catch",
            "finally", "throw", "this", "base", "null", "true", "false", "async", "await"
        ),
        "ruby" to setOf(
            "def", "class", "module", "end", "if", "elsif", "else", "unless", "while",
            "until", "for", "do", "return", "yield", "begin", "rescue", "ensure",
            "raise", "require", "include", "attr_accessor", "nil", "true", "false", "self"
        ),
        "php" to setOf(
            "function", "class", "interface", "trait", "public", "private", "protected",
            "static", "const", "return", "if", "else", "elseif", "for", "foreach", "while",
            "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
            "new", "echo", "namespace", "use", "null", "true", "false"
        ),
        "swift" to setOf(
            "func", "let", "var", "class", "struct", "enum", "protocol", "extension",
            "import", "return", "if", "else", "for", "while", "switch", "case", "break",
            "continue", "guard", "defer", "nil", "true", "false", "self", "init"
        ),
        "sql" to setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set",
            "delete", "create", "table", "drop", "alter", "join", "left", "right",
            "inner", "outer", "on", "group", "by", "order", "having", "limit", "union",
            "and", "or", "not", "null", "as", "distinct"
        ),
        "css" to setOf("@media", "@import", "important", "inherit", "auto", "none"),
        "yaml" to setOf("true", "false", "null"),
        "xml" to setOf("true", "false"),
        "toml" to setOf("true", "false")
    )

    /** 语言是否被支持（决定渲染层要不要走高亮路径）。 */
    fun supports(language: String?): Boolean =
        language != null && ALIASES.containsKey(language.trim().lowercase())

    fun tokenize(code: String, language: String?): List<CodeToken> {
        val lang = ALIASES[language?.trim()?.lowercase()] ?: return plain(code)
        return when (lang) {
            "kotlin" -> scan(code, KEYWORDS.getValue(lang), lineComments = listOf("//"), blockComments = true)
            "python" -> scan(code, KEYWORDS.getValue(lang), lineComments = listOf("#"), blockComments = false)
            "bash" -> scan(code, KEYWORDS.getValue(lang), lineComments = listOf("#"), blockComments = false)
            "json" -> scan(code, KEYWORDS.getValue(lang), lineComments = emptyList(), blockComments = false, quoteChar = '"')
            // C 系语言：// 行注释 + 块注释
            "java", "javascript", "typescript", "go", "rust", "c", "cpp", "csharp", "swift",
            "php" -> scan(code, KEYWORDS.getValue(lang), lineComments = listOf("//", "#"), blockComments = true)
            "sql" -> scan(code, KEYWORDS.getValue(lang), lineComments = listOf("--"), blockComments = true)
            "ruby" -> scan(code, KEYWORDS.getValue(lang), lineComments = listOf("#"), blockComments = false)
            "css", "yaml", "toml" -> scan(code, KEYWORDS.getValue(lang), lineComments = listOf("#"), blockComments = false)
            "xml" -> scan(code, KEYWORDS.getValue(lang), lineComments = emptyList(), blockComments = true)
            else -> plain(code)
        }
    }

    private fun plain(code: String): List<CodeToken> =
        if (code.isEmpty()) emptyList() else listOf(CodeToken(code, TokenKind.PLAIN))

    /**
     * 单遍扫描。刻意不做正则回溯——大代码块（几百行）用正则会在每行都
     * 重扫一遍，滚动时能明显感觉到卡。
     */
    private fun scan(
        code: String,
        keywords: Set<String>,
        lineComments: List<String>,
        blockComments: Boolean,
        quoteChar: Char = '\"'
    ): List<CodeToken> {
        if (code.isEmpty()) return emptyList()
        val out = ArrayList<CodeToken>(32)
        val plain = StringBuilder()
        var i = 0

        fun flush() {
            if (plain.isNotEmpty()) {
                out.add(CodeToken(plain.toString(), TokenKind.PLAIN))
                plain.setLength(0)
            }
        }

        while (i < code.length) {
            val c = code[i]

            // 行注释
            val lineMarker = lineComments.firstOrNull { marker -> code.startsWith(marker, i) }
            if (lineMarker != null) {
                val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                flush()
                out.add(CodeToken(code.substring(i, end), TokenKind.COMMENT))
                i = end
                continue
            }

            // 块注释 /* ... */（Kotlin 支持嵌套，这里只做单层，够用）
            if (blockComments && c == '/' && i + 1 < code.length && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                flush()
                out.add(CodeToken(code.substring(i, end), TokenKind.COMMENT))
                i = end
                continue
            }

            // 字符串：支持跨行（三引号 / 长字符串），到闭引号为止
            if (c == quoteChar) {
                val triple = i + 2 < code.length && code[i + 1] == quoteChar && code[i + 2] == quoteChar
                val markerLen = if (triple) 3 else 1
                var j = i + markerLen
                var end = -1
                while (j < code.length) {
                    if (code[j] == '\\' && !triple) { j += 2; continue }
                    if (triple && j + 2 < code.length && code.startsWith("\"\"\"", j)) { end = j + 3; break }
                    if (!triple && code[j] == quoteChar) { end = j + 1; break }
                    j++
                }
                if (end < 0) end = code.length
                flush()
                out.add(CodeToken(code.substring(i, end), TokenKind.STRING))
                i = end
                continue
            }

            // 数字
            if (c.isDigit()) {
                var j = i
                while (j < code.length && (code[j].isDigit() || code[j] == '.' || code[j] == '_')) j++
                flush()
                out.add(CodeToken(code.substring(i, j), TokenKind.NUMBER))
                i = j
                continue
            }

            // 标识符 / 关键字 / 函数名
            if (c.isLetter() || c == '_') {
                var j = i
                while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                val isCall = j < code.length && code[j] == '('
                when {
                    keywords.contains(word) -> { flush(); out.add(CodeToken(word, TokenKind.KEYWORD)) }
                    isCall -> { flush(); out.add(CodeToken(word, TokenKind.FUNCTION)) }
                    else -> plain.append(word)
                }
                i = j
                continue
            }

            plain.append(c)
            i++
        }
        flush()
        return out
    }
}
