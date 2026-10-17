package com.mochi.ascend.ai

import com.mochi.ascend.data.Goal
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Milestone
import com.mochi.ascend.data.Subtask
import com.mochi.ascend.data.newId
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Turns "I want to learn X, 2h/day, 12 weeks" into a real goal ladder.
 *
 * Two paths:
 *  - [offline]  deterministic, no network, no key. Always works.
 *  - AI         the same contract, filled in by the model.
 *
 * Both produce a [Plan], and [materialize] converts that into goals.
 */
object Planner {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val SYSTEM_PROMPT = """
        You are a pragmatic study and career coach. You turn a person's learning
        goal into a concrete, week-by-week plan.

        Rules:
        - Be specific to the topic. No generic filler like "study more".
        - Respect the stated time budget. Never plan more work than the hours allow.
        - Each week must have exactly 3 actions and exactly 2 short daily habits.
        - Each month must have exactly 2 checkable milestones.
        - Daily habits must be doable in one sitting and phrased as an instruction.
        - Progress from foundations to real, demonstrable output.
        - Do not include markdown, code fences, or commentary. Output JSON only.

        Output exactly this JSON shape:
        {
          "title": "short name for the overall goal",
          "summary": "2-3 sentences on the strategy and what success looks like",
          "months": [
            {
              "title": "Month 1: <theme>",
              "focus": "what this month is really about",
              "milestones": ["checkable milestone", "checkable milestone"],
              "weeks": [
                {
                  "title": "Week 1: <theme>",
                  "actions": ["action one", "action two", "action three"],
                  "daily": ["daily habit one", "daily habit two"]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    fun userPrompt(r: PlanRequest): String = buildString {
        appendLine("Build a plan with these constraints.")
        appendLine()
        appendLine("Goal: ${r.topic}")
        if (r.why.isNotBlank()) appendLine("Why it matters to them: ${r.why}")
        appendLine("Current level: ${r.level}")
        appendLine("Time available: ${r.hoursPerDay} hours per day, ${r.daysPerWeek} days per week")
        appendLine("Total duration: ${r.weeks} weeks")
        if (r.extra.isNotBlank()) appendLine("Other context: ${r.extra}")
        appendLine()
        val months = monthsFor(r.weeks)
        appendLine("Cover the full $months month(s): use 4 weeks per month, ${r.weeks} weeks total.")
        appendLine("After the last week, add one final month focused on proving the skill with a real artefact.")
    }

    fun monthsFor(weeks: Int): Int = ((weeks.coerceAtLeast(1) + 3) / 4).coerceAtLeast(1)

    // ---------------------------------------------------------------- offline

    /** The no-network fallback. Structured enough to actually be useful. */
    fun offline(r: PlanRequest): Plan {
        val weeks = r.weeks.coerceIn(1, 104)
        val months = monthsFor(weeks)
        val perDay = (r.hoursPerDay * 60).toInt().coerceAtLeast(20)
        val topic = r.topic.ifBlank { "your goal" }

        val blocks = (0 until months).map { m ->
            val weekStart = m * 4
            val phase = phaseFor(weekStart.toDouble() / weeks)
            PlanMonth(
                title = "Month ${m + 1}: ${phase.name}",
                focus = phase.focus,
                milestones = phase.milestones.map { "$it — $topic" },
                weeks = (0 until 4).mapNotNull { j ->
                    val idx = weekStart + j
                    if (idx >= weeks) return@mapNotNull null
                    PlanWeek(
                        title = "Week ${idx + 1}: ${phase.name}",
                        actions = phase.actions.map { it.replace("%t", topic) },
                        daily = listOf(
                            "Deep work: ${perDay - 15} min on $topic",
                            "Review + log progress (15 min)",
                        ),
                    )
                },
            )
        }

        return Plan(
            title = topic,
            summary = "A ${weeks}-week plan at ${r.hoursPerDay}h/day, " +
                "${r.daysPerWeek} days a week. It moves from foundations, to hard practice, " +
                "to producing something you can show.",
            months = blocks,
        )
    }

    private class Phase(
        val name: String,
        val focus: String,
        val milestones: List<String>,
        val actions: List<String>,
    )

    private fun phaseFor(fraction: Double): Phase = when {
        fraction < 0.25 -> Phase(
            name = "Foundations",
            focus = "Build solid fundamentals and a rhythm you can actually keep.",
            milestones = listOf("Explain the core ideas out loud without notes", "Finish the introductory material end to end"),
            actions = listOf(
                "Study core %t material in focused blocks",
                "Follow along with guided practice",
                "Write a short recap: what clicked, what did not",
            ),
        )
        fraction < 0.75 -> Phase(
            name = "Building",
            focus = "Push difficulty. Produce real work instead of collecting notes.",
            milestones = listOf("Complete one substantial practice project", "Work at the edge of your current level"),
            actions = listOf(
                "Hands-on build or practice session on %t",
                "Attempt harder exercises beyond comfort",
                "Review mistakes and patch weak spots",
            ),
        )
        else -> Phase(
            name = "Consolidation",
            focus = "Turn knowledge into evidence you can show someone.",
            milestones = listOf("Ship a portfolio-ready artefact", "Pass a self-administered assessment"),
            actions = listOf(
                "Produce a real artefact: project, writeup or demo",
                "Self-test without notes and score it",
                "Teach or explain one concept to someone else",
            ),
        )
    }

    // ------------------------------------------------------------------ parse

    fun parse(text: String): Plan {
        val cleaned = text.replace("```json", " ").replace("```", " ").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw IllegalStateException("The model did not return JSON. Try again or use the offline plan.")
        }
        val plan = json.decodeFromString(Plan.serializer(), cleaned.substring(start, end + 1))
        if (plan.months.isEmpty()) {
            throw IllegalStateException("The plan came back empty. Try again or use the offline plan.")
        }
        return plan
    }

    // ------------------------------------------------------------- materialize

    /**
     * Expands a plan into real goals.
     *
     * Everything is materialised except daily goals, which are created for the
     * first four weeks only — otherwise a year-long plan would dump hundreds of
     * daily items on you on day one. Later weeks keep their actions as subtasks.
     */
    fun materialize(plan: Plan, r: PlanRequest, start: LocalDate): List<Goal> {
        val out = mutableListOf<Goal>()
        val weeks = r.weeks.coerceIn(1, 104)
        val perDay = (r.hoursPerDay * 60).toInt().coerceAtLeast(20)
        val end = start.plusWeeks(weeks.toLong()).minusDays(1)

        val year = Goal(
            id = newId(),
            title = plan.title.ifBlank { r.topic },
            horizon = Horizon.YEAR,
            detail = plan.summary,
            startDate = start.toString(),
            endDate = end.toString(),
            minutes = perDay,
            fromAi = true,
        )
        out += year

        var weekIndex = 0
        plan.months.forEachIndexed { monthIndex, block ->
            if (weekIndex >= weeks) return@forEachIndexed

            val monthStart = start.plusWeeks(weekIndex.toLong())
            val monthEnd = monthStart.plusWeeks(4).minusDays(1).let { if (it.isAfter(end)) end else it }

            val month = Goal(
                id = newId(),
                title = block.title.ifBlank { "Month ${monthIndex + 1}" },
                horizon = Horizon.MONTH,
                parentId = year.id,
                detail = block.focus,
                startDate = monthStart.toString(),
                endDate = monthEnd.toString(),
                fromAi = true,
                milestones = block.milestones.map { Milestone(newId(), it) },
            )
            out += month

            block.weeks.forEach { w ->
                if (weekIndex >= weeks) return@forEach

                val wStart = start.plusWeeks(weekIndex.toLong())
                val wEnd = wStart.plusDays(6)
                val week = Goal(
                    id = newId(),
                    title = w.title.ifBlank { "Week ${weekIndex + 1}" },
                    horizon = Horizon.WEEK,
                    parentId = month.id,
                    startDate = wStart.toString(),
                    endDate = wEnd.toString(),
                    fromAi = true,
                    subtasks = w.actions.map { Subtask(newId(), it) },
                )
                out += week

                if (weekIndex < 4) {
                    w.daily.take(4).forEach { d ->
                        out += Goal(
                            id = newId(),
                            title = d,
                            horizon = Horizon.DAY,
                            parentId = week.id,
                            startDate = wStart.toString(),
                            endDate = wEnd.toString(),
                            fromAi = true,
                            minutes = minutesIn(d, perDay),
                        )
                    }
                }
                weekIndex++
            }
        }
        return out
    }

    /** Reads "45 min ..." out of a habit string, else falls back. */
    private fun minutesIn(text: String, fallback: Int): Int {
        val match = Regex("(\\d{1,3})\\s*(min|m\\b|minutes)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(5, 480) ?: fallback
    }
}
