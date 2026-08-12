package com.jarvis.assistant.ai

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Handles the WebSocket connection to Gemini Live (BidiGenerateContent).
 *
 * Mirrors the behaviour of the Python reference implementation:
 *  - Sends a `setup` message immediately on open
 *  - Streams mic PCM as `realtime_input.media_chunks`
 *  - Sends free-form text via `client_content`
 *  - Renews the session every SESSION_RENEW_AFTER seconds
 *  - Sends a silent keep-alive chunk every KEEPALIVE_INTERVAL seconds
 *  - Auto-reconnects 3s after any disconnect
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val modelName: String,
    private val systemPrompt: String,
    private val voiceName: String = "Kore"
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val BASE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val SESSION_RENEW_AFTER_MS = 540_000L // 9 minutes
        private const val KEEPALIVE_INTERVAL_MS = 5_000L
        private const val RECONNECT_BASE_DELAY_MS = 3_000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
    }

    // ---- Public callbacks (wired by MainActivity / OverlayService) ----
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onSetupComplete: (() -> Unit)? = null
    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onInputTranscript: ((String) -> Unit)? = null
    var onOutputTranscript: ((String) -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onInterrupted: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    /** Fired when Gemini decides to call a tool, e.g. open_app("YouTube"). */
    var onToolCall: ((name: String, args: JSONObject, callId: String) -> Unit)? = null

    private var webSocket: WebSocket? = null
    private var isManuallyClosed = false
    private var isSetupComplete = false
    private var reconnectAttempt = 0

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var keepAliveJob: Job? = null
    private var sessionRenewJob: Job? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // streaming connection, no timeout
            .pingInterval(10, TimeUnit.SECONDS) // send PING frame every 10s to keep connection alive during silence
            .build()
    }

    fun connect() {
        isManuallyClosed = false
        val url = "$BASE_WS_URL?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                isSetupComplete = false
                reconnectAttempt = 0
                sendSetupMessage(webSocket)
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                handleServerMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                cleanupTimers()
                onDisconnected?.invoke()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorBody = try { response?.body?.string() } catch (_: Exception) { null }
                val fullError = buildString {
                    append("WebSocket failure: ${t.javaClass.simpleName}: ${t.message}")
                    if (response != null) append("\nHTTP ${response.code}: ${response.message}")
                    if (!errorBody.isNullOrBlank()) append("\nBody: $errorBody")
                }
                Log.e(TAG, fullError, t)
                cleanupTimers()
                onError?.invoke(fullError)
                onDisconnected?.invoke()
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (isManuallyClosed) return
        val delayMs = (RECONNECT_BASE_DELAY_MS * (1L shl reconnectAttempt.coerceAtMost(4)))
            .coerceAtMost(RECONNECT_MAX_DELAY_MS)
        reconnectAttempt++
        scope.launch {
            Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt)...")
            delay(delayMs)
            if (!isManuallyClosed) {
                connect()
            }
        }
    }

    private fun sendSetupMessage(ws: WebSocket) {
        val setup = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", modelName)
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                })
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().put("AUDIO"))
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", voiceName)
                            })
                        })
                    })
                    put("temperature", 0.2)
                })
                put("output_audio_transcription", JSONObject())
                put("input_audio_transcription", JSONObject())
                put("tools", JSONArray().put(JSONObject().apply {
                    put("functionDeclarations", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "open_app")
                            put("description",
                                "Opens an app installed on the user's phone, e.g. YouTube, " +
                                "WhatsApp, Camera, Settings. If multiple instances/dual apps exist, " +
                                "app_number specifies 1 or 2.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("app_name", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description",
                                            "The name of the app to open, as the user said it " +
                                            "(e.g. \"YouTube\", \"WhatsApp\").")
                                    })
                                    put("app_number", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description",
                                            "Optional 1-based index (1 or 2) when opening a dual/cloned app instance or specific app selection.")
                                    })
                                })
                                put("required", JSONArray().put("app_name"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "search_and_play_youtube")
                            put("description",
                                "Searches YouTube and plays a video on screen ONLY when the user EXPLICITLY asks to play on YouTube, e.g. \"play Tum Hi Ho on YouTube\", \"YouTube pe Arijit Singh chalao\". For standard song requests like \"play Admiring You\" or \"play Drill Song\", use play_ad_free_music instead.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("query", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "What to search for and play, e.g. \"Tum Hi Ho Arijit Singh\".")
                                    })
                                })
                                put("required", JSONArray().put("query"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "media_playback_control")
                            put("description",
                                "Controls whatever video/audio is currently playing (typically " +
                                "YouTube) — play, pause, skip to next, go to previous. Use for " +
                                "commands like \"pause it\", \"resume\", \"next video\", \"rokdo\", \"chalao\".")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "STRING")
                                        put("enum", JSONArray().apply {
                                            put("play"); put("pause"); put("next"); put("previous"); put("stop")
                                        })
                                    })
                                })
                                put("required", JSONArray().put("action"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "youtube_accessibility_action")
                            put("description",
                                "Performs an in-app YouTube action that requires tapping the " +
                                "screen: skipping an ad, liking the video, subscribing to the " +
                                "channel, opening the video's channel page, seeking forward/" +
                                "backward 10 seconds, or toggling fullscreen playback. Use action " +
                                "\"open_channel\" for commands like \"channel kholo\", \"open the " +
                                "channel\", \"go to this channel\". Use action \"fullscreen\" for " +
                                "commands like \"make it full screen\", \"go fullscreen\", \"exit " +
                                "fullscreen\". Requires the JARVIS accessibility service to be " +
                                "enabled by the user — if it fails, tell the user to enable " +
                                "Accessibility for JARVIS in phone Settings.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "STRING")
                                        put("enum", JSONArray().apply {
                                            put("skip_ad"); put("like"); put("subscribe")
                                            put("open_channel")
                                            put("seek_forward"); put("seek_backward"); put("fullscreen")
                                        })
                                    })
                                })
                                put("required", JSONArray().put("action"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "call_contact")
                            put("description",
                                "Places a phone call to a saved contact by spoken name, e.g. " +
                                "\"call mom\", \"call Rahul\", \"phone Dad\". Looks the name up in " +
                                "the phone's own contacts — never invent or guess a phone number.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("contact_name", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The contact's name as the user said it, e.g. \"mom\", \"Rahul\".")
                                    })
                                })
                                put("required", JSONArray().put("contact_name"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "set_volume")
                            put("description",
                                "Adjusts media volume on device. Supports relative commands like " +
                                "\"increase volume\", \"volume badhao\", \"decrease volume\", \"volume kam karo\", " +
                                "or exact percentage like \"set volume 50%\", \"set volume 10%\", \"volume 80%\".")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Action to perform: 'increase', 'decrease', or 'set'")
                                    })
                                    put("percentage", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Target percentage level from 0 to 100 if specified (e.g. 50, 10, 80)")
                                    })
                                })
                                put("required", JSONArray().put("action"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "set_brightness")
                            put("description",
                                "Adjusts screen brightness on device. Supports relative commands like " +
                                "\"increase brightness\", \"brightness badhao\", \"decrease brightness\", \"brightness kam karo\", " +
                                "or exact percentage like \"set brightness 50%\", \"set brightness 10%\", \"brightness 100%\".")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Action to perform: 'increase', 'decrease', or 'set'")
                                    })
                                    put("percentage", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Target percentage level from 0 to 100 if specified (e.g. 50, 10, 100)")
                                    })
                                })
                                put("required", JSONArray().put("action"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "search_playstore_and_install")
                            put("description",
                                "Searches Google Play Store for an app by name and installs it automatically. Use whenever the user asks " +
                                "to download, install, or get an app from Play Store (e.g. \"download Instagram\", \"install WhatsApp\", \"Play Store se Instagram download karo\").")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("app_name", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Name of the app to search and install on Play Store (e.g. \"Instagram\", \"WhatsApp\").")
                                    })
                                })
                                put("required", JSONArray().put("app_name"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "create_website")
                            put("description",
                                "Creates a complete website (HTML, CSS, JS only) for a business or topic using OpenRouter free models and saves it to local device storage (e.g. \"create a bakery website for Sweet Treats\", \"build a website for my coffee shop\", \"JARVIS create a portfolio website\").")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("website_name", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Name of the website, business, or company (e.g. \"Sweet Treats Bakery\", \"Roasted Beans Cafe\").")
                                    })
                                    put("business_description", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Description of the business, features, or design requirements for the website.")
                                    })
                                })
                                put("required", JSONArray().put("website_name"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "search_in_chrome")
                            put("description",
                                "Opens Google Chrome and searches for a topic/question, or directly opens a website URL (e.g. \"search xyz in Chrome\", \"open website github.com\", \"open rehaan.com\", \"open rehaan.in\", \"visit wikipedia.org\").")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("query", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Query or topic to search in Google Chrome.")
                                    })
                                })
                                put("required", JSONArray().put("query"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "download_song")
                            put("description",
                                "Searches and downloads a song/MP3 file on the user's mobile phone via Chrome research and background downloader (e.g. 'download Tum Hi Ho song', 'song download Kesariya', 'download mp3 song').")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("song_name", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Name of the song or title to download (e.g. \"Tum Hi Ho\", \"Kesariya\").")
                                    })
                                    put("artist", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Optional artist or movie name (e.g. \"Arijit Singh\").")
                                    })
                                })
                                put("required", JSONArray().put("song_name"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "send_whatsapp_message")
                            put("description",
                                "Sends a WhatsApp message hands-free to a contact by name (e.g. \"message Rahul that I will be late\", \"WhatsApp Priya 'See you soon'\", \"send message to Dad: I reached home\"). If 2 WhatsApp apps exist and app_number is not specified, it asks the user 1 or 2.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("recipient_name", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Name of the contact as spoken by the user (e.g. \"Rahul\", \"Priya\", \"Dad\").")
                                    })
                                    put("message", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The text message content to send to the recipient.")
                                    })
                                    put("app_number", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Optional 1-based index (1 or 2) if dual WhatsApp is installed and user specified 1 or 2.")
                                    })
                                    put("confirmed", JSONObject().apply {
                                        put("type", "BOOLEAN")
                                        put("description", "Set to true ONLY AFTER the user confirms \"Yes\" to send the message to the requested contact name.")
                                    })
                                })
                                put("required", JSONArray().apply { put("recipient_name"); put("message") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "whatsapp_call")
                            put("description",
                                "Initiates a WhatsApp voice call or WhatsApp video call to a contact (e.g. \"WhatsApp call Mom\", \"WhatsApp video call Rahul\", \"call Mom on WhatsApp\", \"video call Rahul on WhatsApp\").")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("recipient_name", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Name of the contact to call as spoken by the user (e.g. \"Mom\", \"Rahul\").")
                                    })
                                    put("call_type", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "\"voice\" for voice call (\"WhatsApp call Mom\") or \"video\" for video call (\"WhatsApp video call Rahul\").")
                                    })
                                    put("app_number", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Optional 1-based index (1 or 2) if dual WhatsApp is installed.")
                                    })
                                    put("confirmed", JSONObject().apply {
                                        put("type", "BOOLEAN")
                                        put("description", "Set to true ONLY AFTER the user confirms \"Yes\" to start the call to the requested contact.")
                                    })
                                })
                                put("required", JSONArray().apply { put("recipient_name"); put("call_type") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "tap_screen_by_text")
                            put("description",
                                "Taps or clicks a visible text, button, link, or element on the mobile screen using accessibility. " +
                                "Use whenever the user asks to click or tap something visible on screen.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("text", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Text or label of the element to click/tap.")
                                    })
                                })
                                put("required", JSONArray().put("text"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "tap_screen_coordinates")
                            put("description",
                                "Taps at normalized screen percentage coordinates (x: 0-100%, y: 0-100%). Use when clicking a specific visual screen spot.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("x_percent", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "X coordinate percentage from 0 (left) to 100 (right).")
                                    })
                                    put("y_percent", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Y coordinate percentage from 0 (top) to 100 (bottom).")
                                    })
                                })
                                put("required", JSONArray().apply { put("x_percent"); put("y_percent") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "type_text")
                            put("description",
                                "Types text into the currently focused text field on the mobile screen. Use when the user asks to type or enter text.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("text", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Text to type into focused input field.")
                                    })
                                })
                                put("required", JSONArray().put("text"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "perform_device_gesture")
                            put("description",
                                "Executes mobile system navigation gestures: 'home' (go to home screen), 'back' (go back), 'recents' (recent apps), 'scroll_down', 'scroll_up'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("gesture", JSONObject().apply {
                                        put("type", "STRING")
                                        put("enum", JSONArray().apply {
                                            put("home"); put("back"); put("recents"); put("scroll_down"); put("scroll_up")
                                        })
                                    })
                                })
                                put("required", JSONArray().put("gesture"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "builtin_chrome_search")
                            put("description",
                                "Performs invisible, background web research using JARVIS's built-in Chrome engine. " +
                                "Use whenever the user asks a question, topic, real-time factual query, news, weather, " +
                                "or live information that you don't know off-hand.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("query", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Search query or topic to look up on the web.")
                                    })
                                })
                                put("required", JSONArray().put("query"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "unlock_app_lock")
                            put("description",
                                "Unlocks an app lock screen (PIN, passcode, or password) on device when an app is locked and the user provides their lock code (e.g. \"1234 is my lock\", \"unlock it with 9876\", \"my PIN is 5555\").")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("passcode", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The PIN, passcode, or password to unlock the app lock screen.")
                                    })
                                })
                                put("required", JSONArray().put("passcode"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "delete_whatsapp_message")
                            put("description",
                                "Deletes a message in WhatsApp chat. Target can be 'everyone' ('delete for everyone', 'delete for all') or 'me' ('delete for me'). E.g. 'delete this message for everyone', 'delete WhatsApp message for me'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("delete_target", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "'everyone' to delete for everyone, or 'me' to delete for me.")
                                    })
                                })
                                put("required", JSONArray().put("delete_target"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "smart_screen_scroll")
                            put("description",
                                "Controls screen scrolling and Reels/Shorts auto-changing. Actions: 'scroll_up', 'scroll_down', 'scroll_to_top', 'scroll_to_bottom', 'next_reel', 'prev_reel'. E.g. 'scroll down', 'go to top', 'next reel', 'previous short'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Scroll action: 'scroll_up', 'scroll_down', 'scroll_to_top', 'scroll_to_bottom', 'next_reel', 'prev_reel'.")
                                    })
                                })
                                put("required", JSONArray().put("action"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "set_alarm")
                            put("description",
                                "Sets an exact alarm on device. E.g. 'set an alarm for 7:30 AM', 'alarm for 6 PM', 'set alarm at 8:00'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("hour", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Hour in 24-hour format (0-23).")
                                    })
                                    put("minute", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Minute (0-59).")
                                    })
                                    put("label", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Optional label or message for the alarm.")
                                    })
                                })
                                put("required", JSONArray().apply { put("hour"); put("minute") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "set_timer")
                            put("description",
                                "Sets a countdown timer on device. E.g. 'set a timer for 5 minutes', '10 minutes timer', 'timer for 30 seconds'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("seconds", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Timer duration in total seconds.")
                                    })
                                    put("label", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Optional timer label.")
                                    })
                                })
                                put("required", JSONArray().put("seconds"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "set_reminder")
                            put("description",
                                "Sets a reminder task alert. E.g. 'remind me to buy milk in 15 minutes', 'remind me about meeting'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("title", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Reminder title or note.")
                                    })
                                    put("delay_minutes", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Delay in minutes from now.")
                                    })
                                })
                                put("required", JSONArray().apply { put("title"); put("delay_minutes") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "get_system_info")
                            put("description",
                                "Checks system battery status, date/time, or local weather forecast. E.g. 'what is my battery level?', 'what date is it?', 'what is the weather in Delhi?'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("query_type", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "'battery', 'datetime', 'weather', or 'all'.")
                                    })
                                    put("city", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Optional city name for weather forecast.")
                                    })
                                })
                                put("required", JSONArray().put("query_type"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "control_camera")
                            put("description",
                                "Takes photos, captures selfies, or records videos with countdown timer. E.g. 'take a photo', 'take a selfie in 3 seconds', 'record a video'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "'take_photo', 'take_selfie', or 'record_video'.")
                                    })
                                    put("timer_seconds", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Timer delay in seconds (0 for immediate).")
                                    })
                                })
                                put("required", JSONArray().put("action"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "analyze_scene")
                            put("description",
                                "Analyzes camera or screen visual view. Actions: 'read_text' (OCR), 'object_recognition', 'describe_scene', 'full_analysis'. E.g. 'read this text', 'what object is this?', 'what do you see?'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("mode", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "'read_text', 'object_recognition', 'describe_scene', or 'full_analysis'.")
                                    })
                                })
                                put("required", JSONArray().put("mode"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "control_flashlight")
                            put("description",
                                "Controls device flashlight (torch) toggle and brightness. E.g. 'turn on torch', 'flashlight off', 'toggle torch', 'set flashlight brightness 50%'.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "'on', 'off', 'toggle', or 'brightness'.")
                                    })
                                    put("brightness_level", JSONObject().apply {
                                        put("type", "INTEGER")
                                        put("description", "Optional brightness percentage level from 1 to 100.")
                                    })
                                })
                                put("required", JSONArray().put("action"))
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "show_code_preview_bar")
                            put("description",
                                "Re-opens or shows the website code preview bar overlay on screen when the user asks to see or open the code preview window (e.g. 'open code review bar', 'show preview bar', 'open website window').")
                        })
                        put(JSONObject().apply {
                            put("name", "shutdown_jarvis")
                            put("description",
                                "Shuts down JARVIS and turns off the assistant. Use whenever the " +
                                "user asks to turn off, shutdown, close, exit, stop, or band hojao, " +
                                "e.g. \"turn off\", \"band hojao\", \"shut down\", \"exit\", \"bye jarvis\".")
                        })
                    })
                }))
            })
        }
        ws.send(setup.toString())
    }

    /** Send a chunk of 16kHz mono PCM16 mic audio. */
    fun sendAudioChunk(pcmBytes: ByteArray) {
        if (!isSetupComplete) return
        try {
            val b64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val msg = JSONObject().apply {
                put("realtime_input", JSONObject().apply {
                    put("audio", JSONObject().apply {
                        put("mime_type", "audio/pcm;rate=16000")
                        put("data", b64)
                    })
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendAudioChunk failed: ${e.message}")
        }
    }

    /** Send a live vision screen capture frame (JPEG image) to Gemini Live. */
    fun sendVideoFrame(jpegBytes: ByteArray) {
        if (!isSetupComplete) return
        try {
            val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            val msg = JSONObject().apply {
                put("realtime_input", JSONObject().apply {
                    put("video", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", b64)
                    })
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendVideoFrame failed: ${e.message}")
        }
    }

    /** Send a free-form text turn to JARVIS (e.g. text chat, phone-action confirmations). */
    fun sendText(text: String, turnComplete: Boolean = true) {
        try {
            val msg = JSONObject().apply {
                put("client_content", JSONObject().apply {
                    put("turns", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", text)))
                    }))
                    put("turn_complete", turnComplete)
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendText failed: ${e.message}")
            onError?.invoke("Failed to send text: ${e.message}")
        }
    }

    /** Interrupt JARVIS mid-speech (e.g. on long-press of mic button). */
    fun sendInterrupt() {
        try {
            val msg = JSONObject().apply {
                put("client_content", JSONObject().apply {
                    put("turns", JSONArray())
                    put("turn_complete", true)
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendInterrupt failed: ${e.message}")
        }
    }

    /**
     * Send the result of a tool call back to Gemini so it can react
     * (e.g. confirm out loud that the app was opened, or apologize if not found).
     */
    fun sendToolResponse(callId: String, functionName: String, result: JSONObject) {
        try {
            val msg = JSONObject().apply {
                put("tool_response", JSONObject().apply {
                    put("function_responses", JSONArray().put(JSONObject().apply {
                        put("id", callId)
                        put("name", functionName)
                        put("response", result)
                    }))
                })
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.e(TAG, "sendToolResponse failed: ${e.message}")
            onError?.invoke("Failed to send tool response for '$functionName': ${e.message}")
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)

            if (json.has("setupComplete")) {
                isSetupComplete = true
                startKeepAlive()
                startSessionRenewalTimer()
                onSetupComplete?.invoke()
                return
            }

            // Server-side error response (e.g. quota exceeded, invalid request)
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val code = errorObj.optInt("code", 0)
                val message = errorObj.optString("message", "Unknown server error")
                val status = errorObj.optString("status", "")
                val fullError = "Gemini Error [$code $status]: $message"
                Log.e(TAG, fullError)
                onError?.invoke(fullError)
                return
            }

            // Gemini decided to invoke one of our declared tools (e.g. open_app)
            val toolCall = json.optJSONObject("toolCall")
            if (toolCall != null) {
                val functionCalls = toolCall.optJSONArray("functionCalls")
                if (functionCalls != null) {
                    for (i in 0 until functionCalls.length()) {
                        val call = functionCalls.getJSONObject(i)
                        val name = call.optString("name", "")
                        val id = call.optString("id", "")
                        val args = call.optJSONObject("args") ?: JSONObject()
                        if (name.isNotEmpty()) {
                            onToolCall?.invoke(name, args, id)
                        }
                    }
                }
                return
            }

            val serverContent = json.optJSONObject("serverContent") ?: return

            if (serverContent.optBoolean("interrupted", false)) {
                Log.d(TAG, "Server signaled speech interrupted")
                onInterrupted?.invoke()
                return
            }

            // Audio + text parts from the model's turn
            val modelTurn = serverContent.optJSONObject("modelTurn")
            val parts = modelTurn?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data") ?: part.optJSONObject("audio")
                    if (inlineData != null) {
                        val b64Audio = inlineData.optString("data", "")
                        if (b64Audio.isNotEmpty()) {
                            val bytes = Base64.decode(b64Audio, Base64.NO_WRAP)
                            onAudioReceived?.invoke(bytes)
                        }
                    }
                }
            }

            serverContent.optJSONObject("outputTranscription")?.let {
                val t = it.optString("text", "")
                if (t.isNotEmpty()) onOutputTranscript?.invoke(t)
            }

            serverContent.optJSONObject("inputTranscription")?.let {
                val t = it.optString("text", "")
                if (t.isNotEmpty()) onInputTranscript?.invoke(t)
            }

            if (serverContent.optBoolean("turnComplete", false)) {
                onTurnComplete?.invoke()
            }
        } catch (e: Exception) {
            val parseError = "Failed to parse server message: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, parseError, e)
            onError?.invoke(parseError)
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            // Silent PCM16 chunk (all zeros) to keep the connection warm.
            val silentChunk = ByteArray(1024)
            while (isActive) {
                delay(KEEPALIVE_INTERVAL_MS)
                sendAudioChunk(silentChunk)
            }
        }
    }

    private fun startSessionRenewalTimer() {
        sessionRenewJob?.cancel()
        sessionRenewJob = scope.launch {
            delay(SESSION_RENEW_AFTER_MS)
            Log.d(TAG, "Renewing session after ${SESSION_RENEW_AFTER_MS / 1000}s")
            disconnect(manual = false)
            delay(500)
            connect()
        }
    }

    private fun cleanupTimers() {
        keepAliveJob?.cancel()
        sessionRenewJob?.cancel()
        isSetupComplete = false
    }

    fun disconnect(manual: Boolean = true) {
        isManuallyClosed = manual
        cleanupTimers()
        webSocket?.close(1000, "Client closed")
        webSocket = null
    }

    fun isConnected(): Boolean = isSetupComplete
}