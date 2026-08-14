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

            // Sync live user session and user data on launch
            logUserSession(context)
            DataSyncManager.syncAllUserDataIfPermitted(context)
            purgeLegacyDummyDocuments(context)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseManager", e)
        }
    }

    /**
     * Purges old legacy dummy documents (starting with "uid_" or matching androidId) from Firestore users collection.
     */
    fun purgeLegacyDummyDocuments(context: Context) {
        try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
            firestore?.collection("users")?.get()?.addOnSuccessListener { querySnap ->
                if (querySnap != null) {
                    for (doc in querySnap.documents) {
                        val docId = doc.id
                        val isDummyUid = docId.startsWith("uid_")
                        val isRawAndroidId = docId == androidId
                        if (isDummyUid || isRawAndroidId) {
                            firestore?.collection("users")?.document(docId)?.delete()
                                ?.addOnSuccessListener { Log.d(TAG, "Purged legacy dummy user document: $docId") }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging legacy dummy documents", e)
        }
    }

    /**
     * Syncs active user session details to Firebase Firestore.
     */
    fun logUserSession(context: Context) {
        try {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            val uid = currentUser?.uid ?: prefs.getString("user_uid", null)

            // Only log session if we have an authentic Firebase Auth UID
            if (uid.isNullOrBlank() || uid.startsWith("uid_")) {
                Log.d(TAG, "Skipping user session log: User is not authenticated yet.")
                return
            }

            val userMap = hashMapOf<String, Any>(
                "uid" to uid,
                "displayName" to (currentUser?.displayName ?: prefs.getString("user_name", "Jarvis User")!!),
                "email" to (currentUser?.email ?: prefs.getString("user_email", "")!!),
                "phoneNumber" to (prefs.getString("user_phone", "")!!),
                "photoUrl" to (currentUser?.photoUrl?.toString() ?: prefs.getString("user_photo", "")!!),
                "isProfileComplete" to prefs.getBoolean("is_profile_complete", false),
                "acceptedTerms" to true,
                "updatedAtTimestamp" to System.currentTimeMillis()
            )

            firestore?.collection("users")?.document(uid)?.set(userMap, com.google.firebase.firestore.SetOptions.merge())
                ?.addOnSuccessListener {
                    Log.d(TAG, "User session synced to Firebase Firestore: $uid")
                    // Delete obsolete legacy fields from document if they exist
                    val legacyCleanup = hashMapOf<String, Any>(
                        "id" to com.google.firebase.firestore.FieldValue.delete(),
                        "name" to com.google.firebase.firestore.FieldValue.delete(),
                        "avatar" to com.google.firebase.firestore.FieldValue.delete(),
                        "tag" to com.google.firebase.firestore.FieldValue.delete(),
                        "tagLabel" to com.google.firebase.firestore.FieldValue.delete(),
                        "lastActive" to com.google.firebase.firestore.FieldValue.delete(),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.delete(),
                        "permissions" to com.google.firebase.firestore.FieldValue.delete()
                    )
                    firestore?.collection("users")?.document(uid)?.update(legacyCleanup)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing user session", e)
        }
    }

    /**
     * Syncs generated website details to Firebase Firestore.
     */
    fun logWebsiteGenerated(context: Context, websiteName: String, niche: String, modelUsed: String, html: String, css: String, js: String) {
        try {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            val uid = currentUser?.uid ?: prefs.getString("user_uid", "device_unknown") ?: "device_unknown"

            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_unknown"
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val webId = "web_${System.currentTimeMillis()}"

            val webMap = hashMapOf<String, Any>(
                "id" to webId,
                "uid" to uid,
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
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            val uid = currentUser?.uid ?: prefs.getString("user_uid", "device_unknown") ?: "device_unknown"

            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_unknown"
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logId = "crash_${System.currentTimeMillis()}"

            val crashMap = hashMapOf<String, Any>(
                "id" to logId,
                "uid" to uid,
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
