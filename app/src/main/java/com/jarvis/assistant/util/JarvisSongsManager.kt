package com.jarvis.assistant.util

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Manages the "Jarvis Songs" storage folder, fast flash downloading of MP3 files,
 * and local playlist indexing for Next / Previous track playback.
 */
object JarvisSongsManager {

    private const val TAG = "JarvisSongsManager"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    data class SongFile(
        val title: String,
        val file: File
    )

    data class PlayResult(
        val success: Boolean,
        val songFile: File? = null,
        val title: String = "",
        val isCached: Boolean = false,
        val message: String = ""
    )

    /** Gets or creates the primary "Jarvis Songs" storage directory. */
    fun getJarvisSongsFolder(): File {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val folder = File(musicDir, "Jarvis Songs")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }

    /** Scans the "Jarvis Songs" folder and returns all available MP3 files. */
    fun getPlaylistTracks(): List<SongFile> {
        val folder = getJarvisSongsFolder()
        val files = folder.listFiles { _, name -> name.lowercase().endsWith(".mp3") } ?: emptyArray()
        return files.sortedBy { it.name.lowercase() }.map { file ->
            val cleanTitle = file.nameWithoutExtension.replace("_", " ").trim()
            SongFile(cleanTitle, file)
        }
    }

    /** Checks if a song matching [query] already exists in "Jarvis Songs". */
    fun findLocalSong(query: String): File? {
        if (query.isBlank()) return null
        val cleanQuery = query.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
        val tracks = getPlaylistTracks()

        // 1. Exact or substring match
        for (track in tracks) {
            val trackClean = track.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (trackClean.contains(cleanQuery) || cleanQuery.contains(trackClean)) {
                return track.file
            }
        }
        return null
    }

    /**
     * Flash Fast Download & Retrieval:
     * 1. If song exists locally in "Jarvis Songs", returns immediately.
     * 2. If not, searches online and downloads directly via fast HTTP stream to "Jarvis Songs".
     */
    suspend fun getOrDownloadSong(context: Context, songQuery: String): PlayResult = withContext(Dispatchers.IO) {
        if (songQuery.isBlank()) {
            return@withContext PlayResult(success = false, message = "Song name cannot be empty.")
        }

        val cleanTitle = songQuery.trim()
        val localMatch = findLocalSong(cleanTitle)

        if (localMatch != null && localMatch.exists() && localMatch.length() > 0) {
            Log.d(TAG, "Instant play from Jarvis Songs local cache: ${localMatch.absolutePath}")
            return@withContext PlayResult(
                success = true,
                songFile = localMatch,
                title = localMatch.nameWithoutExtension.replace("_", " "),
                isCached = true,
                message = "Playing from Jarvis Songs folder."
            )
        }

        // Fast flash download to "Jarvis Songs" folder
        try {
            val fileName = sanitizeFileName(cleanTitle) + ".mp3"
            val targetFolder = getJarvisSongsFolder()
            val targetFile = File(targetFolder, fileName)

            Log.d(TAG, "Searching MP3 link for flash download: $cleanTitle")
            val downloadUrl = findDirectMp3Url(cleanTitle)

            if (!downloadUrl.isNullOrBlank()) {
                Log.d(TAG, "Flash downloading MP3 from $downloadUrl to ${targetFile.absolutePath}")
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "⚡ Flash downloading $cleanTitle to Jarvis Songs...", Toast.LENGTH_SHORT).show()
                }

                val downloadSuccess = downloadFileDirectly(downloadUrl, targetFile)
                if (downloadSuccess && targetFile.exists() && targetFile.length() > 0) {
                    Log.d(TAG, "Flash download successful: ${targetFile.length()} bytes")
                    return@withContext PlayResult(
                        success = true,
                        songFile = targetFile,
                        title = cleanTitle,
                        isCached = false,
                        message = "Downloaded to Jarvis Songs and playing."
                    )
                }
            }

            Log.w(TAG, "Could not find direct MP3 link for: $cleanTitle")
            return@withContext PlayResult(
                success = false,
                message = "Sorry sir, I couldn't find a direct audio link for $cleanTitle."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Flash download error for $cleanTitle", e)
            return@withContext PlayResult(
                success = false,
                message = "Error downloading $cleanTitle: ${e.message}"
            )
        }
    }

    private fun downloadFileDirectly(urlStr: String, destinationFile: File): Boolean {
        return try {
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) return false

            response.body!!.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.exists() && destinationFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error in direct HTTP download: ${e.message}", e)
            if (destinationFile.exists()) destinationFile.delete()
            false
        }
    }

    private fun findDirectMp3Url(songQuery: String): String? {
        try {
            val searchQueries = listOf(
                "site:pagalnew.com $songQuery",
                "pagalnew.com $songQuery",
                "$songQuery mp3 download pagalnew"
            )

            for (sq in searchQueries) {
                val encoded = URLEncoder.encode(sq, "UTF-8")
                val searchUrl = "https://html.duckduckgo.com/html/?q=$encoded"

                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val response = httpClient.newCall(request).execute()
                val html = response.body?.string() ?: ""

                val targetUrls = mutableListOf<String>()
                val linkPattern = Pattern.compile("uddg=([^&\"'\\s]+)", Pattern.CASE_INSENSITIVE)
                val linkMatcher = linkPattern.matcher(html)
                while (linkMatcher.find() && targetUrls.size < 4) {
                    val rawUrl = URLDecoder.decode(linkMatcher.group(1) ?: "", "UTF-8")
                    if (rawUrl.contains("pagalnew.com") && !rawUrl.contains("duckduckgo")) {
                        targetUrls.add(rawUrl)
                    }
                }

                for (targetUrl in targetUrls) {
                    try {
                        val pageReq = Request.Builder().url(targetUrl).header("User-Agent", USER_AGENT).build()
                        val pageResp = httpClient.newCall(pageReq).execute()
                        val pageHtml = pageResp.body?.string() ?: ""

                        val mp3Pattern = Pattern.compile("https?://[^\"'\\s]+\\.mp3", Pattern.CASE_INSENSITIVE)
                        val mp3Matcher = mp3Pattern.matcher(pageHtml)
                        if (mp3Matcher.find()) {
                            return mp3Matcher.group(0)
                        }

                        val hrefPattern = Pattern.compile("href=[\"']([^\"']*(?:320|128|download|mp3)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                        val hrefMatcher = hrefPattern.matcher(pageHtml)
                        while (hrefMatcher.find()) {
                            val href = hrefMatcher.group(1) ?: ""
                            if (!href.contains("javascript") && (href.contains("320") || href.contains("128") || href.endsWith(".mp3") || href.contains("download"))) {
                                val absoluteUrl = if (href.startsWith("http")) href else resolveRelativeUrl(targetUrl, href)
                                if (absoluteUrl.startsWith("http")) return absoluteUrl
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking target URL $targetUrl", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findDirectMp3Url", e)
        }
        return null
    }

    private fun resolveRelativeUrl(baseUrl: String, relativePath: String): String {
        return try {
            java.net.URL(java.net.URL(baseUrl), relativePath).toString()
        } catch (e: Exception) {
            relativePath
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
    }
}
