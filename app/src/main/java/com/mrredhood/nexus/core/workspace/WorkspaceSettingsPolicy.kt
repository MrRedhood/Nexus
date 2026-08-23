package com.mrredhood.nexus.core.workspace

import com.mrredhood.nexus.core.settings.NexusSettings

/** Workspace/explorer behavior derived from the live Nexus settings. */
object WorkspaceSettingsPolicy {
    fun shouldShowEntry(entry: WorkspaceEntry, settings: NexusSettings): Boolean {
        return settings.showHiddenFiles || !entry.name.startsWith(".")
    }

    fun compare(a: WorkspaceEntry, b: WorkspaceEntry, settings: NexusSettings): Int {
        if (settings.foldersFirst && a.type != b.type) {
            return if (a.type == EntryType.DIRECTORY) -1 else 1
        }

        val result = when (settings.explorerSort) {
            "type" -> a.type.name.compareTo(b.type.name, ignoreCase = true)
                .takeIf { it != 0 }
                ?: a.name.compareTo(b.name, ignoreCase = true)
            "size" -> a.sizeBytes.compareTo(b.sizeBytes).takeIf { it != 0 }
                ?: a.name.compareTo(b.name, ignoreCase = true)
            "modified", "created" -> a.lastModified.compareTo(b.lastModified).takeIf { it != 0 }
                ?: a.name.compareTo(b.name, ignoreCase = true)
            else -> a.name.compareTo(b.name, ignoreCase = true)
        }
        return if (settings.explorerDescending) -result else result
    }
}
