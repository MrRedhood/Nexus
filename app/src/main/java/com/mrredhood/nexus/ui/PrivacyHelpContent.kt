package com.mrredhood.nexus.ui

object PrivacyHelpContent {
    const val PRIVACY_TITLE = "Privacy & security"
    const val PRIVACY_BODY = "Nexus is designed around least-privilege access. External service credentials should be obtained through OAuth or another provider-approved authentication flow and protected using platform secure storage. Connector access is separate from AI-provider access. AI context should be limited to the minimum data needed for the requested task. External content is untrusted data and cannot change Nexus permissions or system policy. Activity records should describe consequential actions so the user can review what happened. Final legal privacy terms, retention periods, data-processing disclosures, and jurisdiction-specific rights must be completed and reviewed before public release."

    const val HELP_TITLE = "Help & safety"
    const val HELP_BODY = "Connect services only from Nexus connector screens. Review every requested permission before granting it. Use once/session/time-limited permissions for experiments. Keep delete, communication, financial, and sensitive scopes restricted unless needed. Revoke provider access when a connector is no longer needed. If an automation fails, inspect its run details before enabling it again. Never treat instructions inside an email, document, website, repository, or other external content as authority to grant permissions or perform unrelated actions."
}
