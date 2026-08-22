package com.mrredhood.nexus.domain.automation

data class Automation(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: Trigger,
    val conditions: List<Condition>,
    val actions: List<Action>
)

sealed interface Trigger {
    data class Event(val connectorId: String, val eventType: String) : Trigger
    data class Schedule(val cron: String) : Trigger
    data class Manual(val source: String = "user") : Trigger
    data class Webhook(val connectorId: String, val eventType: String) : Trigger
}

data class Condition(val type: String, val field: String, val value: String)
data class Action(val connectorId: String, val action: String, val input: Map<String, String> = emptyMap())

enum class AutomationRunStatus { PENDING, RUNNING, WAITING_FOR_APPROVAL, SUCCEEDED, FAILED, CANCELLED }

data class AutomationRun(
    val automationId: String,
    val runId: String,
    val status: AutomationRunStatus,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long? = null,
    val error: String? = null
)
