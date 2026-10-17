package com.mochi.ascend.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mochi.ascend.MainActivity
import com.mochi.ascend.R
import com.mochi.ascend.data.Goal
import com.mochi.ascend.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local-only reminders. No cloud, no push.
 *
 * Inexact alarms are used deliberately: they need no special permission on
 * modern Android and a daily nudge does not need to-the-second precision.
 */
object Reminders {

    const val CHANNEL_ID = "ascend.reminders"
    const val EXTRA_GOAL = "goalId"

    fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Goal reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Nudges for the goals you scheduled" }
        mgr.createNotificationChannel(channel)
    }

    fun rescheduleAll(ctx: Context) {
        val alarm = ctx.getSystemService(AlarmManager::class.java) ?: return
        val goals = Store.data.value.goals.filter { !it.archived }
        goals.forEach { goal ->
            val pending = pending(ctx, goal)
            if (pending != null) alarm.cancel(pending)
        }
        if (!Store.data.value.settings.remindersEnabled) return

        goals.filter { it.reminderAt != null }.forEach { schedule(ctx, it) }
    }

    fun schedule(ctx: Context, goal: Goal) {
        if (!Store.data.value.settings.remindersEnabled) return
        val trigger = nextTrigger(goal) ?: return
        val alarm = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pending = pending(ctx, goal) ?: return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    private fun pending(ctx: Context, goal: Goal): PendingIntent? {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_GOAL, goal.id)
            // Extras differ per goal, so make the filter unique.
            data = android.net.Uri.parse("ascend://goal/${goal.id}")
        }
        return PendingIntent.getBroadcast(
            ctx,
            goal.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Next moment this goal should fire, respecting the chosen weekdays. */
    private fun nextTrigger(goal: Goal): Long? {
        val at = goal.reminderAt ?: return null
        val parts = at.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val now = LocalDateTime.now()
        var day = LocalDate.now()
        repeat(8) {
            val iso = day.dayOfWeek.value // 1 = Monday .. 7 = Sunday
            if (goal.reminderDays.isEmpty() || goal.reminderDays.contains(iso)) {
                val candidate = LocalDateTime.of(day, LocalTime.of(hour, minute))
                if (candidate.isAfter(now)) {
                    return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
            day = day.plusDays(1)
        }
        return null
    }

    fun show(ctx: Context, goal: Goal) {
        ensureChannel(ctx)
        val mgr = NotificationManagerCompat.from(ctx)
        if (!mgr.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            ctx,
            goal.id.hashCode() + 7,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val line = goal.detail.ifBlank { "Small step now beats a perfect plan later." }
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(goal.title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        try {
            mgr.notify(goal.id.hashCode(), notif)
        } catch (_: SecurityException) {
            // Permission revoked between check and post.
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val goalId = intent.getStringExtra(Reminders.EXTRA_GOAL) ?: return
        Store.init(context)
        val goal = Store.data.value.goals.firstOrNull { it.id == goalId && !it.archived } ?: return
        Reminders.show(context, goal)
        // Queue tomorrow's nudge.
        Reminders.schedule(context, goal)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(context)
        Reminders.rescheduleAll(context)
    }
}
