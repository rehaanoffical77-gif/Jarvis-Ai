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
import android.widget.Toast
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
import java.net.HttpURLConnection
import java.net.URL

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
    private var isStrictBackgroundMode = false
    private var activeSessionTimerJob: Job? = null

    fun setAppForeground(isForeground: Boolean) {
        isAppInForeground = isForeground
        if (isForeground) {
            isActivatedSession = true
            touchSessionActivity()
        } else {
            // When minimizing app to background / home screen, keep conversation active for continuous hands-free talking
            isActivatedSession = true
            touchSessionActivity()
            Log.d("JarvisVoiceService", "App backgrounded — keeping live voice conversation active in background foreground service.")
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
    private val currentTurnOutputText = StringBuilder()
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
        currentTurnOutputText.clear()
        interruptSentThisTurn = false
        // In strict background mode, require explicit wake word for every turn.
        currentTurnHasWakeWord = if (isStrictBackgroundMode) false else (isAppInForeground || isActivatedSession)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private var tts: android.speech.tts.TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = android.speech.tts.TextToSpeech(applicationContext) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.US
            }
        }
    }

    fun speakAloud(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) return
        Log.d("JarvisVoiceService", "Speaking aloud: $text")
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        }
        tts?.let { engine ->
            val params = android.os.Bundle()
            engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "jarvis_tts_msg")
            if (onDone != null) {
                engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
                    }
                    override fun onError(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
                    }
                })
            }
        } ?: run {
            onDone?.invoke()
        }
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
                        currentTurnOutputText.append(text)
                        if (currentTurnHasWakeWord) {
                            uiListener?.onOutputTranscript(text)
                        }
                    }
                }
                onTurnComplete = {
                    if (isUserMuted) {
                        resetTurnState()
                    } else {
                        val hasWakeWord = currentTurnHasWakeWord || textHasWakeWord(currentTurnInputText.toString()) || isActivatedSession
                        val userMsg = currentTurnInputText.toString().trim()
                        val jarvisMsg = currentTurnOutputText.toString().trim()

                        if (hasWakeWord && (userMsg.isNotEmpty() || jarvisMsg.isNotEmpty())) {
                            val toSave = mutableListOf<com.jarvis.assistant.model.ChatMessage>()
                            if (userMsg.isNotEmpty()) toSave.add(com.jarvis.assistant.model.ChatMessage(userMsg, isUser = true))
                            if (jarvisMsg.isNotEmpty()) toSave.add(com.jarvis.assistant.model.ChatMessage(jarvisMsg, isUser = false))
                            if (toSave.isNotEmpty()) {
                                com.jarvis.assistant.util.ChatHistoryManager.saveMessages(this@JarvisVoiceService, toSave)
                            }
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
                    val appNumber = if (args.has("app_number")) args.optInt("app_number", 0) else null
                    if (appName.isBlank()) {
                        result.put("success", false)
                        result.put("message", "App name cannot be empty.")
                    } else {
                        val res = AppLauncher.openAppResult(this, appName, if (appNumber != null && appNumber > 0) appNumber else null)
                        when (res) {
                            is AppLauncher.OpenAppResult.Success -> {
                                result.put("success", true)
                                result.put("opened_app", res.app.label)
                                appNumber?.let { num ->
                                    if (num > 0) {
                                        JarvisAccessibilityService.instance?.handleDualAppSelection(appName, num)
                                    }
                                }
                            }
                            is AppLauncher.OpenAppResult.MultipleFound -> {
                                result.put("success", false)
                                result.put("multiple_apps", true)
                                result.put("count", res.matches.size)
                                result.put("app_name", appName)
                                result.put(
                                    "message",
                                    "In your mobile there are ${res.matches.size} $appName apps. Ask the user in their language: \"In your mobile there are ${res.matches.size} $appName apps. Which one should I open, 1 or 2?\""
                                )
                            }
                            is AppLauncher.OpenAppResult.NotFound -> {
                                result.put("success", false)
                                result.put("message", "No app matching \"$appName\" was found.")
                            }
                            AppLauncher.OpenAppResult.Failure -> {
                                result.put("success", false)
                                result.put("message", "Failed to launch \"$appName\".")
                            }
                        }
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
                            speakAloud("Okay sir, calling ${callResult.contact.name}.")
                            monitorPhoneCallAndKeepQuiet()
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
                "send_whatsapp_message" -> {
                    val recipientName = args.optString("recipient_name", "")
                        .ifBlank { args.optString("contact_name", "") }
                        .ifBlank { args.optString("name", "") }
                    val message = args.optString("message", "")
                        .ifBlank { args.optString("text", "") }
                    val appNumber = if (args.has("app_number")) args.optInt("app_number", 0).takeIf { it in 1..2 } else null
                    val confirmed = args.optBoolean("confirmed", false)

                    when (val res = com.jarvis.assistant.util.WhatsAppMessenger.sendMessage(this, recipientName, message, appNumber, confirmed)) {
                        is com.jarvis.assistant.util.WhatsAppMessenger.SendResult.Success -> {
                            result.put("success", true)
                            result.put("message", "Sending message to ${res.contactName} via ${res.appName}.")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.SendResult.RequiresConfirmation -> {
                            result.put("success", false)
                            result.put("requires_confirmation", true)
                            result.put("contact_name", res.contactName)
                            result.put("message", "Found contact \"${res.contactName}\". Ask user: \"Is this ${res.contactName} contact to send a message?\" (or in Hindi: \"Kya main ${res.contactName} ko ye message bhej doon?\"). When user confirms yes, call send_whatsapp_message(recipient_name=\"${res.contactName}\", message=\"${res.message}\", confirmed=true).")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.SendResult.MultipleAppsFound -> {
                            result.put("success", false)
                            result.put("multiple_apps", true)
                            result.put("message", "In your mobile there are 2 WhatsApp apps. Ask user: \"In your mobile there are 2 WhatsApp apps. Which one should I use, 1 or 2?\"")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.SendResult.ContactNotFound -> {
                            result.put("success", false)
                            result.put("message", "Could not find any contact named \"${res.name}\" in phone contacts.")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.SendResult.MultipleContactsFound -> {
                            val matchesList = res.matches.joinToString(", ")
                            result.put("success", false)
                            result.put("message", "Multiple contacts found for \"${res.name}\": $matchesList. Ask user which person to message.")
                        }
                        com.jarvis.assistant.util.WhatsAppMessenger.SendResult.MissingPermission -> {
                            result.put("success", false)
                            result.put("message", "Contacts permission is required to read contact phone numbers.")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.SendResult.Error -> {
                            result.put("success", false)
                            result.put("message", res.reason)
                        }
                    }
                }
                "whatsapp_call" -> {
                    val recipientName = args.optString("recipient_name", "")
                        .ifBlank { args.optString("contact_name", "") }
                        .ifBlank { args.optString("name", "") }
                    val callType = args.optString("call_type", "voice")
                    val appNumber = if (args.has("app_number")) args.optInt("app_number", 0).takeIf { it in 1..2 } else null
                    val confirmed = args.optBoolean("confirmed", false)

                    when (val res = com.jarvis.assistant.util.WhatsAppMessenger.placeCall(this, recipientName, callType, appNumber, confirmed)) {
                        is com.jarvis.assistant.util.WhatsAppMessenger.CallResult.Success -> {
                            result.put("success", true)
                            result.put("message", "Connecting WhatsApp ${res.callType} call to ${res.contactName} via ${res.appName}.")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.CallResult.RequiresConfirmation -> {
                            result.put("success", false)
                            result.put("requires_confirmation", true)
                            result.put("contact_name", res.contactName)
                            result.put("call_type", res.callType)
                            val callPrompt = if (res.callType == "video") "Should I start a WhatsApp video call to ${res.contactName}?" else "Should I call ${res.contactName} on WhatsApp?"
                            result.put("message", "Found contact \"${res.contactName}\". Ask user: \"$callPrompt\" (or in Hindi: \"Kya main ${res.contactName} ko WhatsApp call karoon?\"). When user confirms yes, call whatsapp_call(recipient_name=\"${res.contactName}\", call_type=\"${res.callType}\", confirmed=true).")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.CallResult.MultipleAppsFound -> {
                            result.put("success", false)
                            result.put("multiple_apps", true)
                            result.put("message", "In your mobile there are 2 WhatsApp apps. Ask user: \"In your mobile there are 2 WhatsApp apps. Which one should I use, 1 or 2?\"")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.CallResult.ContactNotFound -> {
                            result.put("success", false)
                            result.put("message", "Could not find any contact named \"${res.name}\" in phone contacts.")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.CallResult.MultipleContactsFound -> {
                            val matchesList = res.matches.joinToString(", ")
                            result.put("success", false)
                            result.put("message", "Multiple contacts found for \"${res.name}\": $matchesList. Ask user which person to call.")
                        }
                        com.jarvis.assistant.util.WhatsAppMessenger.CallResult.MissingPermission -> {
                            result.put("success", false)
                            result.put("message", "Contacts permission is required to read contact phone numbers.")
                        }
                        is com.jarvis.assistant.util.WhatsAppMessenger.CallResult.Error -> {
                            result.put("success", false)
                            result.put("message", res.reason)
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
                    val cleanAppQuery = appName.replace("download", "", ignoreCase = true).replace("install", "", ignoreCase = true).trim()

                    toolScope.launch {
                        val knownPkg = resolvePlayStorePackageName(appName)
                        val playStoreUri = if (knownPkg != null) {
                            Uri.parse("market://details?id=$knownPkg")
                        } else {
                            val encoded = Uri.encode(cleanAppQuery.ifBlank { appName })
                            Uri.parse("market://search?q=$encoded&c=apps")
                        }

                        val playStoreIntent = Intent(Intent.ACTION_VIEW, playStoreUri).apply {
                            setPackage("com.android.vending")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val launched = try {
                            startActivity(playStoreIntent)
                            true
                        } catch (e: Exception) {
                            val marketFallbackIntent = Intent(Intent.ACTION_VIEW, playStoreUri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try { startActivity(marketFallbackIntent); true } catch (e3: Exception) { false }
                        }
                        if (launched) {
                            if (JarvisAccessibilityService.isEnabled()) {
                                JarvisAccessibilityService.instance?.startAutoInstallScanner(cleanAppQuery.ifBlank { appName })
                            }
                        }
                    }

                    result.put("success", true)
                    result.put("message", "Opening Google Play Store for $appName and starting auto-installer.")
                }
                "create_website" -> {
                    val websiteName = args.optString("website_name", "").ifBlank { args.optString("name", "JarvisWebsite") }
                    val businessDesc = args.optString("business_description", "").ifBlank { websiteName }
                    val openRouterKey = com.jarvis.assistant.util.OpenRouterWebsiteGenerator.getApiKey(this)

                    if (openRouterKey.isBlank()) {
                        val noKeyMsg = "Sir, please add your OpenRouter API key in Settings under Website Builder to create websites."
                        speakAloud(noKeyMsg)
                        result.put("success", false)
                        result.put("message", noKeyMsg)
                    } else {
                        val startMsg = "Oh yeah Sir, I have started coding your $websiteName website!"
                        speakAloud(startMsg)
                        result.put("success", true)
                        result.put("message", startMsg)

                        toolScope.launch {
                            delay(2000L)

                            val overlayIntent = Intent(this@JarvisVoiceService, com.jarvis.assistant.service.WebsiteOverlayService::class.java).apply {
                                action = com.jarvis.assistant.service.WebsiteOverlayService.ACTION_SHOW
                                putExtra(com.jarvis.assistant.service.WebsiteOverlayService.EXTRA_WEBSITE_NAME, websiteName)
                            }
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(overlayIntent)
                                } else {
                                    startService(overlayIntent)
                                }
                            } catch (e: Exception) {
                                Log.e("JarvisVoiceService", "startForegroundService overlay failed", e)
                            }

                            val genRes = com.jarvis.assistant.util.OpenRouterWebsiteGenerator.generateWebsite(
                                context = this@JarvisVoiceService,
                                websiteName = websiteName,
                                businessDescription = businessDesc
                            )

                            delay(500L)
                            if (genRes.success) {
                                speakAloud("Sir, I have finished coding your website! Please check your mobile screen.")
                            } else {
                                speakAloud("Sir, website creation encountered an issue: ${genRes.message}")
                            }
                        }
                    }
                }
                "search_in_chrome" -> {
                    val query = args.optString("query", "")
                    val targetUrl = parseTargetUrl(query)
                    val encoded = Uri.encode(query)
                    val searchUrl = targetUrl ?: "https://www.google.com/search?q=$encoded"
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
                    if (launched) {
                        if (targetUrl != null) {
                            result.put("message", "Opened website $targetUrl in Chrome.")
                        } else {
                            result.put("message", "Searched \"$query\" in Chrome.")
                        }
                    }
                }
                "download_song" -> {
                    val songName = args.optString("song_name", "").ifBlank { args.optString("query", "") }
                    val artist = args.optString("artist", "")
                    val fullQuery = if (artist.isNotBlank()) "$songName $artist" else songName
                    val searchQuery = "pagalnew.com $fullQuery"
                    val encoded = Uri.encode(searchQuery)
                    val chromeSearchUrl = "https://www.google.com/search?q=$encoded"

                    val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(chromeSearchUrl)).apply {
                        setPackage("com.android.chrome")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val launched = try {
                        startActivity(chromeIntent)
                        true
                    } catch (e: Exception) {
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(chromeSearchUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { startActivity(fallbackIntent); true } catch (e2: Exception) { false }
                    }

                    // Perform background research & download via DownloadManager targeting pagalnew.com
                    toolScope.launch {
                        val songRes = com.jarvis.assistant.util.SongDownloader.checkAndDownloadPagalNew(this@JarvisVoiceService, fullQuery)
                        if (songRes.isAvailable) {
                            if (JarvisAccessibilityService.isEnabled()) {
                                JarvisAccessibilityService.instance?.startPagalNewSongScanner(songName)
                            }
                        } else {
                            // Song is not available on pagalnew.com: SPEAK ALOUD exact apology and redirect to home screen
                            val apologyMsg = "Sorry sir, you asked me to download $fullQuery. It is not available so please I am sorry."
                            speakAloud(apologyMsg) {
                                val accSvc = JarvisAccessibilityService.instance
                                if (accSvc != null) {
                                    accSvc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                } else {
                                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    try { startActivity(homeIntent) } catch (e: Exception) {}
                                }
                            }
                        }
                    }

                    result.put("success", launched)
                    result.put("message", "Opened Chrome for pagalnew.com $fullQuery research.")
                }
                "play_music" -> {
                    val songName = args.optString("song_name", "").ifBlank { args.optString("query", "") }.ifBlank { args.optString("title", "") }
                    if (songName.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_SEARCH).apply {
                            setPackage("com.google.android.youtube")
                            putExtra("query", songName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            startActivity(intent)
                            result.put("success", true)
                            result.put("message", "Opening YouTube for $songName.")
                        } catch (e: Exception) {
                            val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(songName, "UTF-8"))).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(webIntent)
                            result.put("success", true)
                            result.put("message", "Opening YouTube web for $songName.")
                        }
                    } else {
                        result.put("success", false)
                        result.put("message", "Song name was empty.")
                    }
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
                "delete_whatsapp_message" -> {
                    val target = args.optString("delete_target", "everyone")
                    val svc = JarvisAccessibilityService.instance
                    if (svc == null) {
                        result.put("success", false)
                        result.put("message", "Accessibility Service is not enabled yet — ask user to enable it in Settings > Accessibility.")
                    } else {
                        val ok = svc.deleteWhatsAppMessage(target)
                        result.put("success", ok)
                        result.put("message", if (ok) "Deleted WhatsApp message for $target." else "Could not delete WhatsApp message.")
                    }
                }
                "smart_screen_scroll" -> {
                    val action = args.optString("action", "scroll_down")
                    val svc = JarvisAccessibilityService.instance
                    if (svc == null) {
                        result.put("success", false)
                        result.put("message", "Accessibility Service is not enabled.")
                    } else {
                        val ok = svc.smartScroll(action)
                        result.put("success", ok)
                        result.put("message", if (ok) "Executed scroll action: $action." else "Scroll action failed.")
                    }
                }
                "set_alarm" -> {
                    val hour = args.optInt("hour", 0)
                    val minute = args.optInt("minute", 0)
                    val label = args.optString("label", "JARVIS Alarm")
                    val (ok, msg) = com.jarvis.assistant.util.AlarmTimerManager.setAlarm(this, hour, minute, label)
                    result.put("success", ok)
                    result.put("message", msg)
                }
                "set_timer" -> {
                    val seconds = args.optInt("seconds", 60)
                    val label = args.optString("label", "JARVIS Timer")
                    val (ok, msg) = com.jarvis.assistant.util.AlarmTimerManager.setTimer(this, seconds, label)
                    result.put("success", ok)
                    result.put("message", msg)
                }
                "set_reminder" -> {
                    val title = args.optString("title", "Reminder")
                    val delayMins = args.optInt("delay_minutes", 10)
                    val (ok, msg) = com.jarvis.assistant.util.AlarmTimerManager.setReminder(this, title, delayMins)
                    result.put("success", ok)
                    result.put("message", msg)
                }
                "get_system_info" -> {
                    val queryType = args.optString("query_type", "all")
                    val city = args.optString("city", "")
                    val info = com.jarvis.assistant.util.SystemInfoManager.getSystemSummary(this, queryType, city)
                    result.put("success", true)
                    result.put("system_info", info)
                }
                "control_camera" -> {
                    val action = args.optString("action", "take_photo")
                    val (ok, msg) = com.jarvis.assistant.util.CameraManagerHelper.captureDirectPhoto(this, action)
                    result.put("success", ok)
                    result.put("message", msg)
                }
                "analyze_scene" -> {
                    val mode = args.optString("mode", "full_analysis")
                    if (!isCameraVisionActive() && !isScreenSharing()) {
                        startCameraVision(useFront = false)
                    }
                    val promptText = when (mode) {
                        "read_text" -> "[SYSTEM COMMAND] Perform OCR: read all text visible in the current camera/screen view out loud to the user."
                        "object_recognition" -> "[SYSTEM COMMAND] Identify and describe the main objects currently visible in the camera/screen view."
                        "describe_scene" -> "[SYSTEM COMMAND] Describe the current scene and context in full detail to the user."
                        else -> "[SYSTEM COMMAND] Provide a full visual analysis: read any text, identify objects, and describe the scene context."
                    }
                    geminiLive?.sendText(promptText)
                    result.put("success", true)
                    result.put("message", "Analyzing scene: $mode.")
                }
                "control_flashlight" -> {
                    val action = args.optString("action", "toggle")
                    val level = if (args.has("brightness_level")) args.optInt("brightness_level", -1).takeIf { it > 0 } else null
                    val (ok, msg) = com.jarvis.assistant.util.FlashlightController.controlFlashlight(this, action, level)
                    result.put("success", ok)
                    result.put("message", msg)
                }
                "show_code_preview_bar", "open_code_review_bar" -> {
                    val overlayIntent = Intent(this@JarvisVoiceService, com.jarvis.assistant.service.WebsiteOverlayService::class.java).apply {
                        action = com.jarvis.assistant.service.WebsiteOverlayService.ACTION_SHOW
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(overlayIntent)
                        } else {
                            startService(overlayIntent)
                        }
                    } catch (e: Exception) {
                        Log.e("JarvisVoiceService", "startForegroundService overlay failed", e)
                    }
                    result.put("success", true)
                    result.put("message", "Re-opened website code preview bar overlay on screen.")
                }
                "control_floating_orb" -> {
                    val action = args.optString("action", "off").lowercase()
                    if (action == "off" || action == "hide") {
                        com.jarvis.assistant.service.FloatingOrbService.stopService(this@JarvisVoiceService)
                        val msg = "Floating Voice Orb turned off, sir. I am running and talking in the background."
                        result.put("success", true)
                        result.put("message", msg)
                    } else {
                        com.jarvis.assistant.service.FloatingOrbService.startService(this@JarvisVoiceService)
                        val msg = "Floating Voice Orb turned back on, sir."
                        result.put("success", true)
                        result.put("message", msg)
                    }
                }
                "send_to_background" -> {
                    isStrictBackgroundMode = true
                    val msg = "Sir, I am in the background. If you want me to work, please say my wake-up words."
                    result.put("success", true)
                    result.put("message", msg)
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

    private fun parseIntegerFromAny(obj: Any?): Int {
        if (obj == null) return -1
        if (obj is Int) return obj
        if (obj is Double) return obj.toInt()
        if (obj is Long) return obj.toInt()
        if (obj is Float) return obj.toInt()
        if (obj is String) {
            val digits = obj.replace(Regex("[^0-9]"), "")
            if (digits.isNotEmpty()) {
                return digits.toIntOrNull() ?: -1
            }
        }
        return -1
    }

    private fun adjustVolume(args: JSONObject): Boolean {
        return try {
            val rawAction = args.optString("action", "").lowercase()
            val rawDir = args.optString("direction", "").lowercase()
            val rawMode = args.optString("mode", "").lowercase()
            val combinedStr = "$rawAction $rawDir $rawMode".lowercase()

            Log.d("JarvisVoiceService", "adjustVolume raw args: $args")

            var percent = -1
            // Inspect all fields in JSON object for a percentage / level number
            val keys = args.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val parsed = parseIntegerFromAny(args.get(key))
                if (parsed in 0..100) {
                    percent = parsed
                    break
                }
            }

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val isDecrease = combinedStr.contains("decrease") || combinedStr.contains("down") || combinedStr.contains("lower") || combinedStr.contains("kam") || combinedStr.contains("reduce") || combinedStr.contains("less") || combinedStr.contains("minus")
            val isIncrease = combinedStr.contains("increase") || combinedStr.contains("up") || combinedStr.contains("raise") || combinedStr.contains("badhao") || combinedStr.contains("more") || combinedStr.contains("high") || combinedStr.contains("plus")

            var finalDisplayPercent = percent

            if (percent in 0..100) {
                // Apply exact target percentage across STREAM_MUSIC and STREAM_VOICE_CALL
                val streams = listOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_VOICE_CALL)
                for (stream in streams) {
                    try {
                        val maxVol = audioManager.getStreamMaxVolume(stream)
                        val targetVol = kotlin.math.round((percent / 100f) * maxVol).toInt().coerceIn(0, maxVol)
                        audioManager.setStreamVolume(stream, targetVol, AudioManager.FLAG_SHOW_UI)
                        Log.d("JarvisVoiceService", "Set stream $stream to targetVol=$targetVol for $percent%")
                    } catch (e: Exception) {
                        Log.e("JarvisVoiceService", "Error setting stream $stream: ${e.message}")
                    }
                }
                finalDisplayPercent = percent
            } else if (isDecrease) {
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_LOWER, 0) } catch (_: Exception) {}
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                finalDisplayPercent = kotlin.math.round((curVol.toFloat() / maxVol.toFloat()) * 100).toInt()
            } else if (isIncrease) {
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) } catch (_: Exception) {}
                try { audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_RAISE, 0) } catch (_: Exception) {}
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                finalDisplayPercent = kotlin.math.round((curVol.toFloat() / maxVol.toFloat()) * 100).toInt()
            } else {
                Log.w("JarvisVoiceService", "adjustVolume: no direction or percent found in $args")
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                finalDisplayPercent = kotlin.math.round((curVol.toFloat() / maxVol.toFloat()) * 100).toInt()
            }

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "🔊 Volume: $finalDisplayPercent%", Toast.LENGTH_SHORT).show()
            }

            true
        } catch (e: Exception) {
            android.util.Log.e("JarvisVoiceService", "adjustVolume failed", e)
            false
        }
    }

    private fun adjustBrightness(args: JSONObject): Boolean {
        return try {
            val actionStr = (args.optString("action", "") + " " + args.optString("direction", "") + " " + args.optString("mode", "")).lowercase()
            
            var percent = -1
            val keys = listOf("percentage", "percent", "level", "value", "brightness")
            for (k in keys) {
                if (args.has(k)) {
                    val valObj = args.get(k)
                    if (valObj is Int) percent = valObj
                    else if (valObj is Double) percent = valObj.toInt()
                    else if (valObj is String) {
                        val cleanDigits = valObj.replace(Regex("[^0-9]"), "")
                        if (cleanDigits.isNotEmpty()) percent = cleanDigits.toIntOrNull() ?: -1
                    }
                }
                if (percent in 0..100) break
            }

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

            val targetFloat = (targetBrightnessInt / 255f).coerceIn(0.05f, 1f)
            val targetPercentDisplay = (targetFloat * 100).toInt()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this)) {
                try {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, targetBrightnessInt)
                    try {
                        // Set Android 11+ (API 30+) float brightness setting which controls System UI / Control Center brightness slider
                        Settings.System.putFloat(cr, "screen_brightness_float", targetFloat)
                    } catch (_: Exception) {}

                    // Force Android Quick Settings / Control Center bar URIs to update position
                    cr.notifyChange(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), null)
                    try {
                        cr.notifyChange(Settings.System.getUriFor("screen_brightness_float"), null)
                    } catch (_: Exception) {}
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

            // Sync top window brightness in MainActivity
            com.jarvis.assistant.ui.main.MainActivity.instance?.setWindowBrightness(targetBrightnessInt)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "☀️ JARVIS Brightness: $targetPercentDisplay%", Toast.LENGTH_SHORT).show()
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

    private suspend fun resolvePlayStorePackageName(appName: String): String? = withContext(Dispatchers.IO) {
        val known = getKnownPackageName(appName)
        if (known != null) return@withContext known

        return@withContext try {
            val query = appName.replace("download", "", ignoreCase = true)
                .replace("install", "", ignoreCase = true).trim()
            val encoded = Uri.encode(query.ifBlank { appName })
            val url = URL("https://play.google.com/store/search?q=$encoded&c=apps")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile)")
                connectTimeout = 5000
                readTimeout = 5000
            }
            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { reader -> reader.readText() }
                val match = Regex("""/store/apps/details\?id=([a-zA-Z0-9_.]+)""").find(html)
                match?.groupValues?.get(1)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getKnownPackageName(appName: String): String? {
        val clean = appName.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
        return when {
            clean.contains("github") -> "com.github.android"
            clean.contains("blinkit") || clean.contains("grofers") -> "com.grofers.customerapp"
            clean.contains("zomato") -> "com.application.zomato"
            clean.contains("swiggy") -> "in.swiggy.android"
            clean.contains("zepto") -> "com.zepto.customer"
            clean.contains("whatsapp") -> "com.whatsapp"
            clean.contains("instagram") -> "com.instagram.android"
            clean.contains("telegram") -> "org.telegram.messenger"
            clean.contains("facebook") -> "com.facebook.katana"
            clean.contains("spotify") -> "com.spotify.music"
            clean.contains("snapchat") -> "com.snapchat.android"
            clean.contains("paytm") -> "net.one97.paytm"
            clean.contains("phonepe") -> "com.phonepe.app"
            clean.contains("gpay") || clean.contains("googlepay") -> "com.google.android.apps.nfc.payment"
            clean.contains("flipkart") -> "com.flipkart.android"
            clean.contains("amazon") -> "com.amazon.mShop.android.shopping"
            clean.contains("meesho") -> "com.meesho.supply"
            clean.contains("myntra") -> "com.myntra.android"
            clean.contains("uber") -> "com.ubercab"
            clean.contains("ola") -> "com.olacabs.customer"
            clean.contains("rapido") -> "com.rapido.passenger"
            clean.contains("linkedin") -> "com.linkedin.android"
            clean.contains("twitter") || clean == "x" -> "com.twitter.android"
            clean.contains("youtube") -> "com.google.android.youtube"
            clean.contains("netflix") -> "com.netflix.mediaclient"
            clean.contains("chrome") -> "com.android.chrome"
            clean.contains("discord") -> "com.discord"
            clean.contains("reddit") -> "com.reddit.frontpage"
            clean.contains("pinterest") -> "com.pinterest"
            clean.contains("duolingo") -> "com.duolingo"
            clean.contains("truecaller") -> "com.truecaller"
            else -> null
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

    private fun parseTargetUrl(query: String): String? {
        var clean = query.trim()
        val lower = clean.lowercase()

        // Strip common voice command prefixes
        val prefixes = listOf("open website", "open site", "visit website", "visit site", "open url", "navigate to", "visit", "open")
        for (prefix in prefixes) {
            if (lower.startsWith(prefix)) {
                val candidate = clean.substring(prefix.length).trim()
                if (candidate.isNotEmpty()) {
                    clean = candidate
                    break
                }
            }
        }

        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean
        }

        val cleanLower = clean.lowercase()
        // Domain pattern check with common TLDs
        val domainRegex = Regex("""^([a-zA-Z0-9-]+\.)+(com|in|org|net|io|co|dev|app|ai|gov|edu|me|tech|info|online|xyz|site|store|cc|tv|uk|us|ca|de|fr|jp|cn|biz)(/.*)?$""")
        if (domainRegex.matches(cleanLower)) {
            return "https://$clean"
        }

        // General URL host pattern (e.g. www.something or host.ext)
        if (cleanLower.startsWith("www.") || (cleanLower.contains(".") && !cleanLower.contains(" ") && cleanLower.indexOf(".") < cleanLower.length - 2)) {
            return "https://$clean"
        }

        return null
    }

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
        unregisterPhoneCallReceiver()
        tts?.stop()
        tts?.shutdown()
        stopCameraVision()
        geminiLive?.disconnect()
        audioEngine?.release()
        super.onDestroy()
    }

    private var phoneCallReceiverRegistered = false
    private val phoneCallReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                val stateStr = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE)
                Log.d("JarvisVoiceService", "Phone call state changed: $stateStr")
                if (stateStr == android.telephony.TelephonyManager.EXTRA_STATE_IDLE) {
                    Log.d("JarvisVoiceService", "Phone call finished — unmuting mic.")
                    setMicMuted(false)
                    unregisterPhoneCallReceiver()
                }
            }
        }
    }

    fun monitorPhoneCallAndKeepQuiet() {
        setMicMuted(true)
        audioEngine?.clearPlaybackQueue()
        if (!phoneCallReceiverRegistered) {
            val filter = android.content.IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            registerReceiver(phoneCallReceiver, filter)
            phoneCallReceiverRegistered = true
        }
    }

    private fun unregisterPhoneCallReceiver() {
        if (phoneCallReceiverRegistered) {
            try { unregisterReceiver(phoneCallReceiver) } catch (_: Exception) {}
            phoneCallReceiverRegistered = false
        }
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
