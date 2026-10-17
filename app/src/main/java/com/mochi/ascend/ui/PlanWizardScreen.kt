package com.mochi.ascend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochi.ascend.ai.AiClient
import com.mochi.ascend.ai.Plan
import com.mochi.ascend.ai.PlanRequest
import com.mochi.ascend.ai.Planner
import com.mochi.ascend.data.Actions
import com.mochi.ascend.data.AppData
import com.mochi.ascend.data.Dates
import com.mochi.ascend.data.Horizon
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * "I want to learn X, 2h a day, for 12 weeks" -> a full ladder.
 * Uses the configured AI when available, and the offline planner otherwise.
 */
@Composable
fun PlanWizardScreen(
    data: AppData,
    onClose: () -> Unit,
    onCreated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val aiReady = data.settings.ai.apiKey.isNotBlank() && data.settings.ai.model.isNotBlank()

    var topic by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("Beginner") }
    var hours by remember { mutableFloatStateOf(1.5f) }
    var daysPerWeek by remember { mutableFloatStateOf(6f) }
    var weeks by remember { mutableFloatStateOf(12f) }
    var extra by remember { mutableStateOf("") }

    var plan by remember { mutableStateOf<Plan?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var usedAi by remember { mutableStateOf(false) }

    val today = LocalDate.now()

    fun request() = PlanRequest(
        topic = topic.trim(),
        why = why.trim(),
        level = level,
        hoursPerDay = hours.toDouble(),
        daysPerWeek = daysPerWeek.toInt(),
        weeks = weeks.toInt(),
        extra = extra.trim(),
    )

    fun generate(preferAi: Boolean) {
        val req = request()
        if (req.topic.isBlank()) {
            error = "Give the goal a name first."
            return
        }
        error = ""
        if (!preferAi || !aiReady) {
            plan = Planner.offline(req)
            usedAi = false
            return
        }
        loading = true
        scope.launch {
            val result = runCatching {
                AiClient.complete(data.settings.ai, Planner.SYSTEM_PROMPT, Planner.userPrompt(req))
            }.map { Planner.parse(it) }
            loading = false
            result.fold(
                onSuccess = {
                    plan = it
                    usedAi = true
                },
                onFailure = {
                    error = "AI failed: ${it.message?.take(200)}. Falling back to the offline plan."
                    plan = Planner.offline(req)
                    usedAi = false
                },
            )
        }
    }

    // Header stays put: this screen can be tall, and a close button that scrolls
    // out of view is a trap.
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        ScreenHeader(title = "Build a plan", subtitle = if (aiReady) {
            "Your AI planner is configured and will be used."
        } else {
            "No AI key set, so the built-in offline planner will be used."
        }, onClose = onClose)

        LazyPlanColumn(
            Modifier.weight(1f),
            daysPerWeek = daysPerWeek.toInt(),
            onDaysPerWeek = { daysPerWeek = it.toFloat() },
            topic = topic,
            onTopic = { topic = it },
            why = why,
            onWhy = { why = it },
            level = level,
            onLevel = { level = it },
            hours = hours,
            onHours = { hours = it },
            weeks = weeks,
            onWeeks = { weeks = it },
            extra = extra,
            onExtra = { extra = it },
            loading = loading,
            error = error,
            plan = plan,
            usedAi = usedAi,
            onGenerate = { generate(true) },
            onGenerateOffline = { generate(false) },
            aiReady = aiReady,
            today = today,
            onAddPlan = { p ->
                val goals = Planner.materialize(p, request(), today)
                Actions.addGoals(goals)
                onCreated()
            },
        )
    }
}

@Composable
private fun LazyPlanColumn(
    modifier: Modifier,
    topic: String,
    onTopic: (String) -> Unit,
    why: String,
    onWhy: (String) -> Unit,
    level: String,
    onLevel: (String) -> Unit,
    hours: Float,
    onHours: (Float) -> Unit,
    daysPerWeek: Int,
    onDaysPerWeek: (Int) -> Unit,
    weeks: Float,
    onWeeks: (Float) -> Unit,
    extra: String,
    onExtra: (String) -> Unit,
    loading: Boolean,
    error: String,
    plan: Plan?,
    usedAi: Boolean,
    onGenerate: () -> Unit,
    onGenerateOffline: () -> Unit,
    aiReady: Boolean,
    today: LocalDate,
    onAddPlan: (Plan) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
    ) {
        item { SectionHeader("The goal") }
        item {
            Panel {
                OutlinedTextField(
                    value = topic,
                    onValueChange = onTopic,
                    label = { Text("What are you learning or building?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = why,
                    onValueChange = onWhy,
                    label = { Text("Why does it matter? (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Current level", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                PillRow(
                    listOf("Beginner", "Some experience", "Advanced"),
                    selected = level,
                    onSelect = onLevel,
                )
            }
        }

        item { SectionHeader("Commitment") }
        item {
            Panel {
                Text(
                    "Hours per day: ${"%.1f".format(hours)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = hours,
                    onValueChange = onHours,
                    valueRange = 0.5f..6f,
                    steps = 10,
                )
                Spacer(Modifier.height(8.dp))
                Text("Days per week: $daysPerWeek", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = daysPerWeek.toFloat(),
                    onValueChange = { onDaysPerWeek(it.toInt()) },
                    valueRange = 1f..7f,
                    steps = 5,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Duration: ${weeks.toInt()} weeks",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = weeks,
                    onValueChange = onWeeks,
                    valueRange = 4f..52f,
                    steps = 23,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ends around ${Dates.label(Horizon.MONTH, today.plusWeeks(weeks.toLong()))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = extra,
                    onValueChange = onExtra,
                    label = { Text("Anything else? (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            PillRow(
                listOf(
                    if (aiReady) "Generate with AI" else "Generate plan",
                    "Use offline plan",
                ),
                selected = null,
                onSelect = { label ->
                    if (label.startsWith("Generate")) onGenerate() else onGenerateOffline()
                },
                allSelected = true,
            )
        }

        if (loading) {
            item {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Planning your ${weeks.toInt()} weeks...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (error.isNotBlank()) {
            item {
                Spacer(Modifier.height(14.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = Gold)
            }
        }

        plan?.let { p ->
            item { SectionHeader("Preview", "${p.months.sumOf { it.weeks.size }} weeks") }
            item {
                Panel {
                    Text(
                        p.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        p.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (usedAi) "Written by your AI model." else "Written by the offline planner.",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (usedAi) Violet else Blue,
                    )
                }
            }

            p.months.take(4).forEach { month ->
                item {
                    Panel(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            month.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (month.focus.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                month.focus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        month.milestones.forEach { m ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                GlyphIcon(Glyph.TARGET, size = 13.dp, tint = Violet)
                                Spacer(Modifier.width(6.dp))
                                Text(m, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        month.weeks.take(2).forEach { w ->
                            Spacer(Modifier.height(8.dp))
                            Text(w.title, style = MaterialTheme.typography.labelMedium, color = Blue)
                            w.actions.forEach { a ->
                                Text(
                                    "· $a",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (p.months.size > 4) {
                item {
                    Text(
                        "+ ${p.months.size - 4} more months in the full plan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Pill(
                    "Add ${1 + p.months.sumOf { it.weeks.size }} goals to my ladder",
                    onClick = { onAddPlan(p) },
                    selected = true,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Daily goals are created for the first four weeks. Later weeks keep " +
                        "their steps as sub-goals so you are not buried on day one.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
