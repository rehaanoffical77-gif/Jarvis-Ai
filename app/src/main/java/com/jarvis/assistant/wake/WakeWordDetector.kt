package com.jarvis.assistant.wake

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Always-on, local "is someone talking to JARVIS?" gate.
 *
 * This does NOT talk to Gemini at all — it runs Android's own on-device
 * SpeechRecognizer in a tight restart loop, only ever checking short
 * utterances for the word "Jarvis" (optionally preceded by "hi/hello/hey").
 * Nothing leaves the phone and nothing is forwarded to the AI until that
 * word is heard, which is what stops JARVIS from responding to nearby
 * strangers' conversations in public.
 *
 * Lifecycle contract:
 *  - Call [start] only while the conversational mic (AudioEngine's
 *    AudioRecord) is NOT recording — the two cannot hold the mic at once.
 *  - Call [stop] before AudioEngine starts recording (JarvisVoiceService
 *    handles this handoff).
 *  - All calls must happen on the main thread; SpeechRecognizer requires it.
 */
class WakeWordDetector(private val context: Context) {

    companion object {
        private const val TAG = "WakeWordDetector"

        // "Jarvis" is an English name, so wake-word spotting is pinned to
        // English regardless of whatever language the conversation itself
        // uses (e.g. Hinglish) — this makes detection far more reliable.
        private const val WAKE_LANGUAGE = "en-IN"
        private val WAKE_PHRASES = listOf("jarvis", "javis", "jarvis's", "jarvish")
        private const val RESTART_DELAY_MS = 300L
    }

    /** Fired on the main thread the moment a wake phrase is heard. */
    var onWakeWordDetected: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    /** True while we *want* to be listening; false once stop() is called or a wake word fires. */
    private var wantsToListen = false

    fun start() {
        if (wantsToListen) return
        wantsToListen = true
        mainHandler.post { beginListening() }
    }

    fun stop() {
        if (!wantsToListen && recognizer == null) return
        wantsToListen = false
        mainHandler.post {
            recognizer?.setRecognitionListener(null)
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun beginListening() {
        if (!wantsToListen) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition isn't available on this device — wake word disabled.")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    if (!containsWakeWord(results)) restartIfNeeded()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Don't restart here — either a wake word fired (handled
                    // inside containsWakeWord), or we just keep waiting for
                    // onResults/onError on this same recognition pass.
                    containsWakeWord(partialResults)
                }

                override fun onError(error: Int) {
                    // Common/expected: ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT while
                    // it's quiet. Just restart and keep listening.
                    restartIfNeeded()
                }

                override fun onEndOfSpeech() {}
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, WAKE_LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wake-word listening: ${e.message}")
            restartIfNeeded()
        }
    }

    /** Returns true (and fires the callback, stopping further restarts) if a wake phrase is present. */
    private fun containsWakeWord(bundle: Bundle?): Boolean {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        for (candidate in matches) {
            val normalized = candidate.lowercase().trim()
            if (WAKE_PHRASES.any { normalized.contains(it) }) {
                Log.d(TAG, "Wake word heard in: \"$candidate\"")
                wantsToListen = false
                recognizer?.setRecognitionListener(null)
                recognizer?.cancel()
                onWakeWordDetected?.invoke()
                return true
            }
        }
        return false
    }

    private fun restartIfNeeded() {
        if (!wantsToListen) return
        mainHandler.postDelayed({ beginListening() }, RESTART_DELAY_MS)
    }
}
