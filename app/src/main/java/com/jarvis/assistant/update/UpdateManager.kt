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

/**
 * In-App Auto-Updater Manager for Jarvis AI.
 * Enables direct APK installations to check for remote updates, download APKs,
 * and prompt the system package installer.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"

    // Default remote version metadata URL (Can also be retrieved via Firebase Remote Config)
    private const val DEFAULT_VERSION_URL = "https://raw.githubusercontent.com/rehaanoffical77-gif/Jarvis-Ai/main/version.json"

    @Keep
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String,
        val forceUpdate: Boolean = false
    )

    /**
     * Checks for updates asynchronously without blocking the UI thread.
     */
    fun checkForUpdates(activity: Activity, versionUrl: String = DEFAULT_VERSION_URL) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updateInfo = fetchRemoteVersionInfo(versionUrl) ?: return@launch
                val currentVersionCode = getLocalVersionCode(activity)

                if (updateInfo.versionCode > currentVersionCode) {
                    withContext(Dispatchers.Main) {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showUpdateDialog(activity, updateInfo)
                        }
                    }
                } else {
                    Log.d(TAG, "Jarvis AI is up to date (Code: $currentVersionCode)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
            }
        }
    }

    private fun fetchRemoteVersionInfo(url: String): UpdateInfo? {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null

            val json = JSONObject(body)
            UpdateInfo(
                versionCode = json.optInt("versionCode", 0),
                versionName = json.optString("versionName", "1.0.0"),
                apkUrl = json.optString("apkUrl", ""),
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    /**
     * Displays update dialog with release notes.
     */
    private fun showUpdateDialog(activity: Activity, updateInfo: UpdateInfo) {
        val builder = AlertDialog.Builder(activity)
            .setTitle("🚀 New Jarvis AI Update (v${updateInfo.versionName})")
            .setMessage("What's New:\n${updateInfo.changelog}\n\nWould you like to update now?")
            .setPositiveButton("Update Now") { _, _ ->
                startApkDownload(activity, updateInfo)
            }

        if (!updateInfo.forceUpdate) {
            builder.setNegativeButton("Later", null)
        } else {
            builder.setCancelable(false)
        }

        builder.show()
    }

    /**
     * Downloads the APK file using DownloadManager and initiates installation.
     */
    private fun startApkDownload(context: Context, updateInfo: UpdateInfo) {
        if (updateInfo.apkUrl.isBlank()) {
            Toast.makeText(context, "Invalid update package link", Toast.LENGTH_SHORT).show()
            return
        }

        // Check install package permission for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Please allow install permission to update Jarvis AI", Toast.LENGTH_LONG).show()
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

        val request = DownloadManager.Request(downloadUri).apply {
            setTitle("Downloading Jarvis AI v${updateInfo.versionName}")
            setDescription("Fetching update file...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Register receiver for download completion
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // Receiver already unregistered
                    }
                    installApk(context, destinationFile)
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
    }

    /**
     * Triggers Android system package installer using FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
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
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
