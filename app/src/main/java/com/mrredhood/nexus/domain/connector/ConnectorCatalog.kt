package com.mrredhood.nexus.domain.connector

import com.mrredhood.nexus.domain.permission.PermissionScope

/** Official connector definitions exposed before an account is connected. */
object ConnectorCatalog {
    val official: List<ConnectorManifest> = listOf(
        ConnectorManifest(
            id = "google.gmail",
            name = "Gmail",
            version = "1.0",
            authentication = AuthenticationType.OAUTH2,
            permissions = setOf(
                PermissionScope.READ,
                PermissionScope.SEARCH,
                PermissionScope.CREATE,
                PermissionScope.MODIFY,
                PermissionScope.COMMUNICATE,
                PermissionScope.DELETE
            ),
            triggers = setOf("new_email", "email_changed"),
            actions = setOf(
                "search_email", "read_email", "create_draft", "send_email",
                "reply_email", "archive_email", "label_email", "delete_email"
            )
        ),
        ConnectorManifest(
            id = "google.calendar",
            name = "Google Calendar",
            version = "1.0",
            authentication = AuthenticationType.OAUTH2,
            permissions = setOf(PermissionScope.READ, PermissionScope.SEARCH, PermissionScope.CREATE, PermissionScope.MODIFY),
            triggers = setOf("event_created", "event_changed"),
            actions = setOf("search_event", "read_event", "create_event", "update_event", "delete_event")
        ),
        ConnectorManifest(
            id = "google.drive",
            name = "Google Drive",
            version = "1.0",
            authentication = AuthenticationType.OAUTH2,
            permissions = setOf(PermissionScope.READ, PermissionScope.SEARCH, PermissionScope.CREATE, PermissionScope.MODIFY, PermissionScope.DELETE),
            triggers = setOf("file_created", "file_changed"),
            actions = setOf("search_file", "read_file", "upload_file", "download_file", "update_file", "delete_file")
        ),
        ConnectorManifest(
            id = "github",
            name = "GitHub",
            version = "1.0",
            authentication = AuthenticationType.OAUTH2,
            permissions = setOf(PermissionScope.READ, PermissionScope.SEARCH, PermissionScope.CREATE, PermissionScope.MODIFY),
            triggers = setOf("issue_opened", "pull_request_opened", "workflow_failed"),
            actions = setOf("search_repository", "read_issue", "read_pull_request", "create_issue", "comment_issue")
        ),
        ConnectorManifest(
            id = "android.files",
            name = "Android Files",
            version = "1.0",
            authentication = AuthenticationType.LOCAL,
            permissions = setOf(PermissionScope.READ, PermissionScope.SEARCH, PermissionScope.CREATE, PermissionScope.MODIFY, PermissionScope.DELETE),
            triggers = setOf("file_created", "file_changed"),
            actions = setOf("search_file", "read_file", "copy_file", "move_file", "delete_file")
        )
    )
}
