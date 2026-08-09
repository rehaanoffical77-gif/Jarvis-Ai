package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import com.jarvis.assistant.R
import com.jarvis.assistant.ai.AudioEngine
import com.jarvis.assistant.ai.GeminiLiveClient
import com.jarvis.assistant.ui.main.MainActivity
import com.jarvis.assistant.util.AppLauncher
import com.jarvis.assistant.util.ContactCaller
import android.content.Context
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.jarvis.assistant.youtube.YouTubeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Owns the mic capture + Gemini Live WebSocket for the whole app lifetime,
 * independent of whatever Activity is currently on screen.
 *
 * Why this exists: Android blocks microphone access for apps that are not
 * in the foreground and have no active foreground service. Previously
 * AudioEngine/GeminiLiveClient lived inside MainActivity, so the moment
 * another app (e.g. YouTube, opened via open_app) came to the front, the
 * OS cut mic access and JARVIS effectively went silent/crashed. Running this
 * as a foreground service with type "microphone" keeps the conversation
 * alive in the background.
 *
 * MainActivity binds to this service for UI updates (transcripts,
 * amplitude, speaking state) via [JarvisVoiceListener], but the service
 * itself does not depend on the Activity being bound or visible.
 *
 * --- Wake word ("Jarvis") gating ---
 * The mic is ALWAYS on and ALWAYS streaming to Gemini (no separate local
 * speech recognizer, so no extra mic-open/close cycles and no earcon
 * beeping). Gemini transcribes speech in real time via input transcription;
 * this class buffers each spoken turn's transcript and only lets JARVIS
 * actually speak a reply or run a tool (open an app, call a contact, etc.)
 * if that turn's transcript contains "Jarvis". If it doesn't, the reply is
 * silently dropped and no tool is executed — so "open YouTube" alone does
 * nothing, but "Jarvis, open YouTube" works.
 */
class JarvisVoiceService : Service() {

    companion object {
        private const val CHANNEL_ID = "jarvis_voice_channel"
        private const val NOTIFICATION_ID = 101

        // Common correct + likely-misheard spellings of the wake word, so
        // ASR quirks (accents, Hinglish) don't silently break wake detection.
        private val WAKE_PHRASES = listOf(
            "jarvis", "hi jarvis", "hello jarvis", "hey jarvis", "ok jarvis", "okay jarvis", "listen jarvis",
            "jarviss", "jaarvis", "jarvish", "javis", "hey javis", "hi javis", "hello javis", "service", "travis"
        )
    }

    interface JarvisVoiceListener {
        fun onConnected() {}
        fun onSetupComplete() {}
        fun onDisconnected() {}
        fun onError(msg: String) {}
        fun onInputTranscript(text: String) {}
        fun onOutputTranscript(text: String) {}
        fun onTurnComplete() {}
        fun onAmplitudeChanged(rms: Float) {}
        fun onSpeakingStarted() {}
        fun onSpeakingStopped() {}
        fun onToolCall(name: String, args: JSONObject, callId: String) {}
        /** A turn was heard but ignored because it didn't start with "Jarvis". */
        fun onCommandIgnored() {}
        fun onScreenShareStateChanged(isSharing: Boolean) {}
        fun onCameraVisionStateChanged(isActive: Boolean, isFront: Boolean) {}
        fun onResearchStateChanged(isSearching: Boolean, query: String) {}
        fun onShutdownRequested() {}
    }

    inner class LocalBinder : Binder() {
        fun getService(): JarvisVoiceService = this@JarvisVoiceService
    }

    private val binder = LocalBinder()
    private val toolScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** UI layer (MainActivity) sets this while bound; service works fine without it too. */
    var uiListener: JarvisVoiceListener? = null

    private var geminiLive: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null
    private var screenCaptureEngine: com.jarvis.assistant.vision.ScreenCaptureEngine? = null
    private var cameraVisionEngine: com.jarvis.assistant.vision.CameraVisionEngine? = null
    private var isSessionStarted = false
    private var isUserMuted = false

