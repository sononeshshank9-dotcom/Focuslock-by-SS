package com.focuslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Store.active(context)) {
            context.startForegroundService(Intent(context, BlockService::class.java))
        }
    }
}
