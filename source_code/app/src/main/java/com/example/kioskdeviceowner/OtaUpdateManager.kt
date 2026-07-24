package com.example.kioskdeviceowner

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class OtaUpdateManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "ota_update_prefs"
        private const val KEY_LAST_CHECK = "last_ota_check_timestamp"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 Jam Once

        private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/kwadev59/gardakiosk/main/version.json"
        
        const val ACTION_INSTALL_COMPLETE = "com.example.kioskdeviceowner.ACTION_INSTALL_COMPLETE"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val networkInfo = cm.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }

    fun shouldCheckForUpdate(): Boolean {
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        return (now - lastCheck) >= CHECK_INTERVAL_MS
    }

    suspend fun checkAndPerformAutoUpdate(forceCheck: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Skip jika offline
                if (!isNetworkAvailable()) {
                    return@withContext
                }

                // 2. Skip jika belum waktunya (kecuali dipaksa)
                if (!forceCheck && !shouldCheckForUpdate()) {
                    return@withContext
                }

                // 3. Fetch version.json dari GitHub Server
                val jsonStr = fetchUrlText(VERSION_JSON_URL) ?: return@withContext
                val json = JSONObject(jsonStr)

                val serverVersionCode = json.optInt("version_code", 0)
                val apkUrl = json.optString("apk_url", "")

                val currentVersionCode = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                    } else {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                    }
                } catch (e: Exception) {
                    1
                }

                // 4. Jika ada versi lebih baru di server -> DOWNLOAD & INSTALL SEKETIKA
                if (serverVersionCode > currentVersionCode && apkUrl.isNotEmpty()) {
                    val apkFile = File(context.cacheDir, "OtaUpdate.apk")
                    val downloaded = downloadFile(apkUrl, apkFile)

                    if (downloaded && apkFile.exists()) {
                        performSilentInstall(apkFile)
                    }
                } else {
                    // Jika tidak ada update, simpan timestamp agar tidak spam cek selama 12 jam
                    prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchUrlText(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadFile(urlString: String, outputFile: File): Boolean {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = "GET"

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun performSilentInstall(apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            val out = session.openWrite("OtaUpdateSession", 0, apkFile.length())
            apkFile.inputStream().use { input ->
                input.copyTo(out)
                session.fsync(out)
            }
            out.close()

            val intent = Intent(context, OtaInstallReceiver::class.java).apply {
                action = ACTION_INSTALL_COMPLETE
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            session.commit(pendingIntent.intentSender)
            session.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class OtaInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == OtaUpdateManager.ACTION_INSTALL_COMPLETE) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (status == PackageInstaller.STATUS_SUCCESS) {
                // Restart MainActivity after successful update
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(launchIntent)
                }
            }
        }
    }
}