    private var isAppInForeground = true
    private var isActivatedSession = false
    private var activeSessionTimerJob: Job? = null

    fun setAppForeground(isForeground: Boolean) {
        isAppInForeground = isForeground
        if (isForeground) {
            isActivatedSession = true
            touchSessionActivity()
        } else {
            // When minimizing app to background, reset active session window so background listening
            // starts in strict Wake-Word Standby Mode.
            isActivatedSession = false
            currentTurnHasWakeWord = false
            activeSessionTimerJob?.cancel()
            audioEngine?.clearPlaybackQueue()
            Log.d("JarvisVoiceService", "App backgrounded — entered strict wake phrase standby mode.")
        }
    }

    private fun touchSessionActivity() {
        activeSessionTimerJob?.cancel()
        activeSessionTimerJob = toolScope.launch {
            kotlinx.coroutines.delay(60_000L) // 60 seconds active window after wake phrase in background
            if (!isAppInForeground) {
                isActivatedSession = false
                currentTurnHasWakeWord = false
                audioEngine?.clearPlaybackQueue()
                Log.d("JarvisVoiceService", "Background session active window expired; returning to wake phrase standby.")
            }
        }
    }

    private val currentTurnInputText = StringBuilder()
    private var currentTurnHasWakeWord = false
    private var interruptSentThisTurn = false

    private fun textHasWakeWord(text: String): Boolean {
        val cleanText = text.lowercase().trim()
        if (cleanText.isEmpty()) return false
        val words = cleanText.split("\\s+".toRegex())
        return WAKE_PHRASES.any { cleanText.contains(it) } ||
                words.any { w -> w == "jarvis" || w == "jarviss" || w == "jaarvis" || w == "jarvish" || w == "javis" || w == "service" || w == "travis" }
    }

