package dev.kidyoh.liquid.wallpaper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import dev.kidyoh.liquid.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * The wallpaper the launcher draws itself. Owning the pixels is what lets the dock, folders and
 * widgets show real frosted glass: they paint a pre-blurred copy aligned with the screen.
 */
data class Wallpaper(
    val key: String,
    val image: ImageBitmap?,
    val blurred: ImageBitmap?,
    /** True when we couldn't read the system wallpaper and let the window show it instead. */
    val passthrough: Boolean,
    val sharpBitmap: Bitmap? = null,
)

class WallpaperRepository(private val context: Context, private val scope: CoroutineScope, private val settings: SettingsStore) {
    private val _state = MutableStateFlow(Wallpaper("", null, null, passthrough = false))
    val state: StateFlow<Wallpaper> = _state.asStateFlow()
    private val photoFile get() = File(context.filesDir, "wallpaper.jpg")
    private var job: Job? = null

    /** Makes sure the current choice is rendered for this screen size and appearance. */
    fun ensure(choice: String, dark: Boolean, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val key = "$choice|$dark|${width}x$height"
        if (_state.value.key == key) return
        job?.cancel()
        job = scope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { render(choice, dark, width, height, key) }
                    .onFailure { Log.e("Wallpaper", "Couldn't render $choice", it) }
                    .getOrElse { Wallpaper(key, null, null, passthrough = true) }
            }
            _state.value = result
        }
    }

    /** The system wallpaper may have changed while we were away; render it again next time. */
    fun refreshIfSystem() {
        val k = _state.value.key
        if (k.startsWith("system")) {
            val parts = k.split('|')
            val size = parts.getOrNull(2)?.split('x')
            val w = size?.getOrNull(0)?.toIntOrNull() ?: return
            val h = size.getOrNull(1)?.toIntOrNull() ?: return
            _state.value = _state.value.copy(key = "")
            ensure(parts[0], parts.getOrNull(1) == "true", w, h)
        }
    }

    private fun render(choice: String, dark: Boolean, w: Int, h: Int, key: String): Wallpaper {
        val sharp: Bitmap? = when {
            choice.startsWith("preset:") -> Presets.render(Presets.byId(choice.removePrefix("preset:")), w, h, dark)
            choice.startsWith("photo") -> loadPhoto(w, h)
            else -> loadSystem(w, h)
        }
        if (sharp == null) return Wallpaper(key, null, null, passthrough = true)
        val blurred = Blur.frosted(sharp)
        return Wallpaper(key, sharp.asImageBitmap(), blurred.asImageBitmap(), passthrough = false, sharpBitmap = sharp)
    }

    private fun loadPhoto(w: Int, h: Int): Bitmap? = runCatching {
        val f = photoFile
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) >= w && opts.outHeight / (sample * 2) >= h) sample *= 2
        val raw = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        centerCrop(raw, w, h)
    }.getOrNull()

    // Android 13+ only lets privileged apps read it; we catch the SecurityException and let the window show it instead.
    @SuppressLint("MissingPermission")
    private fun loadSystem(w: Int, h: Int): Bitmap? = runCatching {
        val wm = WallpaperManager.getInstance(context)
        if (wm.wallpaperInfo != null) return null // live wallpaper: let it show through the window
        val d = wm.drawable ?: return null
        centerCrop(d.toBitmap(), w, h)
    }.getOrNull()

    /** Copies a picked photo into private storage, then switches to it. */
    suspend fun setPhoto(uri: Uri, w: Int, h: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
                    val scale = max(w.toFloat() / info.size.width, h.toFloat() / info.size.height).coerceAtMost(1f)
                    dec.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            } ?: return@runCatching false
            photoFile.outputStream().use { centerCrop(bmp, w, h).compress(Bitmap.CompressFormat.JPEG, 95, it) }
            withContext(Dispatchers.Main) { settings.update { it.copy(wallpaper = "photo:${System.currentTimeMillis()}") } }
            true
        }.getOrDefault(false)
    }

    /** Also uses the current wallpaper for the system (lock and/or home) — so unlocking flows into the launcher. */
    suspend fun applyToSystem(lock: Boolean): Boolean = withContext(Dispatchers.IO) {
        val bmp = _state.value.sharpBitmap ?: return@withContext false
        runCatching {
            val wm = WallpaperManager.getInstance(context)
            val flags = WallpaperManager.FLAG_SYSTEM or (if (lock) WallpaperManager.FLAG_LOCK else 0)
            wm.setBitmap(bmp, null, true, flags)
            true
        }.getOrDefault(false)
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (src.width * scale).toInt().coerceAtLeast(w)
        val sh = (src.height * scale).toInt().coerceAtLeast(h)
        val scaled = if (sw == src.width && sh == src.height) src else src.scale(sw, sh, filter = true)
        val x = (sw - w) / 2
        val y = (sh - h) / 2
        val soft = if (scaled.config == Bitmap.Config.HARDWARE) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
        return Bitmap.createBitmap(soft, x, y, w, h)
    }
}
