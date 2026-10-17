package com.mochi.ascend.data

import kotlinx.serialization.Serializable

/** The four time horizons a goal can live on. */
@Serializable
enum class Horizon(val label: String, val short: String) {
    DAY("Daily", "Day"),
    WEEK("Weekly", "Week"),
    MONTH("Monthly", "Month"),
    YEAR("Yearly", "Year"),
}

@Serializable
enum class Status(val label: String) {
    DONE("Done"),
    PARTIAL("Partial"),
    SKIPPED("Skipped"),
}

/** The sprite's mood. Drives which face gets drawn. */
enum class Mood { IDLE, HAPPY, EXCITED, SAD, SLEEPY }

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val done: Boolean = false,
)

@Serializable
data class Milestone(
    val id: String,
    val title: String,
    val dueDate: String? = null,
    val done: Boolean = false,
)

@Serializable
data class JournalEntry(
    val id: String,
    val at: Long,
    val text: String,
    val mood: Int = 3,
)

@Serializable
data class Goal(
    val id: String,
    val title: String,
    val horizon: Horizon,
    /** Links this goal upward to the goal it serves. */
    val parentId: String? = null,
    val detail: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val target: Double? = null,
    val unit: String = "",
    /** Committed minutes per day, used by the planner. */
    val minutes: Int = 0,
    val subtasks: List<Subtask> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    /** Local reminder time, "HH:mm". */
    val reminderAt: String? = null,
    /** ISO day numbers 1=Mon..7=Sun; empty means every day. */
    val reminderDays: List<Int> = emptyList(),
    val archived: Boolean = false,
    val fromAi: Boolean = false,
)

@Serializable
data class CheckIn(
    val goalId: String,
    val periodKey: String,
    val status: Status,
    val note: String = "",
    val at: Long = 0L,
)

@Serializable
data class Reflection(
    val id: String,
    val horizon: Horizon,
    val periodKey: String,
    val at: Long,
    val mood: Int,
    val text: String,
    val carried: List<String> = emptyList(),
)

@Serializable
data class AiConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "",
)

/** How much the companion is allowed to observe. Chosen by the user. */
@Serializable
enum class WatchLevel(val label: String, val blurb: String) {
    OFF("Off", "He just sits there. Cute, but blind."),
    IDLE("Idle only", "He knows when you last checked in. No extra permission."),
    APP("Idle + foreground app", "He can see which app is open, not what's in it."),
    SCREEN("On-screen content", "He can read window titles. Grants broad access."),
}

@Serializable
data class CompanionSettings(
    val enabled: Boolean = false,
    val watch: WatchLevel = WatchLevel.OFF,
    val speak: Boolean = true,
    val voiceInput: Boolean = true,
    val jokes: Boolean = true,
    val petGivesXp: Boolean = true,
)

/** A logged stretch of focused work against one goal. */
@Serializable
data class FocusSession(
    val id: String,
    val goalId: String,
    val startedAt: Long,
    val minutes: Int,
)

@Serializable
data class Settings(
    val ai: AiConfig = AiConfig(),
    val dark: Boolean = true,
    val themeId: String = "ember",
    val remindersEnabled: Boolean = false,
    val digestAt: String = "08:00",
    val catName: String = "Mochi",
    val showCat: Boolean = true,
    /** How many minutes a day the user says they can actually commit to. */
    val capacityMinutes: Int = 120,
    /** Weekly plan rollover: generate the next week when Monday arrives. */
    val autoRoll: Boolean = false,
    /** Weekly JSON snapshot. Opt-in, off by default. */
    val autoBackup: Boolean = false,
    val lastBackupAt: Long = 0L,
    val companion: CompanionSettings = CompanionSettings(),
)

@Serializable
data class AppData(
    val goals: List<Goal> = emptyList(),
    val checkIns: List<CheckIn> = emptyList(),
    val reflections: List<Reflection> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val settings: Settings = Settings(),
    val lastOpen: Long = 0L,
    val lastSeenWeek: String = "",
)

/** Goal tree helpers shared by the UI. */
fun AppData.children(parentId: String): List<Goal> =
    goals.filter { it.parentId == parentId && !it.archived }

fun AppData.rootGoals(horizon: Horizon): List<Goal> =
    goals.filter { it.horizon == horizon && it.parentId == null && !it.archived }

fun AppData.activeGoals(): List<Goal> = goals.filter { !it.archived }
