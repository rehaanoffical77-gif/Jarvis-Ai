package com.jarvis.assistant.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the YouTube Data API v3 search endpoint.
 * Used so JARVIS can jump straight to the right video instead of
 * relying on typing into YouTube's own search box.
 */
object YouTubeApiClient {

    data class VideoResult(val videoId: String, val title: String, val channelTitle: String)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Returns the top search result for [query], or null if the API key is
     * missing, the request fails, or nothing was found.
     */
    fun searchTopVideo(apiKey: String, query: String): VideoResult? {
        if (apiKey.isBlank() || query.isBlank()) return null

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&maxResults=1&q=$encodedQuery&key=$apiKey"

        val request = Request.Builder().url(url).get().build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "(no body)"
                    android.util.Log.e("YouTubeApiClient", "Search failed: HTTP ${response.code} — $errorBody")
                    return null
                }
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: return null
                if (items.length() == 0) {
                    android.util.Log.e("YouTubeApiClient", "Search returned 0 items for query: $query")
                    return null
                }

                val item = items.getJSONObject(0)
                val videoId = item.optJSONObject("id")?.optString("videoId") ?: return null
                val snippet = item.optJSONObject("snippet")
                val title = snippet?.optString("title") ?: query
                val channel = snippet?.optString("channelTitle") ?: ""

                if (videoId.isBlank()) null else VideoResult(videoId, title, channel)
            }
        } catch (e: IOException) {
            android.util.Log.e("YouTubeApiClient", "Search failed", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("YouTubeApiClient", "Unexpected error during search", e)
            null
        }
    }
}