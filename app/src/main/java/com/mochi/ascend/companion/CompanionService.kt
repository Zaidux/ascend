package com.mochi.ascend.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.ui.graphics.toArgb
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.mochi.ascend.R
import com.mochi.ascend.data.Horizon
import com.mochi.ascend.data.Snapshot
import com.mochi.ascend.data.Store
import com.mochi.ascend.data.WatchLevel
import com.mochi.ascend.ui.themeById
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * The on-screen companion.
 *
 * Draws a single small walkable window. Only the cat's own pixels are
 * touchable, so it never blocks the app underneath.
 */
class CompanionService : Service() {

    companion object {
        const val ACTION_START = "dev.ascend.companion.START"
        const val ACTION_STOP = "dev.ascend.companion.STOP"
        const val EXTRA_SAY = "say"
        const val CHANNEL = "ascend.companion"

        @Volatile
        var running: Boolean = false
            private set

        fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        fun start(ctx: Context) {
            if (!canOverlay(ctx)) return
            ctx.startForegroundService(Intent(ctx, CompanionService::class.java).apply { action = ACTION_START })
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, CompanionService::class.java).apply { action = ACTION_STOP })
        }

        /** Nudge him to say something, without disturbing the user. */
        fun say(ctx: Context, line: String) {
            if (!running) return
            runCatching {
                ctx.startService(
                    Intent(ctx, CompanionService::class.java).apply {
                        action = ACTION_START
                        putExtra(EXTRA_SAY, line)
                    }
                )
            }
        }
    }

    private lateinit var wm: WindowManager
    private var catView: CatView? = null
    private var catParams: WindowManager.LayoutParams? = null
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var talker: Talker? = null
    private var recogniser: SpeechRecognizer? = null

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var x = 0f
    private var direction = 1f
    private var lastFrame = 0L
    private var lastNagAt = 0L

    private val density get() = resources.displayMetrics.density
    private val catSize get() = (104 * density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
        }

        if (!canOverlay(this)) {
            shutdown()
            return START_NOT_STICKY
        }

        if (!running) bootstrap()

        intent?.getStringExtra(EXTRA_SAY)?.let { line ->
            handler.postDelayed({ speak(line) }, 400)
        }
        return START_STICKY
    }

    // ------------------------------------------------------------------ setup

    private fun bootstrap() {
        running = true
        startForegroundCompat()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        talker = Talker(this)
        showCat()
        startWalking()
        startWatching()
    }

    private fun startForegroundCompat() {
        ensureChannel()
        val open = android.app.PendingIntent.getActivity(
            this,
            11,
            Intent(this, com.mochi.ascend.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = android.app.PendingIntent.getService(
            this,
            12,
            Intent(this, CompanionService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("${catName()} is on your screen")
            .setContentText("Tap to pet. Hold to talk.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(R.drawable.ic_notification, "Hide", stop).build())
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(7, n)
        }
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "Companion", NotificationManager.IMPORTANCE_MIN)
        )
    }

    private fun catName(): String = Store.data.value.settings.catName.ifBlank { "Mochi" }

    private fun showCat() {
        val view = CatView(this) { Store.data.value.settings.themeId }
        view.listener = object : CatView.Listener {
            override fun onPet() = pet()
            override fun onTalk() = openBubble()
        }

        val params = WindowManager.LayoutParams(
            catSize,
            catSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 0
            y = 0
        }

        catView = view
        catParams = params
        runCatching { wm.addView(view, params) }
        speak(Meow.pick(Meow.greeting))
    }

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.width
        }

    // --------------------------------------------------------------- walking

    private val walker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dt = if (lastFrame == 0L) 0f else (now - lastFrame) / 1_000_000_000f
            lastFrame = now

            val limit = (screenWidth() - catSize).toFloat().coerceAtLeast(0f)
            val speed = 42f * density
            x += direction * speed * dt

            when {
                x >= limit -> {
                    x = limit
                    direction = -1f
                }
                x <= 0f -> {
                    x = 0f
                    direction = 1f
                }
            }
            catParams?.x = x.toInt()
            runCatching { catView?.let { wm.updateViewLayout(it, catParams!!) } }

            handler.postDelayed(this, 16)
        }
    }

    private fun startWalking() {
        lastFrame = 0L
        handler.post(walker)
    }

    // ------------------------------------------------------------- watching

    private val watcher = object : Runnable {
        override fun run() {
            if (!running) return
            val settings = Store.data.value.settings
            when (settings.companion.watch) {
                WatchLevel.OFF -> Unit

                WatchLevel.IDLE -> nagOnIdle()

                WatchLevel.APP -> {
                    ForegroundWatcher.refresh(this@CompanionService)
                    nagOnIdle()
                    nagIfDrifting()
                }

                WatchLevel.SCREEN -> {
                    ForegroundWatcher.refresh(this@CompanionService)
                    nagOnIdle()
                    nagIfDrifting()
                    nagOnScreenText()
                }
            }
            handler.postDelayed(this, 20_000)
        }
    }

    private fun startWatching() {
        handler.postDelayed(watcher, 6_000)
    }

    /** "You said two hours a day and it is lunchtime." */
    private fun nagOnIdle() {
        val data = Store.data.value
        if (!data.settings.companion.enabled) return
        val snap = Snapshot(data)
        val today = LocalDate.now()
        if (snap.dailyGoals.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastNagAt < 45 * 60_000L) return

        val capacity = data.settings.capacityMinutes
        val hour = java.time.LocalTime.now().hour
        // Only nag once it is reasonable to have started.
        if (hour < 11) return

        val doneToday = snap.dailyGoals.count {
            snap.checkIn(it.id, com.mochi.ascend.data.Dates.dayKey(today))?.status ==
                com.mochi.ascend.data.Status.DONE
        }
        if (doneToday == 0 && snap.focusToday < capacity) {
            lastNagAt = now
            speak(Meow.pick(Meow.nag))
            catView?.mood = com.mochi.ascend.data.Mood.SAD
        }
    }

    /** He can see which app is open, so he can comment on it. */
    private fun nagIfDrifting() {
        val data = Store.data.value
        if (!data.settings.companion.enabled) return
        if (data.settings.capacityMinutes <= 0) return
        if (ForegroundWatcher.lastCheckedAt == 0L) return
        // Only within working hours, and only while something is actually due.
        val hour = java.time.LocalTime.now().hour
        if (hour < 9 || hour > 22) return

        val pkg = ForegroundWatcher.lastPackage ?: return
        if (pkg == packageName) return
        val snap = Snapshot(data)
        if (snap.dailyGoals.isEmpty()) return
        if (snap.focusToday >= snap.level) return

        val now = System.currentTimeMillis()
        if (now - lastNagAt < 45 * 60_000L) return
        lastNagAt = now
        val app = friendly(pkg)
        speak("*looks at $app* mrow. goals?")
    }

    /** The deepest tier: he has actually seen what is on screen. */
    private fun nagOnScreenText() {
        if (!WatchAccessibilityService.connected) return
        val title = WatchAccessibilityService.lastTitle
        if (title.length < 4) return
        val now = System.currentTimeMillis()
        if (now - lastNagAt < 60 * 60_000L) return

        if (Snapshot(Store.data.value).dailyGoals.isEmpty()) return
        lastNagAt = now
        speak("*squints at the screen* ${Meow.pick(listOf("mrow?", "that looks fun", "*is distracted*"))}")
    }

    private fun friendly(pkg: String): String =
        pkg.substringAfterLast('.').replace('_', ' ').lowercase(Locale.ROOT)

    // ------------------------------------------------------------- talking

    private fun pet() {
        catView?.cheer()
        val data = Store.data.value
        if (data.settings.companion.petGivesXp && data.settings.companion.enabled) {
            speak(Meow.pick(Meow.pet))
        }
    }

    private fun speak(line: String) {
        if (line.isBlank()) return
        if (Store.data.value.settings.companion.speak) {
            talker?.speak(line)
        }
    }

    private fun openBubble() {
        if (bubble != null) return
        val data = Store.data.value
        val accent = themeById(data.settings.themeId).catAccent

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (data.settings.dark) 0xFF171D25.toInt() else 0xFFFFFFFF.toInt())
                setStroke(dp(1), accent.copy(alpha = 0.35f).toArgb())
            }
        }

        val reply = TextView(this).apply {
            text = "${catName()} looks at you expectantly."
            setTextColor(if (data.settings.dark) 0xFFE8EDF2.toInt() else 0xFF141A21.toInt())
            textSize = 14f
            maxLines = 4
        }
        root.addView(
            reply,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val input = EditText(this).apply {
            hint = "say something…"
            setSingleLine()
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val mic = TextView(this).apply {
            text = if (data.settings.companion.voiceInput) "\uD83C\uDFA4" else ""
            textSize = 18f
            setPadding(dp(10), dp(6), dp(10), dp(6))
            if (data.settings.companion.voiceInput) setOnClickListener { dictate(input) }
        }

        val send = TextView(this).apply {
            text = "send"
            textSize = 13f
            setTextColor(accent.toArgb())
            setPadding(dp(10), dp(6), dp(4), dp(6))
            setOnClickListener { submit(input.text?.toString().orEmpty(), reply) }
        }

        row.addView(input)
        row.addView(mic)
        row.addView(send)
        root.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        bubble = root
        bubbleParams = params
        runCatching { wm.addView(root, params) }
        positionBubble()

        input.postDelayed({
            input.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 220)

        handler.postDelayed({ closeBubble() }, 45_000)
    }

    private fun submit(text: String, reply: TextView) {
        val data = Store.data.value
        val snap = Snapshot(data)
        if (text.isBlank()) {
            reply.text = "*tilts head*"
            return
        }
        reply.text = "…"

        scope.launch {
            val ai = data.settings.ai
            val usable = ai.apiKey.isNotBlank() && ai.model.isNotBlank()
            val line = if (usable) {
                Meow.ask(ai, snap, text, data.settings.companion.jokes)
            } else {
                null
            } ?: fallback(snap, text)

            reply.text = line
            speak(line)
        }
    }

    private fun fallback(snap: Snapshot, said: String): String = when {
        said.contains("?", ignoreCase = true) -> "mrow! *looks at your goals*"
        said.contains("work", ignoreCase = true) ||
            said.contains("study", ignoreCase = true) -> "good. mrrp."
        said.contains("tired", ignoreCase = true) ||
            said.contains("hard", ignoreCase = true) -> "*sits next to you* prrr."
        said.contains("love", ignoreCase = true) ||
            said.contains("pet", ignoreCase = true) -> "*purrs loudly*"
        else -> Meow.pick(Meow.idle)
    }

    private fun dictate(input: EditText) {
        val sr = recogniser ?: runCatching {
            SpeechRecognizer.createSpeechRecognizer(this).also { recogniser = it }
        }.getOrNull() ?: return
        runCatching {
            sr.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) {
                        input.setText(text)
                        input.setSelection(text.length)
                    }
                }

                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        }
    }

    private fun closeBubble() {
        bubble?.let { view ->
            runCatching { wm.removeViewImmediate(view) }
        }
        bubble = null
        bubbleParams = null
        runCatching { recogniser?.stopListening() }
    }

    private fun positionBubble() {
        val params = bubbleParams ?: return
        val view = bubble ?: return
        val width = view.width.takeIf { it > 0 } ?: dp(280)
        val maxX = (screenWidth() - width - dp(16)).coerceAtLeast(0)
        params.x = (x - width / 2f).coerceIn(0f, maxX.toFloat()).toInt()
        params.y = -dp(8)
        runCatching { wm.updateViewLayout(view, params) }
    }

    // -------------------------------------------------------------- teardown

    private fun shutdown() {
        running = false
        handler.removeCallbacksAndMessages(null)
        closeBubble()
        catView?.let { runCatching { wm.removeViewImmediate(it) } }
        catView = null
        catParams = null
        talker?.shutdown()
        talker = null
        runCatching { recogniser?.destroy() }
        recogniser = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * density).toInt()
}
