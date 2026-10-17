package com.mochi.ascend.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * An indexed, memoised view over [AppData].
 *
 * The whole UI reads from one of these instead of scanning [AppData] on every
 * recomposition. Building it is O(goals + check-ins); every query after that is
 * a hash lookup, and repeat queries are cached.
 *
 * Create one with `remember(data) { Snapshot(data) }` so it is rebuilt only when
 * the data actually changes.
 */
class Snapshot(val data: AppData) {

    private val checks: Map<String, CheckIn> = HashMap<String, CheckIn>(data.checkIns.size * 2)
        .apply {
            data.checkIns.forEach { put(it.goalId + "|" + it.periodKey, it) }
        }

    val active: List<Goal> = data.goals.filter { !it.archived }

    private val byParent: Map<String?, List<Goal>> = active.groupBy { it.parentId }

    private val rootsByHorizon: Map<Horizon, List<Goal>> = byParent[null]
        .orEmpty()
        .groupBy { it.horizon }

    val dailyGoals: List<Goal> = active.filter { it.horizon == Horizon.DAY }

    // Memo caches, keyed per snapshot instance.
    private val completionMemo = HashMap<String, Float>()
    private val periodMemo = HashMap<String, Float>()
    private val streakMemo = HashMap<String, Int>()
    private val dayScoreMemo = HashMap<String, Float>()

    // ------------------------------------------------------------- primitives

    fun checkIn(goalId: String, periodKey: String): CheckIn? = checks[goalId + "|" + periodKey]

    fun valueOf(goalId: String, periodKey: String): Float =
        Progress.value(checkIn(goalId, periodKey)?.status)

    fun children(goalId: String): List<Goal> = byParent[goalId].orEmpty()

    fun roots(horizon: Horizon): List<Goal> = rootsByHorizon[horizon].orEmpty()

    fun goal(id: String): Goal? = active.firstOrNull { it.id == id }

    // ------------------------------------------------------------- completion

    /** Completion of a goal for the period containing [date]. */
    fun completion(goal: Goal, date: LocalDate = LocalDate.now()): Float {
        val periodKey = Dates.key(goal.horizon, date)
        val memoKey = goal.id + "|" + periodKey
        completionMemo[memoKey]?.let { return it }

        val own = checkIn(goal.id, periodKey)
        val kids = children(goal.id)
        val result = if (kids.isEmpty()) {
            Progress.value(own?.status)
        } else {
            var sum = 0f
            kids.forEach { sum += completion(it, date) }
            val kidAvg = sum / kids.size
            if (own != null) (Progress.value(own.status) + kidAvg) / 2f else kidAvg
        }
        completionMemo[memoKey] = result
        return result
    }

    /**
     * Completion across the whole period. For goals built from daily children
     * this looks at every elapsed day, not just today, so weekly and monthly
     * bars do not jump around.
     */
    fun periodProgress(goal: Goal, date: LocalDate = LocalDate.now()): Float {
        val memoKey = goal.id + "|" + Dates.key(goal.horizon, date)
        periodMemo[memoKey]?.let { return it }

        val dayKids = children(goal.id).filter { it.horizon == Horizon.DAY }
        val result = if (dayKids.isEmpty()) {
            completion(goal, date)
        } else {
            val start = Dates.startOf(goal.horizon, date)
            val end = minOf(Dates.endOf(goal.horizon, date), date)
            val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
            if (days <= 0) {
                0f
            } else {
                var total = 0f
                for (kid in dayKids) {
                    var done = 0f
                    var d = start
                    while (!d.isAfter(end)) {
                        done += valueOf(kid.id, Dates.key(Horizon.DAY, d))
                        d = d.plusDays(1)
                    }
                    total += done / days
                }
                total / dayKids.size
            }
        }
        periodMemo[memoKey] = result
        return result
    }

    /** Average completion across all root goals on a rung. */
    fun overall(horizon: Horizon, date: LocalDate = LocalDate.now()): Float {
        val goals = roots(horizon)
        if (goals.isEmpty()) return 0f
        var sum = 0f
        goals.forEach { sum += periodProgress(it, date) }
        return sum / goals.size
    }

    // ---------------------------------------------------------------- streaks

    fun streak(goal: Goal, today: LocalDate = LocalDate.now()): Int {
        streakMemo[goal.id]?.let { return it }

        var count = 0
        var cursor = today
        // Today is still open, so it must not break a streak.
        if (valueOf(goal.id, Dates.key(goal.horizon, cursor)) < 0.5f) {
            cursor = Dates.minus(goal.horizon, cursor, 1)
        }
        var guard = 0
        while (guard++ < 500) {
            if (valueOf(goal.id, Dates.key(goal.horizon, cursor)) < 0.5f) break
            count++
            cursor = Dates.minus(goal.horizon, cursor, 1)
        }
        streakMemo[goal.id] = count
        return count
    }

    fun bestStreak(horizon: Horizon = Horizon.DAY): Int {
        var best = 0
        dailyOr(horizon).forEach { best = maxOf(best, streak(it)) }
        return best
    }

    private fun dailyOr(horizon: Horizon): List<Goal> =
        if (horizon == Horizon.DAY) dailyGoals else active.filter { it.horizon == horizon }

    // ------------------------------------------------------------ heatmap/XP

