package com.mrredhood.nexus.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import com.mrredhood.nexus.core.settings.NexusSettings

/** Language metadata used by the editor. Runtimes are execution descriptors; Nexus does not bundle native interpreters. */
enum class NexusLanguage(
    val displayName: String,
    val extensions: Set<String>,
    val runtime: String,
    val runCommand: String
) {
    PYTHON("Python", setOf("py", "pyw"), "python", "python3 <file>"),
    JAVA("Java", setOf("java"), "java", "javac <file> && java <class>"),
    JAVASCRIPT("JavaScript", setOf("js", "mjs", "cjs"), "node", "node <file>"),
    TYPESCRIPT("TypeScript", setOf("ts", "tsx"), "node/typescript", "npx tsx <file>"),
    C("C", setOf("c", "h"), "gcc", "gcc <file> -o <output> && <output>"),
    CPP("C++", setOf("cpp", "cc", "cxx", "hpp"), "g++", "g++ <file> -o <output> && <output>"),
    CSHARP("C#", setOf("cs"), "dotnet", "dotnet run"),
    RUBY("Ruby", setOf("rb", "rake", "gemspec"), "ruby", "ruby <file>"),
    DART("Dart", setOf("dart"), "dart", "dart run <file>"),
    KOTLIN("Kotlin", setOf("kt", "kts"), "kotlinc", "kotlinc <file> -include-runtime -d <output>.jar && java -jar <output>.jar"),
    SWIFT("Swift", setOf("swift"), "swift", "swift <file>"),
    GO("Go", setOf("go"), "go", "go run <file>"),
    RUST("Rust", setOf("rs"), "rustc", "rustc <file> -o <output> && <output>"),
    PHP("PHP", setOf("php"), "php", "php <file>"),
    HTML("HTML", setOf("html", "htm"), "browser", "open <file>"),
    CSS("CSS", setOf("css"), "browser", "open <file>"),
    JSON("JSON", setOf("json", "jsonc"), "none", ""),
    XML("XML", setOf("xml", "xsd", "svg"), "none", ""),
    SQL("SQL", setOf("sql"), "database", ""),
    SHELL("Shell", setOf("sh", "bash", "zsh", "fish"), "shell", "sh <file>"),
    MARKDOWN("Markdown", setOf("md", "markdown"), "none", ""),
    PLAIN("Plain text", emptySet(), "none", "")
}

object LanguageRegistry {
    fun detect(path: String): NexusLanguage {
        val extension = path.substringAfterLast('.', "").lowercase()
        return NexusLanguage.entries.firstOrNull { extension in it.extensions } ?: NexusLanguage.PLAIN
    }

    fun all(): List<NexusLanguage> = NexusLanguage.entries
}

object EditorFontResolver {
    fun resolve(name: String): FontFamily = when (name.lowercase()) {
        "system mono", "jetbrains mono", "roboto mono", "fira code", "source code pro", "ubuntu mono" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        "sans", "sans serif" -> FontFamily.SansSerif
        else -> FontFamily.Monospace
    }
}

/** Lightweight editor lexer. It is deliberately dependency-free so V1 remains cloud-build-first. */
object SyntaxHighlighter {
    private val keyword = SpanStyle(color = Color(0xFFBB86FC))
    private val string = SpanStyle(color = Color(0xFF80CBC4))
    private val number = SpanStyle(color = Color(0xFFFFCC80))
    private val comment = SpanStyle(color = Color(0xFF78909C))
    private val type = SpanStyle(color = Color(0xFF82AAFF))
    private val function = SpanStyle(color = Color(0xFFFFD54F))
    private val property = SpanStyle(color = Color(0xFF90CAF9))
    private val tag = SpanStyle(color = Color(0xFFF48FB1))

    fun highlight(text: String, language: NexusLanguage, enabled: Boolean): AnnotatedString {
        if (!enabled || text.isEmpty() || language == NexusLanguage.PLAIN) return AnnotatedString(text)
        return when (language) {
            NexusLanguage.HTML, NexusLanguage.XML -> highlightMarkup(text)
            NexusLanguage.JSON -> highlightJson(text)
            NexusLanguage.CSS -> highlightCss(text)
            NexusLanguage.MARKDOWN -> highlightMarkdown(text)
            else -> highlightCode(text, language)
        }
    }

