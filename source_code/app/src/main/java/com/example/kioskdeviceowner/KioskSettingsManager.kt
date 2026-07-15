package com.example.kioskdeviceowner

import android.content.Context
import android.content.SharedPreferences

class KioskSettingsManager(private val context: Context) {

    private val deContext: Context = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        context.createDeviceProtectedStorageContext()
    } else {
        context
    }
    private val prefs: SharedPreferences = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    companion object {
        private const val PREFS_NAME = "kiosk_settings"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
        private const val KEY_BACKGROUND_PACKAGES = "background_packages"
        private const val KEY_LOCKSCREEN_PIN = "kiosk_lockscreen_pin"
        private const val KEY_SETTINGS_PIN = "kiosk_settings_pin"
        private const val KEY_LOCK_MODE = "kiosk_lock_mode"
        private const val KEY_IDLE_TIMEOUT = "kiosk_idle_timeout"
        private const val KEY_IS_KIOSK_ACTIVE = "is_kiosk_active"
        private const val KEY_FOOTER_TEXT = "kiosk_footer_text"
        private const val KEY_USB_DEBUGGING_BLOCKED = "usb_debugging_blocked"
        private const val KEY_HIDDEN_ICONS = "kiosk_hidden_icons"
        
        const val LOCK_MODE_PIN = "PIN"
        const val LOCK_MODE_SWIPE = "SWIPE"
    }

    var footerText: String
        get() = prefs.getString(KEY_FOOTER_TEXT, "Kiosk Device Owner Mode Aktif") ?: "Kiosk Device Owner Mode Aktif"
        set(value) = prefs.edit().putString(KEY_FOOTER_TEXT, value).apply()

    private val defaultGpsPackages = emptySet<String>()

    var allowedPackages: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_PACKAGES, defaultGpsPackages) ?: defaultGpsPackages
        set(value) = prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, value).apply()

    var backgroundPackages: Set<String>
        get() = prefs.getStringSet(KEY_BACKGROUND_PACKAGES, defaultGpsPackages) ?: defaultGpsPackages
        set(value) = prefs.edit().putStringSet(KEY_BACKGROUND_PACKAGES, value).apply()

    var lockscreenPin: String
        get() = prefs.getString(KEY_LOCKSCREEN_PIN, "147258") ?: "147258"
        set(value) = prefs.edit().putString(KEY_LOCKSCREEN_PIN, value).apply()

    var settingsPin: String
        get() = prefs.getString(KEY_SETTINGS_PIN, "354687") ?: "354687"
        set(value) = prefs.edit().putString(KEY_SETTINGS_PIN, value).apply()

    var lockMode: String
        get() = prefs.getString(KEY_LOCK_MODE, LOCK_MODE_SWIPE) ?: LOCK_MODE_SWIPE
        set(value) = prefs.edit().putString(KEY_LOCK_MODE, value).apply()

    var idleTimeoutSeconds: Int
        get() = prefs.getInt(KEY_IDLE_TIMEOUT, 30) // Default 30s
        set(value) = prefs.edit().putInt(KEY_IDLE_TIMEOUT, value).apply()

    var isKioskActive: Boolean
        get() = prefs.getBoolean(KEY_IS_KIOSK_ACTIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_KIOSK_ACTIVE, value).apply()

    var isUsbDebuggingBlocked: Boolean
        get() = prefs.getBoolean(KEY_USB_DEBUGGING_BLOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_USB_DEBUGGING_BLOCKED, value).apply()

    var hiddenIcons: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN_ICONS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_HIDDEN_ICONS, value).apply()

    fun exportSettings(context: Context): String? {
        return try {
            val json = org.json.JSONObject().apply {
                put("allowed_packages", org.json.JSONArray(allowedPackages.toList()))
                put("background_packages", org.json.JSONArray(backgroundPackages.toList()))
                put("hidden_icons", org.json.JSONArray(hiddenIcons.toList()))
                put("lock_mode", lockMode)
                put("idle_timeout", idleTimeoutSeconds)
                put("is_kiosk_active", isKioskActive)
                put("footer_text", footerText)
                put("usb_debugging_blocked", isUsbDebuggingBlocked)
            }
            val dir = context.getExternalFilesDir(null) ?: return null
            val file = java.io.File(dir, "kiosk_settings.json")
            file.writeText(json.toString(4))
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun importSettings(context: Context): Boolean {
        return try {
            val dir = context.getExternalFilesDir(null) ?: return false
            val file = java.io.File(dir, "kiosk_settings.json")
            if (!file.exists()) return false
            val success = importSettingsFromText(context, file.readText())
            if (success) {
                file.delete() // Consume the file to avoid redundant imports
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importSettingsFromText(context: Context, jsonStr: String): Boolean {
        return try {
            val json = org.json.JSONObject(jsonStr)
            
            // Parse and save
            val allowed = mutableSetOf<String>()
            val allowedJson = json.optJSONArray("allowed_packages")
            if (allowedJson != null) {
                for (i in 0 until allowedJson.length()) {
                    allowed.add(allowedJson.getString(i))
                }
            }
            
            val bg = mutableSetOf<String>()
            val bgJson = json.optJSONArray("background_packages")
            if (bgJson != null) {
                for (i in 0 until bgJson.length()) {
                    bg.add(bgJson.getString(i))
                }
            }
            
            val hidden = mutableSetOf<String>()
            val hiddenJson = json.optJSONArray("hidden_icons")
            if (hiddenJson != null) {
                for (i in 0 until hiddenJson.length()) {
                    hidden.add(hiddenJson.getString(i))
                }
            }
            
            // Write to shared preferences
            allowedPackages = allowed
            backgroundPackages = bg
            hiddenIcons = hidden
            if (json.has("lock_mode")) lockMode = json.getString("lock_mode")
            if (json.has("idle_timeout")) idleTimeoutSeconds = json.getInt("idle_timeout")
            if (json.has("is_kiosk_active")) isKioskActive = json.getBoolean("is_kiosk_active")
            if (json.has("footer_text")) footerText = json.getString("footer_text")
            if (json.has("usb_debugging_blocked")) isUsbDebuggingBlocked = json.getBoolean("usb_debugging_blocked")
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
