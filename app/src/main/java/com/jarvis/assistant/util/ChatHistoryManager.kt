package com.jarvis.assistant.util

import android.content.Context
import com.jarvis.assistant.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages persistent storage of conversation history in local device storage.
 * Ensures all messages are stored and rendered strictly in Hinglish (Latin alphabet A-Z, a-z).
 */
object ChatHistoryManager {

    private const val FILE_NAME = "chat_history.json"
    private const val MAX_HISTORY_ITEMS = 200

    fun loadHistory(context: Context): List<ChatMessage> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val text = cleanToHinglish(obj.optString("text", ""))
                val isUser = obj.optBoolean("isUser", false)
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                if (text.isNotBlank()) {
                    list.add(ChatMessage(text, isUser, timestamp))
                }
            }
            list
        } catch (e: Exception) {
            android.util.Log.e("ChatHistoryManager", "Failed to load chat history", e)
            emptyList()
        }
    }

    fun saveMessage(context: Context, message: ChatMessage) {
        val cleanedText = cleanToHinglish(message.text)
        if (cleanedText.isBlank()) return

        val history = loadHistory(context).toMutableList()
        history.add(ChatMessage(cleanedText, message.isUser, message.timestamp))

        // Keep only recent 200 messages for optimal performance
        if (history.size > MAX_HISTORY_ITEMS) {
            history.removeAt(0)
        }

        saveAll(context, history)
    }

    fun saveAll(context: Context, history: List<ChatMessage>) {
        try {
            val array = JSONArray()
            for (msg in history) {
                val cleanedText = cleanToHinglish(msg.text)
                if (cleanedText.isNotBlank()) {
                    val obj = JSONObject().apply {
                        put("text", cleanedText)
                        put("isUser", msg.isUser)
                        put("timestamp", msg.timestamp)
                    }
                    array.put(obj)
                }
            }
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(array.toString(2))
        } catch (e: Exception) {
            android.util.Log.e("ChatHistoryManager", "Failed to save chat history", e)
        }
    }

    fun clearHistory(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            android.util.Log.e("ChatHistoryManager", "Failed to clear chat history", e)
        }
    }

    /**
     * Strips Devanagari/Hindi script and non-Latin characters to enforce
     * 100% Hinglish text (Hindi written using English/Latin A-Z letters).
     */
    fun cleanToHinglish(input: String): String {
        if (input.isBlank()) return ""
        val sb = StringBuilder()
        for (c in input) {
            val code = c.code
            // Accept standard ASCII/Latin letters, digits, punctuation, and spaces
            if (code in 32..126 || c == '\n') {
                sb.append(c)
            }
        }
        return sb.toString().trim()
    }
}
