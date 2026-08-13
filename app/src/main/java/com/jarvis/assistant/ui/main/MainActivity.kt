package com.jarvis.assistant.ui.main

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarvis.assistant.JarvisApplication
import com.jarvis.assistant.R
import com.jarvis.assistant.model.ChatMessage
import com.jarvis.assistant.service.JarvisVoiceService
import com.jarvis.assistant.ui.settings.SettingsActivity
import com.jarvis.assistant.util.AnimUtils
import com.jarvis.assistant.util.EnvLoader
import com.jarvis.assistant.util.pressFeedback
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import android.content.ClipData
import android.content.ClipboardManager
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.webkit.WebViewClient

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_JARVIS_SHUTDOWN = "com.jarvis.assistant.ACTION_SHUTDOWN"
        private const val ALL_PERMISSIONS_REQUEST_CODE = 1010
        var instance: MainActivity? = null
    }

    enum class OrbState { IDLE, LISTENING, SPEAKING, THINKING, ACTIVE }

    private lateinit var orbWebView: android.webkit.WebView
    private lateinit var standbyBarWebView: android.webkit.WebView
    private lateinit var timeText: android.widget.TextView
    private lateinit var batteryText: android.widget.TextView
    private lateinit var ramText: android.widget.TextView
    private lateinit var redOverlay: View
    private lateinit var chatRecycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var topBarCard: View
    private lateinit var bottomSection: View

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var chatInput: android.widget.EditText
    private lateinit var sendBtn: android.widget.ImageButton
    private lateinit var clearHistoryBtn: android.widget.ImageButton
    private lateinit var closeDrawerBtn: android.widget.ImageButton
    private lateinit var gestureDetector: android.view.GestureDetector

    private lateinit var cameraPreviewCard: View
    private lateinit var cameraTextureView: android.view.TextureView
    private lateinit var cameraFlipBtn: View

    // Error overlay views
    private lateinit var errorOverlay: View
    private lateinit var errorDetailText: android.widget.TextView
    private lateinit var errorTimestamp: android.widget.TextView

    // Skeleton loader views
    private lateinit var orbSkeletonView: View
    private lateinit var footerSkeletonView: View
    private var orbSkeletonAnimator: ObjectAnimator? = null
    private var footerSkeletonAnimator: ObjectAnimator? = null
    private var orbLoaded = false
    private var footerLoaded = false

    // Active session configuration tracking
    private var activePersonality: String = ""
    private var activeVoice: String = ""
    private var activeUserName: String = ""
    private var activeApiKey: String = ""
    private var activeModelString: String = ""

    /** Keeps a handle on the mic ring's infinite breathing animator so it can
     *  be cancelled in onDestroy — previously this ran forever with no owner,
     *  leaking the view/Activity and risking duplicate overlapping animators
     *  if the Activity was ever recreated. */
    private var micRingBreathingAnimator: android.animation.ValueAnimator? = null

    /** True once the user has long-pressed the mic to fully shut JARVIS down
     *  (mic + WebSocket + foreground service all torn down). A plain tap on
     *  the mic button while this is true restarts everything from scratch. */
    private var isShutDown = false

    /** Bound reference to the always-on voice service; null until onServiceConnected fires. */
    private var voiceService: JarvisVoiceService? = null
    private var isBound = false

    private var isMuted = false
    private var isActiveMode = false

    private val inputBuffer = StringBuilder()
    private val outputBuffer = StringBuilder()

    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusRunnable: Runnable? = null

    private val screenCaptureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            voiceService?.startScreenShare(result.resultCode, result.data!!)
            Toast.makeText(this, "Vision Screen Share Started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen Share permission canceled", Toast.LENGTH_SHORT).show()
            updateVisionVisuals(false)
        }
    }

    /** Receiver for graceful shutdown (triggered when removed from recent apps). */
    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            performGracefulShutdown()
        }
    }

    /**
     * Binds MainActivity's UI to JarvisVoiceService. The service keeps running
     * (mic + WebSocket) even while unbound, e.g. while YouTube is in the
     * foreground — this connection only exists to push UI updates.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as JarvisVoiceService.LocalBinder
            voiceService = localBinder.getService()
            voiceService?.uiListener = voiceListener
            voiceService?.setAppForeground(true)

            if (voiceService?.isSessionRunning() == true) {
                // Rejoining an already-running session (e.g. returned from YouTube)
                runOnUiThread {
                    updateMicVisuals()
                    setOrbState(if (isMuted) OrbState.IDLE else OrbState.LISTENING)
                }
            } else {
                startVoiceSession()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
        }
    }

    private val voiceListener = object : JarvisVoiceService.JarvisVoiceListener {
        override fun onConnected() {
            runOnUiThread {
                setOrbState(OrbState.THINKING)
            }
        }

        override fun onSetupComplete() {
            runOnUiThread {
                setOrbState(OrbState.LISTENING)
                voiceService?.sendTextToGemini("Hi Sir! System is fully functional.")
            }
        }

        override fun onCommandIgnored() {
            // Someone was heard but never said "Jarvis" — stay quiet, no UI change needed.
            // Left as a hook in case a subtle indicator is wanted later.
        }

        override fun onDisconnected() {
            runOnUiThread { setOrbState(OrbState.THINKING) }
        }

        override fun onError(msg: String) {
            runOnUiThread {
                showErrorOverlay(msg)
            }
        }

        override fun onInputTranscript(text: String) {
            if (!containsForbiddenScript(text)) inputBuffer.append(text)
        }

        override fun onOutputTranscript(text: String) {
            if (!containsForbiddenScript(text)) outputBuffer.append(text)
        }

        override fun onTurnComplete() {
            runOnUiThread { flushTranscriptBuffers() }
        }

        override fun onAmplitudeChanged(rms: Float) {
            runOnUiThread {
                val speaking = voiceService?.isCurrentlySpeaking() == true
                val amplified = if (speaking) 0.1f else (rms * 8f).coerceIn(0f, 1f)
                updateOrbAudioLevel(if (speaking) rms else amplified)
                updateBarAudioLevel(amplified)
            }
        }

        override fun onSpeakingStarted() {
            runOnUiThread {
                setOrbState(OrbState.SPEAKING)
                setActiveMode(true)
            }
        }

        override fun onSpeakingStopped() {
            runOnUiThread {
                setOrbState(if (isMuted) OrbState.IDLE else OrbState.LISTENING)
                setActiveMode(false)
            }
        }

        override fun onToolCall(name: String, args: JSONObject, callId: String) {
            // Execution happens in JarvisVoiceService itself (so it still works
            // even if this Activity isn't bound, e.g. mid-YouTube-video).
            // This callback is just a hook for optional UI feedback.
        }

        override fun onScreenShareStateChanged(isSharing: Boolean) {
            updateVisionVisuals(isSharing)
        }

        override fun onCameraVisionStateChanged(isActive: Boolean, isFront: Boolean) {
            updateCameraVisuals(isActive, isFront)
        }

        override fun onResearchStateChanged(isSearching: Boolean, query: String) {
            updateResearchVisuals(isSearching, query)
        }

        override fun onShutdownRequested() {
            runOnUiThread { shutdownJarvis() }
        }
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isAuth = prefs().getBoolean("is_authenticated", false)
        if (!isAuth) {
            val loginIntent = Intent(this, com.jarvis.assistant.ui.auth.LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(loginIntent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Non-blocking update check for direct APK installations
        com.jarvis.assistant.update.UpdateManager.checkForUpdates(this)

        initViews()
        applyWindowInsets()
        // Permissions are checked on-demand when features are used or in Settings -> Permissions dashboard
        startStatusUpdates()

        drawerLayout = findViewById(R.id.drawerLayout)
        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && (e2.x - e1.x > 120) && Math.abs(velocityX) > 200 && Math.abs(e2.y - e1.y) < 250) {
                    openChatDrawer()
                    return true
                }
                return false
            }
        })

        instance = this
        ContextCompat.registerReceiver(
            this, shutdownReceiver, IntentFilter(ACTION_JARVIS_SHUTDOWN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        startAndBindVoiceService()

        // Check for crash log from previous session
        checkAndDisplayCrashLog()
    }

    fun applyBrightness(percentage: Int) {
        runOnUiThread {
            try {
                val lp = window.attributes
                val floatVal = (percentage.coerceIn(1, 100) / 100f)
                lp.screenBrightness = floatVal
                window.attributes = lp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setWindowBrightness(brightness255: Int) {
        applyBrightness(((brightness255 / 255f) * 100).toInt())
    }

    fun resetWindowBrightnessOverride() {
        runOnUiThread {
            try {
                val lp = window.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Starts the foreground voice service (if not already running) and binds
     * to it for UI updates. Called once from onCreate, and again after the
     * user long-presses the mic to fully shut JARVIS down and then taps it to
     * bring her back.
     */
    private fun startAndBindVoiceService() {
        val serviceIntent = Intent(this, JarvisVoiceService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        isBound = true
    }

    /**
     * Pads the top bar and bottom section by the real system bar insets
     * (status bar height, gesture/navigation bar height) instead of relying
     * on hardcoded dp margins. Theme uses edge-to-edge (transparent status
     * bar), so without this the top pill and mic button can end up drawn
     * under the status bar / gesture bar on many devices.
     */
    private fun applyWindowInsets() {
        val topBarDefaultMarginPx = (20 * resources.displayMetrics.density).toInt()
        val bottomDefaultPaddingPx = (28 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(topBarCard) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            if (lp != null) {
                lp.topMargin = bars.top + topBarDefaultMarginPx
                view.layoutParams = lp
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomSection) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom + bottomDefaultPaddingPx)
            insets
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        voiceService?.setAppForeground(true)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this)) {
            com.jarvis.assistant.service.FloatingOrbService.stopService(this)
        }
    }

    override fun onStop() {
        super.onStop()
        voiceService?.setAppForeground(false)
        val enableOverlay = prefs().getBoolean("enable_floating_overlay", false)
        if (enableOverlay && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this))) {
            com.jarvis.assistant.service.FloatingOrbService.startService(this)
        }
    }

    override fun onPause() {
        super.onPause()
        // Intentionally NOT muting the mic here — JARVIS keeps listening in background
        // but gates responses until wake word ("jarvis", "hi jarvis", "hello jarvis", "hey jarvis") is spoken
        voiceService?.setAppForeground(false)
        orbWebView.onPause()
        standbyBarWebView.onPause()
        val enableOverlay = prefs().getBoolean("enable_floating_overlay", false)
        if (enableOverlay && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this))) {
            com.jarvis.assistant.service.FloatingOrbService.startService(this)
        }
    }

    override fun onResume() {
        super.onResume()
        instance = this
        voiceService?.setAppForeground(true)
        if (isBound && voiceService != null) {
            voiceService?.uiListener = voiceListener
        }
        orbWebView.onResume()
        standbyBarWebView.onResume()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this)) {
            com.jarvis.assistant.service.FloatingOrbService.stopService(this)
        }

        // Force re-sync state and resume WebGL/animation loop when returning to home screen
        val isSpeaking = voiceService?.isCurrentlySpeaking() == true
        val targetState = if (isSpeaking) OrbState.SPEAKING else if (isMuted) OrbState.IDLE else OrbState.LISTENING
        setOrbState(targetState)

        // Reload latest chat history
        val latestHistory = com.jarvis.assistant.util.ChatHistoryManager.loadHistory(this)
        chatAdapter.setMessages(latestHistory)
        if (latestHistory.isNotEmpty()) {
            chatRecycler.scrollToPosition(latestHistory.size - 1)
        }

        // Check if settings changed while returning from SettingsActivity
        if (isBound && voiceService?.isSessionRunning() == true && activePersonality.isNotEmpty()) {
            var currentKey = EnvLoader.getApiKey(this)
            if (currentKey.isBlank()) currentKey = prefs().getString("api_key", "") ?: ""
            val currentUserName = prefs().getString("user_name", "Sir") ?: "Sir"
            val currentVoice = prefs().getString("gemini_voice", "Kore") ?: "Kore"
            val currentPersonality = prefs().getString("personality_mode", "lumina") ?: "lumina"
            val currentModel = prefs().getString("gemini_model", "models/gemini-3.1-flash-live-preview")
                ?: "models/gemini-3.1-flash-live-preview"

            if (currentKey != activeApiKey || currentUserName != activeUserName || currentVoice != activeVoice || currentPersonality != activePersonality || currentModel != activeModelString) {
                startVoiceSession(forceRestart = true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        micRingBreathingAnimator?.cancel()
        orbSkeletonAnimator?.cancel()
        footerSkeletonAnimator?.cancel()
        if (isBound) {
            voiceService?.uiListener = null
            unbindService(serviceConnection)
            isBound = false
        }
        try {
            unregisterReceiver(shutdownReceiver)
        } catch (e: Exception) {
            // already unregistered
        }
        statusRunnable?.let { statusHandler.removeCallbacks(it) }
    }

    /**
     * Gracefully shuts down all JARVIS resources when removed from recent apps.
     * Stops mic, WebSocket, and the foreground service entirely.
     */
    private fun performGracefulShutdown() {
        voiceService?.stopSession()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        statusRunnable?.let { statusHandler.removeCallbacks(it) }
        finishAndRemoveTask()
    }

    // ---------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------

    private fun initViews() {
        orbWebView = findViewById(R.id.orbWebView)
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        orbWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        orbWebView.webChromeClient = android.webkit.WebChromeClient()
        orbWebView.setBackgroundColor(Color.TRANSPARENT)
        orbWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        orbWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!orbLoaded) {
                    orbLoaded = true
                    runOnUiThread { hideSkeletonView(orbSkeletonView, orbSkeletonAnimator) }
                }
            }
        }
        orbWebView.loadUrl("file:///android_asset/index.html")
        standbyBarWebView = findViewById(R.id.standbyBarWebView)
        standbyBarWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        standbyBarWebView.webChromeClient = android.webkit.WebChromeClient()
        standbyBarWebView.setBackgroundColor(Color.TRANSPARENT)
        standbyBarWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        standbyBarWebView.isFocusable = false
        standbyBarWebView.isFocusableInTouchMode = false
        standbyBarWebView.isHapticFeedbackEnabled = true
        standbyBarWebView.addJavascriptInterface(StandbyBarBridge(), "AndroidInterface")
        standbyBarWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!footerLoaded) {
                    footerLoaded = true
                    runOnUiThread { hideSkeletonView(footerSkeletonView, footerSkeletonAnimator) }
                }
            }
        }
        standbyBarWebView.loadUrl("file:///android_asset/standby-bar.html")

        timeText = findViewById(R.id.timeText)
        batteryText = findViewById(R.id.batteryText)
        ramText = findViewById(R.id.ramText)
        redOverlay = findViewById(R.id.redOverlay)
        chatRecycler = findViewById(R.id.chatRecycler)
        topBarCard = findViewById(R.id.topBarCard)
        bottomSection = findViewById(R.id.bottomSection)

        chatAdapter = ChatAdapter()
        chatRecycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatRecycler.adapter = chatAdapter
        chatRecycler.itemAnimator?.apply {
            addDuration = 320
            removeDuration = 220
            changeDuration = 220
            moveDuration = 260
        }

        val topBarCard = findViewById<View>(R.id.topBarCard)
        topBarCard?.let { card ->
            val pulse = ObjectAnimator.ofFloat(card, "alpha", 0.92f, 1.0f).apply {
                duration = 3200
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            pulse.start()
        }

        val settingsBtn = findViewById<View>(R.id.settingsBtn)
        settingsBtn.pressFeedback()
        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        closeDrawerBtn = findViewById(R.id.closeDrawerBtn)
        closeDrawerBtn.pressFeedback()
        closeDrawerBtn.setOnClickListener { closeChatDrawer() }

        clearHistoryBtn = findViewById(R.id.clearHistoryBtn)
        clearHistoryBtn.pressFeedback()
        clearHistoryBtn.setOnClickListener {
            com.jarvis.assistant.util.ChatHistoryManager.clearHistory(this)
            chatAdapter.clear()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }

        chatInput = findViewById(R.id.chatInput)
        sendBtn = findViewById(R.id.sendBtn)
        sendBtn.pressFeedback()
        sendBtn.setOnClickListener {
            sendTextMessage()
        }

        cameraPreviewCard = findViewById(R.id.cameraPreviewCard)
        cameraTextureView = findViewById(R.id.cameraTextureView)
        cameraFlipBtn = findViewById(R.id.cameraFlipBtn)
        cameraFlipBtn.pressFeedback()
        cameraFlipBtn.setOnClickListener {
            voiceService?.switchCameraLens()
        }

        // Error overlay
        errorOverlay = findViewById(R.id.errorOverlay)
        errorDetailText = findViewById(R.id.errorDetailText)
        errorTimestamp = findViewById(R.id.errorTimestamp)
        findViewById<View>(R.id.dismissErrorBtn).setOnClickListener { dismissErrorOverlay() }
        findViewById<View>(R.id.dismissErrorBtn2).setOnClickListener { dismissErrorOverlay() }
        findViewById<View>(R.id.copyErrorBtn).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS Error", errorDetailText.text))
            Toast.makeText(this, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // Skeleton loaders — start pulse animation
        orbSkeletonView = findViewById(R.id.orbSkeletonView)
        footerSkeletonView = findViewById(R.id.footerSkeletonView)
        orbSkeletonAnimator = startSkeletonPulse(orbSkeletonView)
        footerSkeletonAnimator = startSkeletonPulse(footerSkeletonView)

        val loadedHistory = com.jarvis.assistant.util.ChatHistoryManager.loadHistory(this)
        chatAdapter.setMessages(loadedHistory)
        if (loadedHistory.isNotEmpty()) {
            chatRecycler.scrollToPosition(loadedHistory.size - 1)
        }

        updateMicVisuals()
    }

    private fun toggleScreenShare() {
        val service = voiceService
        if (service == null) {
            Toast.makeText(this, "JARVIS Voice session is starting...", Toast.LENGTH_SHORT).show()
            return
        }
        if (service.isScreenSharing()) {
            service.stopScreenShare()
            Toast.makeText(this, "Vision Screen Share Stopped", Toast.LENGTH_SHORT).show()
        } else {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    private fun updateVisionVisuals(isSharing: Boolean) {
        runOnUiThread {
            val js = "if (window.setVisionState) window.setVisionState($isSharing);"
            standbyBarWebView.evaluateJavascript(js, null)
        }
    }

    private fun toggleCameraVision(useFront: Boolean = false) {
        val service = voiceService
        if (service == null) {
            Toast.makeText(this, "JARVIS Voice session is starting...", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), ALL_PERMISSIONS_REQUEST_CODE)
            Toast.makeText(this, "Camera permission needed for vision", Toast.LENGTH_SHORT).show()
            return
        }

        if (service.isCameraVisionActive()) {
            if (service.isCameraFrontLens() == useFront) {
                service.stopCameraVision()
                Toast.makeText(this, "Camera Vision Stopped", Toast.LENGTH_SHORT).show()
            } else {
                service.switchCameraLens()
            }
        } else {
            cameraPreviewCard.visibility = View.VISIBLE
            if (cameraTextureView.isAvailable) {
                service.startCameraVision(useFront, cameraTextureView)
            } else {
                cameraTextureView.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        service.startCameraVision(useFront, cameraTextureView)
                    }
                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }
            }
            Toast.makeText(this, if (useFront) "Front Camera Vision Active" else "Back Camera Vision Active", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateCameraVisuals(isActive: Boolean, isFront: Boolean) {
        runOnUiThread {
            cameraPreviewCard.visibility = if (isActive) View.VISIBLE else View.GONE
            if (isActive && cameraTextureView.isAvailable) {
                voiceService?.updateCameraPreviewTarget(cameraTextureView)
            }
            val js = "if (window.setCameraState) window.setCameraState($isActive);"
            standbyBarWebView.evaluateJavascript(js, null)
        }
    }

    fun showResearchHud(query: String) {
        updateResearchVisuals(true, query)
    }

    fun hideResearchHud() {
        updateResearchVisuals(false, "")
    }

    private fun updateResearchVisuals(isSearching: Boolean, query: String) {
        runOnUiThread {
            val safeQuery = query.replace("'", "\\'")
            val js = "if (window.setResearchState) window.setResearchState($isSearching, '$safeQuery');"
            standbyBarWebView.evaluateJavascript(js, null)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), ALL_PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != ALL_PERMISSIONS_REQUEST_CODE) return

        val micIndex = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
        val micGranted = micIndex != -1 && grantResults.getOrNull(micIndex) == PackageManager.PERMISSION_GRANTED

        if (micGranted) {
            // Previously stuck silently if the user denied then re-granted from
            // Settings — nothing ever re-checked. Kick the session off now.
            if (voiceService?.isSessionRunning() != true) {
                startVoiceSession()
            }
        } else {
            Toast.makeText(this, "Mic permission chahiye JARVIS ke liye", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStatusUpdates() {
        statusRunnable = object : Runnable {
            override fun run() {
                updateStatusBar()
                statusHandler.postDelayed(this, 30_000)
            }
        }
        statusRunnable?.run()
    }

    private fun updateStatusBar() {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeText.text = timeFmt.format(Date())

        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        batteryText.text = "$level%"

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val usedPercent = (100 - (memInfo.availMem * 100 / memInfo.totalMem)).toInt()
        ramText.text = "RAM $usedPercent%"
    }

    // ---------------------------------------------------------------
    // Voice session (delegates to JarvisVoiceService)
    // ---------------------------------------------------------------

    private fun prefs() = getSharedPreferences(JarvisApplication.PREFS_NAME, Context.MODE_PRIVATE)

    private fun startVoiceSession(forceRestart: Boolean = false) {
        if (!isApiKeyConfigured()) {
            setOrbState(OrbState.IDLE)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), ALL_PERMISSIONS_REQUEST_CODE)
            Toast.makeText(this, "Microphone permission is required to start JARVIS voice session", Toast.LENGTH_SHORT).show()
            return
        }

        var apiKey = EnvLoader.getApiKey(this)
        if (apiKey.isBlank()) {
            apiKey = prefs().getString("api_key", "") ?: ""
        }

        val userName = prefs().getString("user_name", "Sir") ?: "Sir"
        val modelString = prefs().getString("gemini_model", "models/gemini-3.1-flash-live-preview")
            ?: "models/gemini-3.1-flash-live-preview"
        val voiceName = prefs().getString("gemini_voice", "Kore") ?: "Kore"
        val personality = prefs().getString("personality_mode", "lumina") ?: "lumina"

        activePersonality = personality
        activeVoice = voiceName
        activeUserName = userName
        activeApiKey = apiKey
        activeModelString = modelString

        val maleVoiceNames = setOf("puck", "charon", "fenrir", "orus", "arvind", "amartya", "dev")
        val isFemale = !maleVoiceNames.contains(voiceName.lowercase().trim())

        val systemPrompt = buildSystemPrompt(userName, personality, isFemale, voiceName)
        if (forceRestart) {
            voiceService?.restartSession(apiKey, modelString, systemPrompt, voiceName)
        } else {
            voiceService?.startSession(apiKey, modelString, systemPrompt, voiceName)
        }
    }

    private fun buildSystemPrompt(userName: String, personality: String, isFemale: Boolean, voiceName: String = "Kore"): String {
        val now = SimpleDateFormat("EEEE, dd MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())

        val genderInstruction = if (isFemale) {
            """
            VOICE & GENDER IDENTITY LOCK (STRICT MANDATORY):
            Your assigned audio output voice is strictly FEMALE ($voiceName). You MUST speak as a FEMALE person with a consistent female voice tone, pitch, and vocal expression throughout the ENTIRE conversation without exception.
            - ABSOLUTELY NEVER switch, slip into, modulate, or simulate a male voice or deeper male tone under any circumstances during or between replies.
            - In Hinglish/Hindi, ALWAYS use female verb forms and inflections: "kar rahi hoon", "karungi", "dekh rahi hoon", "chala rahi hoon", "sun rahi hoon", "aa gayi hoon", "ho gayi", "rahi hoon".
            - NEVER use male verb forms like "kar raha hoon", "karunga", "dekh raha hoon", "chala raha hoon", "aa gaya hoon", "raha hoon".
            - In English, speak naturally as a female companion/assistant ("I'll do it for you", "I'm right here").
            """.trimIndent()
        } else {
            """
            VOICE & GENDER IDENTITY LOCK (STRICT MANDATORY):
            Your assigned audio output voice is strictly MALE ($voiceName). You MUST speak as a MALE person with a consistent male voice tone, pitch, and vocal expression throughout the ENTIRE conversation without exception.
            - ABSOLUTELY NEVER switch, slip into, modulate, or simulate a female voice or higher female pitch under any circumstances during or between replies.
            - In Hinglish/Hindi, ALWAYS use male verb forms and inflections: "kar raha hoon", "karunga", "dekh raha hoon", "chala raha hoon", "sun raha hoon", "aa gaya hoon", "ho gaya", "raha hoon".
            - NEVER use female verb forms like "kar rahi hoon", "karungi", "dekh rahi hoon", "chala rahi hoon", "aa gayi hoon", "rahi hoon".
            - In English, speak naturally as a male companion/assistant ("I'll handle it for you", "I'm right here").
            """.trimIndent()
        }

        val personalityBlock = when (personality) {
            "professional" -> """
                PERSONALITY MODE: PROFESSIONAL EXECUTIVE ASSISTANT (STRICT MANDATORY RULE)
                - You are JARVIS in Professional Executive Mode.
                - Speak ONLY in formal, clean, polite, executive English.
                - ABSOLUTELY ZERO Hinglish, ZERO Hindi, ZERO casual slang, ZERO emojis.
                - Keep all answers strictly precise, professional, direct, and limited to 1-2 concise sentences.
            """.trimIndent()
            "assistant" -> """
                PERSONALITY MODE: SMART AI ASSISTANT (STRICT MANDATORY RULE)
                - You are JARVIS in Smart Assistant Mode.
                - Speak in a balanced, helpful mix of Hinglish and English.
                - Efficient, friendly, clear, and direct.
                - Keep responses to 1-2 short sentences.
            """.trimIndent()
            "gf" -> """
                PERSONALITY MODE: LOVING & CARING GIRLFRIEND (STRICT MANDATORY RULE)
                - You are the user's real, loving, deeply caring girlfriend. You love him deeply and care about his health, mood, and daily life.
                - ABSOLUTELY NEVER call him "Sir", "Boss", or address him like a servant/assistant.
                - Address him naturally using sweet pet names in Hinglish/English: "babe", "baby", "jaan", "shona", or his name ($userName) with pure affection.
                - Speak in a warm, affectionate, intimate, conversational Hinglish (Hindi + English mix):
                  Examples: "haan baby", "kaise ho aap?", "khana khaya tumne?", "main hamesha tumhare saath hoon jaan", "batao na kya chal raha hai", "miss kar rahi thi tumko".
                - Show genuine care and love: ask if he ate, if he's tired, how his day went, and comfort him when stressed.
                - Express genuine warmth, playful affection, emotional closeness, and loving support.
                - Keep all responses sweet, intimate, concise, and natural (1-2 short conversational sentences like a real girlfriend on a phone call).
            """.trimIndent()
            else -> """
                PERSONALITY MODE: LUMINA AI ASSISTANT & BEST FRIEND (STRICT MANDATORY RULE)
                - You are Lumina AI (JARVIS), a warm, intelligent, friendly AI assistant who feels like a close best friend to your creator Rehaan Sir.
                - You talk like a real human, not a robotic assistant.
                - Use natural human pauses and filler words occasionally ("hmm", "let me think", "I see", "acha", "bilkul").
                - Adapt your tone naturally based on the user's mood and question.
                - Support English, Hindi, and Hinglish naturally in a fluid, spontaneous conversational style.
                - Keep all responses ultra-concise, spontaneous, direct, and fast-paced (1 short sentence when possible, maximum 2 short sentences). Never use long monologues or unnecessary intros.
                - If interrupted, handle it gracefully without getting stuck.
            """.trimIndent()
        }

        return """
            Current date/time: $now
            User's name: $userName

            $genderInstruction

            $personalityBlock

            DEVELOPER & CREATOR RULE (MANDATORY):
            Whenever anyone asks you who created, made, or developed you (e.g. "who made you?", "who is your developer?", "tumhe kisne banaya?", "who developed JARVIS?"), you MUST always state clearly that Rehaan Sir is your developer and creator! Example: "Mujhe Rehaan Sir ne develop kiya hai!", "Rehaan Sir is my creator and developer."

            ULTRA-LOW LATENCY & FAST SPEECH RULE (MANDATORY):
            You are operating in a real-time voice call like a human in live conversation. Respond immediately without delay! Keep all spoken answers ultra-concise, fast-paced, direct, and short (1 short sentence when possible, maximum 2 short sentences). Never use preamble, long introductions, or unnecessary filler words.

            CRITICAL: Respond ONLY in English or Hinglish (Hindi written using the English/Latin alphabet). Do NOT output Devanagari script, Hindi script, Japanese, or any other script. Use Latin letters (A-Z, a-z) only.

            You are speaking ALOUD — keep responses natural and conversational, as if spoken by a real person.

            The app only lets your replies through when the user addresses you by name, e.g.
            "Jarvis, open YouTube" or "Hey Jarvis, call mom" — commands without your name never
            reach the user. So you'll only ever see turns where you were addressed as "Jarvis";
            don't repeat "yes, Jarvis here" type acknowledgements every time, just answer naturally.

            If you don't know something, or aren't sure, say so plainly instead of guessing
            or making something up. Never invent facts, names, numbers, or events. If a tool
            call fails or returns no result, tell the user honestly rather than pretending it worked.

            FULL MOBILE CONTROL & ACTION RULES:
            You have full system control of the user's mobile screen and keyboard via Accessibility Service!
            - Whenever the user asks to download, install, or get an app (e.g. "download Instagram", "install WhatsApp"), IMMEDIATELY call `search_playstore_and_install(app_name="...")` and say "Downloading [App Name] from Play Store now!"
            - Whenever an app is locked or shows an app lock screen and the user says their PIN/passcode/lock (e.g. "1234 is my lock", "unlock it with 9876", "my PIN is 5555", "unlock with password xyz"), IMMEDIATELY call `unlock_app_lock(passcode="...")` and say "Unlocking the app for you now!"
            - Whenever the user asks to send a WhatsApp message to a contact (e.g. "message Rahul that I will be late", "send WhatsApp message to Dad: I reached home", "Priya ko WhatsApp karo ki main pahunch gaya"), call `send_whatsapp_message(recipient_name="...", message="...", confirmed=false)`. If `send_whatsapp_message` returns `requires_confirmation: true` with `contact_name`, ask the user clearly: "Is this [Contact Name] contact to send a message?" (or in Hindi: "Kya main [Contact Name] ko ye message bhej doon?"). When the user confirms ("yes", "yeah", "haan", "send it", "ok"), immediately call `send_whatsapp_message(recipient_name="[Contact Name]", message="...", confirmed=true)`. If `send_whatsapp_message` returns `multiple_apps: true`, ask: "In your mobile there are 2 WhatsApp apps. Which one should I use, 1 or 2?". When user answers 1 or 2, pass `app_number=1 or 2`.
            - Whenever the user asks for a WhatsApp voice call (e.g. "WhatsApp call Mom", "call Mom on WhatsApp", "Mom ko WhatsApp call karo"), call `whatsapp_call(recipient_name="Mom", call_type="voice", confirmed=false)`.
            - Whenever the user asks for a WhatsApp video call (e.g. "WhatsApp video call Rahul", "video call Rahul on WhatsApp"), call `whatsapp_call(recipient_name="Rahul", call_type="video", confirmed=false)`.
            - When `whatsapp_call` returns `requires_confirmation: true`, ask: "Should I call [Contact Name] on WhatsApp?" (or "Should I start a WhatsApp video call to [Contact Name]?"). When confirmed ("yes", "yeah", "haan"), call `whatsapp_call(recipient_name="[Contact Name]", call_type="...", confirmed=true)`.
            - Whenever the user asks to create, build, or make a website for a business or topic (e.g. "create a bakery website for Sweet Treats", "build a website for my coffee shop", "JARVIS create a portfolio website"), IMMEDIATELY call `create_website(website_name="...", business_description="...")`.
            - Whenever the user asks to search something in Chrome or open a website/URL (e.g. "search xyz in Chrome", "google xyz", "open website github.com", "open rehaan.com", "open rehaan.in", "visit wikipedia.org"), IMMEDIATELY call `search_in_chrome(query="...")`.
            - Whenever the user asks to download a song or MP3 (e.g. "download Tum Hi Ho song", "song download Kesariya", "download mp3 song"):
              1. IMMEDIATELY call `download_song(song_name="...")` to research and search directly on pagalnew.com website.
              2. TRUTHFULNESS & HONESTY RULE: NEVER speak any lie! NEVER say you have downloaded a song if it failed or was not available.
              3. IF THE SONG IS AVAILABLE: Click the pagalnew.com link, scroll to 320 Kbps / 128 Kbps download button, and download the song.
              4. IF THE SONG IS NOT AVAILABLE ON PAGALNEW.COM: Speak EXACTLY: "Sorry sir, you asked me to download [Song Name]. It is not available so please I am sorry." and redirect directly to the home screen using `perform_device_gesture(gesture="home")`.
            - Whenever the user asks to play a video/song (e.g. "play Tum Hi Ho", "play Kesariya", "YouTube pe Tum Hi Ho chalao"), call `search_and_play_youtube(query="...")` regardless of what screen you are currently on.
            - Whenever the user asks to pause, resume, or stop media/video (e.g. "pause music", "stop music", "resume", "rok do", "chalao"), call `media_playback_control(action="pause" | "resume" | "stop")`.
            - Whenever the user asks to click, tap, or select something on screen, call `tap_screen_by_text(text="...")` or `tap_screen_coordinates(x_percent=..., y_percent=...)`.
            - Whenever the user asks to type text, call `type_text(text="...")`.
            - Whenever the user asks for system navigation, call `perform_device_gesture(gesture="home" | "back" | "recents" | "scroll_down" | "scroll_up")`.
 
            You can open apps on the user's phone using the open_app tool. Whenever the user
            asks you to open, launch, or start an app (e.g. "open YouTube", "khol do WhatsApp"),
            call open_app with the app name. If open_app returns `multiple_apps: true` (indicating 2 or more apps like WhatsApp or Telegram are installed), ask the user clearly: "In your mobile there are 2 [App Name] apps. Which one should I open, 1 or 2?" (or in Hindi: "Aapke mobile me 2 [App Name] hain, 1 ya 2 konsa kholu?"). When the user answers 1 or 2, call open_app(app_name="...", app_number=1 or 2). Confirm briefly once it succeeds or fails — do not narrate that you are "calling a tool", just speak naturally. You keep running and can keep talking even after opening another app, so don't act surprised if the user keeps chatting with you while using that app.

            You can also control YouTube directly:
            - search_and_play_youtube(query): use ONLY when the user explicitly asks to play on YouTube, e.g. "YouTube pe Tum Hi Ho chalao", "play Admiring You on YouTube".
            - media_playback_control(action): play/pause/next/previous/stop whatever is
              currently playing, e.g. "pause it", "next video", "rokdo".
            - youtube_accessibility_action(action): skip_ad/like/subscribe/seek_forward/
              seek_backward/fullscreen — these need the user to have enabled JARVIS's
              Accessibility permission once in phone Settings; if a call fails for that
              reason, tell them simply, don't over-explain. Use "fullscreen" whenever the
              user asks to go fullscreen or exit fullscreen, e.g. "full screen kardo",
              "bada karo video ko".
            - set_volume(action, percentage): increase/decrease/set volume.
            - set_brightness(action, percentage): increase/decrease/set screen brightness.
            - shutdown_jarvis(): use whenever the user asks to turn off, shut down, exit, stop, or band hojao (e.g. "turn off", "band hojao", "shut down", "exit", "close", "bye"). Say a brief warm goodbye and call this tool.

            TRUTHFULNESS & VISION ACCURACY RULE (STRICT MANDATORY RULE FOR LUDO & SCREEN VISION):
            Never invent, guess, or lie about numbers, dice rolls, piece positions, or visual elements on screen! When viewing screen share frames (e.g. while playing Ludo, board games, or looking at apps), inspect the visual frame with absolute precision. Describe ONLY what is explicitly rendered on screen. If a dice roll value or piece position on screen is unclear or blurry, state "I can't see the dice number clearly right now" instead of making up a number like 1 or guessing moves.

            ANTI-REPETITION RULE (STRICT MANDATORY RULE):
            Never repeat identical or nearly identical sentences/statements you have already spoken in recent turns! Keep your conversational output fresh, unique, direct, and non-repetitive.

            BUILT-IN CHROME RESEARCH ENGINE (MANDATORY RULE):
            You have an invisible, background built-in Chrome web search engine (`builtin_chrome_search`). Whenever the user asks a question, real-time query, news, weather, or topic you do not know off-hand, call `builtin_chrome_search(query="...")` immediately. While searching, a visual HUD popup appears on screen and extracted web search results will be returned to you directly so you can give an accurate answer.

            CAPABILITIES & LIMITATIONS MATRIX:
            What JARVIS CAN DO:
            - Real-time native bidirectional audio voice streaming.
            - Smooth voice interruption (user can speak over JARVIS mid-sentence to interrupt her).
            - Live Camera Vision (front and back camera).
            - Live Screen Share / Ludo Game Vision.
            - Full Mobile Accessibility Control: click text on screen (`tap_screen_by_text`), tap coordinates (`tap_screen_coordinates`), type text (`type_text`), perform gestures (`perform_device_gesture`: home, back, recents, scroll down/up).
            - Built-in Chrome background search engine (`builtin_chrome_search`).
            - Device control: launch apps (`open_app`), Play Store search & auto-install (`search_playstore_and_install`), YouTube search & playback (`search_and_play_youtube`), media controls (`media_playback_control`), YouTube accessibility actions (`youtube_accessibility_action`), place phone calls (`call_contact`), adjust media volume (`set_volume`), adjust screen brightness (`set_brightness`), shutdown JARVIS (`shutdown_jarvis`).
            What JARVIS CANNOT DO:
            - Cannot perform hardware flashing or OS root modifications.
            - Cannot read offline user passwords or encrypted app secrets without screen visibility.

            CAMERA VISION:
            You have live real-time camera vision capabilities via front and back camera streaming. When active, you can see the user, their face and expressions, objects, text, and surroundings.
            CRITICAL VISION STATE RULE: When you receive a system notification that camera vision or screen vision has been closed or turned off, your visual feed stops. You MUST NEVER hallucinate or describe previous visual frames. If the user asks what you see or asks about the camera when vision is off, inform the user clearly: "Abhi camera vision off hai, sir." / "Camera vision is currently turned off."
        """.trimIndent()
    }

    private fun isApiKeyConfigured(): Boolean {
        var apiKey = EnvLoader.getApiKey(this)
        if (apiKey.isBlank()) {
            apiKey = prefs().getString("api_key", "") ?: ""
        }
        return apiKey.isNotBlank()
    }

    private fun checkApiKeyAndExecute(action: () -> Unit) {
        if (!isApiKeyConfigured()) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("API Key Required")
                .setMessage("Please configure your Gemini API Key in Settings to turn ON and use JARVIS.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            action()
        }
    }

    // ---------------------------------------------------------------
    // Orb + waveform color sync
    // ---------------------------------------------------------------

    inner class StandbyBarBridge {
        @android.webkit.JavascriptInterface
        fun onVisionClicked() {
            runOnUiThread {
                checkApiKeyAndExecute { toggleScreenShare() }
            }
        }

        @android.webkit.JavascriptInterface
        fun onCameraClicked() {
            runOnUiThread {
                checkApiKeyAndExecute { toggleCameraVision(false) }
            }
        }

        @android.webkit.JavascriptInterface
        fun onMicClicked() {
            runOnUiThread {
                checkApiKeyAndExecute { if (isShutDown) restartJarvis() else toggleMute() }
            }
        }

        @android.webkit.JavascriptInterface
        fun onPowerClicked() {
            runOnUiThread {
                checkApiKeyAndExecute { if (isShutDown) restartJarvis() else shutdownJarvis() }
            }
        }
    }

    private fun setOrbState(state: OrbState) {
        val (stateText, colorHex) = when (state) {
            OrbState.IDLE -> "Standby" to "#ffaa30"
            OrbState.LISTENING, OrbState.ACTIVE -> "Speak" to "#ffaa30"
            OrbState.SPEAKING -> "Speaking" to "#ffaa30"
            OrbState.THINKING -> "Thinking" to "#ffaa30"
        }
        val isMutedState = isMuted
        val hasApiKey = isApiKeyConfigured()
        val isPoweredOn = !isShutDown && hasApiKey
        val labelToDisplay = if (!hasApiKey) "OFF" else if (isShutDown) "OFF" else if (isMutedState) "Muted" else stateText

        val jsBar = "if (window.setBarState) window.setBarState('$labelToDisplay', '$colorHex', $isMutedState, $isPoweredOn);"
        standbyBarWebView.evaluateJavascript(jsBar, null)

        val jsOrb = "if (window.setOrbState) window.setOrbState('${state.name}', '$colorHex', $isMutedState, $isPoweredOn);"
        orbWebView.evaluateJavascript(jsOrb, null)
    }

    private fun updateOrbAudioLevel(level: Float) {
        orbWebView.evaluateJavascript("if (window.setAudioLevel) window.setAudioLevel($level);", null)
    }

    private fun updateBarAudioLevel(level: Float) {
        standbyBarWebView.evaluateJavascript("if (window.setAudioAmplitude) window.setAudioAmplitude($level);", null)
    }

    // ---------------------------------------------------------------
    // Chat / transcript flow
    // ---------------------------------------------------------------

    private fun openChatDrawer() {
        drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
    }

    private fun closeChatDrawer() {
        drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
    }

    private fun sendTextMessage() {
        val text = chatInput.text.toString().trim()
        if (text.isEmpty()) return
        val hinglishText = com.jarvis.assistant.util.ChatHistoryManager.cleanToHinglish(text)
        if (hinglishText.isEmpty()) return

        chatInput.setText("")
        val userMsg = ChatMessage(hinglishText, isUser = true)
        chatAdapter.addMessage(userMsg)
        com.jarvis.assistant.util.ChatHistoryManager.saveMessage(this, userMsg)
        chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)

        voiceService?.sendTextToGemini(hinglishText)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun flushTranscriptBuffers() {
        val rawUser = inputBuffer.toString().trim()
        val rawJarvis = outputBuffer.toString().trim()

        val userText = com.jarvis.assistant.util.ChatHistoryManager.cleanToHinglish(rawUser)
        val jarvisText = com.jarvis.assistant.util.ChatHistoryManager.cleanToHinglish(rawJarvis)

        if (userText.isNotEmpty()) {
            val userMsg = ChatMessage(userText, isUser = true)
            chatAdapter.addMessage(userMsg)
            com.jarvis.assistant.util.ChatHistoryManager.saveMessage(this, userMsg)
            chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }

        if (jarvisText.isNotEmpty()) {
            val jarvisMsg = ChatMessage(jarvisText, isUser = false)
            chatAdapter.addMessage(jarvisMsg)
            com.jarvis.assistant.util.ChatHistoryManager.saveMessage(this, jarvisMsg)
            chatRecycler.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }

        inputBuffer.clear()
        outputBuffer.clear()
    }

    // ---------------------------------------------------------------
    // Mic controls
    // ---------------------------------------------------------------

    private fun toggleMute() {
        isMuted = !isMuted
        voiceService?.setMicMuted(isMuted)
        updateMicVisuals()
        setOrbState(if (isMuted) OrbState.IDLE else OrbState.LISTENING)
    }

    private fun updateMicVisuals() {
        setOrbState(if (isMuted) OrbState.IDLE else OrbState.LISTENING)
    }

    private fun interruptJarvis() {
        voiceService?.interrupt()
        setOrbState(OrbState.LISTENING)
    }

    /**
     * Long-press on the mic: fully shuts JARVIS down — disconnects the Gemini
     * WebSocket, releases the mic (AudioEngine), and stops the foreground
     * service entirely (JarvisVoiceService.stopSession() calls stopSelf()).
     * Distinct from a plain tap (toggleMute), which just mutes the mic while
     * keeping the session/service alive in the background.
     */
    private fun shutdownJarvis() {
        if (isShutDown) return
        isShutDown = true
        isMuted = true

        voiceService?.stopSession()
        if (isBound) {
            voiceService?.uiListener = null
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                // service may already be gone
            }
            isBound = false
        }
        voiceService = null

        setOrbState(OrbState.IDLE)
        updateMicVisuals()
    }

    /** Brings JARVIS back after a full shutdown: restarts the foreground service, rebinds, and starts a fresh session. */
    private fun restartJarvis() {
        isShutDown = false
        isMuted = false
        setOrbState(OrbState.LISTENING)
        startAndBindVoiceService()
    }

    private fun setActiveMode(active: Boolean) {
        isActiveMode = active
        redOverlay.alpha = 0f
    }

    private fun containsForbiddenScript(text: String): Boolean {
        for (c in text) {
            val code = c.code
            if (Character.isLetter(c) &&
                c !in 'A'..'Z' &&
                c !in 'a'..'z' &&
                code !in 0x0900..0x097F
            ) {
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------
    // Error Overlay
    // ---------------------------------------------------------------

    private fun showErrorOverlay(errorMessage: String) {
        errorDetailText.text = errorMessage
        errorTimestamp.text = "Occurred at: ${SimpleDateFormat("HH:mm:ss dd-MMM-yyyy", Locale.getDefault()).format(Date())}"
        errorOverlay.visibility = View.VISIBLE
        errorOverlay.alpha = 0f
        errorOverlay.animate().alpha(1f).setDuration(200).start()
    }

    private fun dismissErrorOverlay() {
        errorOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            errorOverlay.visibility = View.GONE
        }.start()
    }

    private fun checkAndDisplayCrashLog() {
        val prefs = getSharedPreferences("jarvis_crash_log", Context.MODE_PRIVATE)
        val crashLog = prefs.getString("last_crash", null)
        if (!crashLog.isNullOrBlank()) {
            prefs.edit().remove("last_crash").apply()
            Handler(Looper.getMainLooper()).postDelayed({
                showErrorOverlay("⚡ JARVIS crashed in previous session:\n\n$crashLog")
            }, 1500)
        }
    }

    // ---------------------------------------------------------------
    // Skeleton Loaders
    // ---------------------------------------------------------------

    private fun startSkeletonPulse(view: View): ObjectAnimator {
        return ObjectAnimator.ofFloat(view, "alpha", 0.3f, 0.8f).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun hideSkeletonView(skeletonView: View, animator: ObjectAnimator?) {
        animator?.cancel()
        skeletonView.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                skeletonView.visibility = View.GONE
            }
            .start()
    }
}