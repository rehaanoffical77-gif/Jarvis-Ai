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
     * Fetches free models list from OpenRouter API. Falls back to static list on error.
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
     * Generates HTML, CSS, and JS website files using OpenRouter API and saves them to local storage.
     */
    suspend fun generateWebsite(
        context: Context,
        websiteName: String,
        businessDescription: String
    ): GenerationResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        val model = getSelectedModel(context)
        val cleanName = websiteName.ifBlank { "JarvisWebsite" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")

        val systemPrompt = """
            You are a world-class senior web designer and developer. Your goal is to build an ULTRA-PROFESSIONAL, STUNNING, HIGH-CONVERTING, fully responsive website based on the user's business request.

            CRITICAL FORMATTING & TECHNICAL REQUIREMENTS:
            1. TECH STACK: Write strictly pure HTML5, CSS3, and JavaScript (Vanilla JS). Do NOT use React, Vue, Python, Node, or external build tools.
            2. LINE BREAKS & FORMATTING:
               - Write clean, line-wise indented code with proper formatting for index.html, style.css, and script.js.
               - Do NOT double-escape quotes or newlines.
            3. LINKING & ASSETS:
               - The HTML must link style.css via `<link rel="stylesheet" href="style.css">`.
               - The HTML must link script.js via `<script src="script.js"></script>`.
               - Import Google Fonts (e.g. Google Fonts Outfit, Playfair Display, Inter, or Poppins) in HTML `<head>`.
               - Use high-res Unsplash images (e.g., `https://images.unsplash.com/photo-...`) for hero background, menu/product cards, story section, and highlights.
            4. DESIGN & SECTIONS (Must feel like a premium $10,000 professional website):
               - Header & Navigation: Sticky header with backdrop blur, logo, nav links, CTA button ("Book Table" / "Order Online"), and mobile drawer toggle.
               - Hero Section: Full-bleed hero container with dark gradient overlay, bold typography, subtext, primary CTA button, and secondary action button.
               - Features / Badges: Grid of feature cards with icons (e.g. Organic Ingredients, Master Chefs, Fast Delivery, Ambiance).
               - Menu / Products Grid: Filterable category tabs (All, Starters, Mains, Desserts, Drinks) with card items featuring image, title, price badge, description, and Order button.
               - Story / About Section: Side-by-side layout with image gallery and brand story text.
               - Testimonials: Customer review cards with 5-star ratings, user quotes, and reviewer avatars.
               - Interactive Reservation / Contact: Form with Date, Time, Guests, Name, Phone inputs, plus Opening Hours and Location info card.
               - Footer: Brand info, quick links, social media icons, newsletter form, and copyright line.
            5. CSS & STYLING:
               - Define CSS Variables (`:root`) for color palette (primary accent, secondary, dark bg, card bg, surface, text primary, text muted).
               - Modern glassmorphism, subtle shadows, rounded borders (`border-radius: 12px`), smooth hover transitions (`transition: all 0.3s ease`), flexbox & CSS grid.
               - Full Mobile Responsiveness with `@media (max-width: 768px)`.
            6. JAVASCRIPT:
               - Mobile navigation drawer toggle.
               - Category filter tab click switching.
               - Interactive form submission modal/alert notification.
               - Smooth scrolling for anchor links.

            7. OUTPUT FORMAT:
               Respond ONLY with a valid raw JSON object containing 3 keys: "html", "css", and "js". Do NOT wrap the JSON inside markdown ```json blocks.
               JSON Structure:
               {
                 "html": "<!DOCTYPE html>...",
                 "css": "/* CSS */...",
                 "js": "// JS..."
               }
        """.trimIndent()

        val userPrompt = "Create a modern, complete, responsive, ultra-professional website for: \"$websiteName\". Business description: \"$businessDescription\"."

        var rawHtml = ""
        var rawCss = ""
        var rawJs = ""

        try {
            if (apiKey.isNotBlank()) {
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
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("HTTP-Referer", "https://github.com/jarvis-ai")
                    setRequestProperty("X-Title", "JARVIS AI Assistant")
                    doOutput = true
                    connectTimeout = 25000
                    readTimeout = 45000
                }

                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                if (connection.responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(responseText)
                    val choices = responseJson.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val rawContent = choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
                        var jsonString = rawContent
                        if (jsonString.startsWith("```json")) jsonString = jsonString.removePrefix("```json").trim()
                        else if (jsonString.startsWith("```")) jsonString = jsonString.removePrefix("```").trim()
                        if (jsonString.endsWith("```")) jsonString = jsonString.removeSuffix("```").trim()

                        try {
                            val parsedCode = JSONObject(jsonString)
                            rawHtml = parsedCode.optString("html", "")
                            rawCss = parsedCode.optString("css", "")
                            rawJs = parsedCode.optString("js", "")
                        } catch (pe: Exception) {
                            Log.w(TAG, "Parsing OpenRouter raw JSON failed: ${pe.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter generation exception: ${e.message}")
        }

        // Fallback generator if OpenRouter key is blank or request returned empty
        if (rawHtml.isBlank()) {
            val title = websiteName.ifBlank { "Sweet Artisan Bakery" }
            val desc = businessDescription.ifBlank { "Handcrafted Fresh Pastries, Artisanal Bread & Custom Cakes" }

            rawHtml = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>$title</title>
                    <link rel="stylesheet" href="style.css">
                    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
                </head>
                <body>
                    <header class="header">
                        <div class="logo">🥐 $title</div>
                        <nav class="nav">
                            <a href="#hero">Home</a>
                            <a href="#menu">Our Bakes</a>
                            <a href="#about">Our Story</a>
                            <a href="#contact" class="btn-cta">Order Online</a>
                        </nav>
                    </header>
                    <section id="hero" class="hero">
                        <h1>Freshly Baked Happiness Every Morning</h1>
                        <p>$desc</p>
                        <div class="cta-group">
                            <button class="btn-primary" onclick="scrollToMenu()">Explore Menu</button>
                            <button class="btn-secondary" onclick="handleOrder()">Order Now</button>
                        </div>
                    </section>
                    <section id="menu" class="menu-section">
                        <h2>Signature Baked Delights</h2>
                        <div class="grid">
                            <div class="card">
                                <img src="https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=500" alt="Croissant" class="card-img">
                                <h3>Butter Croissants</h3>
                                <p>Golden, flaky, 100% French butter croissants baked fresh daily at dawn.</p>
                                <div class="card-footer">
                                    <span class="price">\$4.50</span>
                                    <button class="btn-sm" onclick="addToCart('Butter Croissants')">Add to Cart</button>
                                </div>
                            </div>
                            <div class="card">
                                <img src="https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=500" alt="Chocolate Cake" class="card-img">
                                <h3>Velvet Chocolate Cake</h3>
                                <p>Rich Belgian chocolate layers topped with silky ganache and fresh berries.</p>
                                <div class="card-footer">
                                    <span class="price">\$28.00</span>
                                    <button class="btn-sm" onclick="addToCart('Velvet Chocolate Cake')">Add to Cart</button>
                                </div>
                            </div>
                            <div class="card">
                                <img src="https://images.unsplash.com/photo-1509440159596-0249088772ff?w=500" alt="Sourdough" class="card-img">
                                <h3>Artisanal Sourdough</h3>
                                <p>Naturally fermented 36-hour sourdough bread with a crispy crust and soft crumb.</p>
                                <div class="card-footer">
                                    <span class="price">\$7.00</span>
                                    <button class="btn-sm" onclick="addToCart('Artisanal Sourdough')">Add to Cart</button>
                                </div>
                            </div>
                        </div>
                    </section>
                    <footer class="footer">
                        <p>&copy; 2026 $title. All rights reserved. Built with JARVIS AI Website Builder.</p>
                    </footer>
                    <script src="script.js"></script>
                </body>
                </html>
            """.trimIndent()

            rawCss = """
                :root {
                    --primary: #E29578;
                    --primary-hover: #DDA15E;
                    --bg-dark: #0D1117;
                    --surface: #161B22;
                    --border: #30363D;
                    --text-main: #F0F6FC;
                    --text-muted: #8B949E;
                }
                * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif; }
                body { background-color: var(--bg-dark); color: var(--text-main); line-height: 1.6; }
                .header { display: flex; justify-content: space-between; align-items: center; padding: 20px 5%; background: rgba(22, 27, 34, 0.85); backdrop-filter: blur(12px); position: sticky; top: 0; z-index: 100; border-bottom: 1px solid var(--border); }
                .logo { font-size: 1.5rem; font-weight: 700; color: var(--primary); }
                .nav a { color: var(--text-main); text-decoration: none; margin-left: 20px; transition: color 0.3s; }
                .nav a:hover { color: var(--primary-hover); }
                .btn-cta { background: var(--primary); color: #fff !important; padding: 8px 18px; border-radius: 20px; font-weight: 600; }
                .hero { text-align: center; padding: 100px 20px; background: linear-gradient(180deg, rgba(22,27,34,0.7) 0%, rgba(13,17,23,1) 100%), url('https://images.unsplash.com/photo-1509440159596-0249088772ff?w=1200') center/cover; }
                .hero h1 { font-size: 3rem; margin-bottom: 20px; color: #FFFFFF; text-shadow: 0 4px 12px rgba(0,0,0,0.8); }
                .hero p { font-size: 1.2rem; color: #E6EDF3; max-width: 650px; margin: 0 auto 30px; text-shadow: 0 2px 8px rgba(0,0,0,0.8); }
                .btn-primary { background: var(--primary); color: white; border: none; padding: 14px 28px; border-radius: 30px; font-size: 1rem; font-weight: 600; cursor: pointer; margin-right: 15px; }
                .btn-secondary { background: transparent; color: var(--text-main); border: 1px solid var(--border); padding: 14px 28px; border-radius: 30px; font-size: 1rem; font-weight: 600; cursor: pointer; }
                .menu-section { padding: 80px 5%; text-align: center; }
                .menu-section h2 { font-size: 2.2rem; margin-bottom: 40px; color: var(--primary); }
                .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 24px; }
                .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; overflow: hidden; text-align: left; transition: transform 0.3s; }
                .card:hover { transform: translateY(-6px); }
                .card-img { width: 100%; height: 200px; object-fit: cover; }
                .card h3 { font-size: 1.3rem; margin: 16px 16px 8px; color: var(--text-main); }
                .card p { font-size: 0.95rem; color: var(--text-muted); padding: 0 16px 16px; }
                .card-footer { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px 20px; border-top: 1px solid var(--border); }
                .price { font-size: 1.2rem; font-weight: 700; color: var(--primary); }
                .footer { text-align: center; padding: 30px; border-top: 1px solid var(--border); color: var(--text-muted); font-size: 0.9rem; }

                /* --- ULTRA MOBILE RESPONSIVE DESIGN (Professional App Style) --- */
                @media (max-width: 768px) {
                    .header { padding: 14px 16px; flex-direction: column; gap: 12px; text-align: center; }
                    .nav { flex-wrap: wrap; justify-content: center; gap: 10px; }
                    .nav a { margin-left: 0; font-size: 0.9rem; }
                    .hero { padding: 60px 16px; }
                    .hero h1 { font-size: 2rem; }
                    .hero p { font-size: 1rem; }
                    .cta-group { display: flex; flex-direction: column; gap: 12px; }
                    .btn-primary, .btn-secondary { width: 100%; margin-right: 0; }
                    .grid { grid-template-columns: 1fr; gap: 16px; }
                }
            """.trimIndent()

            rawJs = """
                console.log("Bakery Website Initialized Successfully.");
                function scrollToMenu() {
                    document.getElementById("menu").scrollIntoView({ behavior: "smooth" });
                }
                function handleOrder() {
                    alert("Thank you! Your online bakery order is ready for checkout.");
                }
                function addToCart(itemName) {
                    alert(itemName + " has been added to your bakery cart!");
                }
            """.trimIndent()
        }

        val htmlContent = cleanCodeString(rawHtml)
        val cssContent = cleanCodeString(rawCss)
        val jsContent = cleanCodeString(rawJs)

        // Stream true ONE LINE AT A TIME live typing animation
        val htmlLines = htmlContent.lines()
        val cssLines = cssContent.lines()
        val jsLines = jsContent.lines()

        // Phase 1: Write HTML line by line
        for (i in 1..htmlLines.size) {
            val currHtml = htmlLines.take(i).joinToString("\n")
            com.jarvis.assistant.service.WebsiteOverlayService.updateProgress(context, websiteName, currHtml, "", "")
            try { kotlinx.coroutines.delay(110L) } catch (_: Exception) {}
        }

        // Phase 2: Write CSS line by line
        for (i in 1..cssLines.size) {
            val currCss = cssLines.take(i).joinToString("\n")
            com.jarvis.assistant.service.WebsiteOverlayService.updateProgress(context, websiteName, htmlContent, currCss, "")
            try { kotlinx.coroutines.delay(90L) } catch (_: Exception) {}
        }

        // Phase 3: Write JS line by line
        for (i in 1..jsLines.size) {
            val currJs = jsLines.take(i).joinToString("\n")
            com.jarvis.assistant.service.WebsiteOverlayService.updateProgress(context, websiteName, htmlContent, cssContent, currJs)
            try { kotlinx.coroutines.delay(90L) } catch (_: Exception) {}
        }

        // Write files to Documents/JarvisWebsites/[cleanName]
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

        // Mark complete on floating overlay window
        com.jarvis.assistant.service.WebsiteOverlayService.markComplete(
            context, websiteName, targetFolder.absolutePath, htmlContent, cssContent, jsContent
        )

        Log.d(TAG, "Website successfully saved to ${targetFolder.absolutePath}")

        return@withContext GenerationResult(
            success = true,
            message = "Website successfully created in ${targetFolder.name} folder with index.html, style.css, and script.js!",
            folderPath = targetFolder.absolutePath
        )
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
