package com.jarvis.assistant.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Manages Firebase Remote Config to fetch parameters (like YOUTUBE_API_KEY)
 * securely from the Firebase backend without exposing them in client APK code.
 *
 * NOTE: Strictly NO chat, voice audio, or command history is sent to or stored on Firebase.
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"
    private const val KEY_YOUTUBE_API_KEY = "youtube_api_key"

    private var remoteConfig: FirebaseRemoteConfig? = null

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            remoteConfig = FirebaseRemoteConfig.getInstance().apply {
                val configSettings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(0) // Fetch fresh config instantly for active session
                    .build()
                setConfigSettingsAsync(configSettings)

                // Set local fallback defaults
                val defaults = mapOf<String, Any>(
                    KEY_YOUTUBE_API_KEY to ""
                )
                setDefaultsAsync(defaults)

                // Fetch latest config from Firebase backend
                fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fetchedKey = getString(KEY_YOUTUBE_API_KEY)
                        Log.d(TAG, "Firebase Remote Config updated successfully. YouTube key present? ${fetchedKey.isNotBlank()}")
                    } else {
                        Log.w(TAG, "Firebase Remote Config fetch failed", task.exception)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseManager", e)
        }
    }

    /**
     * Returns the YouTube API key retrieved from Firebase Remote Config backend.
     * Returns empty string if not configured in Firebase backend yet.
     */
    fun getYoutubeApiKey(): String {
        return try {
            val key = remoteConfig?.getString(KEY_YOUTUBE_API_KEY)?.trim() ?: ""
            if (key != "YOUR_YOUTUBE_DATA_API_KEY") key else ""
        } catch (e: Exception) {
            Log.e(TAG, "Error reading YouTube API key from Firebase Remote Config", e)
            ""
        }
    }
}
