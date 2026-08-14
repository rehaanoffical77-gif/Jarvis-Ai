package com.jarvis.assistant.util

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Manages storage access across all device music folders, local song caching,
 * and the RiPlay YouTube Music Audio Engine (Innertube + Piped/Cobalt stream resolution).
 */
object JarvisSongsManager {

    private const val TAG = "JarvisSongsManager"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

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
     * RiPlay Engine Retrieval & Flash Download:
     * 1. Checks ALL local storage folders on phone. If found locally, plays INSTANTLY without downloading.
     * 2. RiPlay YouTube Music Engine (Innertube / Piped / Cobalt / Invidious stream extraction).
     * 3. Multi-source audio web scraper fallback.
     */
    suspend fun getOrDownloadSong(context: Context, songQuery: String): PlayResult = withContext(Dispatchers.IO) {
        if (songQuery.isBlank()) {
            return@withContext PlayResult(success = false, message = "Song name cannot be empty.")
        }

        val cleanTitle = songQuery.trim()
        val localMatch = findLocalSong(cleanTitle)

        // 1. Local Storage Instant Playback Cache
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

        // 2. RiPlay Music Engine Stream Download
        try {
            val fileName = sanitizeFileName(cleanTitle) + ".mp3"
            val targetFolder = getJarvisSongsFolder()
            val targetFile = File(targetFolder, fileName)

            Log.d(TAG, "Searching RiPlay YouTube Music Engine for: $cleanTitle")
            val downloadUrl = findRiPlayAudioUrl(cleanTitle)

            if (!downloadUrl.isNullOrBlank()) {
                Log.d(TAG, "RiPlay downloading audio stream from $downloadUrl to ${targetFile.absolutePath}")

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
            Log.e(TAG, "RiPlay download error for $cleanTitle", e)
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

    /**
     * RiPlay Music Engine Stream Resolver:
     * 1. Resolves YouTube Video/Song ID via YouTube Music Innertube Search & Web Search
     * 2. Resolves audio stream via multi-instance Piped / Cobalt / Invidious stream proxies
     * 3. Fallback: Multi-source web search scraper
     */
    private fun findRiPlayAudioUrl(songQuery: String): String? {
        val videoId = searchYouTubeVideoId(songQuery)
        if (!videoId.isNullOrBlank()) {
            Log.d(TAG, "Found YouTube video ID for '$songQuery': $videoId")
            val streamUrl = extractAudioStreamUrl(videoId)
            if (!streamUrl.isNullOrBlank()) {
                return streamUrl
            }
        }

        // Fallback: Web search audio scraper
        return searchMultiSourceWeb(songQuery)
    }

    private fun searchYouTubeVideoId(songQuery: String): String? {
        // 1. YouTube Music Innertube API Search
        try {
            val jsonPayload = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20240401.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": "$songQuery"
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            val videoIdPattern = Pattern.compile("\"videoId\":\\s*\"([a-zA-Z0-9_-]{11})\"")
            val matcher = videoIdPattern.matcher(body)
            if (matcher.find()) {
                val foundId = matcher.group(1)
                if (!foundId.isNullOrBlank()) return foundId
            }
        } catch (e: Exception) {
            Log.e(TAG, "Innertube search failed for '$songQuery'", e)
        }

        // 2. Fallback: YouTube Search HTML Scraper
        try {
            val encodedQuery = URLEncoder.encode("$songQuery song audio", "UTF-8")
            val searchUrl = "https://www.youtube.com/results?search_query=$encodedQuery"

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val videoIdPattern = Pattern.compile("\"videoId\":\\s*\"([a-zA-Z0-9_-]{11})\"")
            val matcher = videoIdPattern.matcher(html)
            if (matcher.find()) {
                val foundId = matcher.group(1)
                if (!foundId.isNullOrBlank()) return foundId
            }
        } catch (e: Exception) {
            Log.e(TAG, "YouTube HTML search failed for '$songQuery'", e)
        }

        return null
    }

    private fun extractAudioStreamUrl(videoId: String): String? {
        val streamInstances = listOf(
            "https://pipedapi.kavin.rocks/streams/",
            "https://api.piped.video/streams/",
            "https://piped-api.garudalinux.org/streams/",
            "https://pipedapi.mha.fi/streams/",
            "https://invidious.privacydev.net/api/v1/videos/",
            "https://yewtu.be/api/v1/videos/"
        )

        for (instanceUrl in streamInstances) {
            try {
                val req = Request.Builder()
                    .url(instanceUrl + videoId)
                    .header("User-Agent", USER_AGENT)
                    .build()

                val resp = httpClient.newCall(req).execute()
                val jsonStr = resp.body?.string() ?: ""

                if (jsonStr.isNotBlank() && jsonStr.startsWith("{")) {
                    val rootObj = JSONObject(jsonStr)

                    // Piped API format
                    if (rootObj.has("audioStreams")) {
                        val audioStreams = rootObj.getJSONArray("audioStreams")
                        if (audioStreams.length() > 0) {
                            for (i in 0 until audioStreams.length()) {
                                val stream = audioStreams.getJSONObject(i)
                                val url = stream.optString("url", "")
                                if (url.isNotBlank() && url.startsWith("http")) {
                                    return url
                                }
                            }
                        }
                    }

                    // Invidious API format
                    if (rootObj.has("adaptiveFormats")) {
                        val formats = rootObj.getJSONArray("adaptiveFormats")
                        for (i in 0 until formats.length()) {
                            val fmt = formats.getJSONObject(i)
                            val type = fmt.optString("type", "")
                            if (type.contains("audio")) {
                                val url = fmt.optString("url", "")
                                if (url.isNotBlank() && url.startsWith("http")) {
                                    return url
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream extraction instance $instanceUrl failed for $videoId", e)
            }
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
