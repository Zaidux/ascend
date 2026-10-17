package com.mochi.ascend.ai

import kotlinx.serialization.Serializable

@Serializable
data class PlanWeek(
    val title: String = "",
    val actions: List<String> = emptyList(),
    val daily: List<String> = emptyList(),
)

@Serializable
data class PlanMonth(
    val title: String = "",
    val focus: String = "",
    val milestones: List<String> = emptyList(),
    val weeks: List<PlanWeek> = emptyList(),
)

/** The shape we ask the model for, and the shape the offline planner produces. */
@Serializable
data class Plan(
    val title: String = "",
    val summary: String = "",
    val months: List<PlanMonth> = emptyList(),
)

/** Everything the planner needs to know. */
data class PlanRequest(
    val topic: String,
    val why: String = "",
    val level: String = "Beginner",
    val hoursPerDay: Double = 1.0,
    val daysPerWeek: Int = 6,
    val weeks: Int = 12,
    val extra: String = "",
)