    /** Resets per-turn bookkeeping, ready for the next utterance. */
    private fun resetTurnState() {
        currentTurnInputText.clear()
        interruptSentThisTurn = false
        // Allow immediate turns ONLY if app is in foreground OR if an active background turn was explicitly triggered via wake phrase
        currentTurnHasWakeWord = isAppInForeground || isActivatedSession
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: Exception) {
                android.util.Log.e("JarvisVoiceService", "startForeground failed", e)
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e2: Exception) {
                    android.util.Log.e("JarvisVoiceService", "startForeground plain failed", e2)
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    /** Restarts the session with updated settings (personality, voice, API key, etc.). */
    fun restartSession(
        apiKey: String,
        modelString: String,
        systemPrompt: String,
        voiceName: String
    ) {
        if (isSessionStarted) {
            resetTurnState()
            geminiLive?.disconnect()
            audioEngine?.release()
            geminiLive = null
            audioEngine = null
            isSessionStarted = false
        }
        startSession(apiKey, modelString, systemPrompt, voiceName)
    }

    /** Starts the mic/WebSocket session. Safe to call repeatedly; a no-op once already running. */
    fun startSession(
        apiKey: String,
        modelString: String,
        systemPrompt: String,
        voiceName: String
    ) {
        if (isSessionStarted) return
        isSessionStarted = true

        resetTurnState()

        audioEngine = AudioEngine(this).apply {
            onAudioChunkCaptured = { chunk ->
                if (!isUserMuted) geminiLive?.sendAudioChunk(chunk)
            }
            onAmplitudeChanged = { rms -> uiListener?.onAmplitudeChanged(rms) }
            onSpeakingStarted = { uiListener?.onSpeakingStarted() }
            onSpeakingStopped = { uiListener?.onSpeakingStopped() }
            onInterruptTriggered = { interrupt() }
            setMuted(isUserMuted)
        }

        if (apiKey.isNotBlank()) {
            geminiLive = GeminiLiveClient(apiKey, modelString, systemPrompt, voiceName).apply {
                onConnected = { uiListener?.onConnected() }
                onSetupComplete = {
                    audioEngine?.startRecording()
                    audioEngine?.startPlayback()
                    uiListener?.onSetupComplete()
                }
                onInterrupted = {
                    audioEngine?.clearPlaybackQueue()
                    uiListener?.onSpeakingStopped()
                }
                onAudioReceived = { bytes ->
                    if (!isUserMuted) {
                        if (!currentTurnHasWakeWord) {
                            audioEngine?.clearPlaybackQueue()
                            if (!interruptSentThisTurn) {
                                interruptSentThisTurn = true
                                geminiLive?.sendInterrupt()
                                uiListener?.onCommandIgnored()
                            }
                        } else {
                            audioEngine?.queueAudio(bytes)
                        }
                    }
                }
                onInputTranscript = { text ->
                    if (!isUserMuted) {
                        currentTurnInputText.append(text)
                        if (!currentTurnHasWakeWord && textHasWakeWord(currentTurnInputText.toString())) {
                            currentTurnHasWakeWord = true
                            isActivatedSession = true
                            touchSessionActivity()
                        }
                        if (currentTurnHasWakeWord) {
                            touchSessionActivity()
                            uiListener?.onInputTranscript(text)
                        }
                    }
                }
                onOutputTranscript = { text ->
                    if (!isUserMuted) {
                        if (currentTurnHasWakeWord) {
                            uiListener?.onOutputTranscript(text)
                        }
                    }
                }
                onTurnComplete = {
                    if (isUserMuted) {
                        resetTurnState()
                    } else {
                        if (currentTurnHasWakeWord) {
                            uiListener?.onTurnComplete()
                            touchSessionActivity()
                        }
                        resetTurnState()
                    }
                }
                onDisconnected = { uiListener?.onDisconnected() }
                onError = { msg -> uiListener?.onError(msg) }
                onToolCall = { name, args, callId ->
                    if (!isUserMuted) {
                        if (currentTurnHasWakeWord) {
                            isActivatedSession = true
                            touchSessionActivity()
                            uiListener?.onToolCall(name, args, callId)
                            toolScope.launch { handleToolCall(name, args, callId) }
                        } else {
                            val ignored = JSONObject().apply {
                                put("success", false)
                                put("message", "Ignored: user did not address JARVIS by name.")
                            }
                            sendToolResponse(callId, name, ignored)
                        }
                    }
                }
            }
            geminiLive?.connect()
        } else {
            audioEngine?.startRecording()
            audioEngine?.startPlayback()
            uiListener?.onConnected()
            uiListener?.onSetupComplete()
        }
    }

    // ---------------------------------------------------------------
    // Tool execution — runs here (not in MainActivity) so voice commands
    // like "open YouTube" or "pause it" still work even when the Activity
    // is backgrounded, e.g. while the user is already inside another app.
    // ---------------------------------------------------------------

    private suspend fun handleToolCall(name: String, args: JSONObject, callId: String) {
        val result = JSONObject()
        try {
            when (name) {
                "open_app" -> {
                    val appName = args.optString("app_name", "")
                    val launched = if (appName.isBlank()) null else AppLauncher.openApp(this, appName)
                    if (launched != null) {
                        result.put("success", true)
                        result.put("opened_app", launched.label)
                    } else {
                        result.put("success", false)
                        result.put("message", "No app matching \"$appName\" was found.")
                    }
                }
                "search_and_play_youtube" -> {
                    val query = args.optString("query", "")
                    val play = YouTubeController.searchAndPlay(this, query)
                    result.put("success", play.success)
                    play.title?.let { result.put("playing", it) }
                    play.message?.let { result.put("message", it) }
                }
                "media_playback_control" -> {
                    val action = args.optString("action", "")
                    val ok = YouTubeController.sendMediaKey(this, action)
                    result.put("success", ok)
                    if (!ok) result.put("message", "Couldn't send the $action command — nothing seems to be playing.")
                }
                "youtube_accessibility_action" -> {
                    val action = args.optString("action", "")
                    val svc = JarvisAccessibilityService.instance
                    if (svc == null) {
                        result.put("success", false)
                        result.put("message", "Accessibility isn't enabled for JARVIS yet — ask the user to turn it on in Settings > Accessibility.")
                    } else {
                        val ok = when (action) {
                            "skip_ad" -> svc.skipAd()
                            "like" -> svc.likeVideo()
                            "subscribe" -> svc.subscribeChannel()
                            "open_channel" -> svc.openChannel()
                            "seek_forward" -> svc.seekForward()
                            "seek_backward" -> svc.seekBackward()
                            "fullscreen" -> svc.toggleFullscreen()
                            else -> false
                        }
                        result.put("success", ok)
                        if (!ok) result.put("message", "Couldn't find that control on screen right now.")
                    }
                }
                "call_contact" -> {
                    val contactName = args.optString("contact_name", "")
                        .ifBlank { args.optString("name", "") }
                        .ifBlank { args.optString("query", "") }
                        .ifBlank { args.optString("contact", "") }
                        .ifBlank { args.optString("number", "") }
                    when (val callResult = ContactCaller.callContact(this, contactName)) {
                        is ContactCaller.CallResult.Success -> {
                            result.put("success", true)
                            result.put("calling", callResult.contact.name)
                        }
                        is ContactCaller.CallResult.NoMatch -> {
                            result.put("success", false)
                            result.put("message", "No contact matching \"${callResult.query}\" was found.")
                        }
                        is ContactCaller.CallResult.MultipleMatches -> {
                            val names = callResult.matches.map { it.name }.distinct().joinToString(", ")
                            result.put("success", false)
                            result.put("message", "Found more than one match for \"${callResult.query}\": $names. Ask the user which one they meant.")
                        }
                        ContactCaller.CallResult.MissingPermission -> {
                            result.put("success", false)
                            result.put("message", "JARVIS doesn't have Contacts/Phone permission yet — ask the user to grant it in Settings.")
                        }
                        ContactCaller.CallResult.CallFailed -> {
                            result.put("success", false)
                            result.put("message", "Found the contact but couldn't place the call.")
                        }
                    }
                }
                "set_volume" -> {
                    val ok = adjustVolume(args)
                    result.put("success", ok)
                }
                "set_brightness" -> {
                    val ok = adjustBrightness(args)
                    result.put("success", ok)
                }
                "search_playstore_and_install" -> {
                    val appName = args.optString("app_name", "").ifBlank { args.optString("query", "") }
                    val encoded = Uri.encode(appName)

                    // Explicitly target Google Play Store ("com.android.vending") package
                    // so OEM stores like Vivo V-Appstore or Samsung Galaxy Store are NOT opened.
                    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps")).apply {
                        setPackage("com.android.vending")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val launched = try {
                        startActivity(playStoreIntent)
                        true
                    } catch (e: Exception) {
                        // Fallback 1: Google Play Store HTTP search URL with package constraint
                        val playWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")).apply {
                            setPackage("com.android.vending")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            startActivity(playWebIntent)
                            true
                        } catch (e2: Exception) {
                            // Fallback 2: Plain market:// intent without package constraint
                            val marketFallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try { startActivity(marketFallbackIntent); true } catch (e3: Exception) { false }
                        }
                    }
                    if (launched) {
                        val accessEnabled = JarvisAccessibilityService.isEnabled()
                        if (accessEnabled) {
                            JarvisAccessibilityService.instance?.startAutoInstallScanner(appName)
                            result.put("success", true)
                            result.put("message", "Opened Google Play Store for $appName and starting auto-install scanner.")
                        } else {
                            result.put("success", true)
                            result.put("message", "Opened Google Play Store for $appName. Ask user to tap Install, or enable Accessibility in Settings for auto-install.")
                        }
                    } else {
                        result.put("success", false)
                        result.put("message", "Could not open Google Play Store.")
                    }
                }
                "search_in_chrome" -> {
                    val query = args.optString("query", "")
                    val encoded = Uri.encode(query)
                    val searchUrl = if (query.startsWith("http://") || query.startsWith("https://")) query else "https://www.google.com/search?q=$encoded"
                    val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                        setPackage("com.android.chrome")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val launched = try {
                        startActivity(chromeIntent)
                        true
                    } catch (e: Exception) {
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { startActivity(fallbackIntent); true } catch (e2: Exception) { false }
                    }
                    result.put("success", launched)
                    if (launched) result.put("message", "Searched \"$query\" in Chrome.")
                }
                "tap_screen_by_text" -> {
                    val text = args.optString("text", "")
                    val svc = JarvisAccessibilityService.instance
                    if (svc == null) {
                        result.put("success", false)
                        result.put("message", "Accessibility Service is not enabled yet — ask user to enable it in Settings > Accessibility.")
                    } else {
                        val ok = svc.clickNodeWithText(text)
                        result.put("success", ok)
                        if (!ok) result.put("message", "Could not find clickable text \"$text\" on screen.")
                    }
                }
                "tap_screen_coordinates" -> {
                    val x = args.optInt("x_percent", 50)
                    val y = args.optInt("y_percent", 50)
                    val svc = JarvisAccessibilityService.instance
                    if (svc == null) {
                        result.put("success", false)
                        result.put("message", "Accessibility Service is not enabled.")
                    } else {
                        val ok = svc.tapAtPercentage(x.toFloat(), y.toFloat())
                        result.put("success", ok)
                    }
                }
                "type_text" -> {
                    val textToType = args.optString("text", "")
                    val svc = JarvisAccessibilityService.instance
                    if (svc == null) {
                        result.put("success", false)
                        result.put("message", "Accessibility Service is not enabled.")
                    } else {
                        val ok = svc.typeText(textToType)
                        result.put("success", ok)
                        if (!ok) result.put("message", "No input text field currently focused.")
                    }
                }
                "perform_device_gesture" -> {
                    val gesture = args.optString("gesture", "home")
                    val svc = JarvisAccessibilityService.instance
                    if (svc == null) {
                        result.put("success", false)
                        result.put("message", "Accessibility Service is not enabled.")
                    } else {
                        val ok = svc.performSystemGesture(gesture)
                        result.put("success", ok)
                    }
                }
                "builtin_chrome_search" -> {
                    val query = args.optString("query", "")
                    withContext(Dispatchers.Main) {
                        uiListener?.onResearchStateChanged(true, query)
                    }
                    val searchResult = com.jarvis.assistant.util.BuiltInChromeEngine.searchAndExtract(query)
                    withContext(Dispatchers.Main) {
                        uiListener?.onResearchStateChanged(false, "")
                    }
                    result.put("success", true)
                    result.put("web_research_result", searchResult)
                }
                "unlock_app_lock" -> {
                    val passcode = args.optString("passcode", "").ifBlank { args.optString("pin", "") }
                    val svc = JarvisAccessibilityService.instance
                    if (svc == null) {
                        result.put("success", false)
                        result.put("message", "Accessibility Service is not enabled yet — ask user to enable it in Settings > Accessibility.")
                    } else {
                        val ok = svc.unlockAppLock(passcode)
                        result.put("success", ok)
                        if (ok) {
                            result.put("message", "Entered passcode '$passcode' to unlock the app lock screen.")
                        } else {
                            result.put("message", "Tried entering passcode '$passcode' on screen, but could not locate password field or keypad buttons.")
                        }
                    }
                }
                "shutdown_jarvis" -> {
                    result.put("success", true)
                    result.put("message", "JARVIS is turning off. Goodbye!")
                    Handler(Looper.getMainLooper()).postDelayed({
                        uiListener?.onShutdownRequested() ?: stopSession()
                    }, 1800)
                }
                else -> {
                    result.put("success", false)
                    result.put("message", "Unknown tool: $name")
                }
            }
        } catch (e: Exception) {
            val errorDetail = "Tool '$name' failed: ${e.javaClass.simpleName}: ${e.message}"
            android.util.Log.e("JarvisVoiceService", errorDetail, e)
            result.put("success", false)
            result.put("message", errorDetail)
            uiListener?.onError(errorDetail)
        }
        sendToolResponse(callId, name, result)
    }

