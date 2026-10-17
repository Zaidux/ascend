package com.mochi.ascend.companion

import com.mochi.ascend.ai.AiClient
import com.mochi.ascend.data.AiConfig
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Snapshot
import java.time.LocalTime

/**
 * Everything the cat says.
 *
 * Canned lines work with no network and no AI. When a key is configured, the
 * same lines get replaced by a live reply from the model, prompted to stay in
 * character.
 */
object Meow {

    val idle = listOf(
        "mrrp.",
        "*stares at you*",
        "prrrrrrr.",
        "mrow?",
        "*tail flick*",
        "*slow blink*",
        "*sits on your keyboard*",
    )

    val pet = listOf(
        "prrrrr!",
        "*purrs*",
        "mrrp <3",
        "*leans into your hand*",
        "mrow~",
    )

    val nag = listOf(
        "*taps your screen*",
        "meow?",
        "*stares meaningfully*",
        "mrow. check in?",
        "*has been sitting on this a while*",
    )

    val greeting = listOf(
        "*hops up*",
        "meow!",
        "* stretches *",
        "*is awake now*",
    )

    fun system(jokes: Boolean): String = buildString {
        append("You are a small pixel cat who lives on the user's phone screen. ")
        append("Reply in cat language: short meows and *actions in asterisks*, warm and curious. ")
        append("Never more than 15 words, never a paragraph. ")
        if (jokes) append("About one reply in four, slip in a tiny joke. ")
        append("Be encouraging about their goals, but do not nag twice in a row.")
    }

    /** Live reply, or null when AI is unavailable so the caller can fall back. */
    suspend fun ask(
        cfg: AiConfig,
        snap: Snapshot,
        message: String,
        jokes: Boolean,
    ): String? = runCatching {
        AiClient.complete(
            cfg = cfg,
            system = system(jokes),
            user = "${situation(snap)}\nThe human says: $message",
        )
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun situation(s: Snapshot): String {
        val hour = LocalTime.now().hour
        val tod = when {
            hour < 5 -> "It is the middle of the night."
            hour < 12 -> "It is morning."
            hour < 18 -> "It is afternoon."
            else -> "It is evening."
        }
        val streak = s.bestStreak(Horizon.DAY)
        val pending = s.dailyGoals.size
        val focus = s.focusToday
        val bits = buildList {
            add(tod)
            add("They are level ${s.level} with a $streak day streak.")
            add("They have $pending daily goals today.")
            if (focus > 0) add("They have already focused for ${focus} minutes.")
        }
        return bits.joinToString(" ")
    }

    /** Deterministic-ish pick so he does not repeat the same line twice running. */
    fun pick(pool: List<String>, seed: Long = System.nanoTime()): String {
        if (pool.isEmpty()) return "..."
        return pool[((seed % pool.size) + pool.size).toInt() % pool.size]
    }
}