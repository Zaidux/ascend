package com.mochi.ascend.companion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.ui.graphics.toArgb
import com.mochi.ascend.art.CatArt
import com.mochi.ascend.data.Mood
import com.mochi.ascend.ui.themeById
import kotlin.math.abs
import kotlin.math.sin

/**
 * The walk-about cat.
 *
 * A plain View rather than Compose, because this redraws every frame and owns
 * its own touch handling — a Recomposer per frame would be wasteful.
 */
class CatView(
    context: Context,
    private val themeIdProvider: () -> String,
) : View(context), Choreographer.FrameCallback {

    interface Listener {
        fun onPet()
        fun onTalk()
    }

    var listener: Listener? = null

    var mood: Mood = Mood.IDLE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Direction of travel: 1 right, -1 left, 0 parked. */
    var walking: Boolean = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellSize = resources.displayMetrics.density * 4f
    private var accent: Int = 0

    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var hearts: MutableList<Heart> = ArrayList()

    private class Heart(
        var x: Float,
        var y: Float,
        var life: Float,
        val drift: Float,
    )

    private var running = false
    private var startNanos = 0L

    init {
        isClickable = true
        isFocusable = true
        accent = themeById(themeIdProvider()).catAccent.toArgb()
    }

    /** Called when the theme changes underneath us. */
    fun refreshTheme() {
        accent = themeById(themeIdProvider()).catAccent.toArgb()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!running) {
            running = true
            startNanos = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (startNanos == 0L) startNanos = frameTimeNanos
        val t = (frameTimeNanos - startNanos) / 1_000_000_000f

        // Walk cycle: a two-frame hop plus a small tail bob.
        if (walking) {
            val cycle = (t * 4f) % 2f
            hop = if (cycle < 1f) -1f else 0f
        } else {
            hop = (sin(t * 2.2f) * 0.35f)
        }

        // Hearts drift up and fade.
        if (hearts.isNotEmpty()) {
            val alive = ArrayList<Heart>()
            hearts.forEach { h ->
                h.life += 0.012f
                h.y -= 0.55f
                h.x += h.drift * 0.12f
                if (h.life < 1f) alive.add(h)
            }
            hearts = alive
        }

        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private var hop = 0f

    fun cheer() {
        mood = Mood.HAPPY
        repeat(3) { i ->
            hearts.add(
                Heart(
                    x = width * (0.3f + i * 0.2f),
                    y = height * 0.35f,
                    life = 0f,
                    drift = if (i % 2 == 0) 1f else -1f,
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cell = cellSize
        val originX = (width - cell * CatArt.COLS) / 2f
        val originY = height - cell * CatArt.ROWS - hop * cell * 0.5f

        val body = themeById(themeIdProvider()).catAccent.toArgb()
        CatArt.cells(mood).forEach { c ->
            paint.color = CatArt.argb(c.role, body)
            canvas.drawRect(
                originX + c.c * cell,
                originY + c.r * cell + hop * cell * 0.5f,
                originX + (c.c + c.w) * cell,
                originY + (c.r + c.h) * cell + hop * cell * 0.5f,
                paint,
            )
        }

        hearts.forEach { h ->
            paint.color = CatArt.argb(CatArt.Role.PINK, body)
            val alpha = (1f - h.life).coerceIn(0f, 1f)
            paint.alpha = (alpha * 255).toInt()
            val s = cell * (0.9f + h.life * 0.7f)
            canvas.drawRoundRect(
                RectF(originX + h.x, originY + h.y, originX + h.x + s, originY + h.y + s),
                s * 0.3f,
                s * 0.3f,
                paint,
            )
            paint.alpha = 255
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longPressed = false
                postDelayed(longPressRunnable, 480)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                    removeCallbacks(longPressRunnable)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (!longPressed) listener?.onPet()
                longPressed = false
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private val longPressRunnable = Runnable {
        longPressed = true
        listener?.onTalk()
    }
}