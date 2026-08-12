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
 * Advanced Multi-Engine Website Generator for Jarvis AI.
 * Uses OpenRouter API, Gemini AI REST API, or Smart Category Template Generator
 * to build custom, ultra-professional HTML, CSS, and JS websites for any business niche.
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
     * Generates HTML, CSS, and JS website files tailored specifically to the requested business.
     */
    suspend fun generateWebsite(
        context: Context,
        websiteName: String,
        businessDescription: String
    ): GenerationResult = withContext(Dispatchers.IO) {
        val openRouterKey = getApiKey(context)
        val geminiKey = EnvLoader.getApiKey(context)
        val model = getSelectedModel(context)
        val cleanName = websiteName.ifBlank { "JarvisWebsite" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")

        val systemPrompt = """
            You are a world-class senior web designer and developer. Build an ULTRA-PROFESSIONAL, STUNNING, HIGH-CONVERTING, fully responsive website based strictly on the user's business request.

            CRITICAL FORMATTING & TECHNICAL REQUIREMENTS:
            1. TECH STACK: Pure HTML5, CSS3, and JavaScript (Vanilla JS). Do NOT use React, Vue, Python, or external build tools.
            2. LINKING:
               - Link style.css via `<link rel="stylesheet" href="style.css">`.
               - Link script.js via `<script src="script.js"></script>`.
               - Import Google Fonts (e.g. Outfit, Inter, Poppins, or Playfair Display) in `<head>`.
               - Use high-res Unsplash images (`https://images.unsplash.com/photo-...`) matching the business category.
            3. DYNAMIC CONTENT & SECTIONS (Tailored strictly to the requested business niche e.g. Gym, Salon, Bakery, Tech, Real Estate):
               - Header & Sticky Nav with logo, navigation links, and primary Call-To-Action button.
               - Hero Section: Full-bleed hero banner with gradient overlay, bold headline, subheadline, and primary/secondary CTA buttons.
               - Features / Highlights Grid: 3-4 feature cards relevant to the business.
               - Products / Services / Membership Grid: Filterable or card grid displaying relevant items, prices, descriptions, and Action buttons.
               - About / Story Section: Brand background and image showcase.
               - Testimonials: Customer review cards with 5-star ratings.
               - Interactive Contact / Booking Form tailored to the business (e.g. Appointment Booking for Salons, Membership Signup for Gyms, Reservations for Restaurants).
               - Footer: Brand details, social media links, and copyright.
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

        // Engine 1: OpenRouter API (if configured)
        if (openRouterKey.isNotBlank()) {
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
                    connectTimeout = 25000
                    readTimeout = 45000
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "OpenRouter generation failed: ${e.message}")
            }
        }

        // Engine 2: Google Gemini REST API (if OpenRouter blank or failed)
        if (rawHtml.isBlank() && geminiKey.isNotBlank()) {
            try {
                val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiKey"
                val promptText = "$systemPrompt\n\nUser Request: $userPrompt"
                
                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", promptText) })
                            })
                        })
                    })
                }

                val connection = (URL(geminiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 25000
                    readTimeout = 45000
                }

                connection.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                if (connection.responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val candidates = JSONObject(responseText).optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val rawText = parts.getJSONObject(0).optString("text", "")
                            val parsed = parseJsonCodeResponse(rawText)
                            rawHtml = parsed.first
                            rawCss = parsed.second
                            rawJs = parsed.third
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemini REST API generation failed: ${e.message}")
            }
        }

        // Engine 3: Smart Category Template Generator (Fallback for any business niche)
        if (rawHtml.isBlank()) {
            val template = generateCategoryNicheWebsite(websiteName, businessDescription)
            rawHtml = template.first
            rawCss = template.second
            rawJs = template.third
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

    /**
     * Smart Category Detector: Generates a tailored template based on business keywords (Gym, Salon, Bakery, Tech, Real Estate, E-Commerce, Corporate).
     */
    private fun generateCategoryNicheWebsite(websiteName: String, businessDescription: String): Triple<String, String, String> {
        val query = "$websiteName $businessDescription".lowercase()
        val title = websiteName.ifBlank { "Professional Business" }
        val desc = businessDescription.ifBlank { "Delivering excellence and premium quality services." }

        return when {
            // 🏋️‍♂️ GYM & FITNESS
            query.contains("gym") || query.contains("fitness") || query.contains("workout") || query.contains("trainer") || query.contains("crossfit") || query.contains("muscle") || query.contains("bodybuilding") -> {
                generateGymWebsite(title, desc)
            }
            // ✂️ SALON, SPA & BEAUTY
            query.contains("salon") || query.contains("spa") || query.contains("hair") || query.contains("barber") || query.contains("beauty") || query.contains("makeup") || query.contains("nail") || query.contains("parlor") || query.contains("parlour") -> {
                generateSalonWebsite(title, desc)
            }
            // 🥖 BAKERY & RESTAURANT
            query.contains("bakery") || query.contains("cake") || query.contains("pastry") || query.contains("cafe") || query.contains("restaurant") || query.contains("food") -> {
                generateBakeryWebsite(title, desc)
            }
            // 💻 TECH & SOFTWARE
            query.contains("tech") || query.contains("software") || query.contains("ai") || query.contains("app") || query.contains("saas") || query.contains("agency") || query.contains("code") -> {
                generateTechWebsite(title, desc)
            }
            // 🏡 REAL ESTATE
            query.contains("estate") || query.contains("realty") || query.contains("property") || query.contains("villa") || query.contains("house") || query.contains("apartment") -> {
                generateRealEstateWebsite(title, desc)
            }
            // 🛍️ E-COMMERCE
            query.contains("store") || query.contains("shop") || query.contains("fashion") || query.contains("clothes") || query.contains("shoe") || query.contains("ecommerce") -> {
                generateEcommerceWebsite(title, desc)
            }
            // 🏢 GENERAL CORPORATE
            else -> {
                generateGeneralWebsite(title, desc)
            }
        }
    }

    // --- GYM & FITNESS TEMPLATE ---
    private fun generateGymWebsite(title: String, desc: String): Triple<String, String, String> {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title | Elite Fitness & Gym Center</title>
                <link rel="stylesheet" href="style.css">
                <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;800;900&display=swap" rel="stylesheet">
            </head>
            <body>
                <header class="header">
                    <div class="logo">🏋️‍♂️ $title</div>
                    <nav class="nav">
                        <a href="#hero">Home</a>
                        <a href="#plans">Memberships</a>
                        <a href="#trainers">Coaches</a>
                        <a href="#contact" class="btn-cta">Free Pass</a>
                    </nav>
                </header>
                <section id="hero" class="hero">
                    <h1>UNLEASH YOUR ULTIMATE POTENTIAL</h1>
                    <p>$desc</p>
                    <div class="cta-group">
                        <button class="btn-primary" onclick="scrollToPlans()">View Membership Plans</button>
                        <button class="btn-secondary" onclick="handleClaimPass()">Claim 1-Day Trial Pass</button>
                    </div>
                </section>
                <section id="plans" class="section">
                    <h2>Membership Plans</h2>
                    <div class="grid">
                        <div class="card">
                            <h3>Basic Fitness Pass</h3>
                            <div class="price">\$29 <span>/ month</span></div>
                            <p>Full access to gym floor, cardio zone, and locker rooms.</p>
                            <button class="btn-sm" onclick="joinPlan('Basic Pass')">Select Plan</button>
                        </div>
                        <div class="card featured">
                            <span class="badge">Most Popular</span>
                            <h3>Pro Athlete Pass</h3>
                            <div class="price">\$59 <span>/ month</span></div>
                            <p>All Basic features + Unlimited HIIT, Boxing & Yoga Group Classes.</p>
                            <button class="btn-sm btn-highlight" onclick="joinPlan('Pro Pass')">Select Plan</button>
                        </div>
                        <div class="card">
                            <h3>Elite Personal Training</h3>
                            <div class="price">\$99 <span>/ month</span></div>
                            <p>Pro Pass + Dedicated Personal Trainer & Custom Nutrition Plan.</p>
                            <button class="btn-sm" onclick="joinPlan('Elite Pass')">Select Plan</button>
                        </div>
                    </div>
                </section>
                <footer class="footer">
                    <p>&copy; 2026 $title. Powering athletic excellence. Built with JARVIS AI Website Builder.</p>
                </footer>
                <script src="script.js"></script>
            </body>
            </html>
        """.trimIndent()

        val css = """
            :root {
                --primary: #FF3B30;
                --primary-hover: #E0281E;
                --bg-dark: #0A0A0C;
                --surface: #141418;
                --border: #26262E;
                --text-main: #FFFFFF;
                --text-muted: #A0A0AB;
            }
            * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif; }
            body { background-color: var(--bg-dark); color: var(--text-main); line-height: 1.6; }
            .header { display: flex; justify-content: space-between; align-items: center; padding: 20px 5%; background: rgba(20,20,24,0.9); backdrop-filter: blur(12px); position: sticky; top: 0; z-index: 100; border-bottom: 1px solid var(--border); }
            .logo { font-size: 1.5rem; font-weight: 800; color: var(--primary); text-transform: uppercase; letter-spacing: 1px; }
            .nav a { color: var(--text-main); text-decoration: none; margin-left: 20px; font-weight: 600; }
            .btn-cta { background: var(--primary); color: #fff !important; padding: 8px 20px; border-radius: 20px; }
            .hero { text-align: center; padding: 120px 20px; background: linear-gradient(180deg, rgba(10,10,12,0.7) 0%, rgba(10,10,12,1) 100%), url('https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=1400') center/cover; }
            .hero h1 { font-size: 3.5rem; font-weight: 900; margin-bottom: 20px; letter-spacing: 1px; }
            .hero p { font-size: 1.2rem; color: #D0D0D5; max-width: 650px; margin: 0 auto 30px; }
            .btn-primary { background: var(--primary); color: white; border: none; padding: 14px 32px; border-radius: 30px; font-size: 1.05rem; font-weight: 700; cursor: pointer; margin-right: 15px; text-transform: uppercase; }
            .btn-secondary { background: transparent; color: var(--text-main); border: 2px solid var(--border); padding: 14px 32px; border-radius: 30px; font-size: 1.05rem; font-weight: 700; cursor: pointer; text-transform: uppercase; }
            .section { padding: 80px 5%; text-align: center; }
            .section h2 { font-size: 2.5rem; margin-bottom: 40px; text-transform: uppercase; color: var(--primary); }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 24px; }
            .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 32px 24px; text-align: center; position: relative; }
            .card.featured { border-color: var(--primary); box-shadow: 0 0 20px rgba(255,59,48,0.2); }
            .badge { position: absolute; top: -12px; left: 50%; transform: translateX(-50%); background: var(--primary); color: white; padding: 4px 14px; border-radius: 12px; font-size: 0.8rem; font-weight: 700; text-transform: uppercase; }
            .card h3 { font-size: 1.4rem; margin-bottom: 12px; }
            .price { font-size: 2.2rem; font-weight: 800; color: var(--primary); margin-bottom: 16px; }
            .price span { font-size: 0.9rem; color: var(--text-muted); font-weight: 400; }
            .card p { color: var(--text-muted); margin-bottom: 24px; font-size: 0.95rem; }
            .btn-sm { width: 100%; background: transparent; border: 1px solid var(--border); color: white; padding: 12px; border-radius: 10px; font-weight: 700; cursor: pointer; text-transform: uppercase; }
            .btn-highlight { background: var(--primary); border: none; }
            .footer { text-align: center; padding: 30px; border-top: 1px solid var(--border); color: var(--text-muted); }
            @media (max-width: 768px) {
                .header { flex-direction: column; gap: 12px; }
                .hero h1 { font-size: 2.2rem; }
                .btn-primary, .btn-secondary { width: 100%; margin-right: 0; margin-bottom: 10px; }
            }
        """.trimIndent()

        val js = """
            console.log("Gym Website Initialized.");
            function scrollToPlans() {
                document.getElementById("plans").scrollIntoView({ behavior: "smooth" });
            }
            function handleClaimPass() {
                alert("Awesome! Your 1-Day VIP Trial Pass for $title has been activated.");
            }
            function joinPlan(planName) {
                alert("Thank you for choosing " + planName + "! Our fitness team will contact you shortly.");
            }
        """.trimIndent()

        return Triple(html, css, js)
    }

    // --- SALON & SPA TEMPLATE ---
    private fun generateSalonWebsite(title: String, desc: String): Triple<String, String, String> {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title | Luxury Salon & Beauty Spa</title>
                <link rel="stylesheet" href="style.css">
                <link href="https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@0,600;0,700;1,400&family=Outfit:wght@300;400;600&display=swap" rel="stylesheet">
            </head>
            <body>
                <header class="header">
                    <div class="logo">✨ $title</div>
                    <nav class="nav">
                        <a href="#hero">Home</a>
                        <a href="#services">Services</a>
                        <a href="#about">About</a>
                        <a href="#booking" class="btn-cta">Book Appointment</a>
                    </nav>
                </header>
                <section id="hero" class="hero">
                    <h1>REDEFINING ELEGANCE & LUXURY BEAUTY</h1>
                    <p>$desc</p>
                    <button class="btn-primary" onclick="scrollToBooking()">Book Your Session</button>
                </section>
                <section id="services" class="section">
                    <h2>Our Signature Services</h2>
                    <div class="grid">
                        <div class="card">
                            <img src="https://images.unsplash.com/photo-1562322140-8baeececf3df?w=500" alt="Hair Styling" class="card-img">
                            <h3>Executive Hair Styling & Color</h3>
                            <p>Custom precision haircuts, balayage, kerating treatments, and blowouts.</p>
                            <div class="price">\$45.00+</div>
                        </div>
                        <div class="card">
                            <img src="https://images.unsplash.com/photo-1519014816548-bf5fe059798b?w=500" alt="Spa Manicure" class="card-img">
                            <h3>Deluxe Spa Manicure & Nails</h3>
                            <p>Organic hand exfoliation, cuticle care, gel polish, and nail art.</p>
                            <div class="price">\$35.00</div>
                        </div>
                        <div class="card">
                            <img src="https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?w=500" alt="Facial Care" class="card-img">
                            <h3>Rejuvenating Glow Facial</h3>
                            <p>Hydra-facial therapy, deep cleansing, skin tightening, and herbal masks.</p>
                            <div class="price">\$65.00</div>
                        </div>
                    </div>
                </section>
                <section id="booking" class="booking-section">
                    <h2>Reserve Your Salon Appointment</h2>
                    <form class="form" onsubmit="event.preventDefault(); handleBooking();">
                        <input type="text" placeholder="Your Full Name" required class="input">
                        <input type="tel" placeholder="Phone Number" required class="input">
                        <select class="input">
                            <option>Select Service</option>
                            <option>Hair Cut & Styling</option>
                            <option>Spa Manicure & Pedicure</option>
                            <option>Rejuvenating Facial</option>
                        </select>
                        <input type="date" required class="input">
                        <button type="submit" class="btn-primary full">Confirm Appointment</button>
                    </form>
                </section>
                <footer class="footer">
                    <p>&copy; 2026 $title. Luxury Beauty Parlor. Powered by JARVIS AI.</p>
                </footer>
                <script src="script.js"></script>
            </body>
            </html>
        """.trimIndent()

        val css = """
            :root {
                --primary: #EC4899;
                --primary-hover: #DB2777;
                --bg-dark: #0F172A;
                --surface: #1E293B;
                --border: #334155;
                --text-main: #F8FAFC;
                --text-muted: #94A3B8;
            }
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { background: var(--bg-dark); color: var(--text-main); font-family: 'Outfit', sans-serif; }
            h1, h2 { font-family: 'Playfair Display', serif; }
            .header { display: flex; justify-content: space-between; align-items: center; padding: 20px 5%; background: rgba(30,41,59,0.85); backdrop-filter: blur(12px); position: sticky; top: 0; z-index: 100; border-bottom: 1px solid var(--border); }
            .logo { font-size: 1.6rem; color: var(--primary); font-weight: 700; }
            .nav a { color: var(--text-main); text-decoration: none; margin-left: 20px; font-weight: 500; }
            .btn-cta { background: var(--primary); color: white !important; padding: 8px 20px; border-radius: 20px; }
            .hero { text-align: center; padding: 110px 20px; background: linear-gradient(180deg, rgba(15,23,42,0.7) 0%, rgba(15,23,42,1) 100%), url('https://images.unsplash.com/photo-1560066984-138dadb4c035?w=1400') center/cover; }
            .hero h1 { font-size: 3.2rem; margin-bottom: 16px; color: #FFFFFF; }
            .hero p { font-size: 1.2rem; color: #CBD5E1; max-width: 600px; margin: 0 auto 30px; }
            .btn-primary { background: var(--primary); color: white; border: none; padding: 14px 30px; border-radius: 25px; font-size: 1rem; font-weight: 600; cursor: pointer; }
            .section { padding: 80px 5%; text-align: center; }
            .section h2 { font-size: 2.4rem; color: var(--primary); margin-bottom: 40px; }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 24px; }
            .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; overflow: hidden; text-align: left; }
            .card-img { width: 100%; height: 200px; object-fit: cover; }
            .card h3 { font-size: 1.2rem; padding: 16px 16px 8px; }
            .card p { font-size: 0.95rem; color: var(--text-muted); padding: 0 16px 16px; }
            .price { font-size: 1.3rem; font-weight: 700; color: var(--primary); padding: 0 16px 20px; }
            .booking-section { padding: 60px 5%; text-align: center; max-width: 600px; margin: 0 auto; }
            .form { display: flex; flex-direction: column; gap: 16px; margin-top: 30px; }
            .input { background: var(--surface); border: 1px solid var(--border); color: white; padding: 14px; border-radius: 12px; font-size: 1rem; }
            .btn-primary.full { width: 100%; margin-top: 10px; }
            .footer { text-align: center; padding: 30px; border-top: 1px solid var(--border); color: var(--text-muted); }
            @media (max-width: 768px) { .header { flex-direction: column; gap: 12px; } .hero h1 { font-size: 2.2rem; } }
        """.trimIndent()

        val js = """
            console.log("Salon Website Initialized.");
            function scrollToBooking() {
                document.getElementById("booking").scrollIntoView({ behavior: "smooth" });
            }
            function handleBooking() {
                alert("Thank you! Your salon appointment request for $title has been received. We will confirm your date via SMS.");
            }
        """.trimIndent()

        return Triple(html, css, js)
    }

    // --- BAKERY & RESTAURANT TEMPLATE ---
    private fun generateBakeryWebsite(title: String, desc: String): Triple<String, String, String> {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title | Artisanal Bakery & Cafe</title>
                <link rel="stylesheet" href="style.css">
                <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
            </head>
            <body>
                <header class="header">
                    <div class="logo">🥐 $title</div>
                    <nav class="nav">
                        <a href="#hero">Home</a>
                        <a href="#menu">Our Bakes</a>
                        <a href="#contact" class="btn-cta">Order Online</a>
                    </nav>
                </header>
                <section id="hero" class="hero">
                    <h1>Freshly Baked Happiness Every Morning</h1>
                    <p>$desc</p>
                    <button class="btn-primary" onclick="scrollToMenu()">Explore Menu</button>
                </section>
                <section id="menu" class="section">
                    <h2>Signature Delights</h2>
                    <div class="grid">
                        <div class="card">
                            <img src="https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=500" alt="Croissant" class="card-img">
                            <h3>French Butter Croissants</h3>
                            <p>Golden, flaky French butter croissants baked fresh at dawn.</p>
                            <div class="price">\$4.50</div>
                        </div>
                        <div class="card">
                            <img src="https://images.unsplash.com/photo-1578985545062-69928b1d9587?w=500" alt="Chocolate Cake" class="card-img">
                            <h3>Belgian Velvet Cake</h3>
                            <p>Rich chocolate layers with silky ganache and fresh berries.</p>
                            <div class="price">\$28.00</div>
                        </div>
                    </div>
                </section>
                <footer class="footer">
                    <p>&copy; 2026 $title. Built with JARVIS AI Website Builder.</p>
                </footer>
                <script src="script.js"></script>
            </body>
            </html>
        """.trimIndent()

        val css = """
            :root { --primary: #D97706; --bg-dark: #0D1117; --surface: #161B22; --border: #30363D; --text-main: #F0F6FC; --text-muted: #8B949E; }
            * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif; }
            body { background: var(--bg-dark); color: var(--text-main); }
            .header { display: flex; justify-content: space-between; align-items: center; padding: 20px 5%; background: var(--surface); sticky: top; }
            .logo { font-size: 1.5rem; font-weight: 700; color: var(--primary); }
            .nav a { color: var(--text-main); text-decoration: none; margin-left: 20px; }
            .btn-cta { background: var(--primary); color: white !important; padding: 8px 18px; border-radius: 20px; }
            .hero { text-align: center; padding: 100px 20px; background: linear-gradient(180deg, rgba(13,17,23,0.7) 0%, rgba(13,17,23,1) 100%), url('https://images.unsplash.com/photo-1509440159596-0249088772ff?w=1200') center/cover; }
            .hero h1 { font-size: 3rem; margin-bottom: 20px; }
            .hero p { font-size: 1.2rem; color: #E6EDF3; max-width: 600px; margin: 0 auto 30px; }
            .btn-primary { background: var(--primary); color: white; border: none; padding: 14px 28px; border-radius: 30px; font-weight: 600; cursor: pointer; }
            .section { padding: 80px 5%; text-align: center; }
            .section h2 { font-size: 2.2rem; color: var(--primary); margin-bottom: 30px; }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 24px; }
            .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; overflow: hidden; text-align: left; }
            .card-img { width: 100%; height: 200px; object-fit: cover; }
            .card h3 { padding: 16px 16px 8px; font-size: 1.2rem; }
            .card p { padding: 0 16px 16px; color: var(--text-muted); font-size: 0.95rem; }
            .price { font-size: 1.2rem; font-weight: 700; color: var(--primary); padding: 0 16px 20px; }
            .footer { text-align: center; padding: 30px; border-top: 1px solid var(--border); color: var(--text-muted); }
        """.trimIndent()

        val js = """
            function scrollToMenu() { document.getElementById("menu").scrollIntoView({ behavior: "smooth" }); }
        """.trimIndent()

        return Triple(html, css, js)
    }

    // --- TECH & SOFTWARE TEMPLATE ---
    private fun generateTechWebsite(title: String, desc: String): Triple<String, String, String> {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title | Next-Gen AI & Tech Solutions</title>
                <link rel="stylesheet" href="style.css">
                <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;800&display=swap" rel="stylesheet">
            </head>
            <body>
                <header class="header">
                    <div class="logo">⚡ $title</div>
                    <nav class="nav">
                        <a href="#hero">Home</a>
                        <a href="#features">Features</a>
                        <a href="#contact" class="btn-cta">Get Started</a>
                    </nav>
                </header>
                <section id="hero" class="hero">
                    <h1>BUILD THE FUTURE WITH SMART TECHNOLOGY</h1>
                    <p>$desc</p>
                    <button class="btn-primary" onclick="alert('Welcome to $title!')">Explore Platform</button>
                </section>
                <section id="features" class="section">
                    <h2>Powerful Core Features</h2>
                    <div class="grid">
                        <div class="card">
                            <h3>🤖 Artificial Intelligence</h3>
                            <p>Automate business workflows using state-of-the-art AI models.</p>
                        </div>
                        <div class="card">
                            <h3>⚡ Real-Time Analytics</h3>
                            <p>Instant insight dashboards with live performance streaming.</p>
                        </div>
                    </div>
                </section>
                <footer class="footer">
                    <p>&copy; 2026 $title. Built with JARVIS AI Website Builder.</p>
                </footer>
                <script src="script.js"></script>
            </body>
            </html>
        """.trimIndent()

        val css = """
            :root { --primary: #3B82F6; --bg-dark: #0B0F19; --surface: #111827; --border: #1F2937; --text-main: #F9FAFB; --text-muted: #9CA3AF; }
            * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif; }
            body { background: var(--bg-dark); color: var(--text-main); }
            .header { display: flex; justify-content: space-between; align-items: center; padding: 20px 5%; background: var(--surface); border-bottom: 1px solid var(--border); }
            .logo { font-size: 1.5rem; font-weight: 800; color: var(--primary); }
            .nav a { color: var(--text-main); text-decoration: none; margin-left: 20px; }
            .btn-cta { background: var(--primary); color: white !important; padding: 8px 20px; border-radius: 20px; }
            .hero { text-align: center; padding: 120px 20px; background: linear-gradient(180deg, rgba(11,15,25,0.8) 0%, rgba(11,15,25,1) 100%), url('https://images.unsplash.com/photo-1518770660439-4636190af475?w=1400') center/cover; }
            .hero h1 { font-size: 3.2rem; margin-bottom: 20px; }
            .hero p { font-size: 1.2rem; color: var(--text-muted); max-width: 600px; margin: 0 auto 30px; }
            .btn-primary { background: var(--primary); color: white; border: none; padding: 14px 30px; border-radius: 25px; font-weight: 600; cursor: pointer; }
            .section { padding: 80px 5%; text-align: center; }
            .section h2 { font-size: 2.2rem; color: var(--primary); margin-bottom: 40px; }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 24px; }
            .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 32px 24px; text-align: left; }
            .card h3 { margin-bottom: 12px; }
            .card p { color: var(--text-muted); }
            .footer { text-align: center; padding: 30px; border-top: 1px solid var(--border); color: var(--text-muted); }
        """.trimIndent()

        val js = """ console.log("Tech Website Initialized."); """

        return Triple(html, css, js)
    }

    // --- REAL ESTATE TEMPLATE ---
    private fun generateRealEstateWebsite(title: String, desc: String): Triple<String, String, String> {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title | Luxury Real Estate & Homes</title>
                <link rel="stylesheet" href="style.css">
                <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
            </head>
            <body>
                <header class="header">
                    <div class="logo">🏡 $title</div>
                    <nav class="nav"><a href="#hero">Home</a><a href="#properties">Properties</a><a href="#contact" class="btn-cta">Schedule Tour</a></nav>
                </header>
                <section id="hero" class="hero">
                    <h1>FIND YOUR DREAM LUXURY RESIDENCE</h1>
                    <p>$desc</p>
                </section>
                <footer class="footer"><p>&copy; 2026 $title. Luxury Properties. Powered by JARVIS AI.</p></footer>
            </body>
            </html>
        """.trimIndent()

        val css = """
            :root { --primary: #059669; --bg-dark: #0F172A; --surface: #1E293B; --text-main: #FFFFFF; }
            * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif; }
            body { background: var(--bg-dark); color: var(--text-main); }
            .header { display: flex; justify-content: space-between; padding: 20px 5%; background: var(--surface); }
            .logo { font-size: 1.5rem; font-weight: 700; color: var(--primary); }
            .nav a { color: white; text-decoration: none; margin-left: 20px; }
            .btn-cta { background: var(--primary); padding: 8px 20px; border-radius: 20px; }
            .hero { text-align: center; padding: 120px 20px; background: linear-gradient(180deg, rgba(15,23,42,0.7) 0%, rgba(15,23,42,1) 100%), url('https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=1400') center/cover; }
            .hero h1 { font-size: 3rem; margin-bottom: 20px; }
            .hero p { font-size: 1.2rem; max-width: 600px; margin: 0 auto; }
            .footer { text-align: center; padding: 30px; border-top: 1px solid #334155; }
        """.trimIndent()

        val js = """ console.log("Real Estate Website Initialized."); """

        return Triple(html, css, js)
    }

    // --- E-COMMERCE TEMPLATE ---
    private fun generateEcommerceWebsite(title: String, desc: String): Triple<String, String, String> {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title | Premium Online Store</title>
                <link rel="stylesheet" href="style.css">
                <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
            </head>
            <body>
                <header class="header">
                    <div class="logo">🛍️ $title</div>
                    <nav class="nav"><a href="#hero">Home</a><a href="#store">Shop Collection</a></nav>
                </header>
                <section id="hero" class="hero">
                    <h1>DISCOVER TRENDING FASHION & PRODUCTS</h1>
                    <p>$desc</p>
                </section>
                <footer class="footer"><p>&copy; 2026 $title. Powered by JARVIS AI.</p></footer>
            </body>
            </html>
        """.trimIndent()

        val css = """
            :root { --primary: #4F46E5; --bg-dark: #090D16; --surface: #111827; --text-main: #FFFFFF; }
            * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif; }
            body { background: var(--bg-dark); color: var(--text-main); }
            .header { display: flex; justify-content: space-between; padding: 20px 5%; background: var(--surface); }
            .logo { font-size: 1.5rem; font-weight: 700; color: var(--primary); }
            .nav a { color: white; text-decoration: none; margin-left: 20px; }
            .hero { text-align: center; padding: 120px 20px; background: linear-gradient(180deg, rgba(9,13,22,0.7) 0%, rgba(9,13,22,1) 100%), url('https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=1400') center/cover; }
            .hero h1 { font-size: 3rem; margin-bottom: 20px; }
            .hero p { font-size: 1.2rem; max-width: 600px; margin: 0 auto; }
            .footer { text-align: center; padding: 30px; border-top: 1px solid #1F2937; }
        """.trimIndent()

        val js = """ console.log("E-Commerce Store Initialized."); """

        return Triple(html, css, js)
    }

    // --- GENERAL CORPORATE TEMPLATE ---
    private fun generateGeneralWebsite(title: String, desc: String): Triple<String, String, String> {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title | Professional Excellence</title>
                <link rel="stylesheet" href="style.css">
                <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&display=swap" rel="stylesheet">
            </head>
            <body>
                <header class="header">
                    <div class="logo">🌐 $title</div>
                    <nav class="nav">
                        <a href="#hero">Home</a>
                        <a href="#about">About</a>
                        <a href="#contact" class="btn-cta">Contact Us</a>
                    </nav>
                </header>
                <section id="hero" class="hero">
                    <h1>INNOVATIVE SOLUTIONS FOR YOUR SUCCESS</h1>
                    <p>$desc</p>
                    <button class="btn-primary" onclick="alert('Thank you for visiting $title!')">Get In Touch</button>
                </section>
                <footer class="footer">
                    <p>&copy; 2026 $title. Built with JARVIS AI Website Builder.</p>
                </footer>
                <script src="script.js"></script>
            </body>
            </html>
        """.trimIndent()

        val css = """
            :root { --primary: #06B6D4; --bg-dark: #0F172A; --surface: #1E293B; --border: #334155; --text-main: #F8FAFC; --text-muted: #94A3B8; }
            * { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Outfit', sans-serif; }
            body { background-color: var(--bg-dark); color: var(--text-main); }
            .header { display: flex; justify-content: space-between; align-items: center; padding: 20px 5%; background: var(--surface); border-bottom: 1px solid var(--border); }
            .logo { font-size: 1.5rem; font-weight: 700; color: var(--primary); }
            .nav a { color: var(--text-main); text-decoration: none; margin-left: 20px; }
            .btn-cta { background: var(--primary); color: white !important; padding: 8px 18px; border-radius: 20px; }
            .hero { text-align: center; padding: 120px 20px; background: linear-gradient(180deg, rgba(15,23,42,0.8) 0%, rgba(15,23,42,1) 100%), url('https://images.unsplash.com/photo-1497366216548-37526070297c?w=1400') center/cover; }
            .hero h1 { font-size: 3rem; margin-bottom: 20px; }
            .hero p { font-size: 1.2rem; color: var(--text-muted); max-width: 600px; margin: 0 auto 30px; }
            .btn-primary { background: var(--primary); color: white; border: none; padding: 14px 28px; border-radius: 30px; font-weight: 600; cursor: pointer; }
            .footer { text-align: center; padding: 30px; border-top: 1px solid var(--border); color: var(--text-muted); }
        """.trimIndent()

        val js = """ console.log("General Website Initialized."); """

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
