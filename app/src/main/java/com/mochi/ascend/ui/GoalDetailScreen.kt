package com.mochi.ascend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.mochi.ascend.data.Goal
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Snapshot
import java.time.LocalDate

@Composable
fun GoalDetailScreen(
    data: AppData,
    goalId: String,
    onBack: () -> Unit,
    onEdit: (Goal) -> Unit,
    onOpenGoal: (Goal) -> Unit,
) {
    val goal = data.goals.firstOrNull { it.id == goalId }
    if (goal == null) {
        // Someone deleted it while it was open; offer a way back instead of
        // mutating navigation during composition.
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("This goal is gone.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Pill("Go back", onBack, selected = true)
        }
        return
    }

    val snap = remember(data) { Snapshot(data) }
    val today = LocalDate.now()
    val pct = snap.periodProgress(goal, today)
    val streak = snap.streak(goal)
    val key = Dates.key(goal.horizon, today)
    val current = snap.checkIn(goal.id, key)?.status
    val kids = snap.children(goal.id)
    val parent = goal.parentId?.let { id -> snap.goal(id) }

    var journalText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val savedNote = snap.checkIn(goal.id, key)?.note.orEmpty()
    // Local copy so typing does not hit the disk on every keystroke.
    var noteText by remember(goal.id) { mutableStateOf(savedNote) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ScreenHeader(
            title = "Goal",
            onClose = onBack,
            actions = {
                Pill("Edit", { onEdit(goal) })
                Pill("Delete", { confirmDelete = true }, accent = Red)
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
        Text(
            goal.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizonTag(goal.horizon)
            if (streak > 0) {
                Spacer(Modifier.width(10.dp))
                GlyphIcon(Glyph.FLAME, size = 14.dp, tint = Gold)
                Spacer(Modifier.width(3.dp))
                Text(
                    "$streak ${goal.horizon.short.lowercase()}${if (streak == 1) "" else "s"} in a row",
                    style = MaterialTheme.typography.labelMedium,
                    color = Gold,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Bar(pct, horizonColor(goal.horizon))
        Spacer(Modifier.height(6.dp))
        Text(
            "${(pct * 100).toInt()}% for this ${goal.horizon.short.lowercase()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (goal.detail.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Panel {
                Text(goal.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (parent != null) {
            Spacer(Modifier.height(14.dp))
            Panel(modifier = Modifier.clickable { onOpenGoal(parent) }) {
                Text(
                    "SERVES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(horizonColor(parent.horizon))
                    Spacer(Modifier.width(8.dp))
                    Text(parent.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionHeader("This ${goal.horizon.short.lowercase()}")
        Panel {
            StatusPicker(current = current) { Actions.toggleCheckIn(goal.id, key, it) }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note for this check-in") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Pill(
                "Save note",
                onClick = {
                    // Default to a partial rather than silently dropping the note.
                    Actions.setCheckIn(goal.id, key, current ?: com.mochi.ascend.data.Status.PARTIAL, noteText)
                },
                selected = noteText != savedNote,
            )
        }

        if (goal.milestones.isNotEmpty()) {
            SectionHeader("Milestones", "${goal.milestones.count { it.done }}/${goal.milestones.size}")
            Panel {
                goal.milestones.forEach { ms ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { Actions.toggleMilestone(goal.id, ms.id) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBoxMark(ms.done)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            ms.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ms.done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        if (goal.subtasks.isNotEmpty()) {
            SectionHeader("Steps", "${goal.subtasks.count { it.done }}/${goal.subtasks.size}")
            Panel {
                goal.subtasks.forEach { sub ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { Actions.toggleSubtask(goal.id, sub.id) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CheckBoxMark(sub.done)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            sub.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (sub.done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        if (kids.isNotEmpty()) {
            SectionHeader("Sub-goals", "${kids.size}")
            kids.forEach { kid ->
                Panel(modifier = Modifier.padding(bottom = 8.dp).clickable { onOpenGoal(kid) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(kid.title, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            HorizonTag(kid.horizon)
                        }
                        Text(
                            "${(snap.periodProgress(kid, today) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = horizonColor(kid.horizon),
                        )
                    }
                }
            }
        }

        SectionHeader("Reminder")
        Panel {
            Text(
                if (goal.reminderAt == null) "No reminder set. Edit the goal to add one."
                else "Nudges at ${goal.reminderAt}" + weekdaySuffix(goal.reminderDays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader("Journal", "${goal.journal.size}")
        Panel {
            goal.journal.sortedByDescending { it.at }.forEach { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            Dates.label(Horizon.DAY, java.time.Instant.ofEpochMilli(entry.at)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GlyphIcon(
                        Glyph.TRASH,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { Actions.deleteJournal(goal.id, entry.id) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = journalText,
                onValueChange = { journalText = it },
                label = { Text("How did it go?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Pill(
                "Add entry",
                onClick = {
                    if (journalText.isNotBlank()) {
                        Actions.addJournal(goal.id, journalText.trim(), 3)
                        journalText = ""
                    }
                },
                selected = journalText.isNotBlank(),
            )
        }

        Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this goal?") },
            text = {
                Text(
                    "This also deletes its sub-goals and check-ins. " +
                        "Export a backup first if you are not sure.",
                )
            },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    Actions.deleteGoal(goal.id)
                    onBack()
                }) { Text("Delete", color = Red) }
            },
            dismissButton = {
                TextButton({ confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CheckBoxMark(done: Boolean) {
    val color = if (done) Green else MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlyphIcon(
            if (done) Glyph.CHECK else Glyph.TARGET,
            size = 16.dp,
            tint = color,
        )
    }
}

private fun weekdaySuffix(days: List<Int>): String {
    if (days.isEmpty()) return " every day."
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return " on " + days.sorted().joinToString(", ") { names.getOrElse(it - 1) { "?" } } + "."
}