    private fun adjustVolume(args: JSONObject): Boolean {
        return try {
            val actionStr = (args.optString("action", "") + " " + args.optString("direction", "") + " " + args.optString("mode", "")).lowercase()
            val percent = listOf("percentage", "percent", "level", "value", "vol", "volume")
                .map { if (args.has(it)) args.optInt(it, -1) else -1 }
                .firstOrNull { it in 0..100 } ?: -1

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            val isDecrease = actionStr.contains("decrease") || actionStr.contains("down") || actionStr.contains("lower") || actionStr.contains("kam") || actionStr.contains("reduce") || actionStr.contains("less")
            val isIncrease = actionStr.contains("increase") || actionStr.contains("up") || actionStr.contains("raise") || actionStr.contains("badhao") || actionStr.contains("more") || actionStr.contains("high")

            val targetVol = when {
                percent in 0..100 -> ((percent / 100f) * maxVol).toInt().coerceIn(1, maxVol)
                isDecrease -> (currentVol - (maxVol * 0.20f).toInt().coerceAtLeast(1)).coerceAtLeast(1)
                isIncrease -> (currentVol + (maxVol * 0.20f).toInt().coerceAtLeast(1)).coerceAtMost(maxVol)
                else -> currentVol
            }

            // Apply ONLY to STREAM_MUSIC (Media Volume). Do NOT touch ringer/notification streams!
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            android.util.Log.e("JarvisVoiceService", "adjustVolume failed", e)
            false
        }
    }

