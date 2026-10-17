package com.mochi.ascend.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.mochi.ascend.art.CatArt
import com.mochi.ascend.data.Mood
import com.mochi.ascend.ui.themeById

/** Draws the same pixel cat the app uses, into a bitmap the widget can show. */
object CatBitmap {

    fun render(mood: Mood, sizePx: Int, themeId: String = "ember"): Bitmap {
        val safe = sizePx.coerceIn(16, 512)
        val bitmap = Bitmap.createBitmap(safe, safe, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cell = safe.toFloat() / CatArt.COLS
        val accent = themeById(themeId).catAccent.toArgb()

        CatArt.cells(mood).forEach { c ->
            paint.color = CatArt.argb(c.role, accent)
            canvas.drawRect(
                c.c * cell,
                c.r * cell,
                (c.c + c.w) * cell,
                (c.r + c.h) * cell,
                paint,
            )
        }
        return bitmap
    }
}
