package com.example.kioskdeviceowner.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast

class KioskDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Kiosk Admin Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Kiosk Admin Disabled", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, KioskDeviceAdminReceiver::class.java)
        }

        /**
         * Set up basic policies when the app is configured as Device Owner.
         */
        fun setupDeviceOwnerPolicies(context: Context, allowedPackages: List<String>) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = getComponentName(context)

            if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    // Paket sistem Samsung tambahan yang dibutuhkan QuickShare & Bluetooth
                    val systemSharePackages = listOf(
                        "com.android.settings",
                        "com.google.android.documentsui",
                        "com.android.documentsui",
                        // Bluetooth & sharing core
                        "com.android.bluetooth",
                        "com.samsung.android.bluetooth",
                        // QuickShare (Samsung)
                        "com.samsung.android.app.sharelive",
                        "com.samsung.android.app.devicesandsharing",
                        "com.samsung.android.aware.service",
                        // Share sheet / intent resolver
                        "com.android.intentresolver",
                        "com.google.android.intentresolver",
                        // File manager & storage access
                        "com.sec.android.app.myfiles",
                        "com.android.externalstorage",
                        // LocalSend
                        "org.localsend.localsend_app"
                    )
                    val lockPackages = (allowedPackages + context.packageName + systemSharePackages)
                        .distinct()
                        .toTypedArray()
                    dpm.setLockTaskPackages(admin, lockPackages)

                    // Enable status bar info (Clock, WiFi, Bluetooth, Battery) but disable pull-down
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        val features = DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
                        dpm.setLockTaskFeatures(admin, features)
                    }

                    // Set as the persistent preferred default Home Launcher activity (0s delay on boot)
                    val filter = android.content.IntentFilter(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                    val activity = ComponentName(context.packageName, "com.example.kioskdeviceowner.MainActivity")
                    dpm.addPersistentPreferredActivity(admin, filter, activity)

                    // Enable notification listener programmatically
                    val componentNameStr = "${context.packageName}/${context.packageName}.service.KioskNotificationListener"
                    try {
                        dpm.setSecureSetting(admin, "enabled_notification_listeners", componentNameStr)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Add useful kiosk restrictions
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)

                    // Disable system keyguard (Samsung Lockscreen) programmatically to avoid double lockscreens
                    try {
                        @Suppress("DEPRECATION")
                        dpm.setKeyguardDisabled(admin, true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Apply USB Debugging block based on settings
                    val settings = com.example.kioskdeviceowner.KioskSettingsManager(context)
                    if (settings.isUsbDebuggingBlocked) {
                        dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
                    } else {
                        dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
                    }
                    


                    // Programmatically grant location permissions to corporate GPS tracking apps (GPS Tanaman & GPS Unit)
                    val gpsPackages = listOf("com.mendhak.gpstanaman", "com.mendhak.gpsunit")
                    val locationPermissions = listOf(
                        "android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.ACCESS_COARSE_LOCATION",
                        "android.permission.ACCESS_BACKGROUND_LOCATION"
                    )
                    for (pkg in gpsPackages) {
                        for (permission in locationPermissions) {
                            try {
                                dpm.setPermissionGrantState(
                                    admin,
                                    pkg,
                                    permission,
                                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    
                    // Keep screen on while plugged in
                    // dpm.setGlobalSetting(admin, android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 
                    //    (BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB).toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
