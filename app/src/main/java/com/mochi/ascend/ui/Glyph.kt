package com.mochi.ascend.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icons drawn as plain geometry instead of pulling in an icon pack.
 * Coordinates are normalised 0..1 and scaled to the requested size.
 */
enum class Glyph {
    CHECK, PLUS, CLOSE, CHEVRON_RIGHT, CHEVRON_LEFT, CHEVRON_DOWN,
    TODAY, LADDER, CHART, BOOK, GEAR, SPARK, FLAME, TRASH, PENCIL, BELL, TARGET,
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val strokeWidth = this.size.minDimension * 0.11f
        drawGlyph(glyph, tint, this.size.width, this.size.height, strokeWidth)
    }
}

private fun DrawScope.drawGlyph(g: Glyph, tint: Color, w: Float, h: Float, sw: Float) {
    fun x(v: Float) = v * w
    fun y(v: Float) = v * h
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = sw) =
        drawLine(tint, Offset(x(x1), y(y1)), Offset(x(x2), y(y2)), width, StrokeCap.Round)

    fun poly(vararg pts: Float) {
        val path = Path()
        var i = 0
        while (i + 1 < pts.size) {
            val px = x(pts[i])
            val py = y(pts[i + 1])
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            i += 2
        }
        drawPath(path, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }

    val stroke = Stroke(width = sw, cap = StrokeCap.Round)

    when (g) {
        Glyph.CHECK -> {
            line(0.20f, 0.55f, 0.42f, 0.78f)
            line(0.42f, 0.78f, 0.80f, 0.26f)
        }
        Glyph.PLUS -> {
            line(0.5f, 0.20f, 0.5f, 0.80f)
            line(0.20f, 0.5f, 0.80f, 0.5f)
        }
        Glyph.CLOSE -> {
            line(0.26f, 0.26f, 0.74f, 0.74f)
            line(0.74f, 0.26f, 0.26f, 0.74f)
        }
        Glyph.CHEVRON_RIGHT -> poly(0.40f, 0.24f, 0.66f, 0.5f, 0.40f, 0.76f)
        Glyph.CHEVRON_LEFT -> poly(0.60f, 0.24f, 0.34f, 0.5f, 0.60f, 0.76f)
        Glyph.CHEVRON_DOWN -> poly(0.24f, 0.40f, 0.5f, 0.66f, 0.76f, 0.40f)

        Glyph.TODAY -> {
            drawCircle(tint, radius = w * 0.20f, center = Offset(x(0.5f), y(0.5f)), style = stroke)
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                val cx = x(0.5f)
                val cy = y(0.5f)
                val r1 = w * 0.30f
                val r2 = w * 0.42f
                drawLine(
                    tint,
                    Offset(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat()),
                    Offset(cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat()),
                    sw * 0.8f,
                    StrokeCap.Round,
                )
            }
        }

        Glyph.LADDER -> {
            line(0.34f, 0.12f, 0.34f, 0.88f)
            line(0.66f, 0.12f, 0.66f, 0.88f)
            line(0.34f, 0.33f, 0.66f, 0.33f, sw * 0.85f)
            line(0.34f, 0.55f, 0.66f, 0.55f, sw * 0.85f)
            line(0.34f, 0.77f, 0.66f, 0.77f, sw * 0.85f)
        }

        Glyph.CHART -> {
            drawRect(tint, Offset(x(0.18f), y(0.56f)), Size(w * 0.16f, h * 0.30f))
            drawRect(tint, Offset(x(0.42f), y(0.36f)), Size(w * 0.16f, h * 0.50f))
            drawRect(tint, Offset(x(0.66f), y(0.20f)), Size(w * 0.16f, h * 0.66f))
        }

        Glyph.BOOK -> {
            drawRect(tint, Offset(x(0.18f), y(0.18f)), Size(w * 0.64f, h * 0.64f), style = stroke)
            line(0.5f, 0.18f, 0.5f, 0.82f, sw * 0.85f)
        }

        Glyph.GEAR -> {
            drawCircle(tint, radius = w * 0.22f, center = Offset(x(0.5f), y(0.5f)), style = stroke)
            line(0.5f, 0.08f, 0.5f, 0.20f)
            line(0.5f, 0.80f, 0.5f, 0.92f)
            line(0.08f, 0.5f, 0.20f, 0.5f)
            line(0.80f, 0.5f, 0.92f, 0.5f)
        }

        Glyph.SPARK -> {
            poly(
                0.50f, 0.12f, 0.61f, 0.39f, 0.88f, 0.50f, 0.61f, 0.61f,
                0.50f, 0.88f, 0.39f, 0.61f, 0.12f, 0.50f, 0.39f, 0.39f, 0.50f, 0.12f,
            )
        }

        Glyph.FLAME -> {
            val path = Path()
            path.moveTo(x(0.50f), y(0.10f))
            path.cubicTo(x(0.78f), y(0.34f), x(0.84f), y(0.52f), x(0.78f), y(0.66f))
            path.cubicTo(x(0.72f), y(0.82f), x(0.60f), y(0.90f), x(0.50f), y(0.90f))
            path.cubicTo(x(0.40f), y(0.90f), x(0.28f), y(0.82f), x(0.22f), y(0.66f))
            path.cubicTo(x(0.16f), y(0.52f), x(0.28f), y(0.40f), x(0.38f), y(0.30f))
            path.cubicTo(x(0.38f), y(0.44f), x(0.44f), y(0.50f), x(0.50f), y(0.42f))
            path.cubicTo(x(0.52f), y(0.34f), x(0.50f), y(0.22f), x(0.50f), y(0.10f))
            drawPath(path, tint, style = stroke)
        }

        Glyph.TRASH -> {
            line(0.20f, 0.28f, 0.80f, 0.28f)
            poly(0.30f, 0.28f, 0.30f, 0.84f, 0.70f, 0.84f, 0.70f, 0.28f)
            line(0.40f, 0.18f, 0.60f, 0.18f)
        }

        Glyph.PENCIL -> {
            line(0.24f, 0.76f, 0.72f, 0.28f)
            line(0.64f, 0.20f, 0.80f, 0.36f)
            line(0.16f, 0.84f, 0.30f, 0.70f)
        }

        Glyph.BELL -> {
            val path = Path()
            path.moveTo(x(0.26f), y(0.68f))
            path.lineTo(x(0.30f), y(0.52f))
            path.cubicTo(x(0.30f), y(0.28f), x(0.70f), y(0.28f), x(0.70f), y(0.52f))
            path.lineTo(x(0.74f), y(0.68f))
            drawPath(path, tint, style = stroke)
            line(0.20f, 0.70f, 0.80f, 0.70f)
            line(0.44f, 0.80f, 0.56f, 0.80f, sw * 0.85f)
        }

        Glyph.TARGET -> {
            drawCircle(tint, radius = w * 0.34f, center = Offset(x(0.5f), y(0.5f)), style = stroke)
            drawCircle(tint, radius = w * 0.14f, center = Offset(x(0.5f), y(0.5f)))
        }
    }
}
