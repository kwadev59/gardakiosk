package com.example.kioskdeviceowner.ui.kiosk

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.kioskdeviceowner.KioskSettingsManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    settingsManager: KioskSettingsManager,
    onLaunchApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLockDevice: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager

    // Date/Time States
    var timeText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }

    // Settings PIN States
    var showPinPrompt by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var shakeTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var clockTapCount by remember { mutableStateOf(0) }
    var lastClockTapTime by remember { mutableStateOf(0L) }
    var selectedAppForOptions by remember { mutableStateOf<AppItem?>(null) }

    val shakeOffset by animateFloatAsState(
        targetValue = if (shakeTrigger > 0) (if (shakeTrigger % 2 == 0) 10f else -10f) else 0f,
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

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        while (true) {
            val now = Date()
            timeText = timeFormat.format(now)
            dateText = dateFormat.format(now)
            delay(1000)
        }
    }

    // Resolve whitelisted apps
    // Resolve whitelisted apps asynchronously to prevent UI lag
    var allowedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    LaunchedEffect(settingsManager.allowedPackages, settingsManager.hiddenIcons) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val hidden = settingsManager.hiddenIcons
            val loaded = settingsManager.allowedPackages
                .filter { pkg -> !hidden.contains(pkg) }
                .mapNotNull { pkg ->
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        AppItem(
                            name = pm.getApplicationLabel(appInfo).toString(),
                            packageName = pkg,
                            icon = pm.getApplicationIcon(appInfo)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.name }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                allowedApps = loaded
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20),
                        Color(0xFF0B0916)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            // Top Bar: Clock, Lock Indicator, Settings Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClockTapTime < 500) {
                            clockTapCount++
                            if (clockTapCount >= 5) {
                                clockTapCount = 0
                                pinInput = ""
                                pinError = ""
                                showPinPrompt = true
                            }
                        } else {
                            clockTapCount = 1
                        }
                        lastClockTapTime = currentTime
                    }
                ) {
                    Text(
                        text = timeText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = dateText,
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Wi-Fi Button
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wi-Fi Config",
                            tint = Color.White
                        )
                    }

                    // Bluetooth Button
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth Config",
                            tint = Color.White
                        )
                    }

                    // Lock Device Button
                    IconButton(
                        onClick = onLockDevice,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Lock Device",
                            tint = Color.White
                        )
                    }
                }
            }

            // Allowed Apps Grid
            if (allowedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Akses Terbatas",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tidak ada aplikasi yang diizinkan untuk perangkat ini.\nHubungi Administrator untuk mengonfigurasi.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(allowedApps) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .combinedClickable(
                                    onClick = { onLaunchApp(app.packageName) },
                                    onLongClick = { selectedAppForOptions = app }
                                )
                                .padding(12.dp)
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(Color.Gray, RoundedCornerShape(12.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = app.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Footer
            Text(
                text = settingsManager.footerText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 24.dp)
            )
        }
    }

    if (showPinPrompt) {
        Dialog(onDismissRequest = { showPinPrompt = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Akses Pengaturan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (pinError.isEmpty()) "Masukkan PIN Administrator" else pinError,
                        fontSize = 12.sp,
                        color = if (pinError.isEmpty()) Color.LightGray else Color(0xFFEF4444),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // PIN Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        val pinLength = settingsManager.settingsPin.length.coerceAtLeast(4)
                        repeat(pinLength) { idx ->
                            val isActive = idx < pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                    .background(
                                        if (isActive) Color.White else Color.Transparent,
                                        CircleShape
                                    )
                            )
                        }
                    }

                    // Keypad
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("Batal", "0", "Hapus")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        keys.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1.3f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (key.isNotEmpty()) Color.White.copy(alpha = 0.05f) else Color.Transparent
                                            )
                                            .clickable(enabled = key.isNotEmpty()) {
                                                val requiredLength = settingsManager.settingsPin.length
                                                when (key) {
                                                    "Batal" -> {
                                                        showPinPrompt = false
                                                    }
                                                    "Hapus" -> {
                                                        if (pinInput.isNotEmpty()) {
                                                            pinInput = pinInput.dropLast(1)
                                                        }
                                                    }
                                                    else -> {
                                                        if (pinInput.length < requiredLength) {
                                                            pinInput += key
                                                            pinError = ""
                                                            if (pinInput.length == requiredLength) {
                                                                val typedPin = pinInput
                                                                scope.launch {
                                                                    delay(150)
                                                                    if (typedPin == settingsManager.settingsPin) {
                                                                        showPinPrompt = false
                                                                        onOpenSettings()
                                                                    } else {
                                                                        shakeTrigger = 1
                                                                        pinError = "PIN Salah!"
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
                                            "Hapus" -> Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "Hapus",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            "Batal" -> Text(
                                                text = "Batal",
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                            else -> Text(
                                                text = key,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
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

    selectedAppForOptions?.let { app ->
        AlertDialog(
            onDismissRequest = { selectedAppForOptions = null },
            title = {
                Text(
                    text = app.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Paket: ${app.packageName}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pilih aksi untuk aplikasi ini. Anda dapat membuka aplikasi atau masuk ke info sistem untuk mengelola izin/data.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedAppForOptions = null
                        onLaunchApp(app.packageName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Text("Buka Aplikasi", color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            selectedAppForOptions = null
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", app.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Info Aplikasi")
                    }
                    
                    TextButton(onClick = { selectedAppForOptions = null }) {
                        Text("Batal", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF151228),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
