package com.mochi.ascend.data

import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Weekly rollover: when a new week starts, give each weekly goal a fresh set of
 * daily goals for that week, derived from its steps.
 *
 * Opt-in, because some people would rather write Monday's list themselves.
 */
object WeeklyRoll {

    private const val MAX_PER_WEEK = 4

    fun currentWeekKey(now: LocalDate = LocalDate.now()): String =
        Dates.weekKey(now)

    fun weekStart(now: LocalDate = LocalDate.now()): LocalDate =
        now.with(DayOfWeek.MONDAY)

    /**
     * Returns the goals to create, or an empty list when nothing is needed.
     * Pure, so it can be previewed and tested without touching the store.
     */
    fun plan(
        data: AppData,
        now: LocalDate = LocalDate.now(),
    ): List<Goal> {
        if (!data.settings.autoRoll) return emptyList()

        val start = weekStart(now)
        val startKey = start.toString()
        val endKey = start.plusDays(6).toString()

        val weekly = data.goals.filter { it.horizon == Horizon.WEEK && !it.archived }
        val out = mutableListOf<Goal>()

        for (week in weekly) {
            val kids = data.goals.filter { it.parentId == week.id && !it.archived }

            // Already has daily goals covering this week? Leave it alone.
            if (kids.any { it.startDate == startKey || it.startDate == null }) continue

            val titles = week.subtasks.filterNot { it.done }.map { it.title }
                .ifEmpty { listOfNotNull(week.title.takeIf { it.isNotBlank() }) }

            // If last week's dailies are still around and still current, reuse them.
            val reusable = kids.filter { it.startDate != null && it.startDate!! < startKey }
                .map { it.title }

            val items = (titles.ifEmpty { reusable })
                .distinct()
                .take(MAX_PER_WEEK)

            if (items.isEmpty()) {
                out += Goal(
                    id = newId(),
                    title = "Work on ${week.title}",
                    horizon = Horizon.DAY,
                    parentId = week.id,
                    startDate = startKey,
                    endDate = endKey,
                    minutes = 30,
                    fromAi = week.fromAi,
                )
                continue
            }

            items.forEachIndexed { index, title ->
                val minutes = week.minutes / items.size.coerceAtLeast(1)
                out += Goal(
                    id = newId(),
                    title = title,
                    horizon = Horizon.DAY,
                    parentId = week.id,
                    startDate = startKey,
                    endDate = endKey,
                    minutes = minutes,
                    fromAi = week.fromAi,
                    detail = if (index == 0) "Rolled over from ${week.title}" else "",
                )
            }
        }
        return out
    }

    /** Applies [plan] and records the week so it never double-runs. */
    fun apply() {
        val data = Store.data.value
        val weekKey = currentWeekKey()
        if (data.lastSeenWeek == weekKey) return

                val created = plan(data)
        Store.update { current ->
            current.copy(
                goals = current.goals + created,
                lastSeenWeek = weekKey,
            )
        }
        // Reminders are rescheduled on app open, so nothing to do here.
    }
}

/**
 * "I said I'd do 2 hours a day." Warns when the ladder schedules more than that.
 */
object Capacity {

    data class Day(val date: LocalDate, val minutes: Int, val over: Boolean)

    /** Scheduled minutes per day for the week containing [now]. */
    fun week(data: AppData, now: LocalDate = LocalDate.now()): List<Day> {
        val start = now.with(DayOfWeek.MONDAY)
        val daily = data.goals.filter { it.horizon == Horizon.DAY && !it.archived }
        val capacity = data.settings.capacityMinutes

        return (0 until 7).map { offset ->
            val date = start.plusDays(offset.toLong())
            var minutes = 0
            daily.forEach { goal ->
                if (!activeOn(goal, date)) return@forEach
                minutes += goal.minutes
            }
            Day(date, minutes, minutes > capacity)
        }
    }

    private fun activeOn(goal: Goal, date: LocalDate): Boolean {
        val start = Dates.parse(goal.startDate)
        val end = Dates.parse(goal.endDate)
        if (start != null && date.isBefore(start)) return false
        if (end != null && date.isAfter(end)) return false
        return true
    }

    fun peak(data: AppData, now: LocalDate = LocalDate.now()): Int =
        week(data, now).maxOfOrNull { it.minutes } ?: 0
}

/** Dated JSON snapshots in app storage. No permission needed, opt-in only. */
object AutoBackup {

    private const val STAMP = "yyyy-MM-dd"

    fun due(data: AppData, now: Long = System.currentTimeMillis()): Boolean {
        if (!data.settings.autoBackup) return false
        if (data.settings.lastBackupAt <= 0L) return true
        val elapsed = ChronoUnit.DAYS.between(
            java.time.Instant.ofEpochMilli(data.settings.lastBackupAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
        )
        return elapsed >= 7
    }

    fun run(ctx: android.content.Context): Boolean {
        if (!due(Store.data.value)) return false
        return try {
            val dir = File(ctx.filesDir, "backups").apply { mkdirs() }
            val name = LocalDate.now().format(DateTimeFormatter.ofPattern(STAMP)) + ".json"
            val out = File(dir, name)
            out.writeText(Store.exportText())

            // Keep the four most recent snapshots.
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(4)
                ?.forEach { it.delete() }

            Store.update { it.copy(settings = it.settings.copy(lastBackupAt = System.currentTimeMillis())) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun list(ctx: android.content.Context): List<File> =
        File(ctx.filesDir, "backups")
            .listFiles { f -> f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}

/** Which day a focus session landed on, for the heatmap. */
object FocusLog {
    fun key(at: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString()

    fun minutesOn(data: AppData, date: LocalDate): Int {
        val k = date.toString()
        return data.focusSessions.filter { key(it.startedAt) == k }.sumOf { it.minutes }
    }

    fun total(data: AppData): Int = data.focusSessions.sumOf { it.minutes }
}