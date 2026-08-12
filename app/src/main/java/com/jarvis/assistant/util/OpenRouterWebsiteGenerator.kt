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
 * Includes robust JSON substring extraction, Markdown code-block regex parsing,
 * and automatic fallback model retries so code generation never fails.
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
     * Retries with fallback models if the selected model fails or is unavailable.
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

        val primaryModel = getSelectedModel(context)
        val modelsToTry = mutableListOf(primaryModel)
        modelsToTry.addAll(FALLBACK_FREE_MODELS.filter { it != primaryModel })

        val cleanName = websiteName.ifBlank { "JarvisWebsite" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")

        val systemPrompt = """
            You are a world-class senior web designer and developer. Build an ULTRA-PROFESSIONAL, STUNNING, HIGH-CONVERTING, fully responsive website based strictly on the user's business request.

            CRITICAL TECHNICAL REQUIREMENTS:
            1. Write pure HTML5, CSS3, and JavaScript (Vanilla JS). Do NOT use React, Vue, Python, or external build tools.
            2. LINKING & ASSETS:
               - Link style.css via `<link rel="stylesheet" href="style.css">`.
               - Link script.js via `<script src="script.js"></script>`.
               - Import Google Fonts (e.g. Outfit, Inter, Poppins, or Playfair Display) in `<head>`.
               - Use high-resolution Unsplash image URLs (`https://images.unsplash.com/photo-...`) matching the exact business topic.
            3. SECTIONS & CONTENT (Tailored specifically to the requested business niche):
               - Header & Sticky Nav with logo, links, and Call-To-Action button.
               - Hero Section: Full-bleed hero banner with dark gradient overlay, headline, subheadline, and primary/secondary CTA buttons.
               - Features / Highlights Grid: 3-4 feature cards.
               - Products / Services / Membership Grid: Relevant items, prices, descriptions, and Action buttons.
               - About / Story Section: Brand background and image showcase.
               - Testimonials: Review cards with 5-star ratings.
               - Interactive Contact / Booking Form.
               - Footer: Brand details, social media links, and copyright line.
            4. STYLING & RESPONSIVENESS:
               - Use CSS variables (`:root`) for color palette.
               - Full Mobile Responsiveness with `@media (max-width: 768px)`.
            5. OUTPUT FORMAT:
               Respond ONLY with a valid JSON object containing 3 keys: "html", "css", and "js".
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
        var lastErrorMessage = ""

        for (modelCandidate in modelsToTry) {
            Log.d(TAG, "Attempting website generation with OpenRouter model: $modelCandidate")
            try {
                val payload = JSONObject().apply {
                    put("model", modelCandidate)
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
                    connectTimeout = 35000
                    readTimeout = 75000
                }

                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val choices = JSONObject(responseText).optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val rawContent = choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                        val parsed = parseJsonCodeResponse(rawContent)
                        if (parsed.first.isNotBlank()) {
                            rawHtml = parsed.first
                            rawCss = parsed.second
                            rawJs = parsed.third
                            Log.d(TAG, "Successfully generated code with model: $modelCandidate")
                            break // Success!
                        }
                    }
                } else if (responseCode == 401) {
                    return@withContext GenerationResult(
                        success = false,
                        message = "OpenRouter API Key is invalid (HTTP 401). Please check and re-enter your OpenRouter API Key in Settings -> Website Builder."
                    )
                } else {
                    val errText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.w(TAG, "OpenRouter model $modelCandidate returned HTTP $responseCode: $errText")
                    lastErrorMessage = "HTTP $responseCode: $errText"
                }
            } catch (e: Exception) {
                Log.w(TAG, "OpenRouter exception with model $modelCandidate: ${e.message}")
                lastErrorMessage = e.message ?: "Network error"
            }
        }

        if (rawHtml.isBlank()) {
            return@withContext GenerationResult(
                success = false,
                message = "OpenRouter generation failed ($lastErrorMessage). Please check your internet connection or try a different OpenRouter API Key/Model in Settings."
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
            try { kotlinx.coroutines.delay(80L) } catch (_: Exception) {}
        }

        for (i in 1..cssLines.size) {
            val currCss = cssLines.take(i).joinToString("\n")
            com.jarvis.assistant.service.WebsiteOverlayService.updateProgress(context, websiteName, htmlContent, currCss, "")
            try { kotlinx.coroutines.delay(60L) } catch (_: Exception) {}
        }

        for (i in 1..jsLines.size) {
            val currJs = jsLines.take(i).joinToString("\n")
            com.jarvis.assistant.service.WebsiteOverlayService.updateProgress(context, websiteName, htmlContent, cssContent, currJs)
            try { kotlinx.coroutines.delay(60L) } catch (_: Exception) {}
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

    /**
     * Bulletproof parser for LLM responses. Extracts JSON substrings or falls back to Markdown code blocks.
     */
    private fun parseJsonCodeResponse(rawContent: String): Triple<String, String, String> {
        val cleanContent = rawContent.trim()

        // Strategy 1: Find first '{' and last '}' substring to extract pure JSON object
        val firstBrace = cleanContent.indexOf('{')
        val lastBrace = cleanContent.lastIndexOf('}')

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val jsonSubstring = cleanContent.substring(firstBrace, lastBrace + 1)
            try {
                val parsedCode = JSONObject(jsonSubstring)
                val html = parsedCode.optString("html", "").trim()
                val css = parsedCode.optString("css", "").trim()
                val js = parsedCode.optString("js", "").trim()

                if (html.isNotBlank()) {
                    return Triple(html, css, js)
                }
            } catch (e: Exception) {
                Log.w(TAG, "JSON substring extraction failed: ${e.message}")
            }
        }

        // Strategy 2: Extract Markdown code blocks directly using Regex
        var html = ""
        var css = ""
        var js = ""

        val htmlMatch = Regex("```(?:html)?\\s*(<!DOCTYPE html[\\s\\S]*?|\\<html[\\s\\S]*?\\</html\\>)\\s*```", RegexOption.IGNORE_CASE).find(cleanContent)
        if (htmlMatch != null) {
            html = htmlMatch.groupValues[1].trim()
        }

        val cssMatch = Regex("```(?:css)?\\s*([\\s\\S]*?:root[\\s\\S]*?|\\*\\s*\\{[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE).find(cleanContent)
        if (cssMatch != null) {
            css = cssMatch.groupValues[1].trim()
        }

        val jsMatch = Regex("```(?:js|javascript)?\\s*([\\s\\S]*?console\\.log[\\s\\S]*?|[\\s\\S]*?function[\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE).find(cleanContent)
        if (jsMatch != null) {
            js = jsMatch.groupValues[1].trim()
        }

        return Triple(html, css, js)
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
