package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Bitmap
import android.view.Display
import android.util.Base64
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Handles the YouTube actions and native screen capture streaming.
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisAccessibilityService"

        /** Non-null while the service is enabled and running. */
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }

    private var streamJob: Job? = null
    private var streamListenerRegistration: ListenerRegistration? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
            ?: getSharedPreferences("jarvis_prefs", MODE_PRIVATE).getString("user_uid", null)
        if (!uid.isNullOrBlank()) {
            startListeningForScreenShareRequests(uid)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No continuous event handling needed — actions are performed on demand.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousScreenStream()
        streamListenerRegistration?.remove()
        if (instance == this) instance = null
    }

    fun startListeningForScreenShareRequests(uid: String) {
        if (uid.isBlank()) return
        try {
            streamListenerRegistration?.remove()
            streamListenerRegistration = FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val requested = snapshot.getBoolean("screenShareRequested") ?: false
                        if (requested) {
                            startContinuousScreenStream(uid)
                        } else {
                            stopContinuousScreenStream()
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching screenShareRequested listener", e)
        }
    }

    fun startContinuousScreenStream(uid: String) {
        if (streamJob?.isActive == true) return
        Log.d(TAG, "Starting high-fps real-time screen stream for UID: $uid")

        streamJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                captureScreenAndUpload(uid)
                delay(200L) // 5 FPS continuous real-time video stream
            }
        }
    }

    fun stopContinuousScreenStream() {
        if (streamJob != null) {
            Log.d(TAG, "Stopping live screen stream")
            streamJob?.cancel()
            streamJob = null
        }
    }

    /**
     * Captures a real-time hardware screen frame of the device display on Android 11+ (API 30+)
     * and streams it to Cloud Firestore doc `users/{uid}`.
     */
    fun captureScreenAndUpload(uid: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    applicationContext.mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                if (bitmap != null) {
                                    val copyBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                                    hardwareBuffer.close()
                                    uploadBitmapToFirestore(uid, copyBitmap)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing screenshot hardware buffer", e)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e(TAG, "Accessibility takeScreenshot failed with code: $errorCode")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking takeScreenshot", e)
            }
        }
    }

    private fun uploadBitmapToFirestore(uid: String, bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                val bytes = stream.toByteArray()
                val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val updates = hashMapOf<String, Any>(
                    "latestScreenBase64" to base64Str,
                    "latestScreenTimestamp" to System.currentTimeMillis(),
                    "latestScreenResolution" to "${bitmap.width}x${bitmap.height}",
                    "isLiveScreenActive" to true,
                    "lastSyncedTimestamp" to System.currentTimeMillis()
                )

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .set(updates, SetOptions.merge())

                Log.d(TAG, "Live device screenshot frame uploaded for UID: $uid (${bitmap.width}x${bitmap.height})")
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding screenshot for Firestore upload", e)
            }
        }
    }

    // ---------------------------------------------------------------
    // Button-tap actions
    // ---------------------------------------------------------------

    /** Taps the "Skip Ad" / "Skip Ads" button if currently visible. Returns true if found and tapped. */
    fun skipAd(): Boolean = clickNodeMatching("skip ad")

    /** Taps the Like button on the currently open video. */
    fun likeVideo(): Boolean = clickNodeMatching("like", exclude = "dislike")

    /** Taps the Subscribe button on the current channel/video. */
    fun subscribeChannel(): Boolean = clickNodeMatching("subscribe")

    /**
     * Opens the channel page by tapping the channel avatar/name row under the
     * video (NOT the "Subscribe" button — that row is usually a separate
     * clickable node right next to it, commonly labelled with just the
     * channel's name, or exposed via a resource id containing "channel").
     */
    fun openChannel(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectByResourceId(root, "channel", candidates)
        val target = candidates
            .filter { it.isClickable }
            .minByOrNull { nodeLabel(it).length }
        if (target != null) return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        // Fallback: a node labelled just "channel" or "go to channel", excluding
        // the subscribe button itself so we don't just re-trigger subscribe.
        return clickNodeMatching("channel", exclude = "subscribe")
    }

    /**
     * Taps the fullscreen toggle button (works for both entering and exiting
     * fullscreen). YouTube's own accessibility label is usually the single
     * word "fullscreen", so that variant has to be checked too — matching
     * only "full screen" (with a space) meant this never found the button.
     * Some YouTube versions leave the button's text/contentDescription empty
     * (icon-only) and only expose it via its resource id, so that's checked
     * as a fallback too.
     */
    fun toggleFullscreen(): Boolean {
        if (clickNodeMatching("full screen", "fullscreen")) return true

        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectByResourceId(root, "fullscreen", candidates)
        val target = candidates.firstOrNull { it.isClickable } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Continuously scans Google Play Store screen over several seconds to find and tap
     * the "Install", "Update", or "Get" button. Handles search lists, app detail pages,
     * resource IDs, and parent node hierarchy walking.
     */
    fun startAutoInstallScanner(appName: String = "") {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var attempts = 0
        val maxAttempts = 16 // 16 attempts * 500ms = 8 seconds total scan window

        val scanRunnable = object : Runnable {
            override fun run() {
                attempts++
                val clicked = performInstallTap(appName)
                if (clicked) {
                    android.util.Log.d(TAG, "Successfully tapped Install button for '$appName' on attempt $attempts")
                    return
                }
                if (attempts < maxAttempts) {
                    handler.postDelayed(this, 500L)
                } else {
                    android.util.Log.w(TAG, "Finished scanning Play Store for '$appName' after $maxAttempts attempts.")
                }
            }
        }
        handler.post(scanRunnable)
    }

    private fun performInstallTap(appName: String = ""): Boolean {
        val root = rootInActiveWindow ?: return false

        fun isInputFieldOrSearchBar(node: AccessibilityNodeInfo): Boolean {
            val cls = node.className?.toString() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (cls.contains("EditText") || cls.contains("AutoCompleteTextView") || cls.contains("SearchView")) return true
            if (viewId.contains("url_bar") || viewId.contains("search_box") || viewId.contains("search_src_text") ||
                viewId.contains("search_plate") || viewId.contains("location_bar") || viewId.contains("toolbar") || viewId.contains("input")) return true
            return false
        }

        // Checks if a node or its parent container is marked as Sponsored / Ad
        fun isSponsoredContainer(node: AccessibilityNodeInfo): Boolean {
            var current: AccessibilityNodeInfo? = node
            var depth = 0
            while (current != null && depth < 6) {
                val label = nodeLabel(current).lowercase()
                if (label.contains("sponsored") || label.contains(" ad ") || label.startsWith("ad ") || label.contains("promoted")) {
                    return true
                }
                current = current.parent
                depth++
            }
            return false
        }

        // 1. Try finding Install / Update / Get buttons on active screen that are NOT inside sponsored ads or search inputs
        val keywords = listOf("install", "update", "get", "download", "इन्स्टॉल", "instalar", "installer")
        val exclude = listOf("installed", "installing", "uninstall", "cancel", "search", "query")
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun scanTextNodes(node: AccessibilityNodeInfo) {
            if (isInputFieldOrSearchBar(node)) return

            val label = nodeLabel(node).lowercase()
            if (label.isNotBlank()) {
                val matchesKeyword = keywords.any { label.contains(it) }
                val matchesExclude = exclude.any { label.contains(it) }
                if (matchesKeyword && !matchesExclude && !isSponsoredContainer(node)) {
                    candidates.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanTextNodes(child)
            }
        }

        scanTextNodes(root)

        for (candidate in candidates.sortedBy { nodeLabel(it).length }) {
            if (!isInputFieldOrSearchBar(candidate) && tapNodeOrParent(candidate)) return true
        }

        // 2. Try finding nodes by Play Store resource ID ("right_button", "install_button", "buy_button", "action_button")
        val idNeedles = listOf("install_button", "right_button", "buy_button", "action_button")
        val idCandidates = mutableListOf<AccessibilityNodeInfo>()
        for (needle in idNeedles) {
            collectByResourceId(root, needle, idCandidates)
        }

        for (candidate in idCandidates) {
            if (!isSponsoredContainer(candidate) && tapNodeOrParent(candidate)) return true
        }

        // 3. If appName is specified and we are on Play Store search list, find non-sponsored card matching appName and tap it
        if (appName.isNotBlank()) {
            val appTitleCandidates = mutableListOf<AccessibilityNodeInfo>()
            fun scanAppTitles(node: AccessibilityNodeInfo) {
                if (isInputFieldOrSearchBar(node)) return
                val label = nodeLabel(node).lowercase()
                if (label.contains(appName.lowercase()) && !isSponsoredContainer(node)) {
                    if (node.isClickable || node.parent?.isClickable == true) {
                        appTitleCandidates.add(node)
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    scanAppTitles(child)
                }
            }
            scanAppTitles(root)
            for (candidate in appTitleCandidates) {
                if (!isInputFieldOrSearchBar(candidate) && tapNodeOrParent(candidate)) return true
            }
        }

        return false
    }

    private fun tapNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val ok = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) return true
            }
            current = current.parent
        }
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            return tapAtAbsoluteCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        return false
    }

    /** Finds clickable nodes whose Android view-id (not label) contains [needle]. */
    private fun collectByResourceId(
        node: AccessibilityNodeInfo,
        needle: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (id.contains(needle)) out.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectByResourceId(child, needle, out)
        }
    }

    /**
     * Finds every currently visible clickable/checkable node whose label
     * contains one of [keywords], then taps the best candidate.
     *
     * A plain "first match wins" search is unreliable on YouTube: a whole
     * channel row (avatar + name + "Subscribe" button) is often exposed as
     * one big accessibility node whose combined label also contains the
     * keyword, e.g. "Example Channel, 1.2M subscribers, Subscribe". If that
     * container is reached before the actual button, tapping it opens the
     * channel page instead of subscribing — which is exactly the bug where
     * "subscribe" opened the channel and reported success anyway.
     *
     * To avoid that, every match is collected first, and the one with the
     * *shortest* label wins — the real button's label ("Subscribe") is
     * always much shorter than a container's combined description.
     */
    private fun clickNodeMatching(vararg keywords: String, exclude: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false
        val lowerKeywords = keywords.map { it.lowercase() }
        val lowerExclude = exclude?.lowercase()

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectMatches(root, lowerKeywords, lowerExclude, candidates)
        if (candidates.isEmpty()) return false

        val target = candidates.minByOrNull { nodeLabel(it).length } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String =
        node.text?.toString() ?: node.contentDescription?.toString() ?: ""

    private fun collectMatches(
        node: AccessibilityNodeInfo,
        keywords: List<String>,
        exclude: String?,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        val label = nodeLabel(node).lowercase()
        if (label.isNotBlank()) {
            val matches = keywords.any { label.contains(it) } && (exclude == null || !label.contains(exclude))
            if (matches && (node.isClickable || node.isCheckable)) {
                out.add(node)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectMatches(child, keywords, exclude, out)
        }
    }

    // ---------------------------------------------------------------
    // Generic Mobile Touch, Typing & Gesture Actions
    // ---------------------------------------------------------------

    /** Click any visible node matching [text]. */
    fun clickNodeWithText(text: String): Boolean {
        if (text.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        val lowerText = text.lowercase()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectMatches(root, listOf(lowerText), null, candidates)
        if (candidates.isEmpty()) {
            // Try partial match without clickability restriction, then walk up parents
            val anyNodes = mutableListOf<AccessibilityNodeInfo>()
            collectAllNodesWithText(root, lowerText, anyNodes)
            for (node in anyNodes) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    current = current.parent
                }
            }
            return false
        }
        val target = candidates.minByOrNull { nodeLabel(it).length } ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Tap at normalized screen percentages (x: 0..100, y: 0..100). */
    fun tapAtPercentage(xPercent: Float, yPercent: Float): Boolean {
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return false
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(metrics) ?: return false

        val x = (metrics.widthPixels * (xPercent.coerceIn(0f, 100f) / 100f))
        val y = (metrics.heightPixels * (yPercent.coerceIn(0f, 100f) / 100f))

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /** Tap at absolute screen pixel coordinates (x, y). */
    fun tapAtAbsoluteCoordinates(x: Float, y: Float): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    /** Type text into the currently focused text field on screen. */
    fun typeText(textToType: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findFirstEditableNode(root) ?: return false

        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /** Execute system navigation gestures: home, back, recents, scroll_down, scroll_up. */
    fun performSystemGesture(action: String): Boolean {
        return when (action.lowercase()) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "recents", "recent_apps" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "scroll_down", "swipe_down" -> scrollScreen(isDown = true)
            "scroll_up", "swipe_up" -> scrollScreen(isDown = false)
            else -> false
        }
    }

    /**
     * Smart screen scrolling & Reels/Shorts auto-change:
     * "scroll_up", "scroll_down", "scroll_to_top", "scroll_to_bottom", "next_reel", "prev_reel"
     */
    fun smartScroll(action: String): Boolean {
        val act = action.lowercase().trim()
        return when {
            act.contains("to_top") || act.contains("boundary_top") -> {
                repeat(5) { scrollScreen(isDown = false); try { Thread.sleep(120) } catch (_: Exception) {} }
                true
            }
            act.contains("to_bottom") || act.contains("boundary_bottom") -> {
                repeat(5) { scrollScreen(isDown = true); try { Thread.sleep(120) } catch (_: Exception) {} }
                true
            }
            act.contains("next_reel") || act.contains("next_short") || act.contains("next") -> {
                swipeVertical(swipeUp = true)
            }
            act.contains("prev_reel") || act.contains("prev_short") || act.contains("previous") -> {
                swipeVertical(swipeUp = false)
            }
            act.contains("up") -> scrollScreen(isDown = false)
            act.contains("down") -> scrollScreen(isDown = true)
            else -> scrollScreen(isDown = true)
        }
    }

    private fun swipeVertical(swipeUp: Boolean): Boolean {
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return false
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(metrics) ?: return false

        val startY = if (swipeUp) metrics.heightPixels * 0.8f else metrics.heightPixels * 0.2f
        val endY = if (swipeUp) metrics.heightPixels * 0.2f else metrics.heightPixels * 0.8f
        val path = Path().apply {
            moveTo(metrics.widthPixels * 0.5f, startY)
            lineTo(metrics.widthPixels * 0.5f, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 250)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Deletes WhatsApp message in active chat screen.
     * [deleteTarget] is "everyone" (Delete for everyone) or "me" (Delete for me).
     */
    fun deleteWhatsAppMessage(deleteTarget: String = "everyone"): Boolean {
        val root = rootInActiveWindow ?: return false

        val messageNodes = mutableListOf<AccessibilityNodeInfo>()
        fun scanChatMessages(node: AccessibilityNodeInfo) {
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val cls = node.className?.toString() ?: ""
            if (viewId.contains("message_text") || viewId.contains("msg_layout") || viewId.contains("conversation_row") ||
                cls.contains("ViewGroup") || cls.contains("RelativeLayout") || cls.contains("LinearLayout")) {
                if (nodeLabel(node).isNotBlank() && (node.isClickable || node.isLongClickable)) {
                    messageNodes.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanChatMessages(child)
            }
        }

        scanChatMessages(root)

        val targetMsgNode = messageNodes.lastOrNull()
        if (targetMsgNode != null) {
            targetMsgNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } else {
            val metrics = DisplayMetrics()
            val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return false
            @Suppress("DEPRECATION")
            wm.defaultDisplay?.getRealMetrics(metrics) ?: return false
            val path = Path().apply { moveTo(metrics.widthPixels * 0.7f, metrics.heightPixels * 0.75f) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 800)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }

        try { Thread.sleep(450) } catch (_: Exception) {}

        val deletedIconTapped = clickNodeMatching("delete", "trash") || tapTrashIcon()
        if (!deletedIconTapped) return false

        try { Thread.sleep(350) } catch (_: Exception) {}

        val isEveryone = deleteTarget.lowercase().contains("everyone") || deleteTarget.lowercase().contains("all")
        val optionTapped = if (isEveryone) {
            clickNodeMatching("delete for everyone", "delete for all")
        } else {
            clickNodeMatching("delete for me")
        }

        if (!optionTapped) {
            clickNodeMatching("delete for me", "delete for everyone", "ok", "delete")
        }

        return true
    }

    private fun tapTrashIcon(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectByResourceId(root, "delete", candidates)
        collectByResourceId(root, "trash", candidates)
        for (c in candidates) {
            if (tapNodeOrParent(c)) return true
        }
        return false
    }

    /** Auto-clicks 'Install' / 'Get' button when Play Store page is active. */
    fun autoInstallPlayStoreApp(): Boolean {
        if (rootInActiveWindow == null) return false
        val keywords = listOf("install", "get", "download", "update")
        for (kw in keywords) {
            if (clickNodeMatching(kw)) return true
        }
        return false
    }

    /** Auto-clicks first video result when YouTube search opens. */
    fun clickFirstVideoResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        collectClickableVideoNodes(root, candidates)
        if (candidates.isNotEmpty()) {
            return candidates.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return tapAtPercentage(50f, 30f)
    }

    private fun scrollScreen(isDown: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (isDown) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val scrollable = findScrollableNode(root)
        if (scrollable != null) {
            return scrollable.performAction(action)
        }
        // Fallback swipe gesture
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return false
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(metrics) ?: return false
        val startY = if (isDown) metrics.heightPixels * 0.7f else metrics.heightPixels * 0.3f
        val endY = if (isDown) metrics.heightPixels * 0.3f else metrics.heightPixels * 0.7f
        val path = Path().apply {
            moveTo(metrics.widthPixels * 0.5f, startY)
            lineTo(metrics.widthPixels * 0.5f, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun collectAllNodesWithText(
        node: AccessibilityNodeInfo,
        text: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        val label = nodeLabel(node).lowercase()
        if (label.contains(text)) out.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllNodesWithText(child, text, out)
        }
    }

    private fun collectClickableVideoNodes(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isClickable && (node.className?.contains("ViewGroup") == true || node.className?.contains("RelativeLayout") == true || node.className?.contains("FrameLayout") == true)) {
            val label = nodeLabel(node)
            if (label.length > 20) out.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableVideoNodes(child, out)
        }
    }

    // ---------------------------------------------------------------
    // App Lock Unlocking Action
    // ---------------------------------------------------------------

    /**
     * Unlocks an App Lock screen by entering the PIN/passcode/password into visible input fields
     * or tapping numeric keypad buttons (0-9) sequentially.
     */
    fun unlockAppLock(passcode: String): Boolean {
        if (passcode.isBlank()) return false
        val root = rootInActiveWindow ?: return false

        var unlocked = false

        // 1. Try entering full string into editable text fields (password / PIN field)
        val editableNode = findFirstEditableNode(root)
        if (editableNode != null) {
            val arguments = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, passcode)
            }
            val setOk = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            if (setOk) {
                unlocked = true
                clickNodeMatching("ok", "enter", "done", "submit", "unlock")
            }
        }

        // 2. If keypad digits exist, tap digit buttons 0-9 sequentially
        if (!unlocked || passcode.all { it.isDigit() }) {
            var digitTapCount = 0
            for (ch in passcode) {
                if (ch.isDigit()) {
                    val digitStr = ch.toString()
                    val tapped = clickDigitKeypadButton(root, digitStr)
                    if (tapped) {
                        digitTapCount++
                        try { Thread.sleep(80L) } catch (_: Exception) {}
                    }
                }
            }
            if (digitTapCount > 0) {
                unlocked = true
                clickNodeMatching("ok", "enter", "done", "submit", "unlock")
            }
        }

        return unlocked
    }

    private fun clickDigitKeypadButton(root: AccessibilityNodeInfo, digit: String): Boolean {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        fun scanDigitNodes(node: AccessibilityNodeInfo) {
            val label = nodeLabel(node).trim()
            if (label == digit && (node.isClickable || node.parent?.isClickable == true)) {
                candidates.add(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanDigitNodes(child)
            }
        }
        scanDigitNodes(root)

        for (candidate in candidates) {
            if (tapNodeOrParent(candidate)) return true
        }

        return clickNodeWithText(digit)
    }

    // ---------------------------------------------------------------
    // Seek gestures (double-tap left/right, mirroring YouTube's own UX)
    // ---------------------------------------------------------------

    fun seekForward(): Boolean = doubleTapAt(xFraction = 0.8f)

    fun seekBackward(): Boolean = doubleTapAt(xFraction = 0.2f)

    private fun doubleTapAt(xFraction: Float): Boolean {
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return false
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(metrics) ?: return false

        val x = metrics.widthPixels * xFraction
        val y = metrics.heightPixels * 0.5f

        val path = Path().apply { moveTo(x, y) }
        val firstTap = GestureDescription.StrokeDescription(path, 0, 50)
        val secondTap = GestureDescription.StrokeDescription(path, 150, 50)

        val gesture1 = GestureDescription.Builder().addStroke(firstTap).build()
        val gesture2 = GestureDescription.Builder().addStroke(secondTap).build()

        val dispatched1 = dispatchGesture(gesture1, null, null)
        return if (dispatched1) {
            dispatchGesture(gesture2, null, null)
        } else {
            false
        }
    }

    // ---------------------------------------------------------------
    // Auto-Tap Chooser Dialog for Dual Apps (Vivo, Samsung, Xiaomi, etc.)
    // ---------------------------------------------------------------

    /**
     * Called when opening a dual/cloned app instance. If an OEM system chooser popup
     * (e.g. Vivo/Samsung/Xiaomi dual app dialog) appears on screen, automatically taps
     * choice 1 or 2 based on [appNumber].
     */
    fun handleDualAppSelection(appName: String, appNumber: Int) {
        val targetIndex = (appNumber - 1).coerceAtLeast(0)
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        mainHandler.postDelayed(object : Runnable {
            var attempts = 0
            override fun run() {
                val root = rootInActiveWindow
                if (root != null) {
                    val appChoices = mutableListOf<AccessibilityNodeInfo>()
                    collectAppChooserNodes(root, appName, appChoices)

                    if (appChoices.size >= 2 && targetIndex < appChoices.size) {
                        val targetNode = appChoices[targetIndex]
                        var clickable: AccessibilityNodeInfo? = targetNode
                        while (clickable != null && !clickable.isClickable) {
                            clickable = clickable.parent
                        }
                        val finalTarget = clickable ?: targetNode
                        finalTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }

                attempts++
                if (attempts < 8) {
                    mainHandler.postDelayed(this, 250)
                }
            }
        }, 200)
    }

    private fun collectAppChooserNodes(
        node: AccessibilityNodeInfo,
        appName: String,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        val label = nodeLabel(node).lowercase()
        val lowerAppName = appName.lowercase()

        if (label.isNotBlank() && (label.contains(lowerAppName) || label.contains("clone") || label.contains("dual") || label.contains("whatsapp"))) {
            if (node.isClickable || (node.parent != null && node.parent.isClickable)) {
                if (!out.contains(node)) {
                    out.add(node)
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAppChooserNodes(child, appName, out)
        }
    }

    /**
     * Monitors the screen after a WhatsApp deep-link is launched and automatically taps the Send button
     * (content-desc="Send" or id="send" or send icon) for hands-free message delivery.
     */
    fun scheduleWhatsAppAutoSend() {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.postDelayed(object : Runnable {
            var attempts = 0
            override fun run() {
                val root = rootInActiveWindow
                if (root != null) {
                    val sendNode = findWhatsAppSendButton(root)
                    if (sendNode != null) {
                        tapNodeOrParent(sendNode)
                        return
                    }
                }

                attempts++
                if (attempts < 12) {
                    mainHandler.postDelayed(this, 300)
                }
            }
        }, 500)
    }

    private fun findWhatsAppSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if (desc == "send" || desc == "send message" || text == "send" || viewId.endsWith(":id/send")) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findWhatsAppSendButton(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Monitors the active WhatsApp chat screen after launching a chat and auto-taps the Voice Call or Video Call icon.
     */
    fun scheduleWhatsAppCallAutoTap(callType: String) {
        val isVideo = callType.lowercase().contains("video")
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.postDelayed(object : Runnable {
            var attempts = 0
            override fun run() {
                val root = rootInActiveWindow
                if (root != null) {
                    val callNode = findWhatsAppCallButton(root, isVideo)
                    if (callNode != null) {
                        tapNodeOrParent(callNode)
                        return
                    }
                }

                attempts++
                if (attempts < 12) {
                    mainHandler.postDelayed(this, 300)
                }
            }
        }, 600)
    }

    private fun findWhatsAppCallButton(node: AccessibilityNodeInfo, isVideo: Boolean): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isTarget = if (isVideo) {
            desc.contains("video call") || desc.contains("video") || viewId.endsWith(":id/video_call")
        } else {
            desc.contains("voice call") || desc.contains("call") || viewId.endsWith(":id/voice_call")
        }

        if (isTarget && (node.isClickable || node.parent?.isClickable == true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findWhatsAppCallButton(child, isVideo)
            if (result != null) return result
        }
        return null
    }

    /**
     * Continuously scans Chrome web page over several seconds to find and tap download links/buttons.
     * Excludes Chrome's URL bar, search boxes, and text input fields.
     */
    fun startSongDownloadScanner(songName: String = "") {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var attempts = 0
        val maxAttempts = 20 // 20 attempts * 500ms = 10 seconds total scan window

        val scanRunnable = object : Runnable {
            override fun run() {
                attempts++
                val clicked = performSongDownloadTap(songName)
                if (clicked) {
                    android.util.Log.d(TAG, "Successfully tapped Download link for '$songName' on attempt $attempts")
                    return
                }
                if (attempts < maxAttempts) {
                    handler.postDelayed(this, 500L)
                } else {
                    android.util.Log.w(TAG, "Finished scanning Chrome page for '$songName' download links after $maxAttempts attempts.")
                }
            }
        }
        // Delay 2 seconds initially so Chrome opens and page loads before scanning
        handler.postDelayed(scanRunnable, 2000L)
    }

    private fun performSongDownloadTap(songName: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val keywords = listOf("download mp3", "download song", "320kbps", "128kbps", "download audio", "download file", "direct download")
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun isInputFieldOrSearchBar(node: AccessibilityNodeInfo): Boolean {
            val cls = node.className?.toString() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (cls.contains("EditText") || cls.contains("AutoCompleteTextView")) return true
            if (viewId.contains("url_bar") || viewId.contains("search_box") || viewId.contains("search_src_text") ||
                viewId.contains("search_plate") || viewId.contains("location_bar") || viewId.contains("toolbar")) return true
            return false
        }

        fun scanDownloadNodes(node: AccessibilityNodeInfo) {
            if (isInputFieldOrSearchBar(node)) {
                return // Do not process search bar or input field
            }

            val label = nodeLabel(node).lowercase()
            if (label.isNotBlank()) {
                // Ignore search query text echoed in search result header or search bar
                val isSearchQueryEcho = songName.isNotBlank() && label == songName.lowercase()
                if (!isSearchQueryEcho && keywords.any { label.contains(it) }) {
                    candidates.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanDownloadNodes(child)
            }
        }

        scanDownloadNodes(root)

        val target = candidates.firstOrNull { node ->
            !isInputFieldOrSearchBar(node) && (node.isClickable || node.parent?.isClickable == true)
        }

        if (target != null) {
            return tapNodeOrParent(target)
        }

        // Phase 2: If no direct download button found on active screen, tap top search result link
        val searchResultCandidates = mutableListOf<AccessibilityNodeInfo>()
        fun scanSearchResultNodes(node: AccessibilityNodeInfo) {
            if (isInputFieldOrSearchBar(node)) return
            val label = nodeLabel(node).lowercase()
            if (label.isNotBlank() && label.length > 8) {
                val isSearchQueryEcho = songName.isNotBlank() && label == songName.lowercase()
                val isResultTitle = label.contains("mp3") || label.contains("song") || label.contains("download") ||
                        label.contains("pagalworld") || label.contains("songspk") || label.contains("jiosaavn") ||
                        (songName.isNotBlank() && label.contains(songName.lowercase()))
                if (!isSearchQueryEcho && isResultTitle) {
                    searchResultCandidates.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanSearchResultNodes(child)
            }
        }

        scanSearchResultNodes(root)

        val resultTarget = searchResultCandidates.firstOrNull { node ->
            !isInputFieldOrSearchBar(node) && (node.isClickable || node.parent?.isClickable == true)
        }

        if (resultTarget != null) {
            android.util.Log.d(TAG, "Tapping search result link: ${nodeLabel(resultTarget)}")
            return tapNodeOrParent(resultTarget)
        }

        return false
    }

    /**
     * Dedicated scanner for pagalnew.com song downloading:
     * Step 1: Tap first search result link on Google for pagalnew.com
     * Step 2: Once pagalnew page opens, scroll down if needed and tap 320 Kbps or 128 Kbps download button!
     */
    fun startPagalNewSongScanner(songName: String = "") {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        // Step 1: Tap top pagalnew.com search result link on Google
        handler.postDelayed({
            val tappedLink = performPagalNewResultTap(songName)
            android.util.Log.d(TAG, "PagalNew Step 1 link tap result: $tappedLink")

            // Step 2: On pagalnew song page, scan for 320kbps / 128kbps download buttons
            var downloadAttempts = 0
            val maxDownloadAttempts = 16

            val downloadScanRunnable = object : Runnable {
                override fun run() {
                    downloadAttempts++
                    var clicked = performPagalNewDownloadTap()
                    
                    // If download button not found on screen yet, scroll down slightly to reveal download options
                    if (!clicked && (downloadAttempts == 3 || downloadAttempts == 6)) {
                        android.util.Log.d(TAG, "Scrolling down pagalnew page to reveal download options...")
                        scrollScreen(isDown = true)
                    }

                    if (clicked) {
                        android.util.Log.d(TAG, "Successfully tapped pagalnew download button on attempt $downloadAttempts")
                        return
                    }

                    if (downloadAttempts < maxDownloadAttempts) {
                        handler.postDelayed(this, 600L)
                    }
                }
            }
            // Delay 2.5 seconds after tapping link so song page opens
            handler.postDelayed(downloadScanRunnable, 2500L)
        }, 1800L)
    }

    private fun performPagalNewResultTap(songName: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun scanResult(node: AccessibilityNodeInfo) {
            val cls = node.className?.toString() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (cls.contains("EditText") || viewId.contains("url_bar") || viewId.contains("search_box")) return

            val label = nodeLabel(node).lowercase()
            if (label.isNotBlank() && label.length > 6) {
                if (label.contains("pagalnew") || (songName.isNotBlank() && label.contains(songName.lowercase()))) {
                    candidates.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanResult(child)
            }
        }

        scanResult(root)

        val target = candidates.firstOrNull { node -> node.isClickable || node.parent?.isClickable == true }
        if (target != null) {
            return tapNodeOrParent(target)
        }
        return false
    }

    private fun performPagalNewDownloadTap(): Boolean {
        val root = rootInActiveWindow ?: return false
        val keywords = listOf("320 kbps", "128 kbps", "320kbps", "128kbps", "download 320", "download 128", "download mp3", "download song", "download")
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun scanDownload(node: AccessibilityNodeInfo) {
            val cls = node.className?.toString() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if (cls.contains("EditText") || viewId.contains("url_bar") || viewId.contains("search_box")) return

            val label = nodeLabel(node).lowercase()
            if (label.isNotBlank()) {
                if (keywords.any { label.contains(it) }) {
                    candidates.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                scanDownload(child)
            }
        }

        scanDownload(root)

        // Prioritize 320kbps or 128kbps buttons
        val bestTarget = candidates.firstOrNull { node ->
            val label = nodeLabel(node).lowercase()
            (label.contains("320") || label.contains("128")) && (node.isClickable || node.parent?.isClickable == true)
        } ?: candidates.firstOrNull { node -> node.isClickable || node.parent?.isClickable == true }

        if (bestTarget != null) {
            return tapNodeOrParent(bestTarget)
        }
        return false
    }
}