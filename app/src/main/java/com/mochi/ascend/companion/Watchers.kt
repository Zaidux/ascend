package com.mochi.ascend.companion

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * The "which app is open" tier.
 *
 * Sees the foreground package and nothing else — not what is inside it. Needs
 * the Usage-access toggle, which is a normal, revocable Settings switch.
 */
object ForegroundWatcher {

    @Volatile
    var lastPackage: String? = null
        private set

    @Volatile
    var lastCheckedAt: Long = 0L
        private set

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun refresh(ctx: Context) {
        if (!hasPermission(ctx)) return
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 30_000, now)
        val event = UsageEvents.Event()
        var found: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                found = event.packageName
            }
        }
        lastCheckedAt = now
        if (found != null) lastPackage = found
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}

/**
 * The deepest tier: window titles and on-screen text.
 *
 * This is broad access — it can see anything rendered on screen. It exists
 * because the user asked for a choice, not because it is recommended. It only
 * runs if the user enables the service in Android's accessibility settings.
 */
class WatchAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var lastTitle: String = ""
            private set

        @Volatile
        var lastPackage: String = ""
            private set

        @Volatile
        var connected: Boolean = false
            private set

        fun clear() {
            lastTitle = ""
            lastPackage = ""
        }
    }

    override fun onServiceConnected() {
        connected = true
    }

    override fun onDestroy() {
        connected = false
        clear()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                lastPackage = e.packageName?.toString().orEmpty()
                val title = e.text?.joinToString(" ").orEmpty()
                if (title.isNotBlank()) lastTitle = title
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val text = e.text?.joinToString(" ").orEmpty()
                if (text.isNotBlank()) lastTitle = text
            }
        }
    }

    override fun onInterrupt() {
        // Nothing to interrupt; observation is passive.
    }
}