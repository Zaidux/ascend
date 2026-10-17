package com.mochi.ascend.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.mochi.ascend.MainActivity
import com.mochi.ascend.R
import com.mochi.ascend.data.Actions
import com.mochi.ascend.data.FocusSession
import com.mochi.ascend.data.Store
import com.mochi.ascend.data.newId
import java.util.Timer
import java.util.TimerTask

/**
 * A plain countdown focus timer that keeps running while you read, browse or
 * code. On stop it writes a FocusSession, which feeds XP, the heatmap and stats.
 */
class FocusService : Service() {

    companion object {
        const val ACTION_START = "dev.ascend.focus.START"
        const val ACTION_STOP = "dev.ascend.focus.STOP"
        const val CHANNEL = "ascend.focus"

        const val EXTRA_GOAL = "goalId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MINUTES = "minutes"

        @Volatile
        var running: Boolean = false

        @Volatile
        var goalId: String? = null

        @Volatile
        var title: String = ""

        @Volatile
        var endsAt: Long = 0L

        /** Seconds remaining, for the UI ticker. */
        fun remaining(): Int {
            if (!running) return 0
            val ms = endsAt - System.currentTimeMillis()
            return if (ms <= 0) 0 else ((ms + 999) / 1000).toInt()
        }

        fun start(context: Context, goalId: String, title: String, minutes: Int) {
            val intent = Intent(context, FocusService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GOAL, goalId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MINUTES, minutes)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private var timer: Timer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_GOAL).orEmpty()
                val label = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 25).coerceIn(1, 480)
                begin(id, label, minutes)
            }

            ACTION_STOP -> {
                finish()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun begin(id: String, label: String, minutes: Int) {
        startedAt = System.currentTimeMillis()
        goalId = id
        title = label
        endsAt = startedAt + minutes * 60_000L
        running = true

        startForegroundCompat(notification(minutes))

        timer?.cancel()
        timer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (System.currentTimeMillis() >= endsAt) {
                        finish()
                        stopSelf()
                    } else {
                        notifyChanged()
                    }
                }
            }, 0, 30_000)
        }
    }

    private fun finish() {
        timer?.cancel()
        timer = null
        val id = goalId ?: return
        val planned = ((endsAt - startedAt) / 60_000L).toInt().coerceAtLeast(0)
        val elapsed = ((System.currentTimeMillis() - startedAt) / 60_000L).toInt()
        val minutes = minOf(planned, elapsed).coerceAtLeast(1)
        val session = FocusSession(newId(), id, startedAt, minutes)
        Store.update { it.copy(focusSessions = it.focusSessions + session) }
        running = false
        goalId = null
    }

    private var startedAt: Long = 0L

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ notification

    private fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "Focus timer", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(minutes: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Focusing on ${title.ifBlank { "your goal" }}")
            .setContentText("${minutes} min")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun notifyChanged() {
        val minutes = ((endsAt - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1)
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(42, notification(minutes))
        } catch (_: Throwable) {
            // Notification may be blocked; the timer still runs.
        }
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(42, n)
        }
    }
}