package com.jarvis.assistant.youtube

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import com.jarvis.assistant.util.EnvLoader

/**
 * Everything JARVIS needs to actually control YouTube:
 *  - search + jump straight to a video (deep link, skips typing in-app)
 *  - play/pause/skip via standard Android media key events (works because
 *    YouTube registers a MediaSession while a video is playing, the same
 *    mechanism headset buttons use)
 *  - volume control
 */
object YouTubeController {

    data class PlayResult(val success: Boolean, val title: String?, val message: String?)

    /**
     * Searches YouTube for [query] and opens the top result directly in the
     * player (via the `vnd.youtube:` deep link, which YouTube's app handles
     * natively). Falls back to opening YouTube's search results screen if no
     * API key is configured or the search fails — still one voice command
     * away from playing, just not fully automatic.
     */
    fun searchAndPlay(context: Context, query: String): PlayResult {
        val apiKey = EnvLoader.getYoutubeApiKey(context)
        android.util.Log.d("YouTubeController", "apiKey blank? ${apiKey.isBlank()} | length=${apiKey.length}")

        if (apiKey.isNotBlank()) {
            val result = YouTubeApiClient.searchTopVideo(apiKey, query)
            if (result != null) {
                val played = playVideoId(context, result.videoId)
                return if (played) {
                    PlayResult(true, result.title, null)
                } else {
                    PlayResult(false, null, "Found the video but couldn't open the YouTube app.")
                }
            }
        }

        // Fallback: no API key, or search failed — open YouTube's search screen & auto-play top result.
        return try {
            val encoded = Uri.encode(query)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                com.jarvis.assistant.service.JarvisAccessibilityService.instance?.clickFirstVideoResult()
            }, 2200)
            PlayResult(true, null, "Opened YouTube search for \"$query\" and starting playback.")
        } catch (e: Exception) {
            android.util.Log.e("YouTubeController", "Fallback search-intent failed", e)
            PlayResult(false, null, "Couldn't open YouTube at all.")
        }
    }

    /** Opens a specific video ID directly in the YouTube app's player. */
    fun playVideoId(context: Context, videoId: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            android.util.Log.e("YouTubeController", "Deep link play failed, trying web fallback", e)
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/watch?v=$videoId")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(webIntent)
                true
            } catch (e2: Exception) {
                android.util.Log.e("YouTubeController", "Web fallback also failed", e2)
                false
            }
        }
    }

    /**
     * Sends a standard media key event (play/pause/next/previous/stop).
     * Controls whatever app currently holds the active media session —
     * in practice this is YouTube whenever a video is actively playing.
     */
    fun sendMediaKey(context: Context, action: String): Boolean {
        val keyCode = when (action.lowercase()) {
            "play", "pause", "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next", "skip", "skip_next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "back", "skip_previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return false
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        } catch (e: Exception) {
            android.util.Log.e("YouTubeController", "Media key dispatch failed", e)
            false
        }
    }

    /** direction: "up" or "down". Adjusts the media/music volume stream. */
    fun adjustVolume(context: Context, direction: String): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val adjust = when (direction.lowercase()) {
            "up", "increase", "louder" -> AudioManager.ADJUST_RAISE
            "down", "decrease", "lower", "quieter" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_MUTE
            "unmute" -> AudioManager.ADJUST_UNMUTE
            else -> return false
        }
        return try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) {
            android.util.Log.e("YouTubeController", "Volume adjust failed", e)
            false
        }
    }
}