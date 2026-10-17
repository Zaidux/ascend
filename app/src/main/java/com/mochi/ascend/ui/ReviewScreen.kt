package com.mochi.ascend.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochi.ascend.data.Actions
import com.mochi.ascend.data.AppData
import com.mochi.ascend.data.Dates
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Snapshot
import com.mochi.ascend.data.Reflection
import com.mochi.ascend.data.newId
import java.time.LocalDate

/**
 * The bit most goal apps skip: stopping to look back, writing it down, and
 * deciding what carries forward.
 */
@Composable
fun ReviewScreen(data: AppData) {
    val today = LocalDate.now()
    var horizon by remember { mutableStateOf(Horizon.WEEK) }
    val key = Dates.key(horizon, today)
    val existing = data.reflections.firstOrNull { it.horizon == horizon && it.periodKey == key }

    var text by remember(horizon, key) { mutableStateOf(existing?.text.orEmpty()) }
    var mood by remember(horizon, key) { mutableIntStateOf(existing?.mood ?: 3) }

    val start = Dates.startOf(horizon, today)
    val end = minOf(Dates.endOf(horizon, today), today)
    val elapsedDays = (java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)

    val snap = remember(data) { Snapshot(data) }
    val (coverage, days) = snap.dailyCoverage(start, end)
    val neglected = snap.slipped(start, end)
    val expected = snap.dailyGoals.size * elapsedDays
    val achieved = coverage * expected
    val achievedPercent = if (expected == 0) 0 else (coverage * 100).toInt()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
    ) {
        item {
            Text(
                "Review",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Look back before looking forward.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("This week", { horizon = Horizon.WEEK }, selected = horizon == Horizon.WEEK, accent = Blue)
                Pill("This month", { horizon = Horizon.MONTH }, selected = horizon == Horizon.MONTH, accent = Violet)
            }
        }

        item { SectionHeader(if (horizon == Horizon.WEEK) "Week of ${Dates.label(Horizon.WEEK, today)}" else Dates.label(Horizon.MONTH, today)) }
        item {
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            "$achievedPercent%",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Green,
                        )
                        Text(
                            "of possible check-ins",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${elapsedDays}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Blue,
                        )
                        Text(
                            "days elapsed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { SectionHeader("Slipped", "${neglected.size}") }
        item {
            Panel {
                if (neglected.isEmpty()) {
                    Text(
                        "Nothing slipped. Genuinely.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green,
                    )
                } else {
                    neglected.take(10).forEach { goal ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Dot(Red, size = 6)
                            Spacer(Modifier.width(8.dp))
                            Text(goal.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (horizon == Horizon.WEEK && neglected.isNotEmpty()) {
                        Pill(
                            "Carry into next week",
                            onClick = {
                                val nextMonday = today.with(java.time.DayOfWeek.MONDAY).plusWeeks(1)
                                Actions.carryForward(neglected.take(8).map { it.title }, nextMonday)
                            },
                            selected = true,
                        )
                    }
                }
            }
        }

        item { SectionHeader("How did it feel?") }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val labels = listOf("Rough", "Meh", "Okay", "Good", "Great")
                labels.forEachIndexed { index, label ->
                    Pill(
                        label,
                        { mood = index + 1 },
                        selected = mood == index + 1,
                        accent = when (index) {
                            0, 1 -> Red
                            2 -> Blue
                            else -> Green
                        },
                    )
                }
            }
        }

        item { SectionHeader("Notes") }
        item {
            Panel {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What worked, what did not, what changes next") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
                Spacer(Modifier.height(12.dp))
                Pill(
                    "Save review",
                    onClick = {
                        Actions.saveReflection(
                            Reflection(
                                id = existing?.id ?: newId(),
                                horizon = horizon,
                                periodKey = key,
                                at = System.currentTimeMillis(),
                                mood = mood,
                                text = text.trim(),
                            )
                        )
                    },
                    selected = text.isNotBlank(),
                )
            }
        }

        val past = data.reflections.sortedByDescending { it.at }.take(5)
        if (past.isNotEmpty()) {
            item { SectionHeader("Earlier reviews") }
            item {
                Panel {
                    past.forEach { r ->
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Text(
                                "${r.horizon.label} · ${r.periodKey}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                r.text.ifBlank { "(no notes)" },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
