package com.mochi.ascend.art

import com.mochi.ascend.data.Mood

/**
 * The sprite's artwork, defined once as a pixel grid so the Compose sprite and
 * the widget bitmap stay identical.
 *
 * Grid is 16x16. The head is a 12x12 stamp placed at (2,3).
 */
object CatArt {

    const val COLS = 16
    const val ROWS = 16

    enum class Role { BODY, DARK, PINK, BLUE, WHITE, GOLD }

    data class Cell(val c: Int, val r: Int, val w: Int = 1, val h: Int = 1, val role: Role = Role.BODY)

    private const val HEAD_C = 2
    private const val HEAD_R = 3

    private val HEAD = listOf(
        "..BB......BB",
        "..BB......BB",
        ".BBBBBBBBBB.",
        "BBBBBBBBBBBB",
        "BBBBBBBBBBBB",
        "BBBBBBBBBBBB",
        "BBBBBBBBBBBB",
        "BBBBBPPBBBBB",
        ".BBBBBBBBBB.",
        ".BBBBBBBBBB.",
        "..BBBBBBBB..",
        "...BBBBBB...",
    )

    init {
        require(HEAD.size == 12) { "head must be 12 rows" }
        require(HEAD.all { it.length == 12 }) { "head rows must be 12 wide" }
    }

    /** Head-local to canvas coordinates. */
    private fun hc(c: Int) = HEAD_C + c
    private fun hr(r: Int) = HEAD_R + r

    fun cells(mood: Mood): List<Cell> {
        val out = mutableListOf<Cell>()

        HEAD.forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                when (ch) {
                    'B' -> out += Cell(HEAD_C + col, HEAD_R + row, role = Role.BODY)
                    'P' -> out += Cell(HEAD_C + col, HEAD_R + row, role = Role.PINK)
                }
            }
        }

        // Eyes live on head-row 5.
        when (mood) {
            Mood.IDLE -> {
                out += Cell(hc(3), hr(5), role = Role.DARK)
                out += Cell(hc(8), hr(5), role = Role.DARK)
            }
            Mood.HAPPY -> {
                out += Cell(hc(3), hr(5), w = 2, role = Role.DARK)
                out += Cell(hc(7), hr(5), w = 2, role = Role.DARK)
                out += Cell(hc(5), hr(8), w = 3, role = Role.DARK)
            }
            Mood.EXCITED -> {
                out += Cell(hc(3), hr(5), role = Role.DARK)
                out += Cell(hc(8), hr(5), role = Role.DARK)
                out += Cell(hc(5), hr(8), w = 3, role = Role.DARK)
                // sparkles
                out += Cell(1, 1, role = Role.WHITE)
                out += Cell(14, 2, role = Role.GOLD)
                out += Cell(0, 5, role = Role.GOLD)
                out += Cell(15, 6, role = Role.WHITE)
            }
            Mood.SAD -> {
                out += Cell(hc(3), hr(5), role = Role.DARK)
                out += Cell(hc(8), hr(5), role = Role.DARK)
                out += Cell(hc(5), hr(9), w = 3, role = Role.DARK)
                out += Cell(hc(3), hr(6), role = Role.BLUE) // tear
            }
            Mood.SLEEPY -> {
                out += Cell(hc(3), hr(5), w = 2, role = Role.DARK)
                out += Cell(hc(7), hr(5), w = 2, role = Role.DARK)
                // little z's
                out += Cell(12, 2, role = Role.WHITE)
                out += Cell(13, 3, role = Role.WHITE)
                out += Cell(14, 4, role = Role.WHITE)
            }
        }
        return out
    }

    /**
     * Role colours. [accent] drives the body and sparkle so the sprite belongs
     * to whatever theme is active.
     */
    fun argb(role: Role, accent: Int = 0xFFF2C14E.toInt()): Int = when (role) {
        Role.BODY -> accent
        Role.GOLD -> lighten(accent)
        Role.DARK -> 0xFF1A1F26.toInt()
        Role.PINK -> 0xFFE8909E.toInt()
        Role.BLUE -> 0xFF7CB7FF.toInt()
        Role.WHITE -> 0xFFEAF2FA.toInt()
    }

    /** Blend [accent] toward white so sparkles stay visible on any accent. */
    private fun lighten(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val mix = 0.45f
        fun ch(c: Int) = (c + (255 - c) * mix).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** Short status line the widget can show next to the sprite. */
    fun blurb(mood: Mood): String = when (mood) {
        Mood.EXCITED -> "On a roll"
        Mood.HAPPY -> "Good work"
        Mood.SAD -> "Missed one"
        Mood.SLEEPY -> "Waiting for you"
        Mood.IDLE -> "Ready when you are"
    }
}
