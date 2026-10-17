package com.mochi.ascend.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochi.ascend.data.AppData
import com.mochi.ascend.data.Goal
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Snapshot
import java.time.LocalDate

/** Goals laid out by horizon, with their sub-goals nested underneath. */
@Composable
fun LadderScreen(
    data: AppData,
    onOpenGoal: (Goal) -> Unit,
    onAddGoal: (Horizon) -> Unit,
) {
    val snap = remember(data) { Snapshot(data) }
    var query by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
    ) {
        item {
            Text(
                "The ladder",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search goals") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Horizon.entries.reversed().forEach { horizon ->
            val rows = snap.flatten(horizon).filter { (goal, _) ->
                query.isBlank() ||
                    goal.title.contains(query, ignoreCase = true) ||
                    goal.detail.contains(query, ignoreCase = true)
            }
            if (query.isNotBlank() && rows.isEmpty()) return@forEach
            item(key = "hdr-${horizon.name}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(horizonColor(horizon))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            horizon.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Pill("+ Add", { onAddGoal(horizon) })
                }
            }

            // A horizon can have no *roots* while still holding goals that are
            // nested under a higher horizon, so only claim emptiness when there
            // are genuinely none anywhere on this rung.
            val anyOfHorizon = snap.active.any { it.horizon == horizon }
            if (rows.isEmpty() && !anyOfHorizon) {
                item(key = "empty-${horizon.name}") {
                    Text(
                        "No ${horizon.label.lowercase()} goals yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                }
            }

            items(rows, key = { it.first.id }) { (goal, depth) ->
                LadderNode(
                    goal = goal,
                    depth = depth,
                    snap = snap,
                    onOpen = { onOpenGoal(goal) },
                )
            }
        }
    }
}

@Composable
private fun LadderNode(
    goal: Goal,
    depth: Int,
    snap: Snapshot,
    onOpen: () -> Unit,
) {
    val today = LocalDate.now()
    val pct = snap.periodProgress(goal, today)
    val streak = snap.streak(goal)
    val kidCount = snap.children(goal.id).size

    Panel(
        modifier = Modifier
            .padding(start = (depth * 16).dp, bottom = 8.dp)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    goal.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizonTag(goal.horizon)
                    if (kidCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$kidCount sub",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (streak > 0) {
                        Spacer(Modifier.width(8.dp))
                        GlyphIcon(Glyph.FLAME, size = 12.dp, tint = Gold)
                        Spacer(Modifier.width(3.dp))
                        Text("$streak", style = MaterialTheme.typography.labelSmall, color = Gold)
                    }
                    if (goal.milestones.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${goal.milestones.count { it.done }}/${goal.milestones.size} milestones",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "${(pct * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = horizonColor(goal.horizon),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(9.dp))
        Bar(pct, horizonColor(goal.horizon))
    }
}
