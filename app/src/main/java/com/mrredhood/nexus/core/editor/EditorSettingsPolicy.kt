package com.mrredhood.nexus.core.editor

import com.mrredhood.nexus.core.settings.NexusSettings

/** Centralizes editor behavior derived from Nexus settings. */
data class EditorSettingsPolicy(
    val fontFamily: String,
    val fontSize: Int,
    val lineHeight: String,
    val wordWrap: String,
    val tabSize: Int,
    val insertSpaces: Boolean,
    val autoIndent: Boolean,
    val autoCloseBrackets: Boolean,
    val autoCloseTags: Boolean,
    val autoRenameTags: Boolean,
    val syntaxHighlighting: Boolean,
    val rainbowBrackets: Boolean,
    val matchingBracketHighlight: Boolean,
    val lineNumbers: String,
    val currentLineHighlight: Boolean,
    val indentGuides: Boolean,
    val showWhitespace: Boolean,
    val codeFolding: Boolean,
    val minimap: Boolean
) {
    companion object {
        fun from(settings: NexusSettings): EditorSettingsPolicy = EditorSettingsPolicy(
            fontFamily = settings.editorFont,
            fontSize = settings.editorFontSize,
            lineHeight = settings.lineHeight,
            wordWrap = settings.wordWrap,
            tabSize = settings.tabSize,
            insertSpaces = settings.useSpaces,
            autoIndent = settings.autoIndent,
            autoCloseBrackets = settings.autoCloseBrackets,
            autoCloseTags = settings.autoCloseTags,
            autoRenameTags = settings.autoRenameTags,
            syntaxHighlighting = settings.syntaxHighlighting,
            rainbowBrackets = settings.rainbowBrackets,
            matchingBracketHighlight = settings.matchingBracketHighlight,
            lineNumbers = settings.lineNumbers,
            currentLineHighlight = settings.currentLineHighlight,
            indentGuides = settings.indentGuides,
            showWhitespace = settings.showWhitespace,
            codeFolding = settings.codeFolding,
            minimap = settings.minimap
        )
    }
}