    fun dayScore(date: LocalDate): Float {
        val k = Dates.dayKey(date)
        dayScoreMemo[k]?.let { return it }
        if (dailyGoals.isEmpty()) {
            dayScoreMemo[k] = 0f
            return 0f
        }
        var sum = 0f
        dailyGoals.forEach { sum += valueOf(it.id, k) }
        val result = sum / dailyGoals.size
        dayScoreMemo[k] = result
        return result
    }

    /** One call for a whole heatmap window instead of N lookups per cell. */
    fun dayScores(start: LocalDate, count: Int): FloatArray {
        val out = FloatArray(count)
        var d = start
        repeat(count) {
            out[it] = if (d.isAfter(LocalDate.now())) -1f else dayScore(d)
            d = d.plusDays(1)
        }
        return out
    }

    val xp: Int by lazy {
        data.checkIns.sumOf {
            when (it.status) {
                Status.DONE -> 10
                Status.PARTIAL -> 5
                Status.SKIPPED -> 0
            }
        } + data.goals.sumOf { g ->
            g.milestones.count { it.done } * 20 + g.subtasks.count { it.done } * 2
        } + FocusLog.total(data)
    }

    /** Focused minutes logged today, across all goals. */
    val focusToday: Int by lazy { FocusLog.minutesOn(data, LocalDate.now()) }

    fun focusOn(date: LocalDate): Int = FocusLog.minutesOn(data, date)

    /** Overcommitted days for the current week, for the capacity warning. */
    fun overcommitted(): List<Capacity.Day> =
        Capacity.week(data).filter { it.over }

    val level: Int get() = Progress.level(xp)
    val levelProgress: Float get() = Progress.levelProgress(xp)

    val doneCount: Int by lazy { data.checkIns.count { it.status == Status.DONE } }

    val milestonesDone: Int by lazy {
        data.goals.sumOf { g -> g.milestones.count { it.done } }
    }

    // ------------------------------------------------------------------ mood

    fun daysSinceOpen(): Long {
        if (data.lastOpen <= 0L) return 0
        val then = java.time.Instant.ofEpochMilli(data.lastOpen)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return ChronoUnit.DAYS.between(then, LocalDate.now())
    }

    fun mood(today: LocalDate = LocalDate.now()): Mood {
        val hour = LocalTime.now().hour
        val todayKey = Dates.key(Horizon.DAY, today)
        var doneToday = false
        var skippedToday = false
        checks.values.forEach { ci ->
            if (ci.periodKey == todayKey) {
                when (ci.status) {
                    Status.DONE -> doneToday = true
                    Status.SKIPPED -> skippedToday = true
                    Status.PARTIAL -> Unit
                }
            }
        }
        val best = bestStreak(Horizon.DAY)

        return when {
            hour < 5 || hour >= 23 -> Mood.SLEEPY
            best >= 7 -> Mood.EXCITED
            doneToday -> Mood.HAPPY
            daysSinceOpen() >= 3 -> Mood.SLEEPY
            skippedToday -> Mood.SAD
            else -> Mood.IDLE
        }
    }

    fun nudge(catName: String): String {
        val mood = mood()
        return when (mood) {
            Mood.EXCITED -> "$catName is vibrating. ${bestStreak(Horizon.DAY)} day streak!"
            Mood.HAPPY -> "Nice. $catName saw that one."
            Mood.SAD -> "$catName saw that skip. Tomorrow is still yours."
            Mood.SLEEPY -> "$catName was wondering where you went."
            Mood.IDLE ->
                if (active.isEmpty()) "No goals yet. Let's build the ladder."
                else "$catName is waiting on today's first check-in."
        }
    }

    // ------------------------------------------------------------- tree shape

    /** Depth-first goals for a rung, so parents are followed by their children. */
    fun flatten(horizon: Horizon): List<Pair<Goal, Int>> {
        val out = ArrayList<Pair<Goal, Int>>()
        fun walk(goal: Goal, depth: Int) {
            out.add(goal to depth)
            if (depth < 6) children(goal.id).forEach { walk(it, depth + 1) }
        }
        roots(horizon).forEach { walk(it, 0) }
        return out
    }

    /** Day-average check-in coverage across the period, for the review screen. */
    fun dailyCoverage(start: LocalDate, end: LocalDate): Pair<Float, Int> {
        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
        if (days <= 0 || dailyGoals.isEmpty()) return 0f to 0
        var sum = 0f
        for (kid in dailyGoals) {
            var done = 0f
            var d = start
            while (!d.isAfter(end)) {
                done += valueOf(kid.id, Dates.key(Horizon.DAY, d))
                d = d.plusDays(1)
            }
            sum += done / days
        }
        return (sum / dailyGoals.size) to days
    }

    /** Daily goals whose coverage across the period was poor. */
    fun slipped(start: LocalDate, end: LocalDate, threshold: Float = 0.5f): List<Goal> {
        if (dailyGoals.isEmpty()) return emptyList()
        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
        if (days <= 0) return emptyList()
        return dailyGoals.filter { kid ->
            var sum = 0f
            var d = start
            while (!d.isAfter(end)) {
                sum += valueOf(kid.id, Dates.key(Horizon.DAY, d))
                d = d.plusDays(1)
            }
            (sum / days) < threshold
        }
    }
}

/** Convenience for callers that only have a date. */
fun startOfWeek(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)