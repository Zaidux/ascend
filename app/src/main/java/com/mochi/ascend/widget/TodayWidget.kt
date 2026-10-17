package com.mochi.ascend.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.mochi.ascend.MainActivity
import com.mochi.ascend.R
import com.mochi.ascend.art.CatArt
import com.mochi.ascend.data.Dates
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Snapshot
import com.mochi.ascend.data.Status
import com.mochi.ascend.data.Store
import java.time.LocalDate

/**
 * "Today" widget: the sprite, plus up to four daily goals you can tick off
 * without opening the app.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    companion object {

        fun refresh(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
                ids.forEach { render(context, manager, it) }
            } catch (_: Throwable) {
                // A widget failure must never break the app.
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            Store.init(context)
            val data = Store.data.value
            val snap = Snapshot(data)
            val today = LocalDate.now()
            val key = Dates.key(Horizon.DAY, today)
            val mood = snap.mood(today)

            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setImageViewBitmap(
                R.id.widget_cat,
                CatBitmap.render(mood, 128, data.settings.themeId),
            )

            val daily = snap.dailyGoals
            val done = daily.count { snap.checkIn(it.id, key)?.status == Status.DONE }

            views.setTextViewText(R.id.widget_title, "Today")
            views.setTextViewText(
                R.id.widget_sub,
                if (daily.isEmpty()) CatArt.blurb(mood) else "${CatArt.blurb(mood)}  ·  $done/${daily.size} done",
            )

            // Tapping the header opens the app.
            val open = PendingIntent.getActivity(
                context,
                99,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_header, open)

            views.removeAllViews(R.id.widget_rows)

            if (daily.isEmpty()) {
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                daily.take(4).forEach { goal ->
                    val checked = snap.checkIn(goal.id, key)?.status == Status.DONE
                    val row = RemoteViews(context.packageName, R.layout.widget_row)
                    row.setTextViewText(
                        R.id.row_text,
                        (if (checked) "✓  " else "○  ") + goal.title,
                    )
                    val tap = PendingIntent.getBroadcast(
                        context,
                        goal.id.hashCode(),
                        Intent(context, WidgetClickReceiver::class.java).putExtra("goalId", goal.id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    row.setOnClickPendingIntent(R.id.row_text, tap)
                    views.addView(R.id.widget_rows, row)
                }

                if (daily.size > 4) {
                    val more = RemoteViews(context.packageName, R.layout.widget_row)
                    more.setTextViewText(R.id.row_text, "  +${daily.size - 4} more today")
                    views.addView(R.id.widget_rows, more)
                }
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}

/** Ticking a goal straight from the widget. */
class WidgetClickReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getStringExtra("goalId") ?: return
        Store.init(context)
        val key = Dates.key(Horizon.DAY, LocalDate.now())
        com.mochi.ascend.data.Actions.toggleCheckIn(goalId, key, Status.DONE)
        TodayWidget.refresh(context)
    }
}
