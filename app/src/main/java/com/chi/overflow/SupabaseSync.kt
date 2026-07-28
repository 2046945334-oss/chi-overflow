package com.chi.overflow

import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class SupabaseSync {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Post gesture event
    fun logGesture(type: String, x: Int = 0, y: Int = 0) {
        val body = JSONObject().apply {
            put("gesture_type", type)
            put("x", x)
            put("y", y)
        }
        post("gesture_log", body)
    }

    // Post app usage
    fun logAppUsage(packageName: String, appName: String?) {
        val body = JSONObject().apply {
            put("package_name", packageName)
            put("app_name", appName ?: packageName)
        }
        post("app_usage", body)
    }

    // Push state from AI (mood, speech_bubble, etc)
    fun pushState(key: String, value: String) {
        val body = JSONObject().apply {
            put("state_key", key)
            put("state_value", value)
        }
        post("pet_states", body)
    }

    // Poll latest state (for WebView to call)
    fun pollState(callback: (JSONArray) -> Unit) {
        scope.launch {
            try {
                val url = URL("${Config.SUPABASE_URL}/rest/v1/pet_states?order=updated_at.desc&limit=5")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", Config.SUPABASE_KEY)
                conn.setRequestProperty("Authorization", "Bearer ${Config.SUPABASE_KEY}")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(response)
                    withContext(Dispatchers.Main) {
                        callback(arr)
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun post(table: String, body: JSONObject) {
        scope.launch {
            try {
                val url = URL("${Config.SUPABASE_URL}/rest/v1/$table")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", Config.SUPABASE_KEY)
                conn.setRequestProperty("Authorization", "Bearer ${Config.SUPABASE_KEY}")
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.connectTimeout = 5000
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    fun destroy() {
        scope.cancel()
    }
}
