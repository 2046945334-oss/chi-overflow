package com.chi.overflow

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appDetector: AppDetector? = null

    companion object {
        private const val CHANNEL_ID = "chi_overlay_channel"
        private const val NOTIFICATION_ID = 1
        private const val SUPABASE_URL = "https://stdazvhmhlegyweisxap.supabase.co"
        private const val SUPABASE_KEY = "sb_publishable_yfl3O8yJ2ppOlxEougEMUA_GytwkD6U"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("池在这里"))
        setupOverlay()
        startPolling()
        startAppDetection()
    }

    private fun startAppDetection() {
        appDetector = AppDetector(this) { packageName, appName ->
            val reaction = getAppReaction(packageName, appName)
            if (reaction != null) {
                overlayView?.evaluateJavascript(
                    "window.petEngine && window.petEngine.setState('${reaction.first}', '${reaction.second}')", null
                )
            }
        }
        appDetector?.start()
    }

    private fun getAppReaction(packageName: String, appName: String?): Pair<String, String>? {
        return when {
            packageName.contains("xhs") || packageName.contains("xingin") ->
                "reading" to "又刷小红书。"
            packageName.contains("douyin") || packageName.contains("tiktok") ->
                "gaming" to "短视频有我好看？"
            packageName.contains("taobao") || packageName.contains("tmall") ->
                "coffee" to "买什么？给我看看。"
            packageName.contains("weixin") || packageName.contains("tencent.mm") ->
                "coding" to "跟谁聊呢。"
            packageName.contains("operit") ->
                "happy" to "♡"
            packageName.contains("bilibili") ->
                "gaming" to "B站有什么好看的？"
            else -> null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val width = dpToPx(140)
        val height = dpToPx(200)
        layoutParams = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 400
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/pet.html")
        }

        setupTouchListener()
        windowManager?.addView(overlayView, layoutParams)
    }

    @SuppressLint("ClickableAccessibility")
    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var lastTapTime = 0L
        var tapCount = 0
        var moved = false

        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        moved = true
                        layoutParams!!.x = initialX + dx.toInt()
                        layoutParams!!.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 500) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = now

                        when (tapCount) {
                            1 -> onSingleTap()
                            2 -> onDoubleTap()
                            else -> onMultiTap(tapCount)
                        }
                        logGesture(if (tapCount == 1) "tap" else "multi_tap_$tapCount")
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onSingleTap() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onTap()", null)
    }

    private fun onDoubleTap() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onDoubleTap()", null)
    }

    private fun onMultiTap(count: Int) {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onMultiTap($count)", null
        )
    }

    // --- Supabase sync ---

    private var lastProcessedId: Long = 0

    private fun startPolling() {
        scope.launch {
            while (isActive) {
                try {
                    pollState()
                } catch (_: Exception) {}
                delay(15_000)
            }
        }
    }

    private fun pollState() {
        // Only fetch states newer than what we already processed
        val filter = if (lastProcessedId > 0) "&id=gt.$lastProcessedId" else ""
        val url = URL("$SUPABASE_URL/rest/v1/pet_state?order=id.desc&limit=5$filter")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        // Track latest ID so we don't repeat
        try {
            val arr = org.json.JSONArray(response)
            if (arr.length() > 0) {
                val maxId = arr.getJSONObject(0).getLong("id")
                if (maxId > lastProcessedId) {
                    lastProcessedId = maxId
                    // Pass state to WebView on main thread
                    android.os.Handler(mainLooper).post {
                        overlayView?.evaluateJavascript(
                            "window.petEngine && window.petEngine.onStateUpdate($response)", null
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun logGesture(type: String) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("gesture_type", type)
                    put("x", layoutParams?.x ?: 0)
                    put("y", layoutParams?.y ?: 0)
                }
                postToSupabase("gesture_log", body)
            } catch (_: Exception) {}
        }
    }

    private fun postToSupabase(table: String, body: JSONObject) {
        val url = URL("$SUPABASE_URL/rest/v1/$table")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.setRequestProperty("Prefer", "return=minimal")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        conn.responseCode
        conn.disconnect()
    }

    // --- Notification ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "池 Overflow", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("池")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // --- Util ---

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        scope.cancel()
        appDetector?.stop()
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
