package com.example.kioskdeviceowner.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.kioskdeviceowner.MainActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action || "android.intent.action.LOCKED_BOOT_COMPLETED" == action) {
            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        }
    }
}