    private fun highlightCode(text: String, language: NexusLanguage): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        val keywords = keywordSet(language)
        var i = 0
        while (i < text.length) {
            val start = i
            val next = when {
                text.startsWith("//", i) -> scanLine(text, i)
                text.startsWith("/*", i) -> scanBlock(text, i, "/*", "*/")
                isHashComment(language, text, i) -> scanLine(text, i)
                text[i] == '"' || text[i] == '\'' || text[i] == '`' -> scanString(text, i, text[i])
                text[i].isDigit() && (i == 0 || !text[i - 1].isLetterOrDigit() && text[i - 1] != '_') -> scanNumber(text, i)
                text[i].isLetter() || text[i] == '_' || text[i] == '$' -> scanIdentifier(text, i)
                else -> i + 1
            }
            val token = text.substring(start, next)
            when {
                token.startsWith("//") || token.startsWith("/*") || token.startsWith("#") -> builder.addStyle(comment, start, next)
                token.startsWith("\"") || token.startsWith("'") || token.startsWith("`") -> builder.addStyle(string, start, next)
                token.firstOrNull()?.isDigit() == true -> builder.addStyle(number, start, next)
                token in keywords -> builder.addStyle(keyword, start, next)
                isTypeToken(token, language) -> builder.addStyle(type, start, next)
                next < text.length && text[next] == '(' && token.firstOrNull()?.isLetter() == true -> builder.addStyle(function, start, next)
                else -> Unit
            }
            i = next
        }
        return builder.toAnnotatedString()
    }

    private fun highlightJson(text: String): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '"' -> {
                    val end = scanString(text, i, '"')
                    val style = if (text.substring(end).trimStart().startsWith(":")) property else string
                    builder.addStyle(style, i, end)
                    i = end
                }
                text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val end = scanNumber(text, i)
                    builder.addStyle(number, i, end); i = end
                }
                else -> i++
            }
        }
        listOf("true", "false", "null").forEach { word -> Regex("\\b$word\\b").findAll(text).forEach { builder.addStyle(keyword, it.range.first, it.range.last + 1) } }
        return builder.toAnnotatedString()
    }

    private fun highlightMarkup(text: String): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        Regex("<!--(?:[\\s\\S]*?)-->").findAll(text).forEach { builder.addStyle(comment, it.range.first, it.range.last + 1) }
        Regex("</?[A-Za-z][^>]*?/?>").findAll(text).forEach { match ->
            builder.addStyle(tag, match.range.first, match.range.last + 1)
            Regex("[A-Za-z_:][A-Za-z0-9_.:-]*(?=\\s*=)").findAll(match.value).forEach { attr -> builder.addStyle(property, match.range.first + attr.range.first, match.range.first + attr.range.last + 1) }
            Regex("\"[^\"]*\"|'[^']*'").findAll(match.value).forEach { value -> builder.addStyle(string, match.range.first + value.range.first, match.range.first + value.range.last + 1) }
        }
        return builder.toAnnotatedString()
    }

    private fun highlightCss(text: String): AnnotatedString {
        val builder = highlightCode(text, NexusLanguage.PLAIN)
        val styled = AnnotatedString.Builder(builder)
        Regex("/\\*[\\s\\S]*?\\*/").findAll(text).forEach { styled.addStyle(comment, it.range.first, it.range.last + 1) }
        Regex("(--?[A-Za-z_-][A-Za-z0-9_-]*)\\s*:").findAll(text).forEach { styled.addStyle(property, it.range.first, it.range.last + 1) }
        Regex("#[0-9A-Fa-f]{3,8}\\b|\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|s|ms)?\\b").findAll(text).forEach { styled.addStyle(number, it.range.first, it.range.last + 1) }
        Regex("\"[^\"]*\"|'[^']*'").findAll(text).forEach { styled.addStyle(string, it.range.first, it.range.last + 1) }
        return styled.toAnnotatedString()
    }

    private fun highlightMarkdown(text: String): AnnotatedString {
        val builder = AnnotatedString.Builder(text)
        Regex("(?m)^#{1,6} .*?$").findAll(text).forEach { builder.addStyle(keyword, it.range.first, it.range.last + 1) }
        Regex("\\*\\*[^*]+\\*\\*|__[^_]+__").findAll(text).forEach { builder.addStyle(type, it.range.first, it.range.last + 1) }
        Regex("`[^`]+`").findAll(text).forEach { builder.addStyle(string, it.range.first, it.range.last + 1) }
        Regex("(?m)^>.*$").findAll(text).forEach { builder.addStyle(comment, it.range.first, it.range.last + 1) }
        Regex("\\[[^]]+\\]\\([^)]*\\)").findAll(text).forEach { builder.addStyle(function, it.range.first, it.range.last + 1) }
        return builder.toAnnotatedString()
    }

    private fun keywordSet(language: NexusLanguage): Set<String> = when (language) {
        NexusLanguage.PYTHON -> setOf("def", "class", "import", "from", "as", "if", "elif", "else", "for", "while", "in", "is", "not", "and", "or", "return", "yield", "try", "except", "finally", "with", "lambda", "match", "case", "True", "False", "None", "async", "await", "pass", "raise", "assert", "global", "nonlocal")
        NexusLanguage.JAVA -> setOf("class", "interface", "enum", "extends", "implements", "package", "import", "public", "private", "protected", "static", "final", "abstract", "new", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "return", "try", "catch", "finally", "throw", "throws", "synchronized", "this", "super", "true", "false", "null", "instanceof")
        NexusLanguage.KOTLIN -> setOf("class", "interface", "object", "fun", "val", "var", "public", "private", "protected", "internal", "data", "sealed", "enum", "abstract", "override", "open", "final", "const", "lateinit", "new", "if", "else", "when", "for", "while", "do", "in", "is", "as", "return", "try", "catch", "finally", "throw", "package", "import", "this", "super", "true", "false", "null", "by", "suspend", "inline", "reified")
        NexusLanguage.JAVASCRIPT, NexusLanguage.TYPESCRIPT -> setOf("const", "let", "var", "function", "class", "extends", "new", "import", "from", "export", "default", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "return", "try", "catch", "finally", "throw", "async", "await", "yield", "this", "typeof", "instanceof", "in", "of", "true", "false", "null", "undefined", "interface", "type", "implements", "public", "private", "protected", "readonly", "declare", "namespace")
        NexusLanguage.C, NexusLanguage.CPP -> setOf("include", "define", "if", "ifdef", "ifndef", "endif", "pragma", "int", "char", "float", "double", "long", "short", "unsigned", "signed", "void", "bool", "struct", "class", "enum", "union", "const", "static", "extern", "inline", "constexpr", "template", "typename", "namespace", "using", "public", "private", "protected", "virtual", "override", "new", "delete", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "return", "true", "false", "nullptr")
        NexusLanguage.CSHARP -> setOf("using", "namespace", "class", "struct", "interface", "enum", "public", "private", "protected", "internal", "static", "readonly", "const", "abstract", "sealed", "virtual", "override", "async", "await", "void", "int", "long", "float", "double", "decimal", "bool", "string", "object", "var", "if", "else", "for", "foreach", "while", "do", "switch", "case", "break", "continue", "return", "try", "catch", "finally", "throw", "new", "this", "base", "true", "false", "null")
        NexusLanguage.RUBY -> setOf("def", "class", "module", "require", "include", "extend", "attr_reader", "attr_writer", "attr_accessor", "if", "elsif", "else", "unless", "while", "until", "for", "in", "do", "begin", "rescue", "ensure", "end", "return", "yield", "self", "super", "true", "false", "nil")
        NexusLanguage.DART -> setOf("class", "extends", "implements", "with", "mixin", "enum", "import", "export", "library", "part", "void", "int", "double", "num", "String", "bool", "final", "const", "late", "var", "dynamic", "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "return", "throw", "try", "catch", "finally", "async", "await", "yield", "true", "false", "null")
        NexusLanguage.SWIFT -> setOf("import", "class", "struct", "enum", "protocol", "extension", "func", "let", "var", "if", "else", "for", "while", "repeat", "switch", "case", "default", "guard", "return", "break", "continue", "throw", "throws", "try", "catch", "async", "await", "in", "where", "true", "false", "nil", "private", "public", "internal", "static", "final", "override")
        NexusLanguage.GO -> setOf("package", "import", "func", "type", "struct", "interface", "var", "const", "if", "else", "for", "range", "switch", "case", "default", "break", "continue", "return", "go", "defer", "select", "chan", "map", "true", "false", "nil")
        NexusLanguage.RUST -> setOf("fn", "let", "mut", "struct", "enum", "impl", "trait", "use", "mod", "pub", "crate", "self", "super", "const", "static", "type", "where", "if", "else", "match", "for", "while", "loop", "break", "continue", "return", "async", "await", "move", "ref", "dyn", "true", "false")
        NexusLanguage.PHP -> setOf("php", "function", "class", "interface", "trait", "extends", "implements", "public", "private", "protected", "static", "final", "abstract", "namespace", "use", "if", "else", "elseif", "foreach", "while", "do", "switch", "case", "break", "continue", "return", "try", "catch", "finally", "throw", "echo", "print", "new", "this", "true", "false", "null")
        NexusLanguage.SQL -> setOf("select", "from", "where", "join", "inner", "left", "right", "full", "outer", "on", "group", "by", "order", "having", "limit", "offset", "insert", "into", "values", "update", "set", "delete", "create", "alter", "drop", "table", "view", "index", "as", "and", "or", "not", "null", "is", "in", "like", "between", "distinct", "union", "all", "case", "when", "then", "else", "end")
        NexusLanguage.SHELL -> setOf("if", "then", "else", "elif", "fi", "for", "in", "do", "done", "case", "esac", "while", "until", "function", "export", "local", "readonly", "source", "alias", "echo", "return", "true", "false")
        else -> emptySet()
    }

    private fun isTypeToken(token: String, language: NexusLanguage): Boolean = when (language) {
        NexusLanguage.JAVA, NexusLanguage.KOTLIN, NexusLanguage.CSHARP, NexusLanguage.DART, NexusLanguage.SWIFT -> token in setOf("String", "Int", "Integer", "Long", "Double", "Float", "Boolean", "Bool", "Byte", "Unit", "Any", "Object", "List", "Map", "Set", "Array")
        NexusLanguage.C, NexusLanguage.CPP -> token in setOf("int", "char", "float", "double", "void", "bool", "size_t", "string")
        else -> false
    }

    private fun isHashComment(language: NexusLanguage, text: String, index: Int): Boolean = language in setOf(NexusLanguage.PYTHON, NexusLanguage.RUBY, NexusLanguage.SHELL) && text[index] == '#'

    private fun scanLine(text: String, start: Int): Int = text.indexOf('\n', start).let { if (it < 0) text.length else it }

    private fun scanBlock(text: String, start: Int, open: String, close: String): Int = text.indexOf(close, start + open.length).let { if (it < 0) text.length else it + close.length }

    private fun scanString(text: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < text.length) {
            if (text[i] == '\\') { i += 2; continue }
            if (text[i] == quote) return i + 1
            i++
        }
        return text.length
    }

    private fun scanNumber(text: String, start: Int): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] in "._+-xX")) i++
        return i
    }

    private fun scanIdentifier(text: String, start: Int): Int {
        var i = start
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$')) i++
        return i
    }
}

