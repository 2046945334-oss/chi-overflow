package com.chi.overflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryReceiver(
    private val onBatteryEvent: (String, Int) -> Unit  // event type, level
) : BroadcastReceiver() {

    private var lastEvent: String = ""

    override fun onReceive(context: Context?, intent: Intent?) {
        intent ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val event = when {
            isCharging && lastEvent != "charging" -> "charging"
            !isCharging && lastEvent == "charging" -> "unplugged"
            level <= 15 && lastEvent != "low" -> "low"
            else -> return
        }
        lastEvent = event
        onBatteryEvent(event, level)
    }

    companion object {
        fun getFilter(): IntentFilter {
            return IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        }
    }
}
