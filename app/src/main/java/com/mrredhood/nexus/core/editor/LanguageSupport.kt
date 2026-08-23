package com.mrredhood.nexus.core.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
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

object SyntaxHighlighter {
    private val keyword = SpanStyle(color = Color(0xFFBB86FC))
    private val string = SpanStyle(color = Color(0xFF80CBC4))
    private val number = SpanStyle(color = Color(0xFFFFCC80))
    private val comment = SpanStyle(color = Color(0xFF78909C))
    private val type = SpanStyle(color = Color(0xFF82AAFF))

    fun highlight(text: String, language: NexusLanguage, enabled: Boolean): AnnotatedString {
        if (!enabled || text.isEmpty()) return AnnotatedString(text)
        val builder = AnnotatedString.Builder(text)
        val keywords = keywordSet(language)
        val pattern = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\n]*|\\\"(?:\\\\.|[^\\\"])*\\\"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b")
        pattern.findAll(text).forEach { match ->
            val token = match.value
            val style = when {
                token.startsWith("//") || token.startsWith("/*") || (token.startsWith("#") && language in setOf(NexusLanguage.PYTHON, NexusLanguage.RUBY, NexusLanguage.SHELL)) -> comment
                token.startsWith("\"") || token.startsWith("'") -> string
                token.firstOrNull()?.isDigit() == true -> number
                token in keywords -> keyword
                token.firstOrNull()?.isUpperCase() == true -> type
                else -> null
            }
            if (style != null) builder.addStyle(style, match.range.first, match.range.last + 1)
        }
        return builder.toAnnotatedString()
    }

    private fun keywordSet(language: NexusLanguage): Set<String> = when (language) {
        NexusLanguage.PYTHON -> setOf("def", "class", "import", "from", "as", "if", "elif", "else", "for", "while", "in", "is", "not", "and", "or", "return", "yield", "try", "except", "with", "lambda", "True", "False", "None", "async", "await")
        NexusLanguage.JAVA, NexusLanguage.KOTLIN -> setOf("class", "interface", "object", "fun", "val", "var", "public", "private", "protected", "static", "final", "new", "if", "else", "for", "while", "return", "package", "import", "when", "is", "in", "true", "false", "null", "override")
        NexusLanguage.JAVASCRIPT, NexusLanguage.TYPESCRIPT -> setOf("const", "let", "var", "function", "class", "extends", "import", "from", "export", "default", "if", "else", "for", "while", "return", "async", "await", "new", "this", "true", "false", "null", "undefined", "interface", "type")
        NexusLanguage.C, NexusLanguage.CPP -> setOf("include", "define", "int", "char", "float", "double", "void", "struct", "class", "const", "static", "if", "else", "for", "while", "return", "namespace", "using", "public", "private", "true", "false", "nullptr")
        NexusLanguage.CSHARP -> setOf("using", "namespace", "class", "public", "private", "protected", "static", "void", "int", "string", "if", "else", "for", "foreach", "while", "return", "new", "true", "false", "null", "async", "await", "var")
        NexusLanguage.RUBY -> setOf("def", "class", "module", "require", "include", "if", "elsif", "else", "unless", "while", "do", "end", "return", "true", "false", "nil")
        NexusLanguage.DART -> setOf("class", "extends", "implements", "import", "library", "void", "int", "double", "String", "final", "const", "var", "if", "else", "for", "while", "return", "async", "await", "true", "false", "null")
        NexusLanguage.SWIFT -> setOf("import", "class", "struct", "enum", "protocol", "func", "let", "var", "if", "else", "for", "while", "return", "guard", "switch", "case", "true", "false", "nil")
        NexusLanguage.GO -> setOf("package", "import", "func", "type", "struct", "interface", "var", "const", "if", "else", "for", "range", "return", "go", "defer", "true", "false", "nil")
        NexusLanguage.RUST -> setOf("fn", "let", "mut", "struct", "enum", "impl", "trait", "use", "mod", "pub", "if", "else", "match", "for", "while", "loop", "return", "true", "false")
        NexusLanguage.PHP -> setOf("function", "class", "public", "private", "protected", "static", "if", "else", "foreach", "while", "return", "echo", "new", "true", "false", "null")
        NexusLanguage.SHELL -> setOf("if", "then", "else", "fi", "for", "in", "do", "done", "case", "esac", "function", "export", "local", "echo")
        else -> emptySet()
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
