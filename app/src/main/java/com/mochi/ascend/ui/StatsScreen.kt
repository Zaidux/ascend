package com.mochi.ascend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochi.ascend.data.AppData
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Snapshot
import com.mochi.ascend.data.Status
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun StatsScreen(data: AppData) {
    val snap = remember(data) { Snapshot(data) }
    val today = LocalDate.now()
    val xp = snap.xp
    val level = snap.level
    val best = snap.bestStreak(Horizon.DAY)
    val doneTotal = snap.doneCount
    val milestonesDone = snap.milestonesDone

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
    ) {
        item {
            Text(
                "Progress",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Everything here is earned from check-ins, not typed in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Level", "$level", catAccent(), Modifier.weight(1f))
                StatTile("XP", "$xp", Green, Modifier.weight(1f))
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Day streak", "$best", catAccent(), Modifier.weight(1f))
                StatTile("Check-ins", "$doneTotal", Blue, Modifier.weight(1f))
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Milestones", "$milestonesDone", Violet, Modifier.weight(1f))
                StatTile("Goals", "${data.goals.count { !it.archived }}", Green, Modifier.weight(1f))
            }
        }

        item { SectionHeader("Consistency", "last $HEATMAP_WEEKS weeks") }
        item { Heatmap(snap) }

        item { SectionHeader("By horizon") }
        item {
            Panel {
                Horizon.entries.forEachIndexed { index, horizon ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    val pct = snap.overall(horizon)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
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

        item { SectionHeader("Strongest habits") }
        item {
            val top = snap.dailyGoals
                .map { it to snap.streak(it) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(6)

            Panel {
                if (top.isEmpty()) {
                    Text(
                        "No streaks yet. Check something off today to start one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    top.forEach { (goal, streak) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(goal.title, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlyphIcon(Glyph.FLAME, size = 13.dp, tint = Gold)
                                Spacer(Modifier.width(4.dp))
                                Text("$streak", style = MaterialTheme.typography.labelLarge, color = Gold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val HEATMAP_WEEKS = 12

/**
 * One box per day: rows are weekdays, columns are weeks.
 * Width-adaptive, and scores come from a single cached pass over the window.
 */
@Composable
private fun Heatmap(snap: Snapshot) {
    val gap = 3.dp
    val labelWidth = 16.dp
    val cell = 15.dp

    Panel {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Shrink the window rather than overflow on narrow screens.
            val usable = maxWidth - labelWidth
            val perWeek = cell + gap
            val weeks = ((usable / perWeek).toInt().coerceIn(4, HEATMAP_WEEKS))

            val today = LocalDate.now()
            val start = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
            val scores = snap.dayScores(start, weeks * 7)
            val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")

            Column(Modifier.fillMaxWidth()) {
                for (dayIndex in 0 until 7) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dayNames[dayIndex],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(labelWidth),
                        )
                        for (week in 0 until weeks) {
                            val idx = week * 7 + dayIndex
                            Box(
                                Modifier
                                    .padding(end = gap)
                                    .size(cell)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(heatColor(scores[idx])),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "less",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    listOf(-1f, 0f, 0.34f, 0.67f, 1f).forEach { sc ->
                        Box(
                            Modifier
                                .padding(end = gap)
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(heatColor(sc)),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun heatColor(score: Float): Color = when {
    score < 0f -> Color.Transparent
    score == 0f -> Color(0xFF232C36)
    score < 0.34f -> Green.copy(alpha = 0.30f)
    score < 0.67f -> Green.copy(alpha = 0.55f)
    score < 1f -> Green.copy(alpha = 0.80f)
    else -> Green
}
