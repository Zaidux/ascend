package com.mochi.ascend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochi.ascend.data.Actions
import com.mochi.ascend.data.AppData
import com.mochi.ascend.data.Dates
import com.mochi.ascend.data.Goal
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Mood
import com.mochi.ascend.data.Snapshot
import com.mochi.ascend.data.Status
import java.time.LocalDate

@Composable
fun TodayScreen(
    data: AppData,
    onOpenGoal: (Goal) -> Unit,
    onAddGoal: (Horizon) -> Unit,
    onBuildPlan: () -> Unit,
) {
    // One indexed pass per data change instead of rescanning on every recomposition.
    val snap = remember(data) { Snapshot(data) }
    val today = LocalDate.now()
    val mood = snap.mood(today)
    val daily = snap.dailyGoals
    val weeklyFocus = snap.active.filter {
        it.horizon == Horizon.WEEK && snap.children(it.id).none { c -> c.horizon == Horizon.DAY }
    }
    val todayKey = Dates.key(Horizon.DAY, today)
    val doneToday = daily.count { snap.checkIn(it.id, todayKey)?.status == Status.DONE }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp,
        ),
    ) {
        if (data.settings.showCat) item { CatHero(snap, mood, data.settings.catName) }

        if (daily.isEmpty() && weeklyFocus.isEmpty()) {
            item {
                Panel(modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        "Nothing scheduled yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Build a plan from a single sentence and the whole ladder fills in, " +
                            "or add one goal at a time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Build a plan", onBuildPlan, selected = true)
                        Pill("Add a goal", { onAddGoal(Horizon.DAY) })
                    }
                }
            }
        }

        item { SectionHeader("The ladder") }
        item { LadderBars(snap) }

        if (daily.isNotEmpty()) {
            item { SectionHeader("Today", "$doneToday/${daily.size} done") }
            items(daily, key = { it.id }) { goal ->
                GoalRow(
                    goal = goal,
                    progress = snap.periodProgress(goal, today),
                    current = snap.checkIn(goal.id, todayKey)?.status,
                    streak = snap.streak(goal),
                    onOpen = { onOpenGoal(goal) },
                    onPick = { status -> Actions.toggleCheckIn(goal.id, todayKey, status) },
                )
            }
        }

        if (weeklyFocus.isNotEmpty()) {
            item { SectionHeader("This week") }
            items(weeklyFocus, key = { it.id }) { goal ->
                val key = Dates.key(Horizon.WEEK, today)
                GoalRow(
                    goal = goal,
                    progress = snap.periodProgress(goal, today),
                    current = snap.checkIn(goal.id, key)?.status,
                    streak = snap.streak(goal),
                    onOpen = { onOpenGoal(goal) },
                    onPick = { status -> Actions.toggleCheckIn(goal.id, key, status) },
                )
            }
        }

        item { SectionHeader("Add") }
        item {
            PillRow(
                listOf("Day", "Week", "Month", "Year"),
                selected = null,
                onSelect = { label -> onAddGoal(Horizon.entries.first { it.short == label }) },
            )
        }
    }
}

@Composable
private fun CatHero(snap: Snapshot, mood: Mood, catName: String) {
    val xp = snap.xp
    val level = snap.level
    val best = snap.bestStreak(Horizon.DAY)

    Panel(modifier = Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatSprite(mood, size = 84.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    snap.nudge(catName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Lv $level",
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.FLAME, size = 15.dp, tint = Gold)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$best day${if (best == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Bar(snap.levelProgress, catAccent())
            }
        }
    }
}

@Composable
private fun LadderBars(snap: Snapshot) {
    Panel {
        Horizon.entries.forEachIndexed { index, horizon ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            val pct = snap.overall(horizon)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(horizonColor(horizon))
                    Spacer(Modifier.width(8.dp))
                    Text(horizon.label, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "${(pct * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = horizonColor(horizon),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Bar(pct, horizonColor(horizon))
        }
    }
}

@Composable
fun GoalRow(
    goal: Goal,
    progress: Float,
    current: Status?,
    streak: Int,
    onOpen: () -> Unit,
    onPick: (Status) -> Unit,
) {
    Panel(modifier = Modifier.padding(bottom = 10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Text(
                goal.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizonTag(goal.horizon)
                if (streak > 0) {
                    Spacer(Modifier.width(8.dp))
                    GlyphIcon(Glyph.FLAME, size = 12.dp, tint = Gold)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "$streak",
                        style = MaterialTheme.typography.labelSmall,
                        color = Gold,
                    )
                }
                if (goal.minutes > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Dates.prettyMinutes(goal.minutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        StatusPicker(current = current, onPick = onPick)
        if (progress > 0f) {
            Spacer(Modifier.height(11.dp))
            Bar(progress, horizonColor(goal.horizon))
        }
    }
}
