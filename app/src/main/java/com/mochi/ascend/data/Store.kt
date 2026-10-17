package com.mochi.ascend.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Single source of truth. Everything lives in one JSON file in app storage:
 * no account, no network, no sync.
 */
object Store {
    private const val FILE_NAME = "ascend.json"

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var file: File? = null

    // Single writer keeps file ordering sane and takes IO off the main thread.
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ascend-store").apply { isDaemon = true }
    }

    private var refreshWidget: (() -> Unit)? = null

    // Widget redraws are binder calls; coalesce bursts and keep them off the
    // main thread so ticking three check-ins in a row stays instant.
    private val refreshQueued = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, FILE_NAME)
        file = f
        _data.value = read(f)
        // Opening the app counts as a visit; the sprite uses this to decide
        // whether it has been neglected.
        _data.value = _data.value.copy(lastOpen = System.currentTimeMillis())
        persist(_data.value)
    }

    private fun read(f: File): AppData = try {
        if (f.exists()) json.decodeFromString(AppData.serializer(), f.readText()) else AppData()
    } catch (_: Throwable) {
        AppData()
    }

    fun onWidgetRefresh(block: () -> Unit) {
        refreshWidget = block
    }

    fun update(block: (AppData) -> AppData) {
        val next = block(_data.value)
        _data.value = next
        persist(next)
        queueWidgetRefresh()
    }

    private fun queueWidgetRefresh() {
        val block = refreshWidget ?: return
        if (!refreshQueued.compareAndSet(false, true)) return
        io.execute {
            try {
                block()
            } catch (_: Throwable) {
                // A widget that fails to redraw must not break the app.
            } finally {
                refreshQueued.set(false)
            }
        }
    }

    /** Runs work off the main thread on the store's serial executor. */
    fun inBackground(work: () -> Unit) {
        io.execute {
            try {
                work()
            } catch (_: Throwable) {
                // Background best-effort work must not crash the app.
            }
        }
    }

    private fun persist(d: AppData) {
        val f = file ?: return
        io.execute {
            try {
                val text = json.encodeToString(AppData.serializer(), d)
                val tmp = File(f.parentFile, "$FILE_NAME.tmp")
                tmp.writeText(text)
                if (f.exists()) f.delete()
                tmp.renameTo(f)
            } catch (_: Throwable) {
                // A failed write must never take the app down.
            }
        }
    }

    fun exportText(): String = json.encodeToString(AppData.serializer(), _data.value)

    fun importText(text: String): Result<Unit> = try {
        val parsed = json.decodeFromString(AppData.serializer(), text)
        update { parsed }
        Result.success(Unit)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    fun touchLastOpen() = update { it.copy(lastOpen = System.currentTimeMillis()) }
}

fun newId(): String = UUID.randomUUID().toString()

/** All mutations the UI needs, in one place. */
object Actions {

    fun setCheckIn(goalId: String, key: String, status: Status, note: String = "") =
        Store.update { d ->
            val prev = d.checkIns.firstOrNull { it.goalId == goalId && it.periodKey == key }
            val entry = CheckIn(
                goalId = goalId,
                periodKey = key,
                status = status,
                note = if (note.isNotBlank()) note else prev?.note.orEmpty(),
                at = System.currentTimeMillis(),
            )
            d.copy(
                checkIns = d.checkIns.filterNot { it.goalId == goalId && it.periodKey == key } + entry
            )
        }

    fun clearCheckIn(goalId: String, key: String) = Store.update { d ->
        d.copy(checkIns = d.checkIns.filterNot { it.goalId == goalId && it.periodKey == key })
    }

    /** Toggle: tapping the status you already have removes it. */
    fun toggleCheckIn(goalId: String, key: String, status: Status) = Store.update { d ->
        val prev = d.checkIns.firstOrNull { it.goalId == goalId && it.periodKey == key }
        if (prev != null && prev.status == status) {
            d.copy(checkIns = d.checkIns.filterNot { it.goalId == goalId && it.periodKey == key })
        } else {
            d.copy(
                checkIns = d.checkIns.filterNot { it.goalId == goalId && it.periodKey == key } +
                    CheckIn(goalId, key, status, prev?.note.orEmpty(), System.currentTimeMillis())
            )
        }
    }

    fun upsertGoal(goal: Goal) = Store.update { d ->
        d.copy(goals = d.goals.filterNot { it.id == goal.id } + goal)
    }

    fun addGoals(goals: List<Goal>) = Store.update { d -> d.copy(goals = d.goals + goals) }

    /** Removes the goal, its descendants, and any check-ins pointing at them. */
    fun deleteGoal(id: String) = Store.update { d ->
        val doomed = mutableSetOf(id)
        var grew = true
        while (grew) {
            grew = false
            d.goals.forEach { g ->
                if (g.parentId != null && g.parentId in doomed && doomed.add(g.id)) grew = true
            }
        }
        d.copy(
            goals = d.goals.filterNot { it.id in doomed },
            checkIns = d.checkIns.filterNot { it.goalId in doomed },
        )
    }

    fun archiveGoal(id: String, archived: Boolean) = Store.update { d ->
        d.copy(goals = d.goals.map { if (it.id == id) it.copy(archived = archived) else it })
    }

    fun toggleSubtask(goalId: String, subId: String) = Store.update { d ->
        d.copy(goals = d.goals.map { g ->
            if (g.id != goalId) g
            else g.copy(subtasks = g.subtasks.map { if (it.id == subId) it.copy(done = !it.done) else it })
        })
    }

    fun toggleMilestone(goalId: String, msId: String) = Store.update { d ->
        d.copy(goals = d.goals.map { g ->
            if (g.id != goalId) g
            else g.copy(milestones = g.milestones.map { if (it.id == msId) it.copy(done = !it.done) else it })
        })
    }

    fun addJournal(goalId: String, text: String, mood: Int) = Store.update { d ->
        d.copy(goals = d.goals.map { g ->
            if (g.id != goalId) g
            else g.copy(
                journal = g.journal + JournalEntry(newId(), System.currentTimeMillis(), text, mood)
            )
        })
    }

    fun deleteJournal(goalId: String, entryId: String) = Store.update { d ->
        d.copy(goals = d.goals.map { g ->
            if (g.id != goalId) g else g.copy(journal = g.journal.filterNot { it.id == entryId })
        })
    }

    fun saveReflection(r: Reflection) = Store.update { d ->
        d.copy(reflections = d.reflections.filterNot { it.id == r.id } + r)
    }

    fun saveSettings(s: Settings) = Store.update { d -> d.copy(settings = s) }

    /** Carries unfinished weekly items into the next week as fresh daily goals. */
    fun carryForward(carriedTitles: List<String>, targetWeekStart: java.time.LocalDate) = Store.update { d ->
        var list = d.goals
        carriedTitles.forEach { title ->
            list = list + Goal(
                id = newId(),
                title = title,
                horizon = Horizon.DAY,
                startDate = targetWeekStart.toString(),
                endDate = targetWeekStart.plusDays(6).toString(),
            )
        }
        d.copy(goals = list)
    }
}
