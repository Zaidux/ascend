package com.mochi.ascend.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mochi.ascend.art.CatArt
import com.mochi.ascend.data.Mood

/**
 * The sprite. It bobs gently, blinks on a slow cycle, and bobs fastest when
 * you're on a streak. Its colours come from the active theme.
 */
@Composable
fun CatSprite(
    mood: Mood,
    size: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    val accent = catAccent().toArgb()

    val transition = rememberInfiniteTransition(label = "cat")

    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == Mood.EXCITED) 480 else 1600,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    val blinkPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
        ),
        label = "blink",
    )
    val blinking = blinkPhase < 0.05f

    Canvas(modifier.size(size)) {
        val cell = this.size.width / CatArt.COLS
        val amplitude = when (mood) {
            Mood.EXCITED -> 0.9f
            Mood.HAPPY -> 0.5f
            Mood.SLEEPY -> 0.25f
            else -> 0.4f
        }
        val dy = (bob - 0.5f) * amplitude * cell

        CatArt.cells(mood).forEach { c ->
            val topLeft = Offset(c.c * cell, c.r * cell + dy)
            // Eyes sit on canvas row 8; blink them by drawing a thinner bar.
            val isEye = c.role == CatArt.Role.DARK && c.r == 8 && c.c in 4..10
            val height = if (blinking && isEye) cell * 0.35f else c.h * cell
            val y = if (blinking && isEye) topLeft.y + cell * 0.33f else topLeft.y
            drawRect(
                color = Color(CatArt.argb(c.role, accent)),
                topLeft = Offset(topLeft.x, y),
                size = Size(c.w * cell, height),
            )
        }
    }
}