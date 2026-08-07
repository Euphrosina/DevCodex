package ai.devcodex.agents.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import java.util.Locale

object CodeHighlighter {

    private val KEYWORDS: Map<String, Set<String>> = mapOf(
        "kotlin" to setOf(
            "fun", "val", "var", "class", "object", "interface", "enum", "sealed",
            "when", "if", "else", "for", "in", "do", "return", "null", "true", "false",
            "is", "as", "private", "public", "protected", "internal", "override",
            "import", "package", "this", "super", "try", "catch", "finally", "throw",
            "suspend", "operator", "companion", "open", "abstract", "lateinit",
            "continue", "repeat", "while"
        ),
        "java" to setOf(
            "class", "interface", "enum", "public", "private", "protected", "static",
            "final", "void", "int", "long", "double", "float", "boolean", "char", "byte",
            "short", "new", "return", "if", "else", "for", "while", "do", "switch",
            "case", "default", "break", "continue", "try", "catch", "finally", "throw",
            "throws", "extends", "implements", "import", "package", "this", "super",
            "null", "true", "false", "abstract", "synchronized", "volatile", "instanceof"
        ),
        "javascript" to setOf(
            "function", "const", "let", "class", "return", "if", "else", "for",
            "while", "do", "switch", "case", "break", "continue", "new", "null",
            "undefined", "true", "false", "try", "catch", "finally", "throw", "typeof",
            "instanceof", "in", "of", "await", "async", "export", "import", "default",
            "extends", "super", "yield", "delete", "void"
        ),
        "typescript" to setOf(
            "function", "const", "let", "var", "class", "interface", "type", "return",
            "if", "else", "for", "while", "switch", "case", "break", "new", "null",
            "undefined", "true", "false", "try", "catch", "finally", "throw", "typeof",
            "instanceof", "in", "of", "await", "async", "export", "import", "default",
            "extends", "super", "static", "readonly", "public", "private", "protected",
            "enum", "implements", "namespace", "declare", "as", "never", "any", "unknown"
        ),
        "python" to setOf(
            "def", "class", "return", "if", "elif", "else", "for", "while", "in", "is",
            "not", "and", "or", "import", "from", "as", "try", "except", "finally",
            "raise", "with", "lambda", "pass", "break", "continue", "True", "False",
            "None", "global", "yield", "async", "await", "del", "assert"
        ),
        "c" to setOf(
            "int", "char", "float", "double", "void", "long", "short", "unsigned",
            "signed", "struct", "union", "enum", "typedef", "const", "static",
            "extern", "volatile", "return", "if", "else", "for", "while", "do",
            "switch", "case", "default", "break", "continue", "goto", "sizeof",
            "true", "false", "NULL", "include", "define"
        ),
        "cpp" to setOf(
            "int", "char", "float", "double", "void", "long", "bool", "auto",
            "const", "constexpr", "static", "template", "typename", "namespace",
            "using", "class", "struct", "enum", "public", "private", "protected",
            "virtual", "new", "delete", "nullptr", "return", "if",
            "else", "for", "while", "try", "catch", "throw", "true", "false",
            "include", "define", "std"
        ),
        "go" to setOf(
            "func", "package", "import", "const", "type", "struct", "map",
            "chan", "if", "else", "for", "range", "switch", "case", "default",
            "break", "continue", "return", "go", "defer", "select",
            "nil", "true", "false", "var", "interface", "make", "len", "cap"
        ),
        "rust" to setOf(
            "fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait",
            "mod", "use", "crate", "return", "if", "else", "match", "for", "while",
            "loop", "break", "continue", "in", "ref", "self", "super", "async", "await",
            "type", "where", "true", "false", "pub"
        ),
        "swift" to setOf(
            "func", "let", "var", "class", "struct", "enum", "protocol", "extension",
            "return", "if", "else", "guard", "for", "in", "while", "repeat", "switch",
            "case", "break", "continue", "import", "public", "private", "internal",
            "static", "final", "override", "nil", "true", "false", "self", "super",
            "try", "catch", "throw", "defer", "where"
        ),
        "sql" to setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set",
            "delete", "create", "table", "database", "drop", "alter", "add",
            "column", "primary", "key", "foreign", "references", "unique", "left",
            "right", "inner", "outer", "join", "on", "group", "by", "having",
            "order", "limit", "offset", "as", "and", "or", "not", "null", "is",
            "like", "between", "distinct", "case", "when", "then", "else", "end",
            "count", "sum", "avg", "min", "max", "default", "constraint", "view"
        ),
        "shell" to setOf(
            "if", "then", "else", "fi", "for", "while", "do", "done", "case",
            "esac", "function", "return", "exit", "echo", "cd", "export", "sudo", "rm"
        )
    )

    private val DEFAULT_KEYWORDS = setOf(
        "true", "false", "null", "if", "else", "for", "while", "return", "class",
        "function", "new", "try", "catch", "finally", "throw", "break", "continue",
        "import", "from", "as", "def", "fn", "fun", "var", "val", "let", "const",
        "switch", "case", "default", "type", "static", "public", "private"
    )

    private val C_LIKE_COMMENT = Regex("//[^\\n]*")
    private val HASH_COMMENT = Regex("#[^\\n]*")
    private val DASH_COMMENT = Regex("--[^\\n]*")
    private val HTML_COMMENT = Regex("(?s)<!--.*?-->")
    private val BLOCK_COMMENT = Regex("(?s)/\\*.*?\\*/")

    fun highlight(code: String, language: String?): AnnotatedString {
        val lang = language?.lowercase(Locale.ROOT) ?: ""
        val keywords = KEYWORDS[lang] ?: DEFAULT_KEYWORDS

        data class Span(val start: Int, val end: Int, val color: Color)

        val spans = mutableListOf<Span>()

        fun collect(regex: Regex, color: Color, apply: Boolean = true) {
            if (!apply) return
            regex.findAll(code).forEach { match ->
                spans.add(Span(match.range.first, match.range.last + 1, color))
            }
        }

        val cLike = setOf("c", "cpp", "csharp", "java", "kotlin", "go", "rust", "swift", "js", "javascript", "ts", "typescript", "scala")
        val hashLike = setOf("python", "ruby", "shell", "sh", "bash", "yaml", "yml", "toml", "perl", "r", "makefile")
        val htmlLike = setOf("html", "xml", "markdown", "md")

        collect(C_LIKE_COMMENT, Pal.comment, apply = lang in cLike)
        collect(HASH_COMMENT, Pal.comment, apply = lang in hashLike || lang !in cLike && lang !in htmlLike && lang != "sql")
        collect(DASH_COMMENT, Pal.comment, apply = lang == "sql")
        collect(HTML_COMMENT, Pal.comment, apply = lang in htmlLike)
        collect(BLOCK_COMMENT, Pal.comment, apply = lang in cLike || lang in hashLike || lang in htmlLike || lang == "sql")

        collect(
            Regex("\\b(?:${keywords.joinToString("|") { Regex.escape(it) }})\\b"),
            Pal.keyword
        )
        collect(Regex("\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b|0[xX][0-9a-fA-F]+\\b"), Pal.number)
        collect(Regex("@[A-Za-z_][A-Za-z0-9_]*"), Pal.annotation)
        collect(Regex("\\b[A-Z][A-Za-z0-9_]*\\b"), Pal.type)
        collect(Regex("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|`(?:[^`\\\\]|\\\\.)*`"), Pal.string)

        val sorted = spans.sortedWith(compareBy<Span> { it.start }.thenBy { it.end - it.start })
        val mono = FontFamily.Monospace

        return buildAnnotatedString {
            var pos = 0
            for (span in sorted) {
                val start = span.start.coerceAtLeast(pos)
                val end = span.end.coerceAtLeast(start)
                if (start > pos) append(code.substring(pos, start))
                val text = code.substring(start, end)
                if (text.isNotEmpty()) {
                    pushStyle(SpanStyle(color = span.color, fontFamily = mono))
                    append(text)
                    pop()
                }
                pos = end
            }
            if (pos < code.length) append(code.substring(pos))
        }
    }
}

internal object Pal {
    val keyword = Color(0xFFFF7B72)
    val string = Color(0xFFA5D6FF)
    val comment = Color(0xFF8B949E)
    val number = Color(0xFF79C1FF)
    val annotation = Color(0xFFD2A8FF)
    val type = Color(0xFF79C1FF)
}