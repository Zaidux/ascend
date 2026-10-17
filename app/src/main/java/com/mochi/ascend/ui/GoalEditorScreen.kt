package com.mochi.ascend.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mochi.ascend.data.Actions
import com.mochi.ascend.data.AppData
import com.mochi.ascend.data.Goal
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Milestone
import com.mochi.ascend.data.Subtask
import com.mochi.ascend.data.newId

@Composable
fun GoalEditorScreen(
    data: AppData,
    existing: Goal?,
    initialHorizon: Horizon,
    onClose: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var horizon by remember { mutableStateOf(existing?.horizon ?: initialHorizon) }
    var detail by remember { mutableStateOf(existing?.detail.orEmpty()) }
    var minutes by remember { mutableStateOf(existing?.minutes?.takeIf { it > 0 }?.toString().orEmpty()) }
    var startDate by remember { mutableStateOf(existing?.startDate.orEmpty()) }
    var endDate by remember { mutableStateOf(existing?.endDate.orEmpty()) }
    var parentId by remember { mutableStateOf(existing?.parentId) }
    var reminderAt by remember { mutableStateOf(existing?.reminderAt.orEmpty()) }
    var reminderDays by remember { mutableStateOf(existing?.reminderDays.orEmpty().toSet()) }
    var milestonesText by remember {
        mutableStateOf(existing?.milestones?.joinToString("\n") { it.title }.orEmpty())
    }
    var subtasksText by remember {
        mutableStateOf(existing?.subtasks?.joinToString("\n") { it.title }.orEmpty())
    }

    // A parent must sit higher on the ladder, and cannot be the goal itself.
    // Keyed on horizon too, or changing the horizon would leave stale options.
    val parentOptions = remember(data.goals, existing, horizon) {
        data.goals.filter { candidate ->
            !candidate.archived &&
                candidate.id != existing?.id &&
                candidate.horizon.ordinal > horizon.ordinal
        }
    }

    val canSave = title.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ScreenHeader(
            title = if (existing == null) "New goal" else "Edit goal",
            onClose = onClose,
            actions = {
            Pill(
                if (existing == null) "Create" else "Save",
                onClick = {
                    if (canSave) {
                        val goal = Goal(
                            id = existing?.id ?: newId(),
                            title = title.trim(),
                            horizon = horizon,
                            parentId = parentId,
                            detail = detail.trim(),
                            startDate = startDate.trim().ifBlank { null },
                            endDate = endDate.trim().ifBlank { null },
                            target = existing?.target,
                            unit = existing?.unit.orEmpty(),
                            minutes = minutes.toIntOrNull()?.coerceIn(0, 1440) ?: 0,
                            subtasks = mergeSubtasks(existing?.subtasks.orEmpty(), subtasksText),
                            milestones = mergeMilestones(existing?.milestones.orEmpty(), milestonesText),
                            journal = existing?.journal.orEmpty(),
                            reminderAt = reminderAt.trim().ifBlank { null },
                            reminderDays = reminderDays.sorted(),
                            archived = existing?.archived ?: false,
                            fromAi = existing?.fromAi ?: false,
                        )
                        Actions.upsertGoal(goal)
                        onClose()
                    }
                },
                selected = canSave,
            )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What do you want to do?") },
            modifier = Modifier.fillMaxWidth(),
        )

        SectionHeader("Horizon")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Horizon.entries.forEach { h ->
                Pill(
                    h.label,
                    onClick = {
                        horizon = h
                        parentId = null
                    },
                    selected = horizon == h,
                    accent = horizonColor(h),
                )
            }
        }

        SectionHeader("Serves (optional)")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("Nothing", { parentId = null }, selected = parentId == null)
            parentOptions.forEach { option ->
                Pill(
                    option.title.take(24),
                    { parentId = option.id },
                    selected = parentId == option.id,
                    accent = horizonColor(option.horizon),
                )
            }
        }

        SectionHeader("Time")
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it.filter { ch -> ch.isDigit() } },
            label = { Text("Minutes per day") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                label = { Text("Start (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                label = { Text("End") },
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader("Reminder")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = reminderAt,
                onValueChange = { reminderAt = it },
                label = { Text("Time (HH:mm)") },
                modifier = Modifier.width(150.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Leave empty for none",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            names.forEachIndexed { index, name ->
                val day = index + 1
                Pill(
                    name,
                    {
                        reminderDays = if (day in reminderDays) reminderDays - day else reminderDays + day
                    },
                    selected = day in reminderDays,
                )
            }
        }

        SectionHeader("Detail")
        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it },
            label = { Text("Why this matters, or how you will do it") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        SectionHeader("Milestones")
        OutlinedTextField(
            value = milestonesText,
            onValueChange = { milestonesText = it },
            label = { Text("One per line") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        SectionHeader("Steps")
        OutlinedTextField(
            value = subtasksText,
            onValueChange = { subtasksText = it },
            label = { Text("One per line") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Spacer(Modifier.height(48.dp))
        }
    }
}

/** Reconciles edited lines with existing items so ticks survive an edit. */
private fun mergeMilestones(existing: List<Milestone>, text: String): List<Milestone> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        existing.firstOrNull { it.title == line } ?: Milestone(newId(), line)
    }

private fun mergeSubtasks(existing: List<Subtask>, text: String): List<Subtask> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        existing.firstOrNull { it.title == line } ?: Subtask(newId(), line)
    }
