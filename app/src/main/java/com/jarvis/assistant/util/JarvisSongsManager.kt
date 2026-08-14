package com.jarvis.assistant.util

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Manages storage access across all device music folders, local song caching,
 * and multi-engine streaming/downloading (JioSaavn + RiPlay YouTube Music + Web Scraper).
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

    /**
     * Scans ALL storage directories (Music/Jarvis Songs, Downloads/Jarvis Songs, Music, Downloads, Audio)
     * for all available local music files.
     */
    fun getPlaylistTracks(): List<SongFile> {
        val searchFolders = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Jarvis Songs"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Jarvis Songs"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
            File(Environment.getExternalStorageDirectory(), "Jarvis Songs")
        )

        val result = mutableListOf<SongFile>()
        val seenPaths = mutableSetOf<String>()

        for (folder in searchFolders) {
            if (folder.exists() && folder.isDirectory) {
                val files = folder.listFiles { _, name ->
                    val l = name.lowercase()
                    l.endsWith(".mp3") || l.endsWith(".m4a") || l.endsWith(".mp4") || l.endsWith(".wav") || l.endsWith(".aac") || l.endsWith(".opus")
                } ?: emptyArray()

                for (f in files) {
                    if (seenPaths.add(f.absolutePath)) {
                        val cleanTitle = f.nameWithoutExtension.replace("_", " ").trim()
                        result.add(SongFile(cleanTitle, f))
                    }
                }
            }
        }
        return result.sortedBy { it.title.lowercase() }
    }

    /**
     * Checks if a song matching [query] already exists in ANY local folder on the device.
     */
    fun findLocalSong(query: String): File? {
        if (query.isBlank()) return null
        val cleanQuery = query.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
        val tracks = getPlaylistTracks()

        for (track in tracks) {
            val trackClean = track.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (trackClean.contains(cleanQuery) || cleanQuery.contains(trackClean)) {
                return track.file
            }
        }
        return null
    }

    /**
     * Multi-Engine Music Retrieval & Download:
     * 1. Checks ALL local storage folders on phone. If found locally, plays INSTANTLY without downloading.
     * 2. Engine 1: JioSaavn Global Audio Engine (320kbps/160kbps MP3s).
     * 3. Engine 2: RiPlay YouTube Music Piped Stream Engine.
     * 4. Engine 3: Web multi-source audio scraper.
     */
    suspend fun getOrDownloadSong(context: Context, songQuery: String): PlayResult = withContext(Dispatchers.IO) {
        if (songQuery.isBlank()) {
            return@withContext PlayResult(success = false, message = "Song name cannot be empty.")
        }

        val cleanTitle = songQuery.trim()
        val localMatch = findLocalSong(cleanTitle)

        // 1. Local Cache Instant Playback
        if (localMatch != null && localMatch.exists() && localMatch.length() > 0) {
            Log.d(TAG, "Instant play from local storage cache: ${localMatch.absolutePath}")
            return@withContext PlayResult(
                success = true,
                songFile = localMatch,
                title = localMatch.nameWithoutExtension.replace("_", " "),
                isCached = true,
                message = "Playing local song from phone storage."
            )
        }

        // 2. Multi-Engine Download to "Jarvis Songs" folder
        try {
            val fileName = sanitizeFileName(cleanTitle) + ".mp3"
            val targetFolder = getJarvisSongsFolder()
            val targetFile = File(targetFolder, fileName)

            Log.d(TAG, "Searching multi-engine audio streams for: $cleanTitle")
            val downloadUrl = findDirectMp3Url(cleanTitle)

            if (!downloadUrl.isNullOrBlank()) {
                Log.d(TAG, "Downloading audio stream from $downloadUrl to ${targetFile.absolutePath}")

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

            Log.w(TAG, "Could not find audio stream link for: $cleanTitle")
            return@withContext PlayResult(
                success = false,
                message = "Sorry sir, I couldn't find an audio link for $cleanTitle."
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
        // Engine 1: JioSaavn Global Audio Engine (Hindi, English, Punjabi, Haryanvi, Pop, EDM, Lo-Fi)
        val saavnAudioUrl = searchJioSaavnApi(songQuery)
        if (!saavnAudioUrl.isNullOrBlank()) {
            Log.d(TAG, "Found high quality audio link via JioSaavn Engine: $saavnAudioUrl")
            return saavnAudioUrl
        }

        // Engine 2: RiPlay YouTube Music Piped Stream Engine
        val riplayAudioUrl = searchYouTubePipedApi(songQuery)
        if (!riplayAudioUrl.isNullOrBlank()) {
            Log.d(TAG, "Found audio stream link via RiPlay YouTube Engine: $riplayAudioUrl")
            return riplayAudioUrl
        }

        // Engine 3: Multi-source web search scraper
        return searchMultiSourceWeb(songQuery)
    }

    private fun searchJioSaavnApi(songQuery: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(songQuery, "UTF-8")
            val searchApiUrl = "https://jiosaavn-api-v3.vercel.app/search?query=$encodedQuery"

            val req = Request.Builder().url(searchApiUrl).header("User-Agent", USER_AGENT).build()
            val resp = httpClient.newCall(req).execute()
            val jsonStr = resp.body?.string() ?: ""

            if (jsonStr.isBlank()) return null
            val rootObj = JSONObject(jsonStr)

            if (rootObj.optBoolean("status", false) && rootObj.has("results")) {
                val resultsArray = rootObj.getJSONArray("results")
                if (resultsArray.length() > 0) {
                    val firstItem = resultsArray.getJSONObject(0)

                    val songId = firstItem.optString("id", "")
                    if (songId.isNotBlank()) {
                        val detailApiUrl = "https://jiosaavn-api-v3.vercel.app/song?id=$songId"
                        val detailReq = Request.Builder().url(detailApiUrl).header("User-Agent", USER_AGENT).build()
                        val detailResp = httpClient.newCall(detailReq).execute()
                        val detailJsonStr = detailResp.body?.string() ?: ""

                        if (detailJsonStr.isNotBlank()) {
                            val detailObj = JSONObject(detailJsonStr)

                            if (detailObj.has("media_urls")) {
                                val mediaUrls = detailObj.getJSONObject("media_urls")
                                val url320 = mediaUrls.optString("320_KBPS", "")
                                if (url320.isNotBlank() && url320.startsWith("http")) return url320

                                val url160 = mediaUrls.optString("160_KBPS", "")
                                if (url160.isNotBlank() && url160.startsWith("http")) return url160
                            }

                            val directMediaUrl = detailObj.optString("media_url", "")
                            if (directMediaUrl.isNotBlank() && directMediaUrl.startsWith("http")) {
                                return directMediaUrl
                            }
                        }
                    }

                    if (firstItem.has("more_info")) {
                        val moreInfo = firstItem.getJSONObject("more_info")
                        val vlink = moreInfo.optString("vlink", "")
                        if (vlink.isNotBlank() && vlink.startsWith("http")) {
                            return vlink
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchJioSaavnApi for '$songQuery': ${e.message}")
        }
        return null
    }

    private fun searchYouTubePipedApi(songQuery: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(songQuery, "UTF-8")
            val instances = listOf("https://pipedapi.kavin.rocks", "https://api.piped.video")

            for (instance in instances) {
                try {
                    val searchApiUrl = "$instance/search?q=$encodedQuery&filter=music_songs"
                    val req = Request.Builder().url(searchApiUrl).header("User-Agent", USER_AGENT).build()
                    val resp = httpClient.newCall(req).execute()
                    val jsonStr = resp.body?.string() ?: ""

                    if (jsonStr.isNotBlank() && jsonStr.startsWith("{")) {
                        val rootObj = JSONObject(jsonStr)
                        if (rootObj.has("items")) {
                            val items = rootObj.getJSONArray("items")
                            if (items.length() > 0) {
                                val firstItem = items.getJSONObject(0)
                                val urlPath = firstItem.optString("url", "")
                                val videoId = urlPath.replace("/watch?v=", "")

                                if (videoId.isNotBlank()) {
                                    val streamApiUrl = "$instance/streams/$videoId"
                                    val streamReq = Request.Builder().url(streamApiUrl).header("User-Agent", USER_AGENT).build()
                                    val streamResp = httpClient.newCall(streamReq).execute()
                                    val streamJson = streamResp.body?.string() ?: ""

                                    if (streamJson.isNotBlank() && streamJson.startsWith("{")) {
                                        val streamObj = JSONObject(streamJson)
                                        if (streamObj.has("audioStreams")) {
                                            val audioStreams = streamObj.getJSONArray("audioStreams")
                                            if (audioStreams.length() > 0) {
                                                val bestAudio = audioStreams.getJSONObject(0)
                                                val audioUrl = bestAudio.optString("url", "")
                                                if (audioUrl.isNotBlank() && audioUrl.startsWith("http")) {
                                                    return audioUrl
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Piped instance $instance failed for $songQuery", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in searchYouTubePipedApi for '$songQuery'", e)
        }
        return null
    }

    private fun searchMultiSourceWeb(songQuery: String): String? {
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
            Log.e(TAG, "Error in searchMultiSourceWeb", e)
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
