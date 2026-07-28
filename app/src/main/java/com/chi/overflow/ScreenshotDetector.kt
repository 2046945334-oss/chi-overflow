package com.chi.overflow

import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import java.io.File

class ScreenshotDetector(
    private val onScreenshot: () -> Unit
) {
    private var observer: FileObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastTrigger = 0L

    fun start() {
        val screenshotDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Screenshots"
        )
        if (!screenshotDir.exists()) screenshotDir.mkdirs()

        observer = object : FileObserver(screenshotDir.path, CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val now = System.currentTimeMillis()
                // Cooldown 3 seconds
                if (now - lastTrigger < 3000) return
                if (path.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") }) {
                    lastTrigger = now
                    handler.post { onScreenshot() }
                }
            }
        }
        observer?.startWatching()
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
    }
}