object EditorInputRules {
    private val bracketPairs = mapOf('(' to ')', '[' to ']', '{' to '}')
    private val quotePairs = mapOf('\"' to '\"', '\'' to '\'', '`' to '`')

    fun transformInput(old: String, new: String, cursor: Int, settings: NexusSettings, language: NexusLanguage): Pair<String, Int> {
        if (new.length != old.length + 1 || cursor <= 0) return new to cursor
        val inserted = new.getOrNull(cursor - 1) ?: return new to cursor
        if (settings.autoCloseBrackets && inserted in bracketPairs.keys) {
            val close = bracketPairs[inserted]!!
            if (cursor >= new.length || new[cursor] != close) return new.substring(0, cursor) + close + new.substring(cursor) to cursor
        }
        if (settings.autoCloseBrackets && inserted in quotePairs.keys) {
            val close = quotePairs[inserted]!!
            if (cursor >= new.length || new[cursor] != close) return new.substring(0, cursor) + close + new.substring(cursor) to cursor
        }
        if (settings.autoCloseTags && language in setOf(NexusLanguage.HTML, NexusLanguage.XML)) {
            val tagMatch = Regex("<([A-Za-z][A-Za-z0-9:_-]*)>").find(new.substring(0, cursor))
            if (tagMatch != null && tagMatch.range.last + 1 == cursor) {
                val tag = tagMatch.groupValues[1]
                val close = "</$tag>"
                if (!new.substring(cursor).startsWith(close)) return new.substring(0, cursor) + close + new.substring(cursor) to cursor
            }
        }
        return new to cursor
    }
}

object RuntimeCatalog {
    fun description(language: NexusLanguage): String = when (language.runtime) {
        "none" -> "No runtime required"
        "browser" -> "Runs in a browser/web preview"
        else -> "Requires ${language.runtime} runtime/toolchain on the execution host"
    }
}
