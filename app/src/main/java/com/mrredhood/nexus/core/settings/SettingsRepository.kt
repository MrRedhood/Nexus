package com.mrredhood.nexus.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nexusSettingsDataStore by preferencesDataStore(name = "nexus_settings")

data class NexusSettings(
    val theme: String = "system",
    val accent: String = "blue",
    val editorTheme: String = "nexus_dark",
    val uiScale: Float = 1f,
    val animations: String = "system",
    val compactMode: Boolean = false,
    val fullscreen: Boolean = false,
    val immersiveCoding: Boolean = false,
    val editorFont: String = "JetBrains Mono",
    val editorFontSize: Int = 14,
    val lineHeight: String = "normal",
    val wordWrap: String = "off",
    val tabSize: Int = 4,
    val useSpaces: Boolean = true,
    val autoIndent: Boolean = true,
    val autoCloseBrackets: Boolean = true,
    val autoCloseTags: Boolean = true,
    val autoRenameTags: Boolean = true,
    val syntaxHighlighting: Boolean = true,
    val rainbowBrackets: Boolean = false,
    val matchingBracketHighlight: Boolean = true,
    val lineNumbers: String = "absolute",
    val currentLineHighlight: Boolean = true,
    val indentGuides: Boolean = true,
    val showWhitespace: Boolean = false,
    val codeFolding: Boolean = true,
    val minimap: Boolean = false,
    val explorerSort: String = "name",
    val explorerDescending: Boolean = false,
    val foldersFirst: Boolean = true,
    val explorerView: String = "list",
    val showHiddenFiles: Boolean = false,
    val showFullPath: Boolean = false,
    val workspacePermission: String = "standard",
    val indexing: String = "automatic",
    val workspaceContext: String = "smart",
    val aiCompletion: String = "manual",
    val formatOnSave: Boolean = false,
    val diagnostics: Boolean = true,
    val agentAutonomy: String = "approve_risky",
    val maxParallelAgents: Int = 2,
    val snapshotBeforeAiEdit: Boolean = true,
    val showDiffBeforeApply: String = "always",
    val autoApplyPatches: String = "never",
    val terminalFontSize: Int = 13,
    val terminalScrollback: Int = 5000,
    val confirmTerminalClose: Boolean = true,
    val aiStreaming: Boolean = true,
    val maxContextFiles: Int = 10,
    val includeCurrentFile: Boolean = true,
    val includeSelection: Boolean = true,
    val includeGitDiff: Boolean = true,
    val includeTerminalContext: Boolean = false,
    val includeWorkspaceSummary: Boolean = true,
    val memoryApproval: String = "ask",
    val appLock: Boolean = false,
    val autoAnalyzeCiFailures: Boolean = true,
    val ciRefreshSeconds: Int = 10,
    val rememberOpenFiles: Boolean = true,
    val rememberCursorPosition: Boolean = true,
    val rememberExplorerState: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("appearance.theme")
        val accent = stringPreferencesKey("appearance.accent")
        val editorTheme = stringPreferencesKey("appearance.editor_theme")
        val uiScale = floatPreferencesKey("appearance.ui_scale")
        val animations = stringPreferencesKey("appearance.animations")
        val compact = booleanPreferencesKey("appearance.compact")
        val fullscreen = booleanPreferencesKey("appearance.fullscreen")
        val immersive = booleanPreferencesKey("appearance.immersive")
        val editorFont = stringPreferencesKey("editor.font")
        val editorFontSize = intPreferencesKey("editor.font_size")
        val lineHeight = stringPreferencesKey("editor.line_height")
        val wordWrap = stringPreferencesKey("editor.word_wrap")
        val tabSize = intPreferencesKey("editor.tab_size")
        val spaces = booleanPreferencesKey("editor.spaces")
        val autoIndent = booleanPreferencesKey("editor.auto_indent")
        val autoCloseBrackets = booleanPreferencesKey("editor.auto_close_brackets")
        val autoCloseTags = booleanPreferencesKey("editor.auto_close_tags")
        val autoRenameTags = booleanPreferencesKey("editor.auto_rename_tags")
        val syntax = booleanPreferencesKey("editor.syntax")
        val rainbow = booleanPreferencesKey("editor.rainbow")
        val matching = booleanPreferencesKey("editor.matching")
        val lineNumbers = stringPreferencesKey("editor.line_numbers")
        val currentLine = booleanPreferencesKey("editor.current_line")
        val indentGuides = booleanPreferencesKey("editor.indent_guides")
        val whitespace = booleanPreferencesKey("editor.whitespace")
        val folding = booleanPreferencesKey("editor.folding")
        val minimap = booleanPreferencesKey("editor.minimap")
        val explorerSort = stringPreferencesKey("explorer.sort")
        val explorerDescending = booleanPreferencesKey("explorer.descending")
        val foldersFirst = booleanPreferencesKey("explorer.folders_first")
        val explorerView = stringPreferencesKey("explorer.view")
        val hidden = booleanPreferencesKey("explorer.hidden")
        val fullPath = booleanPreferencesKey("explorer.full_path")
        val workspacePermission = stringPreferencesKey("workspace.permission")
        val indexing = stringPreferencesKey("workspace.indexing")
        val workspaceContext = stringPreferencesKey("workspace.context")
        val aiCompletion = stringPreferencesKey("assistant.ai_completion")
        val formatOnSave = booleanPreferencesKey("assistant.format_on_save")
        val diagnostics = booleanPreferencesKey("diagnostics.enabled")
        val autonomy = stringPreferencesKey("agents.autonomy")
        val parallel = intPreferencesKey("agents.parallel")
        val snapshot = booleanPreferencesKey("safety.snapshot")
        val diff = stringPreferencesKey("safety.diff")
        val autoApply = stringPreferencesKey("safety.auto_apply")
        val terminalFont = intPreferencesKey("terminal.font_size")
        val terminalScrollback = intPreferencesKey("terminal.scrollback")
        val terminalClose = booleanPreferencesKey("terminal.confirm_close")
        val streaming = booleanPreferencesKey("ai.streaming")
        val maxFiles = intPreferencesKey("ai.max_context_files")
        val currentFile = booleanPreferencesKey("ai.current_file")
        val selection = booleanPreferencesKey("ai.selection")
        val gitDiff = booleanPreferencesKey("ai.git_diff")
        val terminalContext = booleanPreferencesKey("ai.terminal")
        val workspaceSummary = booleanPreferencesKey("ai.workspace_summary")
        val memoryApproval = stringPreferencesKey("memory.approval")
        val appLock = booleanPreferencesKey("security.app_lock")
        val autoAnalyze = booleanPreferencesKey("ci.auto_analyze")
        val ciRefresh = intPreferencesKey("ci.refresh_seconds")
        val rememberFiles = booleanPreferencesKey("projects.remember_files")
        val rememberCursor = booleanPreferencesKey("projects.remember_cursor")
        val rememberExplorer = booleanPreferencesKey("projects.remember_explorer")
    }

    val settings: Flow<NexusSettings> = context.nexusSettingsDataStore.data.map { p ->
        NexusSettings(
            theme = p[Keys.theme] ?: "system", accent = p[Keys.accent] ?: "blue", editorTheme = p[Keys.editorTheme] ?: "nexus_dark",
            uiScale = p[Keys.uiScale] ?: 1f, animations = p[Keys.animations] ?: "system", compactMode = p[Keys.compact] ?: false,
            fullscreen = p[Keys.fullscreen] ?: false, immersiveCoding = p[Keys.immersive] ?: false, editorFont = p[Keys.editorFont] ?: "JetBrains Mono",
            editorFontSize = p[Keys.editorFontSize] ?: 14, lineHeight = p[Keys.lineHeight] ?: "normal", wordWrap = p[Keys.wordWrap] ?: "off",
            tabSize = p[Keys.tabSize] ?: 4, useSpaces = p[Keys.spaces] ?: true, autoIndent = p[Keys.autoIndent] ?: true,
            autoCloseBrackets = p[Keys.autoCloseBrackets] ?: true, autoCloseTags = p[Keys.autoCloseTags] ?: true, autoRenameTags = p[Keys.autoRenameTags] ?: true,
            syntaxHighlighting = p[Keys.syntax] ?: true, rainbowBrackets = p[Keys.rainbow] ?: false, matchingBracketHighlight = p[Keys.matching] ?: true,
            lineNumbers = p[Keys.lineNumbers] ?: "absolute", currentLineHighlight = p[Keys.currentLine] ?: true, indentGuides = p[Keys.indentGuides] ?: true,
            showWhitespace = p[Keys.whitespace] ?: false, codeFolding = p[Keys.folding] ?: true, minimap = p[Keys.minimap] ?: false,
            explorerSort = p[Keys.explorerSort] ?: "name", explorerDescending = p[Keys.explorerDescending] ?: false, foldersFirst = p[Keys.foldersFirst] ?: true,
            explorerView = p[Keys.explorerView] ?: "list", showHiddenFiles = p[Keys.hidden] ?: false, showFullPath = p[Keys.fullPath] ?: false,
            workspacePermission = p[Keys.workspacePermission] ?: "standard", indexing = p[Keys.indexing] ?: "automatic", workspaceContext = p[Keys.workspaceContext] ?: "smart",
            aiCompletion = p[Keys.aiCompletion] ?: "manual", formatOnSave = p[Keys.formatOnSave] ?: false, diagnostics = p[Keys.diagnostics] ?: true,
            agentAutonomy = p[Keys.autonomy] ?: "approve_risky", maxParallelAgents = p[Keys.parallel] ?: 2, snapshotBeforeAiEdit = p[Keys.snapshot] ?: true,
            showDiffBeforeApply = p[Keys.diff] ?: "always", autoApplyPatches = p[Keys.autoApply] ?: "never", terminalFontSize = p[Keys.terminalFont] ?: 13,
            terminalScrollback = p[Keys.terminalScrollback] ?: 5000, confirmTerminalClose = p[Keys.terminalClose] ?: true, aiStreaming = p[Keys.streaming] ?: true,
            maxContextFiles = p[Keys.maxFiles] ?: 10, includeCurrentFile = p[Keys.currentFile] ?: true, includeSelection = p[Keys.selection] ?: true,
            includeGitDiff = p[Keys.gitDiff] ?: true, includeTerminalContext = p[Keys.terminalContext] ?: false, includeWorkspaceSummary = p[Keys.workspaceSummary] ?: true,
            memoryApproval = p[Keys.memoryApproval] ?: "ask", appLock = p[Keys.appLock] ?: false, autoAnalyzeCiFailures = p[Keys.autoAnalyze] ?: true,
            ciRefreshSeconds = p[Keys.ciRefresh] ?: 10, rememberOpenFiles = p[Keys.rememberFiles] ?: true, rememberCursorPosition = p[Keys.rememberCursor] ?: true,
            rememberExplorerState = p[Keys.rememberExplorer] ?: true
        )
    }

    suspend fun update(transform: (NexusSettings) -> NexusSettings) {
        context.nexusSettingsDataStore.edit { p ->
            val old = NexusSettings(
                theme = p[Keys.theme] ?: "system", accent = p[Keys.accent] ?: "blue", editorTheme = p[Keys.editorTheme] ?: "nexus_dark", uiScale = p[Keys.uiScale] ?: 1f,
                animations = p[Keys.animations] ?: "system", compactMode = p[Keys.compact] ?: false, fullscreen = p[Keys.fullscreen] ?: false, immersiveCoding = p[Keys.immersive] ?: false,
                editorFont = p[Keys.editorFont] ?: "JetBrains Mono", editorFontSize = p[Keys.editorFontSize] ?: 14, lineHeight = p[Keys.lineHeight] ?: "normal", wordWrap = p[Keys.wordWrap] ?: "off",
                tabSize = p[Keys.tabSize] ?: 4, useSpaces = p[Keys.spaces] ?: true, autoIndent = p[Keys.autoIndent] ?: true, autoCloseBrackets = p[Keys.autoCloseBrackets] ?: true,
                autoCloseTags = p[Keys.autoCloseTags] ?: true, autoRenameTags = p[Keys.autoRenameTags] ?: true, syntaxHighlighting = p[Keys.syntax] ?: true, rainbowBrackets = p[Keys.rainbow] ?: false,
                matchingBracketHighlight = p[Keys.matching] ?: true, lineNumbers = p[Keys.lineNumbers] ?: "absolute", currentLineHighlight = p[Keys.currentLine] ?: true, indentGuides = p[Keys.indentGuides] ?: true,
                showWhitespace = p[Keys.whitespace] ?: false, codeFolding = p[Keys.folding] ?: true, minimap = p[Keys.minimap] ?: false, explorerSort = p[Keys.explorerSort] ?: "name",
                explorerDescending = p[Keys.explorerDescending] ?: false, foldersFirst = p[Keys.foldersFirst] ?: true, explorerView = p[Keys.explorerView] ?: "list", showHiddenFiles = p[Keys.hidden] ?: false,
                showFullPath = p[Keys.fullPath] ?: false, workspacePermission = p[Keys.workspacePermission] ?: "standard", indexing = p[Keys.indexing] ?: "automatic", workspaceContext = p[Keys.workspaceContext] ?: "smart",
                aiCompletion = p[Keys.aiCompletion] ?: "manual", formatOnSave = p[Keys.formatOnSave] ?: false, diagnostics = p[Keys.diagnostics] ?: true, agentAutonomy = p[Keys.autonomy] ?: "approve_risky",
                maxParallelAgents = p[Keys.parallel] ?: 2, snapshotBeforeAiEdit = p[Keys.snapshot] ?: true, showDiffBeforeApply = p[Keys.diff] ?: "always", autoApplyPatches = p[Keys.autoApply] ?: "never",
                terminalFontSize = p[Keys.terminalFont] ?: 13, terminalScrollback = p[Keys.terminalScrollback] ?: 5000, confirmTerminalClose = p[Keys.terminalClose] ?: true, aiStreaming = p[Keys.streaming] ?: true,
                maxContextFiles = p[Keys.maxFiles] ?: 10, includeCurrentFile = p[Keys.currentFile] ?: true, includeSelection = p[Keys.selection] ?: true, includeGitDiff = p[Keys.gitDiff] ?: true,
                includeTerminalContext = p[Keys.terminalContext] ?: false, includeWorkspaceSummary = p[Keys.workspaceSummary] ?: true, memoryApproval = p[Keys.memoryApproval] ?: "ask", appLock = p[Keys.appLock] ?: false,
                autoAnalyzeCiFailures = p[Keys.autoAnalyze] ?: true, ciRefreshSeconds = p[Keys.ciRefresh] ?: 10, rememberOpenFiles = p[Keys.rememberFiles] ?: true,
                rememberCursorPosition = p[Keys.rememberCursor] ?: true, rememberExplorerState = p[Keys.rememberExplorer] ?: true
            )
            val s = transform(old)
            p[Keys.theme] = s.theme; p[Keys.accent] = s.accent; p[Keys.editorTheme] = s.editorTheme; p[Keys.uiScale] = s.uiScale; p[Keys.animations] = s.animations
            p[Keys.compact] = s.compactMode; p[Keys.fullscreen] = s.fullscreen; p[Keys.immersive] = s.immersiveCoding; p[Keys.editorFont] = s.editorFont; p[Keys.editorFontSize] = s.editorFontSize
            p[Keys.lineHeight] = s.lineHeight; p[Keys.wordWrap] = s.wordWrap; p[Keys.tabSize] = s.tabSize; p[Keys.spaces] = s.useSpaces; p[Keys.autoIndent] = s.autoIndent
            p[Keys.autoCloseBrackets] = s.autoCloseBrackets; p[Keys.autoCloseTags] = s.autoCloseTags; p[Keys.autoRenameTags] = s.autoRenameTags; p[Keys.syntax] = s.syntaxHighlighting; p[Keys.rainbow] = s.rainbowBrackets
            p[Keys.matching] = s.matchingBracketHighlight; p[Keys.lineNumbers] = s.lineNumbers; p[Keys.currentLine] = s.currentLineHighlight; p[Keys.indentGuides] = s.indentGuides; p[Keys.whitespace] = s.showWhitespace
            p[Keys.folding] = s.codeFolding; p[Keys.minimap] = s.minimap; p[Keys.explorerSort] = s.explorerSort; p[Keys.explorerDescending] = s.explorerDescending; p[Keys.foldersFirst] = s.foldersFirst
            p[Keys.explorerView] = s.explorerView; p[Keys.hidden] = s.showHiddenFiles; p[Keys.fullPath] = s.showFullPath; p[Keys.workspacePermission] = s.workspacePermission; p[Keys.indexing] = s.indexing
            p[Keys.workspaceContext] = s.workspaceContext; p[Keys.aiCompletion] = s.aiCompletion; p[Keys.formatOnSave] = s.formatOnSave; p[Keys.diagnostics] = s.diagnostics; p[Keys.autonomy] = s.agentAutonomy
            p[Keys.parallel] = s.maxParallelAgents; p[Keys.snapshot] = s.snapshotBeforeAiEdit; p[Keys.diff] = s.showDiffBeforeApply; p[Keys.autoApply] = s.autoApplyPatches; p[Keys.terminalFont] = s.terminalFontSize
            p[Keys.terminalScrollback] = s.terminalScrollback; p[Keys.terminalClose] = s.confirmTerminalClose; p[Keys.streaming] = s.aiStreaming; p[Keys.maxFiles] = s.maxContextFiles; p[Keys.currentFile] = s.includeCurrentFile
            p[Keys.selection] = s.includeSelection; p[Keys.gitDiff] = s.includeGitDiff; p[Keys.terminalContext] = s.includeTerminalContext; p[Keys.workspaceSummary] = s.includeWorkspaceSummary; p[Keys.memoryApproval] = s.memoryApproval
            p[Keys.appLock] = s.appLock; p[Keys.autoAnalyze] = s.autoAnalyzeCiFailures; p[Keys.ciRefresh] = s.ciRefreshSeconds; p[Keys.rememberFiles] = s.rememberOpenFiles; p[Keys.rememberCursor] = s.rememberCursorPosition; p[Keys.rememberExplorer] = s.rememberExplorerState
        }
    }
}
