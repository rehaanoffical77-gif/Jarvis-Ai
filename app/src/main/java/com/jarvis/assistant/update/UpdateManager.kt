package com.jarvis.assistant.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.jarvis.assistant.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * In-App Auto-Updater Manager for Jarvis AI.
 * Handles remote version checking, background APK streaming with status verification,
 * fallback direct downloader, and system package installer launch via FileProvider.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"

    // Default remote version metadata URL
    private const val DEFAULT_VERSION_URL = "https://raw.githubusercontent.com/rehaanoffical77-gif/Jarvis-Ai/main/version.json"

    @Keep
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String,
        val forceUpdate: Boolean = false
    )

    @Volatile
    private var isDialogOpen = false

    @Volatile
    private var isUpdateInProgress = false

    /**
     * Checks for updates asynchronously without blocking the UI thread.
     */
    fun checkForUpdates(activity: Activity, versionUrl: String = DEFAULT_VERSION_URL, manualCheck: Boolean = false) {
        if (isDialogOpen || isUpdateInProgress) {
            Log.d(TAG, "Update check skipped: dialog open = $isDialogOpen, update in progress = $isUpdateInProgress")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updateInfo = fetchRemoteVersionInfo(versionUrl) ?: return@launch
                val currentVersionCode = getLocalVersionCode(activity)

                Log.d(TAG, "Checking updates: Remote Code = ${updateInfo.versionCode}, Installed Local Code = $currentVersionCode")

                // Show update dialog if remote version code is GREATER than local installed version code
                if (updateInfo.versionCode > currentVersionCode) {
                    val prefs = activity.getSharedPreferences("jarvis_update_prefs", Context.MODE_PRIVATE)
                    val dismissedVersion = prefs.getInt("dismissed_version_code", 0)

                    if (manualCheck || (dismissedVersion < updateInfo.versionCode && !isUpdateInProgress) || updateInfo.forceUpdate) {
                        withContext(Dispatchers.Main) {
                            if (!activity.isFinishing && !activity.isDestroyed && !isDialogOpen) {
                                showUpdateDialog(activity, updateInfo)
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Jarvis AI is up to date (Installed Code: $currentVersionCode, Remote Code: ${updateInfo.versionCode})")
                    if (manualCheck) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(activity, "Jarvis AI is up to date (v${com.jarvis.assistant.BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
            }
        }
    }

    private fun fetchRemoteVersionInfo(url: String): UpdateInfo? {
        return try {
            val cacheBusterUrl = if (url.contains("?")) "$url&t=${System.currentTimeMillis()}" else "$url?t=${System.currentTimeMillis()}"
            val client = OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(cacheBusterUrl)
                .header("User-Agent", "Mozilla/5.0 JarvisAI-Android")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            val urlFromKey = json.optString("downloadUrl", "").ifBlank { json.optString("apkUrl", "") }
            val baseApkUrl = if (urlFromKey.isNotBlank()) urlFromKey else "https://raw.githubusercontent.com/rehaanoffical77-gif/Jarvis-Ai/main/Jarvis-AI-Release.apk"
            val finalApkUrl = if (baseApkUrl.contains("?")) "$baseApkUrl&cb=${System.currentTimeMillis()}" else "$baseApkUrl?cb=${System.currentTimeMillis()}"

            UpdateInfo(
                versionCode = json.optInt("versionCode", 0),
                versionName = json.optString("versionName", "1.0.0"),
                apkUrl = finalApkUrl,
                changelog = json.optString("changelog", "Bug fixes and performance improvements."),
                forceUpdate = json.optBoolean("forceUpdate", false)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching version info", e)
            null
        }
    }

    private fun getLocalVersionCode(context: Context): Int {
        return try {
            val pmCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
            if (pmCode > 0) pmCode else com.jarvis.assistant.BuildConfig.VERSION_CODE
        } catch (e: Exception) {
            com.jarvis.assistant.BuildConfig.VERSION_CODE
        }
    }

    /**
     * Displays update dialog with release notes.
     */
    private fun showUpdateDialog(activity: Activity, updateInfo: UpdateInfo) {
        if (isDialogOpen) return
        isDialogOpen = true

        val builder = AlertDialog.Builder(activity)
            .setTitle("🚀 New Jarvis AI Update (v${updateInfo.versionName})")
            .setMessage("What's New:\n${updateInfo.changelog}\n\nWould you like to update now?")
            .setPositiveButton("Update Now") { _, _ ->
                isDialogOpen = false
                isUpdateInProgress = true
                activity.getSharedPreferences("jarvis_update_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("dismissed_version_code", updateInfo.versionCode)
                    .apply()
                startApkDownload(activity, updateInfo)
            }
            .setNeutralButton("Browser Direct") { _, _ ->
                isDialogOpen = false
                isUpdateInProgress = true
                activity.getSharedPreferences("jarvis_update_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("dismissed_version_code", updateInfo.versionCode)
                    .apply()
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(browserIntent)
                } catch (e: Exception) {
                    Toast.makeText(activity, "Error opening browser link", Toast.LENGTH_SHORT).show()
                }
            }

        if (!updateInfo.forceUpdate) {
            builder.setNegativeButton("Later") { _, _ ->
                isDialogOpen = false
                activity.getSharedPreferences("jarvis_update_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("dismissed_version_code", updateInfo.versionCode)
                    .apply()
            }
        } else {
            builder.setCancelable(false)
        }

        builder.setOnDismissListener {
            isDialogOpen = false
        }

        builder.show()
    }

    /**
     * Downloads the APK file using DownloadManager with status query verification
     * and automatic fallback to direct HTTP downloader.
     */
    private fun startApkDownload(context: Context, updateInfo: UpdateInfo) {
        if (updateInfo.apkUrl.isBlank()) {
            Toast.makeText(context, "Invalid update package URL", Toast.LENGTH_SHORT).show()
            return
        }

        // Check install package permission for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Please grant permission to install updates", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            }
        }

        Toast.makeText(context, "Downloading update in background...", Toast.LENGTH_SHORT).show()

        val downloadUri = Uri.parse(updateInfo.apkUrl)
        val fileName = "Jarvis-AI-v${updateInfo.versionName}.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        try {
            val request = DownloadManager.Request(downloadUri).apply {
                setTitle("Downloading Jarvis AI v${updateInfo.versionName}")
                setDescription("Fetching update package...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setMimeType("application/vnd.android.package-archive")
                addRequestHeader("User-Agent", "Mozilla/5.0 JarvisAI-Android")
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            // Already unregistered
                        }

                        // Query DownloadManager status
                        val query = DownloadManager.Query().setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        var success = false

                        if (cursor != null && cursor.moveToFirst()) {
                            val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (statusCol >= 0) cursor.getInt(statusCol) else -1
                            if (status == DownloadManager.STATUS_SUCCESSFUL && destinationFile.exists() && destinationFile.length() > 0) {
                                success = true
                                installApk(context, destinationFile)
                            }
                            cursor.close()
                        }

                        if (!success) {
                            Log.w(TAG, "DownloadManager failed, trying fallback direct download...")
                            downloadDirectFallback(context, updateInfo.apkUrl, destinationFile)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    onComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    onComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "DownloadManager failed to enqueue, attempting direct download", e)
            downloadDirectFallback(context, updateInfo.apkUrl, destinationFile)
        }
    }

    /**
     * Fallback direct OkHttp downloader in case DownloadManager fails or is blocked.
     */
    private fun downloadDirectFallback(context: Context, apkUrl: String, destinationFile: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(apkUrl)
                    .header("User-Agent", "Mozilla/5.0 JarvisAI-Android")
                    .build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful && response.body != null) {
                    val inputStream = response.body!!.byteStream()
                    val outputStream = FileOutputStream(destinationFile)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (destinationFile.exists() && destinationFile.length() > 0) {
                        withContext(Dispatchers.Main) {
                            installApk(context, destinationFile)
                        }
                        return@launch
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to download update APK. Please check connection.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct download fallback failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Update error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Triggers Android system package installer using FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Log.e(TAG, "APK file is missing or empty: ${apkFile.absolutePath}")
            Toast.makeText(context, "Downloaded APK is invalid or corrupt", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }

            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer", e)
            Toast.makeText(context, "Failed to launch package installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
