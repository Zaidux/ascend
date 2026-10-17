package com.mochi.ascend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochi.ascend.data.Goal
import com.mochi.ascend.focus.FocusService
import kotlinx.coroutines.delay

private val PRESETS = listOf(15, 25, 45, 60)

/**
 * Pick a goal, pick a length, start. The timer keeps counting in a foreground
 * service so you can switch apps while it runs.
 */
@Composable
fun FocusTimerPanel(
    goals: List<Goal>,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var goalId by remember { mutableStateOf(goals.firstOrNull()?.id.orEmpty()) }
    var minutes by remember { mutableIntStateOf(25) }

    var running by remember { mutableStateOf(FocusService.running) }
    var left by remember { mutableIntStateOf(FocusService.remaining()) }

    // One ticker for the whole panel instead of one per row.
    LaunchedEffect(running) {
        while (running) {
            left = FocusService.remaining()
            if (left <= 0) { running = false; onDone() }
            delay(1_000)
        }
    }
    DisposableEffect(Unit) {
        left = FocusService.remaining()
        running = FocusService.running
        onDispose { }
    }

    val goal = goals.firstOrNull { it.id == goalId }

    Panel {
        Text("Focus timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        if (FocusService.running) {
            Spacer(Modifier.height(10.dp))
            Text(
                formatClock(left),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = catAccent(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Focusing on ${FocusService.title.ifBlank { "your goal" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Pill("Stop and log", {
                FocusService.stop(context)
                running = false
                onDone()
            }, accent = Red)
            return@Panel
        }

        Spacer(Modifier.height(10.dp))
        if (goals.isEmpty()) {
            Text(
                "Add a daily goal first — the timer logs minutes against a goal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Panel
        }

        OutlinedTextField(
            value = goals.firstOrNull { it.id == goalId }?.title.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Goal") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                // Cycle through the list rather than opening a picker.
                val index = goals.indexOfFirst { it.id == goalId }
                Pill("Next", {
                    goalId = goals[(index + 1).coerceAtMost(goals.lastIndex)].id
                })
            },
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { preset ->
                Pill("$preset min", { minutes = preset }, selected = minutes == preset)
            }
        }

        Spacer(Modifier.height(14.dp))
        Pill("Start", {
            val target = goal ?: return@Pill
            FocusService.start(context, target.id, target.title, minutes)
            running = true
            left = minutes * 60
        }, selected = goal != null)
    }
}

private fun formatClock(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}