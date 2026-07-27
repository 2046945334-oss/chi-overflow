package com.chi.overflow

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class AppDetector(
    private val context: Context,
    private val onAppChanged: (String, String?) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPackage: String = ""
    private var running = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val current = getCurrentApp()
            if (current != null && current != lastPackage) {
                lastPackage = current
                val appName = getAppName(current)
                onAppChanged(current, appName)
            }
            handler.postDelayed(this, 3000) // every 3s
        }
    }

    fun start() {
        running = true
        handler.post(pollRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(pollRunnable)
    }

    private fun getCurrentApp(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000,
            now
        )
        if (stats.isNullOrEmpty()) return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun getAppName(packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            null
        }
    }
}