    private fun adjustBrightness(args: JSONObject): Boolean {
        return try {
            val actionStr = (args.optString("action", "") + " " + args.optString("direction", "") + " " + args.optString("mode", "")).lowercase()
            val percent = listOf("percentage", "percent", "level", "value", "brightness")
                .map { if (args.has(it)) args.optInt(it, -1) else -1 }
                .firstOrNull { it in 0..100 } ?: -1

            val isDecrease = actionStr.contains("decrease") || actionStr.contains("down") || actionStr.contains("lower") || actionStr.contains("kam") || actionStr.contains("reduce") || actionStr.contains("less")
            val isIncrease = actionStr.contains("increase") || actionStr.contains("up") || actionStr.contains("raise") || actionStr.contains("badhao") || actionStr.contains("more") || actionStr.contains("high")

            val cr = contentResolver
            val currentBrightness = try {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) { 128 }

            val targetBrightnessInt = when {
                percent in 0..100 -> ((percent / 100f) * 255).toInt().coerceIn(15, 255)
                isDecrease -> (currentBrightness - 50).coerceAtLeast(15)
                isIncrease -> (currentBrightness + 50).coerceAtMost(255)
                else -> currentBrightness
            }

            // Reset local window override so Android System Control Center brightness slider stays in 100% sync
            com.jarvis.assistant.ui.main.MainActivity.instance?.resetWindowBrightnessOverride()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this)) {
                try {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, targetBrightnessInt)
                } catch (e: Exception) {
                    android.util.Log.e("JarvisVoiceService", "Settings.System write failed", e)
                }
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("JarvisVoiceService", "adjustBrightness failed", e)
            false
        }
    }

    /** Send a text message to Gemini Live (used for text chat input). */
    fun sendTextToGemini(text: String) {
        currentTurnHasWakeWord = true
        geminiLive?.sendText(text)
    }

    fun setMicMuted(muted: Boolean) {
        isUserMuted = muted
        audioEngine?.setMuted(muted)
    }

    fun isMicMuted(): Boolean = isUserMuted

    fun isCurrentlySpeaking(): Boolean = audioEngine?.isCurrentlySpeaking() ?: false

    fun interrupt() {
        audioEngine?.clearPlaybackQueue()
        geminiLive?.sendInterrupt()
    }

    fun sendToolResponse(callId: String, name: String, result: JSONObject) {
        geminiLive?.sendToolResponse(callId, name, result)
    }

    fun isSessionRunning(): Boolean = isSessionStarted

    fun startScreenShare(resultCode: Int, data: Intent) {
        try {
            stopScreenShare()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(NOTIFICATION_ID, buildNotification(), serviceType)
            }

            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopScreenShare()
                }
            }, Handler(Looper.getMainLooper()))

            screenCaptureEngine = com.jarvis.assistant.vision.ScreenCaptureEngine(this, mediaProjection) { jpegBytes ->
                geminiLive?.sendVideoFrame(jpegBytes)
            }
            screenCaptureEngine?.start()
            uiListener?.onScreenShareStateChanged(true)
        } catch (e: Exception) {
            android.util.Log.e("JarvisVoiceService", "startScreenShare failed", e)
            stopScreenShare()
        }
    }

    fun stopScreenShare() {
        val wasActive = screenCaptureEngine != null
        screenCaptureEngine?.stop()
        screenCaptureEngine = null
        uiListener?.onScreenShareStateChanged(false)
        if (wasActive) {
            geminiLive?.sendText("[SYSTEM COMMAND] Screen vision has been closed by the user. You are no longer receiving screen frames.")
        }
    }

    fun isScreenSharing(): Boolean = screenCaptureEngine != null

    fun startCameraVision(useFront: Boolean = false, previewTextureView: android.view.TextureView? = null) {
        try {
            stopScreenShare() // Screen share and camera vision are mutually exclusive

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    startForeground(NOTIFICATION_ID, buildNotification(), serviceType)
                } catch (e: Exception) {
                    android.util.Log.w("JarvisVoiceService", "Could not update foreground service type to camera", e)
                }
            }

            if (cameraVisionEngine == null) {
                cameraVisionEngine = com.jarvis.assistant.vision.CameraVisionEngine(this) { jpegBytes ->
                    geminiLive?.sendVideoFrame(jpegBytes)
                }
            }
            cameraVisionEngine?.setPreviewTextureView(previewTextureView)
            cameraVisionEngine?.startCamera(useFront)
            uiListener?.onCameraVisionStateChanged(true, useFront)
        } catch (e: Exception) {
            android.util.Log.e("JarvisVoiceService", "startCameraVision failed", e)
            stopCameraVision()
        }
    }

    fun updateCameraPreviewTarget(previewTextureView: android.view.TextureView?) {
        cameraVisionEngine?.setPreviewTextureView(previewTextureView)
    }

    fun switchCameraLens() {
        cameraVisionEngine?.let { engine ->
            if (engine.isCameraStreaming()) {
                val newLensFront = !engine.isFrontLens()
                engine.switchCamera()
                uiListener?.onCameraVisionStateChanged(true, newLensFront)
            }
        }
    }

    fun stopCameraVision() {
        val wasActive = cameraVisionEngine?.isCameraStreaming() == true
        cameraVisionEngine?.stopCamera()
        cameraVisionEngine = null
        uiListener?.onCameraVisionStateChanged(false, false)
        if (wasActive) {
            geminiLive?.sendText("[SYSTEM COMMAND] Camera vision has been closed by the user. You are no longer receiving camera frames. If asked, inform the user that camera vision is currently off.")
        }
    }

    fun isCameraVisionActive(): Boolean = cameraVisionEngine?.isCameraStreaming() == true
    fun isCameraFrontLens(): Boolean = cameraVisionEngine?.isFrontLens() == true

    /** Fully tears down the voice session and stops the service (e.g. user quit JARVIS entirely). */
    fun stopSession() {
        resetTurnState()
        stopScreenShare()
        stopCameraVision()
        geminiLive?.disconnect()
        audioEngine?.release()
        toolScope.coroutineContext.cancelChildren()
        geminiLive = null
        audioEngine = null
        isSessionStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopCameraVision()
        geminiLive?.disconnect()
        audioEngine?.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "JARVIS Voice", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps JARVIS listening while other apps are open"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS is listening")
            .setContentText("Tap to return to JARVIS")
            .setSmallIcon(R.drawable.ic_jarvis_notif)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
