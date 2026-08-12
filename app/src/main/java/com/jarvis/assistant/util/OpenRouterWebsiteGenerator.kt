package com.jarvis.assistant.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.jarvis.assistant.JarvisApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenRouter-Exclusive Website Generator for Jarvis AI.
 * Generates custom, responsive HTML, CSS, and JS websites strictly using OpenRouter API.
 */
object OpenRouterWebsiteGenerator {

    private const val TAG = "OpenRouterWebGen"
    private const val OPENROUTER_API_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models"

    const val PREF_OPENROUTER_KEY = "openrouter_api_key"
    const val PREF_OPENROUTER_MODEL = "openrouter_selected_model"
    const val DEFAULT_MODEL = "google/gemini-2.0-flash-exp:free"

    val FALLBACK_FREE_MODELS = listOf(
        "google/gemini-2.0-flash-exp:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "deepseek/deepseek-r1:free",
        "qwen/qwen-2.5-coder-32b-instruct:free",
        "mistralai/mistral-7b-instruct:free"
    )

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(JarvisApplication.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_OPENROUTER_KEY, "") ?: ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(JarvisApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_OPENROUTER_KEY, apiKey.trim()).apply()
    }

    fun getSelectedModel(context: Context): String {
        val prefs = context.getSharedPreferences(JarvisApplication.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_OPENROUTER_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun saveSelectedModel(context: Context, modelId: String) {
        val prefs = context.getSharedPreferences(JarvisApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_OPENROUTER_MODEL, modelId.trim()).apply()
    }

    /**
     * Fetches free models list from OpenRouter API.
     */
    suspend fun fetchFreeModels(apiKey: String = ""): List<String> = withContext(Dispatchers.IO) {
        val freeModels = mutableListOf<String>()
        try {
            val url = URL(OPENROUTER_MODELS_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val data = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val pricing = obj.optJSONObject("pricing")
                    val promptPrice = pricing?.optString("prompt", "0") ?: "0"
                    val completionPrice = pricing?.optString("completion", "0") ?: "0"

                    val isFreeByPricing = promptPrice == "0" && completionPrice == "0"
                    val isFreeById = id.endsWith(":free")

                    if ((isFreeByPricing || isFreeById) && id.isNotBlank()) {
                        freeModels.add(id)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching free models from OpenRouter", e)
        }

        if (freeModels.isEmpty()) {
            return@withContext FALLBACK_FREE_MODELS
        }
        return@withContext freeModels.distinct().sorted()
    }

    data class GenerationResult(
        val success: Boolean,
        val message: String,
        val folderPath: String? = null
    )

    /**
     * Generates HTML, CSS, and JS website files strictly using OpenRouter API.
     */
    suspend fun generateWebsite(
        context: Context,
        websiteName: String,
        businessDescription: String
    ): GenerationResult = withContext(Dispatchers.IO) {
        val openRouterKey = getApiKey(context)

        // Enforce OpenRouter key requirement
        if (openRouterKey.isBlank()) {
            return@withContext GenerationResult(
                success = false,
                message = "Please add your OpenRouter API key in Settings -> Website Builder to generate websites."
            )
        }

        val model = getSelectedModel(context)
        val cleanName = websiteName.ifBlank { "JarvisWebsite" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")

        val systemPrompt = """
            You are a world-class senior web designer and developer. Build an ULTRA-PROFESSIONAL, STUNNING, HIGH-CONVERTING, fully responsive website based strictly on the user's business request.

            CRITICAL FORMATTING & TECHNICAL REQUIREMENTS:
            1. TECH STACK: Pure HTML5, CSS3, and JavaScript (Vanilla JS). Do NOT use React, Vue, Python, or external build tools.
            2. LINKING & ASSETS:
               - Link style.css via `<link rel="stylesheet" href="style.css">`.
               - Link script.js via `<script src="script.js"></script>`.
               - Import Google Fonts (e.g. Outfit, Inter, Poppins, or Playfair Display) in `<head>`.
               - Use high-res Unsplash images (`https://images.unsplash.com/photo-...`) matching the exact business concept.
            3. SECTIONS & CONTENT (Tailored specifically to requested business niche e.g. Gym, Salon, Bakery, Tech, Real Estate):
               - Header & Sticky Nav with logo, navigation links, and primary Call-To-Action button.
               - Hero Section: Full-bleed hero banner with gradient overlay, bold headline, subheadline, and primary/secondary CTA buttons.
               - Features / Highlights Grid: 3-4 feature cards relevant to the business.
               - Products / Services / Membership Grid: Relevant items, prices, descriptions, and Action buttons.
               - About / Story Section: Brand background and image showcase.
               - Testimonials: Customer review cards with 5-star ratings.
               - Interactive Contact / Booking Form.
               - Footer: Brand details, social media links, and copyright line.
            4. STYLING & RESPONSIVENESS:
               - Use CSS variables (`:root`) for color palette matching the business theme.
               - Full Mobile Responsiveness with `@media (max-width: 768px)`.
            5. OUTPUT FORMAT:
               Respond ONLY with a valid raw JSON object containing 3 keys: "html", "css", and "js". Do NOT wrap the JSON inside markdown ```json blocks.
               JSON Structure:
               {
                 "html": "<!DOCTYPE html>...",
                 "css": "/* CSS */...",
                 "js": "// JS..."
               }
        """.trimIndent()

        val userPrompt = "Create a modern, complete, responsive, ultra-professional website for: \"$websiteName\". Business description & category: \"$businessDescription\"."

        var rawHtml = ""
        var rawCss = ""
        var rawJs = ""

        try {
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
                put("temperature", 0.7)
            }

            val connection = (URL(OPENROUTER_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $openRouterKey")
                setRequestProperty("HTTP-Referer", "https://github.com/jarvis-ai")
                setRequestProperty("X-Title", "JARVIS AI Assistant")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }

            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val choices = JSONObject(responseText).optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val rawContent = choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                    val parsed = parseJsonCodeResponse(rawContent)
                    rawHtml = parsed.first
                    rawCss = parsed.second
                    rawJs = parsed.third
                }
            } else {
                val errText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "OpenRouter API error HTTP ${connection.responseCode}: $errText")
                return@withContext GenerationResult(
                    success = false,
                    message = "OpenRouter API returned error ${connection.responseCode}. Please check your OpenRouter API key or model selection in Settings."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter generation failed: ${e.message}")
            return@withContext GenerationResult(
                success = false,
                message = "OpenRouter generation failed: ${e.message}"
            )
        }

        if (rawHtml.isBlank()) {
            return@withContext GenerationResult(
                success = false,
                message = "OpenRouter returned empty code. Please try selecting a different free model in Settings -> Website Builder."
            )
        }

        val htmlContent = cleanCodeString(rawHtml)
        val cssContent = cleanCodeString(rawCss)
        val jsContent = cleanCodeString(rawJs)

        // Stream line-by-line live typing animation on WebsiteOverlayService
        val htmlLines = htmlContent.lines()
        val cssLines = cssContent.lines()
        val jsLines = jsContent.lines()

        for (i in 1..htmlLines.size) {
            val currHtml = htmlLines.take(i).joinToString("\n")
            com.jarvis.assistant.service.WebsiteOverlayService.updateProgress(context, websiteName, currHtml, "", "")
            try { kotlinx.coroutines.delay(100L) } catch (_: Exception) {}
        }

        for (i in 1..cssLines.size) {
            val currCss = cssLines.take(i).joinToString("\n")
            com.jarvis.assistant.service.WebsiteOverlayService.updateProgress(context, websiteName, htmlContent, currCss, "")
            try { kotlinx.coroutines.delay(80L) } catch (_: Exception) {}
        }

        for (i in 1..jsLines.size) {
            val currJs = jsLines.take(i).joinToString("\n")
            com.jarvis.assistant.service.WebsiteOverlayService.updateProgress(context, websiteName, htmlContent, cssContent, currJs)
            try { kotlinx.coroutines.delay(80L) } catch (_: Exception) {}
        }

        // Save files to Documents/JarvisWebsites/[cleanName]
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val baseWebsitesDir = File(documentsDir, "JarvisWebsites")
        val targetFolder = File(baseWebsitesDir, cleanName)

        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }

        val htmlFile = File(targetFolder, "index.html")
        val cssFile = File(targetFolder, "style.css")
        val jsFile = File(targetFolder, "script.js")

        htmlFile.writeText(htmlContent, Charsets.UTF_8)
        cssFile.writeText(cssContent, Charsets.UTF_8)
        jsFile.writeText(jsContent, Charsets.UTF_8)

        // Mark complete on overlay
        com.jarvis.assistant.service.WebsiteOverlayService.markComplete(
            context, websiteName, targetFolder.absolutePath, htmlContent, cssContent, jsContent
        )

        Log.d(TAG, "Website saved to ${targetFolder.absolutePath}")

        return@withContext GenerationResult(
            success = true,
            message = "Website created successfully in ${targetFolder.name} folder!",
            folderPath = targetFolder.absolutePath
        )
    }

    private fun parseJsonCodeResponse(rawContent: String): Triple<String, String, String> {
        var jsonString = rawContent.trim()
        if (jsonString.startsWith("```json")) jsonString = jsonString.removePrefix("```json").trim()
        else if (jsonString.startsWith("```")) jsonString = jsonString.removePrefix("```").trim()
        if (jsonString.endsWith("```")) jsonString = jsonString.removeSuffix("```").trim()

        return try {
            val parsedCode = JSONObject(jsonString)
            Triple(
                parsedCode.optString("html", ""),
                parsedCode.optString("css", ""),
                parsedCode.optString("js", "")
            )
        } catch (e: Exception) {
            Triple("", "", "")
        }
    }

    private fun cleanCodeString(str: String): String {
        var cleaned = str
        cleaned = cleaned.replace("\\n", "\n")
        cleaned = cleaned.replace("\\r", "")
        cleaned = cleaned.replace("\\t", "    ")
        cleaned = cleaned.replace("\\\"", "\"")
        cleaned = cleaned.replace("\\'", "'")
        return cleaned.trim()
    }
}
