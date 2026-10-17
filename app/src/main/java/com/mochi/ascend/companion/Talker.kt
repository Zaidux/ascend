package com.mochi.ascend.companion

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Mochi's voice, via Android's built-in text-to-speech.
 *
 * TTS rather than audio files: it means he can say anything the AI writes
 * without shipping a single sound file, and it costs nothing in APK size.
 */
class Talker(context: Context) {

    @Volatile
    var ready: Boolean = false
        private set

    var onSpeaking: ((Boolean) -> Unit)? = null

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.getDefault()
            // Slightly higher and slower than default; reads as a small cat.
            tts?.setPitch(1.35f)
            tts?.setSpeechRate(1.02f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeaking?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeaking?.invoke(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeaking?.invoke(false)
                }
            })
        }
    }

    fun speak(text: String) {
        val engine = tts ?: return
        if (!ready || text.isBlank()) return
        runCatching {
            engine.speak(
                text.replace("*", "").trim(),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "meow-${System.nanoTime()}",
            )
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
        onSpeaking?.invoke(false)
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
    }
}