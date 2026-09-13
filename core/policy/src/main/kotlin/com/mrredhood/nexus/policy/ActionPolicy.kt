package com.mrredhood.nexus.policy

import com.mrredhood.nexus.domain.ActionRisk
import com.mrredhood.nexus.domain.ActionType
import com.mrredhood.nexus.domain.PermissionMode
import com.mrredhood.nexus.domain.WorkspaceAction

class ActionPolicy {
    fun requiresApproval(action: WorkspaceAction, mode: PermissionMode): Boolean {
        if (action.type == ActionType.DeleteFile) return true
        if (action.risk == ActionRisk.CREDENTIAL || action.risk == ActionRisk.RELEASE) return true
        return when (mode) {
            PermissionMode.NEVER -> true
            PermissionMode.SOME -> action.risk != ActionRisk.READ_ONLY
            PermissionMode.AUTONOMOUS -> false
        }
    }
}
