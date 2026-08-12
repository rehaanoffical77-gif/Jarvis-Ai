package com.jarvis.assistant.firebase

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Firebase Remote Config and Firestore live telemetry sync for Jarvis AI Admin Panel CRM.
 * Syncs user device specs, website generation metrics, and crash logs to Firebase (jarvis-ai-a09b2).
 */
object FirebaseManager {

    private const val TAG = "FirebaseManager"
    private const val KEY_YOUTUBE_API_KEY = "youtube_api_key"

    private var remoteConfig: FirebaseRemoteConfig? = null
    private var firestore: FirebaseFirestore? = null

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }

            firestore = FirebaseFirestore.getInstance()

            remoteConfig = FirebaseRemoteConfig.getInstance().apply {
                val configSettings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(0)
                    .build()
                setConfigSettingsAsync(configSettings)

                val defaults = mapOf<String, Any>(
                    KEY_YOUTUBE_API_KEY to ""
                )
                setDefaultsAsync(defaults)

                fetchAndActivate().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fetchedKey = getString(KEY_YOUTUBE_API_KEY)
                        Log.d(TAG, "Firebase Remote Config fetched. Key present? ${fetchedKey.isNotBlank()}")
                    }
                }
            }

            // Sync live user session on launch
            logUserSession(context)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseManager", e)
        }
    }

    /**
     * Syncs active user session details to Firebase Firestore.
     */
    fun logUserSession(context: Context) {
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_unknown"
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val userMap = hashMapOf<String, Any>(
                "id" to androidId,
                "name" to "Jarvis User ($deviceModel)",
                "email" to "$androidId@jarvis-ai.app",
                "avatar" to Build.MANUFACTURER.substring(0, 1).uppercase(),
                "device" to deviceModel,
                "os" to osVersion,
                "tag" to "power",
                "tagLabel" to "Active App User",
                "lastActive" to "Just now",
                "updatedAt" to dateStr,
                "permissions" to listOf("Mic", "Camera", "Overlay", "Accessibility")
            )

            firestore?.collection("users")?.document(androidId)?.set(userMap)
                ?.addOnSuccessListener { Log.d(TAG, "User session synced to Firebase Firestore") }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user session", e)
        }
    }

    /**
     * Syncs generated website details to Firebase Firestore.
     */
    fun logWebsiteGenerated(context: Context, websiteName: String, niche: String, modelUsed: String, html: String, css: String, js: String) {
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_unknown"
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val webId = "web_${System.currentTimeMillis()}"

            val webMap = hashMapOf<String, Any>(
                "id" to webId,
                "name" to websiteName,
                "niche" to niche,
                "user" to "User ($androidId)",
                "model" to modelUsed,
                "date" to dateStr,
                "html" to html,
                "css" to css,
                "js" to js
            )

            firestore?.collection("websites")?.document(webId)?.set(webMap)
                ?.addOnSuccessListener { Log.d(TAG, "Website logged to Firebase Firestore") }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging website to Firebase", e)
        }
    }

    /**
     * Syncs uncaught exception crash logs to Firebase Firestore.
     */
    fun logCrash(context: Context, throwable: Throwable) {
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_unknown"
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logId = "crash_${System.currentTimeMillis()}"

            val crashMap = hashMapOf<String, Any>(
                "id" to logId,
                "type" to "Crash Trace",
                "timestamp" to dateStr,
                "device" to "${Build.MODEL} ($androidId)",
                "details" to "${throwable.javaClass.name}: ${throwable.message}",
                "severity" to "Error"
            )

            firestore?.collection("crash_logs")?.document(logId)?.set(crashMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging crash to Firebase", e)
        }
    }

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
