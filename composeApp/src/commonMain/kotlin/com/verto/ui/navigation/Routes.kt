package com.verto.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object InboxRoute

@Serializable
data object PlanRoute

@Serializable
data class TaskEditorRoute(val taskId: String? = null)

@Serializable
data class RoutineEditorRoute(val routineId: String? = null, val fromInboxId: String? = null)

@Serializable
data class AnchorEditorRoute(val anchorId: String? = null)

@Serializable
data object AccountRoute

@Serializable
data object SettingsRoute
