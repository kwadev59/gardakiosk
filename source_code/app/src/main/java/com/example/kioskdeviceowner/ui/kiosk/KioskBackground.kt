package com.example.kioskdeviceowner.ui.kiosk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.kioskdeviceowner.KioskSettingsManager
import java.io.File

fun loadOrientedBitmap(path: String): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    val bitmap = BitmapFactory.decodeFile(path) ?: return null
    return try {
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        bitmap
    }
}

enum class KioskScreenType {
    DASHBOARD,
    LOCKSCREEN
}

data class WallpaperPresetInfo(
    val id: String,
    val name: String,
    val colors: List<Color>
)

object WallpaperPresets {
    val COSMIC = WallpaperPresetInfo(
        "COSMIC",
        "Dark Cosmic",
        listOf(Color(0xFF0F0C20), Color(0xFF0B0916))
    )
    val EMERALD = WallpaperPresetInfo(
        "EMERALD",
        "Emerald Forest",
        listOf(Color(0xFF061A14), Color(0xFF030D0A))
    )
    val OCEAN = WallpaperPresetInfo(
        "OCEAN",
        "Deep Ocean",
        listOf(Color(0xFF061320), Color(0xFF030A12))
    )
    val PURPLE = WallpaperPresetInfo(
        "PURPLE",
        "Midnight Purple",
        listOf(Color(0xFF160824), Color(0xFF0B0414))
    )
    val SUNSET = WallpaperPresetInfo(
        "SUNSET",
        "Sunset Glow",
        listOf(Color(0xFF240A10), Color(0xFF120408))
    )
    val DARK = WallpaperPresetInfo(
        "DARK",
        "Solid Charcoal",
        listOf(Color(0xFF161618), Color(0xFF0A0A0C))
    )

    val ALL = listOf(COSMIC, EMERALD, OCEAN, PURPLE, SUNSET, DARK)

    fun getColors(id: String): List<Color> {
        return ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }?.colors ?: COSMIC.colors
    }
}

@Composable
fun KioskBackground(
    settingsManager: KioskSettingsManager,
    screenType: KioskScreenType,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val updateTrigger = settingsManager.wallpaperUpdateTrigger
    val target = settingsManager.wallpaperTarget
    val isTargetActive = when (target) {
        KioskSettingsManager.WALLPAPER_TARGET_BOTH -> true
        KioskSettingsManager.WALLPAPER_TARGET_DASHBOARD -> screenType == KioskScreenType.DASHBOARD
        KioskSettingsManager.WALLPAPER_TARGET_LOCKSCREEN -> screenType == KioskScreenType.LOCKSCREEN
        else -> true
    }

    val type = if (isTargetActive) settingsManager.wallpaperType else KioskSettingsManager.WALLPAPER_TYPE_PRESET
    val presetId = if (isTargetActive) settingsManager.wallpaperPreset else "COSMIC"
    val imagePath = if (isTargetActive) settingsManager.wallpaperImagePath else ""
    val dimAlpha = if (isTargetActive) settingsManager.wallpaperDim.coerceIn(0f, 0.85f) else 0.2f

    var loadedBitmap by remember(updateTrigger, imagePath, type) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(updateTrigger, imagePath, type) {
        if (type == KioskSettingsManager.WALLPAPER_TYPE_CUSTOM && imagePath.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val file = File(imagePath)
                    if (file.exists()) {
                        val bmp = loadOrientedBitmap(file.absolutePath)
                        if (bmp != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                loadedBitmap = bmp.asImageBitmap()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            loadedBitmap = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (type == KioskSettingsManager.WALLPAPER_TYPE_CUSTOM && loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap!!,
                contentDescription = "Wallpaper",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val colors = WallpaperPresets.getColors(presetId)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = colors))
            )
        }

        // Dark dim overlay for content contrast
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }

        // Content
        content()
    }
}
