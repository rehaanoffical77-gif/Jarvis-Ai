package com.jarvis.assistant.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * Lets JARVIS open any app installed on the device by spoken name,
 * e.g. "open YouTube", "open camera", "launch WhatsApp".
 *
 * Works by listing every launchable app on the device and fuzzy-matching
 * the spoken name against each app's visible label — no hardcoded
 * package list, so it works for whatever the user has installed.
 */
object AppLauncher {

    data class InstalledApp(val label: String, val packageName: String)

    /**
     * Returns every user-launchable app (has a launcher icon), sorted by label.
     * Only includes activities that are actually enabled and resolvable via
     * getLaunchIntentForPackage, so we never "match" something we can't open.
     */
    fun getLaunchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (e: Exception) {
            android.util.Log.e("AppLauncher", "queryIntentActivities failed", e)
            emptyList()
        }
        return resolveInfos
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (!ri.activityInfo.enabled) return@mapNotNull null
                // Only keep apps we can actually build a launch intent for.
                if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                InstalledApp(ri.loadLabel(pm).toString(), pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Attempts to open an app whose name best matches [spokenName].
     * Matching order: exact label match -> label starts-with -> label contains
     * -> package name contains. Returns the app that was launched, or null if
     * nothing matched or the launch failed for any reason (never throws).
     */
    fun openApp(context: Context, spokenName: String): InstalledApp? {
        val query = spokenName.trim().lowercase()
        if (query.isEmpty()) return null

        val apps = getLaunchableApps(context)

        val match = apps.firstOrNull { it.label.lowercase() == query }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(query) }
            ?: apps.firstOrNull { it.label.lowercase().contains(query) }
            ?: apps.firstOrNull { it.packageName.lowercase().contains(query.replace(" ", "")) }
            ?: return null

        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)
                ?: return null
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)
            match
        } catch (e: Exception) {
            android.util.Log.e("AppLauncher", "Failed to launch ${match.packageName}", e)
            null
        }
    }

    /** True if at least one non-system app label/package matches [spokenName] closely. */
    fun isAppInstalled(context: Context, spokenName: String): Boolean {
        val query = spokenName.trim().lowercase()
        return getLaunchableApps(context).any {
            it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }
}
