package com.example.kioskdeviceowner.ui.kiosk

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kioskdeviceowner.KioskSettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LockscreenScreen(
    settingsManager: KioskSettingsManager,
    onUnlock: () -> Unit
) {
    val lockMode = settingsManager.lockMode
    var pinInput by remember { mutableStateOf("") }
    // Phase: "swipe" = tampilkan swipe up dulu, "pin" = tampilkan keypad PIN
    var phase by remember { mutableStateOf("swipe") }
    var shakeTrigger by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    var isGpsHardwareEnabled by remember { mutableStateOf(false) }
    var isGpsAppRunning by remember { mutableStateOf(false) }
    var activeNotifications by remember { mutableStateOf<List<android.service.notification.StatusBarNotification>>(emptyList()) }

    DisposableEffect(Unit) {
        val listenerObj = object : com.example.kioskdeviceowner.service.KioskNotificationListener.NotificationUpdateListener {
            override fun onNotificationsUpdated() {
                activeNotifications = com.example.kioskdeviceowner.service.KioskNotificationListener.activeNotificationsList.toList()
            }
        }
        com.example.kioskdeviceowner.service.KioskNotificationListener.listener = listenerObj
        activeNotifications = com.example.kioskdeviceowner.service.KioskNotificationListener.activeNotificationsList.toList()
        
        onDispose {
            if (com.example.kioskdeviceowner.service.KioskNotificationListener.listener == listenerObj) {
                com.example.kioskdeviceowner.service.KioskNotificationListener.listener = null
            }
        }
    }

    // Date/Time States
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        
        var ticks = 0
        while (true) {
            val now = Date()
            timeText = timeFormat.format(now)
            dateText = dateFormat.format(now)
            
            if (ticks % 2 == 0) {
                isGpsHardwareEnabled = try {
                    locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                } catch (e: Exception) {
                    false
                }
                val gpsPackages = listOf("com.mendhak.gpstanaman", "com.mendhak.gpsunit", "com.eclipsim.gpsstatus2")
                val isNotificationActive = synchronized(com.example.kioskdeviceowner.service.KioskNotificationListener.activeNotificationsList) {
                    com.example.kioskdeviceowner.service.KioskNotificationListener.activeNotificationsList.any { gpsPackages.contains(it.packageName) }
                }
                
                isGpsAppRunning = isNotificationActive || try {
                    val runningProcesses = activityManager.runningAppProcesses ?: emptyList()
                    runningProcesses.any { gpsPackages.contains(it.processName) }
                } catch (e: Exception) {
                    false
                }
            }
            ticks++
            delay(1000)
        }
    }

    // Shake Offset Animation
    val shakeOffset by animateFloatAsState(
        targetValue = if (shakeTrigger > 0) (if (shakeTrigger % 2 == 0) 15f else -15f) else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        finishedListener = {
            if (shakeTrigger > 0) {
                if (shakeTrigger < 6) {
                    shakeTrigger += 1
                } else {
                    shakeTrigger = 0
                }
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Floating decorative blurred circles
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-50).dp)
                .background(Color(0x0A8B5CF6), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = 100.dp)
                .background(Color(0x0AE11D48), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Minimalist jam & tanggal di pojok kiri atas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = timeText,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Thin,
                        color = Color.White,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = dateText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 0.5.sp
                    )
                }
                // Badge status GPS kanan atas
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (isGpsHardwareEnabled) Color(0xFF10B981) else Color(0xFFEF4444),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "GPS",
                            fontSize = 10.sp,
                            color = if (isGpsHardwareEnabled) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (isGpsAppRunning) Color(0xFF10B981) else Color(0xFFEF4444),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isGpsAppRunning) "APP" else "APP",
                            fontSize = 10.sp,
                            color = if (isGpsAppRunning) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tengah: Notifikasi GPS (jika ada)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val allowedPackages = settingsManager.allowedPackages
                val visibleNotifications = activeNotifications.filter { allowedPackages.contains(it.packageName) }
                if (visibleNotifications.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        visibleNotifications.forEach { sbn ->
                            val extras = sbn.notification.extras
                            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
                            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                            val appName = try {
                                val pm = context.packageManager
                                val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                sbn.packageName
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)),
                                color = Color.White.copy(alpha = 0.05f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = appName,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.LightGray
                                            )
                                            Text(
                                                text = "Notifikasi",
                                                fontSize = 8.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        if (title.isNotEmpty()) {
                                            Text(
                                                text = title,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        if (text.isNotEmpty()) {
                                            Text(
                                                text = text,
                                                fontSize = 10.sp,
                                                color = Color.LightGray.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }


            // Body: Unlock Swipe or PIN
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (phase == "swipe") {
                    // PHASE 1: SWIPE UP - selalu tampil pertama kali
                    var dragY by remember { mutableStateOf(0f) }
                    val maxDrag = -300f // Swipe Up adalah Y negatif

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (dragY / 3).dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = {
                                        if (dragY < maxDrag) {
                                            // Jika mode PIN → tampilkan keypad PIN
                                            // Jika mode SWIPE → langsung buka dashboard
                                            if (lockMode == KioskSettingsManager.LOCK_MODE_PIN) {
                                                phase = "pin"
                                            } else {
                                                onUnlock()
                                            }
                                        }
                                        dragY = 0f
                                    },
                                    onDragCancel = { dragY = 0f },
                                    onDrag = { _, dragAmount ->
                                        if (dragAmount.y < 0 || dragY < 0) {
                                            dragY += dragAmount.y
                                        }
                                    }
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Bounce arrow animation
                        val infiniteTransition = rememberInfiniteTransition()
                        val arrowYOffset by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = -15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )

                        Text(
                            text = "▲",
                            fontSize = 24.sp,
                            color = Color(0xFF8B5CF6).copy(alpha = 0.8f),
                            modifier = Modifier.offset(y = arrowYOffset.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Geser ke atas untuk membuka",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                } else if (phase == "pin") {
                    // PHASE 2: PIN KEYPAD
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = if (errorMessage.isEmpty()) "Masukkan PIN" else errorMessage,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (errorMessage.isEmpty()) Color.White.copy(alpha = 0.8f) else Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // PIN indicator circles
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            val pinLength = settingsManager.lockscreenPin.length.coerceAtLeast(4)
                            repeat(pinLength) { index ->
                                val isActive = index < pinInput.length
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                        .background(
                                            if (isActive) Color.White else Color.Transparent,
                                            CircleShape
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(30.dp))

                        // PIN keyboard grid
                        val keys = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("", "0", "Delete")
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            keys.forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                    row.forEach { key ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .clip(CircleShape)
                                                .background(
                                                    if (key.isNotEmpty()) Color.White.copy(alpha = 0.05f) else Color.Transparent
                                                )
                                                .clickable(enabled = key.isNotEmpty()) {
                                                    val requiredLength = settingsManager.lockscreenPin.length
                                                    when (key) {
                                                        "Delete" -> {
                                                            if (pinInput.isNotEmpty()) {
                                                                pinInput = pinInput.dropLast(1)
                                                            }
                                                        }
                                                        else -> {
                                                            if (pinInput.length < requiredLength) {
                                                                pinInput += key
                                                                errorMessage = ""
                                                                if (pinInput.length == requiredLength) {
                                                                    val typedPin = pinInput
                                                                    android.util.Log.d("KioskLock", "PIN entered. Typed: '$typedPin', Expected: '${settingsManager.lockscreenPin}'")
                                                                    scope.launch {
                                                                        delay(150)
                                                                        if (typedPin == settingsManager.lockscreenPin) {
                                                                            onUnlock()
                                                                            pinInput = ""
                                                                        } else {
                                                                            shakeTrigger = 1
                                                                            errorMessage = "PIN Salah!"
                                                                            pinInput = ""
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (key) {
                                                "Delete" -> Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                    contentDescription = "Delete",
                                                    tint = Color.White
                                                )
                                                else -> Text(
                                                    text = key,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.Light,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { phase = "swipe"; pinInput = ""; errorMessage = "" }) {
                            Text("← Kembali", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Footer info
            Text(
                text = "Kiosk Device Owner | Secured",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}
