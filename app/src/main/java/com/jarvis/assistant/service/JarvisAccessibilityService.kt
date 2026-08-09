package com.jarvis.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Handles the YouTube actions that neither the Data API nor media keys can
 * reach: tapping "Skip Ad", "Subscribe", "Like", and seeking within a video
 * via double-tap gestures (mirrors YouTube's own touch-seek UX).
 *
 * The user must manually enable this once under
 * Settings > Accessibility > JARVIS (Android does not allow enabling
 * accessibility services programmatically, for security reasons).
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No continuous event handling needed — actions are performed on demand.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
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
                val clicked = performInstallTap()
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

    private fun performInstallTap(): Boolean {
        val root = rootInActiveWindow ?: return false

        // 1. Try finding nodes by text / contentDescription
        val keywords = listOf("install", "update", "get")
        val exclude = listOf("installed", "installing", "uninstall", "cancel")
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        fun scanTextNodes(node: AccessibilityNodeInfo) {
            val label = nodeLabel(node).lowercase()
            if (label.isNotBlank()) {
                val matchesKeyword = keywords.any { label.contains(it) }
                val matchesExclude = exclude.any { label.contains(it) }
                if (matchesKeyword && !matchesExclude) {
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
            if (tapNodeOrParent(candidate)) return true
        }

        // 2. Try finding nodes by Play Store resource ID ("right_button", "install_button", "buy_button")
        val idNeedles = listOf("install_button", "right_button", "buy_button")
        val idCandidates = mutableListOf<AccessibilityNodeInfo>()
        for (needle in idNeedles) {
            collectByResourceId(root, needle, idCandidates)
        }

        for (candidate in idCandidates) {
            if (tapNodeOrParent(candidate)) return true
        }

        return false
    }

    private fun tapNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
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

    /** Auto-clicks 'Install' / 'Get' button when Play Store page is active. */
    fun autoInstallPlayStoreApp(): Boolean {
        val keywords = listOf("install", "get", "download", "update")
        val root = rootInActiveWindow ?: return false
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
}