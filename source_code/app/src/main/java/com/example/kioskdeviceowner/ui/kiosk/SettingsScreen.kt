package com.example.kioskdeviceowner.ui.kiosk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.kioskdeviceowner.KioskSettingsManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: KioskSettingsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager

    // Settings States
    var allowedList by remember { mutableStateOf(settingsManager.allowedPackages) }
    var backgroundList by remember { mutableStateOf(settingsManager.backgroundPackages) }
    var lockscreenPinText by remember { mutableStateOf(settingsManager.lockscreenPin) }
    var settingsPinText by remember { mutableStateOf(settingsManager.settingsPin) }
    var lockMode by remember { mutableStateOf(settingsManager.lockMode) }
    var idleTimeout by remember { mutableStateOf(settingsManager.idleTimeoutSeconds) }
    var isKioskActive by remember { mutableStateOf(settingsManager.isKioskActive) }
    var footerText by remember { mutableStateOf(settingsManager.footerText) }
    var isUsbDebuggingBlocked by remember { mutableStateOf(settingsManager.isUsbDebuggingBlocked) }
    var hiddenList by remember { mutableStateOf(settingsManager.hiddenIcons) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonStr = inputStream?.bufferedReader()?.use { it.readText() }
                if (!jsonStr.isNullOrEmpty()) {
                    val success = settingsManager.importSettingsFromText(context, jsonStr)
                    if (success) {
                        allowedList = settingsManager.allowedPackages
                        backgroundList = settingsManager.backgroundPackages
                        lockscreenPinText = settingsManager.lockscreenPin
                        settingsPinText = settingsManager.settingsPin
                        lockMode = settingsManager.lockMode
                        idleTimeout = settingsManager.idleTimeoutSeconds
                        isKioskActive = settingsManager.isKioskActive
                        footerText = settingsManager.footerText
                        isUsbDebuggingBlocked = settingsManager.isUsbDebuggingBlocked
                        hiddenList = settingsManager.hiddenIcons
                        android.widget.Toast.makeText(context, "Konfigurasi berhasil diimpor!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Format JSON tidak valid", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Gagal membaca berkas", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // App Search State
    var searchQuery by remember { mutableStateOf("") }
    
    // Tab State
    var selectedTab by remember { mutableStateOf(0) }

    // Query installed apps asynchronously to prevent UI lag on entry-level hardware
    var isLoadingApps by remember { mutableStateOf(true) }
    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val list = pm.queryIntentActivities(intent, 0)
            val loaded = list.map {
                AppItem(
                    name = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }.filter { it.packageName != context.packageName } // exclude self
             .sortedBy { it.name }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                installedApps = loaded
                isLoadingApps = false
            }
        }
    }

    val filteredApps = installedApps.filter {
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.packageName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kiosk Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Simply go back without saving (Cancel)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (lockscreenPinText.length != 6) {
                            android.widget.Toast.makeText(context, "PIN Lockscreen harus terdiri dari 6 digit!", android.widget.Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        if (settingsPinText.length != 6) {
                            android.widget.Toast.makeText(context, "PIN Pengaturan harus terdiri dari 6 digit!", android.widget.Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        // Save settings
                        settingsManager.allowedPackages = allowedList
                        settingsManager.backgroundPackages = backgroundList
                        settingsManager.lockscreenPin = lockscreenPinText
                        settingsManager.settingsPin = settingsPinText
                        settingsManager.lockMode = lockMode
                        settingsManager.idleTimeoutSeconds = idleTimeout
                        settingsManager.isKioskActive = isKioskActive
                        settingsManager.footerText = footerText
                        settingsManager.isUsbDebuggingBlocked = isUsbDebuggingBlocked
                        settingsManager.hiddenIcons = hiddenList

                        // Sync lock task packages immediately
                        if (dpm.isDeviceOwnerApp(context.packageName)) {
                            try {
                                val admin = com.example.kioskdeviceowner.receiver.KioskDeviceAdminReceiver.getComponentName(context)
                                val lockPackages = (allowedList + context.packageName + "com.android.settings" + "com.google.android.documentsui" + "com.android.documentsui").toTypedArray()
                                dpm.setLockTaskPackages(admin, lockPackages)

                                // Apply debugging restriction
                                if (isUsbDebuggingBlocked) {
                                    dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                                } else {
                                    dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        android.widget.Toast.makeText(context, "Pengaturan Berhasil Disimpan!", android.widget.Toast.LENGTH_SHORT).show()
                        onBack()
                    }) {
                        Text(
                            text = "SIMPAN",
                            color = Color(0xFF8B5CF6),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0C20)
                )
            )
        },
        containerColor = Color(0xFF0B0916)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Material3 TabRow
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0F0C20),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF8B5CF6)
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("List Aplikasi", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Apps, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Keamanan", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("About", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // TAB 0: List Aplikasi
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Search bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari aplikasi...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Text(
                                "Pilih aplikasi yang diizinkan untuk Kiosk Launcher. Tandai 'BG' agar aplikasi tetap berjalan di latar belakang.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )

                            if (isLoadingApps) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF8B5CF6))
                                }
                            } else {
                                // List of apps
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                if (filteredApps.isEmpty()) {
                                    item {
                                        Text("Aplikasi tidak ditemukan", color = Color.Gray, fontSize = 14.sp)
                                    }
                                } else {
                                    items(filteredApps) { app ->
                                        val isAllowed = allowedList.contains(app.packageName)
                                        val isBackground = backgroundList.contains(app.packageName)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.02f))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // App Icon
                                            if (app.icon != null) {
                                                Image(
                                                    bitmap = app.icon.toBitmap().asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color.Gray, RoundedCornerShape(4.dp))
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(12.dp))
                                            
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                                Text(app.packageName, fontSize = 10.sp, color = Color.Gray)
                                            }

                                            // Whitelist Checkbox
                                            Checkbox(
                                                checked = isAllowed,
                                                onCheckedChange = { checked ->
                                                    val current = allowedList.toMutableSet()
                                                    if (checked) current.add(app.packageName) else current.remove(app.packageName)
                                                    allowedList = current
                                                }
                                            )

                                            // Show/Hide Icon Eye Toggle Button
                                            IconButton(
                                                onClick = {
                                                    val current = hiddenList.toMutableSet()
                                                    if (current.contains(app.packageName)) {
                                                        current.remove(app.packageName)
                                                    } else {
                                                        current.add(app.packageName)
                                                    }
                                                    hiddenList = current
                                                },
                                                enabled = isAllowed,
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                val isHidden = hiddenList.contains(app.packageName)
                                                Icon(
                                                    imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (isHidden) "Sembunyi" else "Tampil",
                                                    tint = if (!isAllowed) Color.DarkGray else if (isHidden) Color(0xFFEF4444) else Color(0xFF10B981)
                                                )
                                            }

                                            // Background Checkbox
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("BG", fontSize = 9.sp, color = if (isAllowed) Color.LightGray else Color.DarkGray)
                                                Checkbox(
                                                    checked = isBackground && isAllowed,
                                                    enabled = isAllowed,
                                                    onCheckedChange = { checked ->
                                                        val current = backgroundList.toMutableSet()
                                                        if (checked) current.add(app.packageName) else current.remove(app.packageName)
                                                        backgroundList = current
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
                    1 -> {
                        // TAB 1: Keamanan (PIN & Settings)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Mode Kiosk Aktif", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(
                                                "Kiosk mengunci navigasi HP bawaan jika diaktifkan.",
                                                fontSize = 11.sp,
                                                color = Color.LightGray
                                            )
                                        }
                                        Switch(
                                            checked = isKioskActive,
                                            onCheckedChange = { isKioskActive = it }
                                        )
                                    }
                                }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Kunci USB Debugging (Anti-ADB)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(
                                                "Menolak semua koneksi ADB dari komputer untuk mencegah Kiosk dimatikan dari luar.",
                                                fontSize = 11.sp,
                                                color = Color.LightGray
                                            )
                                        }
                                        Switch(
                                            checked = isUsbDebuggingBlocked,
                                            onCheckedChange = { isUsbDebuggingBlocked = it }
                                        )
                                    }
                                }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text("Metode Penguncian Layar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))

                                        // Lockscreen Mode
                                        Column {
                                            Text("Tipe Lockscreen Utama", fontSize = 13.sp, color = Color.LightGray)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = lockMode == KioskSettingsManager.LOCK_MODE_SWIPE,
                                                        onClick = { lockMode = KioskSettingsManager.LOCK_MODE_SWIPE }
                                                    )
                                                    Text("Swipe Up", color = Color.White, modifier = Modifier.padding(start = 4.dp))
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = lockMode == KioskSettingsManager.LOCK_MODE_PIN,
                                                        onClick = { lockMode = KioskSettingsManager.LOCK_MODE_PIN }
                                                    )
                                                    Text("PIN (6 Digit)", color = Color.White, modifier = Modifier.padding(start = 4.dp))
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                        // Lockscreen PIN
                                        OutlinedTextField(
                                            value = lockscreenPinText,
                                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) lockscreenPinText = it },
                                            label = { Text("PIN Lockscreen (6 Digit)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF8B5CF6)
                                            )
                                        )

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                        // Settings PIN
                                        OutlinedTextField(
                                            value = settingsPinText,
                                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) settingsPinText = it },
                                            label = { Text("PIN Masuk Pengaturan (6 Digit)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF8B5CF6)
                                            )
                                        )

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                        // Custom Footer Text Display
                                        OutlinedTextField(
                                            value = footerText,
                                            onValueChange = { if (it.length <= 50) footerText = it },
                                            label = { Text("Teks Display Informasi (Bawah)") },
                                            placeholder = { Text("Kiosk Device Owner Mode Aktif") },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFF8B5CF6)
                                            )
                                        )

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                        // Notification Access Button
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Akses Notifikasi Sistem", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                                            Text(
                                                "Buka akses notifikasi agar Garda Kiosk dapat memantau dan menampilkan status GPS Tanaman/Unit asli di layar kunci.",
                                                fontSize = 11.sp,
                                                color = Color.LightGray
                                            )
                                            Button(
                                                onClick = {
                                                    try {
                                                        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        android.widget.Toast.makeText(context, "Gagal membuka pengaturan", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1B4B))
                                            ) {
                                                Text("Buka Izin Akses Notifikasi", color = Color.White)
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                        // Export / Import Section
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("Cadangan Konfigurasi", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                                            Text(
                                                "Ekspor setelan ke file JSON atau impor dari setelan perangkat lain untuk mempercepat setup massal.",
                                                fontSize = 11.sp,
                                                color = Color.LightGray
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val path = settingsManager.exportSettings(context)
                                                        if (path != null) {
                                                            android.widget.Toast.makeText(context, "Berhasil diekspor ke: $path", android.widget.Toast.LENGTH_LONG).show()
                                                        } else {
                                                            android.widget.Toast.makeText(context, "Gagal mengekspor setelan", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1B4B))
                                                ) {
                                                    Text("Ekspor JSON", color = Color.White)
                                                }

                                                Button(
                                                    onClick = {
                                                        try {
                                                            filePickerLauncher.launch("*/*")
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                            android.widget.Toast.makeText(context, "Gagal membuka File Picker", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                                                ) {
                                                    Text("Impor JSON", color = Color.White)
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                                        // Timeout Settings
                                        Column {
                                            Text("Waktu Layar Mati (Idle Screenoff)", fontSize = 13.sp, color = Color.LightGray)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val timeouts = listOf(
                                                15 to "15 Detik",
                                                30 to "30 Detik",
                                                60 to "1 Menit",
                                                120 to "2 Menit",
                                                300 to "5 Menit",
                                                0 to "Nonaktifkan"
                                            )
                                            
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                timeouts.forEach { (sec, label) ->
                                                    val isSelected = idleTimeout == sec
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = { idleTimeout = sec },
                                                        label = { Text(label) },
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = Color(0xFF8B5CF6),
                                                            selectedLabelColor = Color.White,
                                                            containerColor = Color.White.copy(alpha = 0.05f),
                                                            labelColor = Color.LightGray
                                                        )
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Setelan Sistem HP",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF8B5CF6)
                                        )
                                        Text(
                                            text = "Buka pengaturan sistem Samsung secara langsung untuk mengatur Wi-Fi, Bluetooth, Tampilan, atau opsi pengembang.",
                                            fontSize = 11.sp,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    android.widget.Toast.makeText(context, "Gagal membuka setelan sistem", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1B4B)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Buka Pengaturan Samsung", color = Color.White)
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Administrasi Sistem (Device Owner)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Red
                                        )
                                        Text(
                                            text = "Lepaskan status Device Owner secara permanen dari perangkat ini. Kiosk mode akan dinonaktifkan dan aplikasi dapat dihapus/di-uninstall secara normal.",
                                            fontSize = 11.sp,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        var showConfirmDODialog by remember { mutableStateOf(false) }
                                        
                                        Button(
                                            onClick = { showConfirmDODialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Lepaskan Device Owner", color = Color.White)
                                        }
                                        
                                        if (showConfirmDODialog) {
                                            AlertDialog(
                                                onDismissRequest = { showConfirmDODialog = false },
                                                title = { Text("Konfirmasi Lepas Device Owner", color = Color.White, fontWeight = FontWeight.Bold) },
                                                text = {
                                                    Text(
                                                        "Apakah Anda yakin ingin melepas status Device Owner? Kiosk mode akan segera dinonaktifkan dan perangkat kembali ke kondisi normal.",
                                                        color = Color.LightGray
                                                    )
                                                },
                                                confirmButton = {
                                                    Button(
                                                        onClick = {
                                                            showConfirmDODialog = false
                                                            if (dpm.isDeviceOwnerApp(context.packageName)) {
                                                                try {
                                                                    val admin = com.example.kioskdeviceowner.receiver.KioskDeviceAdminReceiver.getComponentName(context)
                                                                    dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
                                                                    dpm.clearDeviceOwnerApp(context.packageName)
                                                                    dpm.removeActiveAdmin(admin)
                                                                    android.widget.Toast.makeText(context, "Device Owner & Admin berhasil dilepas!", android.widget.Toast.LENGTH_LONG).show()
                                                                    onBack()
                                                                } catch (e: Exception) {
                                                                    android.widget.Toast.makeText(context, "Gagal melepas: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                                                }
                                                            } else {
                                                                android.widget.Toast.makeText(context, "Aplikasi bukan Device Owner", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                                    ) {
                                                        Text("Ya, Lepaskan", color = Color.White)
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = { showConfirmDODialog = false }) {
                                                        Text("Batal", color = Color.Gray)
                                                    }
                                                },
                                                containerColor = Color(0xFF151228)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // TAB 2: About (Tentang Kita)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .background(Color(0xFF8B5CF6).copy(alpha = 0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "K",
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF8B5CF6)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = "Kiosk Device Owner",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Versi 1.0 (Custom Build)",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Device Information grid
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        InfoRow("Model Perangkat", android.os.Build.MODEL)
                                        InfoRow("Pabrikan", android.os.Build.MANUFACTURER)
                                        InfoRow("Versi Android", android.os.Build.VERSION.RELEASE)
                                        InfoRow(
                                            "Status Device Owner",
                                            if (dpm.isDeviceOwnerApp(context.packageName)) "AKTIF (Sangat Aman)" else "TIDAK AKTIF"
                                        )
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Kredit & Tim Pengembang",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF8B5CF6)
                                    )
                                    InfoRow("Developer Utama", "kwadev69")
                                    InfoRow("My Team (AI)", "Deepseek, Antygravity, Chatgpt, Cloude Code, Open Code, GLM")
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
                                shape = RoundedCornerShape(12.dp)
                              ) {
                                  Column(
                                      modifier = Modifier
                                          .fillMaxWidth()
                                          .padding(16.dp),
                                      verticalArrangement = Arrangement.spacedBy(8.dp)
                                  ) {
                                      Text(
                                          text = "Tentang Aplikasi",
                                          fontSize = 14.sp,
                                          fontWeight = FontWeight.Bold,
                                          color = Color(0xFF8B5CF6)
                                      )
                                      Text(
                                          text = "Aplikasi Kiosk Kustom ini dibuat khusus untuk membatasi akses pada HP Samsung A07 Anda. Dengan memanfaatkan hak akses administratif tingkat tinggi (Device Owner), Kiosk ini menjamin keamanan perangkat dari modifikasi ilegal, reset pabrik, dan pemasangan aplikasi yang tidak diizinkan.\n\nSemua hak kontrol dan pengaturan berada penuh di tangan administrator.",
                                          fontSize = 12.sp,
                                          color = Color.LightGray,
                                          lineHeight = 18.sp
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

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
