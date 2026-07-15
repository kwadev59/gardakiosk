package com.example.kioskdeviceowner

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kioskdeviceowner.receiver.KioskDeviceAdminReceiver
import com.example.kioskdeviceowner.ui.kiosk.LauncherScreen
import com.example.kioskdeviceowner.ui.kiosk.LockscreenScreen
import com.example.kioskdeviceowner.ui.kiosk.SettingsScreen
import com.example.kioskdeviceowner.theme.KioskDeviceOwnerTheme

sealed interface Screen {
    object Launcher : Screen
    object Settings : Screen
}

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: KioskSettingsManager
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private var fileObserver: android.os.FileObserver? = null

    // States
    private var isLockedState = mutableStateOf(true)
    private var currentScreenState = mutableStateOf<Screen>(Screen.Launcher)
    private var isDeviceOwnerState = mutableStateOf(false)

    // Handler for Idle Screen-off
    private val handler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable {
        goToSleepAndLock()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "allowed_packages" || key == "is_kiosk_active") {
            updateKioskPolicies()
        }
    }

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isLockedState.value = true
                    suspendBackgroundApps()
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (isLockedState.value) {
                        // Bring MainActivity to the front immediately to draw the lockscreen
                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        context?.startActivity(launchIntent)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        settingsManager = KioskSettingsManager(this)
        // Auto-import settings if config JSON is present on startup
        settingsManager.importSettings(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = KioskDeviceAdminReceiver.getComponentName(this)

        isDeviceOwnerState.value = dpm.isDeviceOwnerApp(packageName)

        // Listen for remote setting updates
        val sharedPrefs = getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Register screen off/on receiver to lock device and draw lockscreen on wake
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)

        // Process ADB commands (like EXIT_KIOSK) passed in intent
        handleIntentExtras(intent)

        // Initialize FileObserver to auto-apply pushed settings from adb push
        val filesDir = getExternalFilesDir(null)
        if (filesDir != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                fileObserver = object : android.os.FileObserver(filesDir, android.os.FileObserver.CLOSE_WRITE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path == "kiosk_settings.json") {
                            android.util.Log.d("KioskFileObserver", "kiosk_settings.json modified via ADB push. Reloading...")
                            runOnUiThread {
                                reloadSettingsAndSync()
                            }
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                fileObserver = object : android.os.FileObserver(filesDir.absolutePath, android.os.FileObserver.CLOSE_WRITE) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path == "kiosk_settings.json") {
                            android.util.Log.d("KioskFileObserver", "kiosk_settings.json modified via ADB push. Reloading...")
                            runOnUiThread {
                                reloadSettingsAndSync()
                            }
                        }
                    }
                }
            }
            fileObserver?.startWatching()
        }

        setContent {
            KioskDeviceOwnerTheme {
                val isLocked by isLockedState
                val currentScreen by currentScreenState
                val isDeviceOwner by isDeviceOwnerState

                // Intercept back button to prevent exiting launcher
                BackHandler(enabled = settingsManager.isKioskActive) {
                    // Do nothing
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLocked) {
                            LockscreenScreen(
                                settingsManager = settingsManager,
                                onUnlock = {
                                    isLockedState.value = false
                                    resetIdleTimer()
                                }
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Device Owner warning banner
                                if (!isDeviceOwner) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFDC2626))
                                            .statusBarsPadding()
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "DEVICE OWNER TIDAK AKTIF! Jalankan perintah ADB agar kiosk aman.",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    when (currentScreen) {
                                        Screen.Launcher -> {
                                            LauncherScreen(
                                                settingsManager = settingsManager,
                                                onLaunchApp = { pkg ->
                                                    launchAllowedApp(pkg)
                                                },
                                                onOpenSettings = {
                                                    currentScreenState.value = Screen.Settings
                                                },
                                                onLockDevice = {
                                                    isLockedState.value = true
                                                    suspendBackgroundApps()
                                                }
                                            )
                                        }
                                        Screen.Settings -> {
                                            SettingsScreen(
                                                settingsManager = settingsManager,
                                                onBack = {
                                                    currentScreenState.value = Screen.Launcher
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0) // Remove transition delay/glitch when waking up
    }

    private fun handleIntentExtras(intent: Intent?) {
        intent?.let {
            if (it.getStringExtra("action") == "EXIT_KIOSK") {
                stopKioskMode()
                isLockedState.value = false
                currentScreenState.value = Screen.Launcher
                Toast.makeText(this, "Lockdown task stopped.", Toast.LENGTH_SHORT).show()
            } else if (it.getStringExtra("action") == "OPEN_SETTINGS") {
                isLockedState.value = false
                currentScreenState.value = Screen.Settings
            }
        }
    }

    private fun reloadSettingsAndSync() {
        val success = settingsManager.importSettings(this)
        if (success) {
            Toast.makeText(this, "[Garda Kiosk] Setelan berhasil di-update otomatis!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        isDeviceOwnerState.value = dpm.isDeviceOwnerApp(packageName)

        // Unsuspend all allowed packages when returning to Kiosk dashboard so they open instantly on tap
        if (dpm.isDeviceOwnerApp(packageName)) {
            val allowed = settingsManager.allowedPackages.toTypedArray()
            if (allowed.isNotEmpty()) {
                try {
                    dpm.setPackagesSuspended(adminComponent, allowed, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Start Kiosk Lockdown Mode (LockTask)
        startKioskMode()
        resetIdleTimer()
    }

    override fun onPause() {
        super.onPause()
        stopIdleTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
        val sharedPrefs = getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    // Intercept touch events to reset idle timer
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        resetIdleTimer()
        return super.dispatchTouchEvent(ev)
    }

    private fun resetIdleTimer() {
        stopIdleTimer()
        val timeout = settingsManager.idleTimeoutSeconds
        if (timeout > 0 && !isLockedState.value) {
            handler.postDelayed(idleRunnable, timeout * 1000L)
        }
    }

    private fun stopIdleTimer() {
        handler.removeCallbacks(idleRunnable)
    }

    private fun goToSleepAndLock() {
        isLockedState.value = true
        suspendBackgroundApps()
        if (dpm.isAdminActive(adminComponent)) {
            try {
                dpm.lockNow()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startKioskMode() {
        if (settingsManager.isKioskActive && dpm.isDeviceOwnerApp(packageName)) {
            updateKioskPolicies()
            try {
                startLockTask()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopKioskMode() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateKioskPolicies() {
        if (dpm.isDeviceOwnerApp(packageName)) {
            KioskDeviceAdminReceiver.setupDeviceOwnerPolicies(this, settingsManager.allowedPackages.toList())
        }
    }

    private fun launchAllowedApp(pkg: String) {
        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            // Unsuspend the app first
            if (dpm.isDeviceOwnerApp(packageName)) {
                try {
                    dpm.setPackagesSuspended(adminComponent, arrayOf(pkg), false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            startActivity(launchIntent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0) // Remove slide transition when launching apps in kiosk mode
        } else {
            Toast.makeText(this, "Aplikasi tidak dapat dibuka", Toast.LENGTH_SHORT).show()
        }
    }

    private fun suspendBackgroundApps() {
        if (dpm.isDeviceOwnerApp(packageName)) {
            val allowed = settingsManager.allowedPackages
            val bgAllowed = settingsManager.backgroundPackages
            
            // Suspend all whitelisted apps that are not allowed in background
            val toSuspend = allowed.filter { !bgAllowed.contains(it) }.toTypedArray()
            if (toSuspend.isNotEmpty()) {
                try {
                    dpm.setPackagesSuspended(adminComponent, toSuspend, true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
