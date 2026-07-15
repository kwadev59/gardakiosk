package com.example.kioskdeviceowner.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.kioskdeviceowner.KioskSettingsManager

class AdbControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val settings = KioskSettingsManager(context)
        
        Log.d("KioskAdbReceiver", "Received action: $action")
        
        when (action) {
            "com.kiosk.action.ADD_APP" -> {
                val pkgName = intent.getStringExtra("package_name")
                if (!pkgName.isNullOrEmpty()) {
                    val current = settings.allowedPackages.toMutableSet()
                    current.add(pkgName)
                    settings.allowedPackages = current
                    
                    // Update policies if active
                    KioskDeviceAdminReceiver.setupDeviceOwnerPolicies(context, current.toList())
                    showToast(context, "App added: $pkgName")
                }
            }
            "com.kiosk.action.HIDE_ICON" -> {
                val pkgName = intent.getStringExtra("package_name")
                if (!pkgName.isNullOrEmpty()) {
                    val current = settings.hiddenIcons.toMutableSet()
                    current.add(pkgName)
                    settings.hiddenIcons = current
                    showToast(context, "Icon hidden: $pkgName")
                }
            }
            "com.kiosk.action.SHOW_ICON" -> {
                val pkgName = intent.getStringExtra("package_name")
                if (!pkgName.isNullOrEmpty()) {
                    val current = settings.hiddenIcons.toMutableSet()
                    current.remove(pkgName)
                    settings.hiddenIcons = current
                    showToast(context, "Icon shown: $pkgName")
                }
            }
            "com.kiosk.action.REMOVE_APP" -> {
                val pkgName = intent.getStringExtra("package_name")
                if (!pkgName.isNullOrEmpty()) {
                    val current = settings.allowedPackages.toMutableSet()
                    current.remove(pkgName)
                    settings.allowedPackages = current
                    
                    // Update policies if active
                    KioskDeviceAdminReceiver.setupDeviceOwnerPolicies(context, current.toList())
                    showToast(context, "App removed: $pkgName")
                }
            }
            "com.kiosk.action.SET_LOCKSCREEN_PIN" -> {
                val pin = intent.getStringExtra("pin")
                if (!pin.isNullOrEmpty() && pin.length >= 4) {
                    settings.lockscreenPin = pin
                    showToast(context, "Lockscreen PIN updated successfully")
                } else {
                    showToast(context, "Failed: PIN must be at least 4 digits")
                }
            }
            "com.kiosk.action.SET_SETTINGS_PIN" -> {
                val pin = intent.getStringExtra("pin")
                if (!pin.isNullOrEmpty() && pin.length >= 4) {
                    settings.settingsPin = pin
                    showToast(context, "Settings PIN updated successfully")
                } else {
                    showToast(context, "Failed: PIN must be at least 4 digits")
                }
            }
            "com.kiosk.action.SET_LOCK_MODE" -> {
                val mode = intent.getStringExtra("mode")
                if (mode == "PIN" || mode == "SWIPE") {
                    settings.lockMode = mode
                    showToast(context, "Lock mode set to: $mode")
                }
            }
            "com.kiosk.action.SET_IDLE_TIMEOUT" -> {
                val seconds = intent.getIntExtra("seconds", -1)
                if (seconds >= 0) {
                    settings.idleTimeoutSeconds = seconds
                    showToast(context, "Idle timeout set to $seconds seconds")
                }
            }
            "com.kiosk.action.EXIT_KIOSK" -> {
                settings.isKioskActive = false
                showToast(context, "Kiosk Mode Disabled. Please reboot or reopen app to exit lockdown.")
                
                // Also trigger main activity refresh by launching it with clear flag
                val i = Intent(context, com.example.kioskdeviceowner.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("action", "EXIT_KIOSK")
                }
                context.startActivity(i)
            }
            "com.kiosk.action.OPEN_SETTINGS" -> {
                showToast(context, "Opening Settings...")
                val i = Intent(context, com.example.kioskdeviceowner.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("action", "OPEN_SETTINGS")
                }
                context.startActivity(i)
            }
            "com.kiosk.action.CLEAR_DEVICE_OWNER" -> {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val admin = KioskDeviceAdminReceiver.getComponentName(context)
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    try {
                        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
                    } catch (e: Exception) {
                        Log.e("KioskAdbReceiver", "Failed to clear package preferred activities: ${e.message}")
                    }
                    try {
                        dpm.clearDeviceOwnerApp(context.packageName)
                    } catch (e: Exception) {
                        Log.e("KioskAdbReceiver", "Failed to clear DO: ${e.message}")
                    }
                }
                try {
                    dpm.removeActiveAdmin(admin)
                    showToast(context, "Device Owner & Admin cleared.")
                } catch (e: Exception) {
                    showToast(context, "Failed to remove admin: ${e.message}")
                }
            }
        }
    }

    private fun showToast(context: Context, msg: String) {
        Toast.makeText(context, "[ADB Kiosk] $msg", Toast.LENGTH_SHORT).show()
    }
}
