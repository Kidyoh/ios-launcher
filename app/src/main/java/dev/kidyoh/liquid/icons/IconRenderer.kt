package dev.kidyoh.liquid.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import dev.kidyoh.liquid.data.AppEntry
import dev.kidyoh.liquid.data.IconStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns any Android icon into an iOS-style squircle in one of the four Home Screen styles. */
class IconRenderer(private val context: Context) {
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(48 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val density = context.resources.displayMetrics.densityDpi

    fun cached(app: AppEntry, style: IconStyle, size: Int, tint: Int): ImageBitmap? = cache[key(app.id, style, size, tint)]

    suspend fun icon(app: AppEntry, style: IconStyle, size: Int, tint: Int): ImageBitmap {
        val k = key(app.id, style, size, tint)
        cache[k]?.let { return it }
        return withContext(Dispatchers.Default) {
            val drawable = runCatching { app.iconLoader(density) }.getOrNull()
                ?: context.packageManager.defaultActivityIcon
            val bmp = render(drawable, style, size, tint)
            if (app.isWork) badge(bmp, app)
            bmp.asImageBitmap().also { cache.put(k, it) }
        }
    }

    /** Renders a plain drawable (e.g. an app shortcut icon) as a Default-style squircle. */
    suspend fun plain(drawable: Drawable, size: Int, style: IconStyle = IconStyle.Default, tint: Int = 0): ImageBitmap =
        withContext(Dispatchers.Default) { render(drawable, style, size, tint).asImageBitmap() }

    fun clear() = cache.evictAll()

    private fun key(id: String, style: IconStyle, size: Int, tint: Int) = "$id|$style|$size|$tint"

    fun render(drawable: Drawable, style: IconStyle, s: Int, tint: Int): Bitmap {
        val out = createBitmap(s, s)
        val canvas = Canvas(out)
        val shape = Squircle.androidPath(s.toFloat())
        val adaptive = drawable as? AdaptiveIconDrawable

        canvas.save()
        canvas.clipPath(shape)
        when (style) {
            IconStyle.Default -> {
                if (adaptive != null) {
                    drawLayer(canvas, adaptive.background, s)
                    drawLayer(canvas, adaptive.foreground, s)
                } else {
                    drawLegacy(canvas, drawable, s, background = Color.WHITE)
                }
            }
            IconStyle.Dark -> {
                darkBackground(canvas, s, tint = null)
                if (adaptive != null) drawLayer(canvas, adaptive.foreground, s)
                else drawLegacy(canvas, drawable, s, background = null, scale = 0.72f)
            }
            IconStyle.Tinted -> {
                darkBackground(canvas, s, tint = tint)
                val glyph = glyph(drawable, s)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = tintFilter(tint) }
                canvas.drawBitmap(glyph, 0f, 0f, paint)
            }
            IconStyle.Clear -> {
                // The glass plate itself is drawn live behind the icon; here we only make the glyph frosted white.
                val glyph = glyph(drawable, s)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = frostFilter() }
                canvas.drawBitmap(glyph, 0f, 0f, paint)
            }
        }
        canvas.restore()
        drawRim(canvas, shape, s, strong = style == IconStyle.Clear)
        return out
    }

    /** Adaptive layers are 108dp with the visible 72dp in the middle, so draw them at 1.5x and let the mask crop. */
    private fun drawLayer(canvas: Canvas, layer: Drawable?, s: Int) {
        layer ?: return
        val inset = (s * 0.25f).toInt()
        layer.setBounds(-inset, -inset, s + inset, s + inset)
        layer.draw(canvas)
    }

    private fun drawLegacy(canvas: Canvas, d: Drawable, s: Int, background: Int?, scale: Float = 0.78f) {
        val raster = rasterize(d, s)
        if (background != null && isFullBleed(raster)) {
            canvas.drawBitmap(raster, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            return
        }
        background?.let { canvas.drawColor(it) }
        val inner = (s * scale).toInt()
        val off = (s - inner) / 2
        d.setBounds(off, off, off + inner, off + inner)
        d.draw(canvas)
    }

    private fun isFullBleed(b: Bitmap): Boolean {
        val i = (b.width * 0.1f).toInt()
        val j = b.width - 1 - i
        return listOf(b.getPixel(i, i), b.getPixel(j, i), b.getPixel(i, j), b.getPixel(j, j)).all { Color.alpha(it) > 240 }
    }

    private fun rasterize(d: Drawable, s: Int): Bitmap {
        if (d is BitmapDrawable && d.bitmap != null && d.bitmap.width == s) return d.bitmap
        val b = createBitmap(s, s)
        d.setBounds(0, 0, s, s)
        d.draw(Canvas(b))
        return b
    }

    /** The bit of the icon worth keeping when the background is replaced. */
    private fun glyph(d: Drawable, s: Int): Bitmap {
        val b = createBitmap(s, s)
        val c = Canvas(b)
        val adaptive = d as? AdaptiveIconDrawable
        val mono = if (adaptive != null && Build.VERSION.SDK_INT >= 33) adaptive.monochrome else null
        when {
            mono != null -> {
                mono.mutate().colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                drawLayer(c, mono, s)
            }
            adaptive != null -> drawLayer(c, adaptive.foreground, s)
            else -> {
                val inner = (s * 0.7f).toInt()
                val off = (s - inner) / 2
                d.setBounds(off, off, off + inner, off + inner)
                d.draw(c)
            }
        }
        return b
    }

    private fun darkBackground(canvas: Canvas, s: Int, tint: Int?) {
        val top = if (tint == null) 0xFF323236.toInt() else blend(0xFF2A2A2E.toInt(), tint, 0.10f)
        val bottom = if (tint == null) 0xFF0E0E10.toInt() else blend(0xFF0B0B0D.toInt(), tint, 0.05f)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 0f, s.toFloat(), top, bottom, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
    }

    /** Luminance mapped onto the tint colour, like iOS tinted icons: white becomes exactly the tint. */
    private fun tintFilter(tint: Int): ColorMatrixColorFilter {
        val r = Color.red(tint) / 255f
        val g = Color.green(tint) / 255f
        val b = Color.blue(tint) / 255f
        val lr = 0.30f; val lg = 0.59f; val lb = 0.11f
        // A little lift so dark glyph parts stay visible on the dark plate.
        val lift = 0.18f
        val k = 1f - lift
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    lr * r * k, lg * r * k, lb * r * k, 0f, 255f * r * lift,
                    lr * g * k, lg * g * k, lb * g * k, 0f, 255f * g * lift,
                    lr * b * k, lg * b * k, lb * b * k, 0f, 255f * b * lift,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    /** White glyph whose opacity follows the original's coverage and brightness. */
    private fun frostFilter() = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 255f,
                0f, 0f, 0f, 0f, 255f,
                0f, 0f, 0f, 0f, 255f,
                0.10f, 0.20f, 0.04f, 0.62f, 0f,
            )
        )
    )

    /** The faint glassy edge Liquid Glass icons have. */
    private fun drawRim(canvas: Canvas, shape: android.graphics.Path, s: Int, strong: Boolean) {
        val w = s * (if (strong) 0.03f else 0.022f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 2
            shader = LinearGradient(
                0f, 0f, s * 0.4f, s.toFloat(),
                intArrayOf(
                    Color.argb(if (strong) 200 else 120, 255, 255, 255),
                    Color.argb(if (strong) 40 else 10, 255, 255, 255),
                    Color.argb(if (strong) 110 else 50, 255, 255, 255),
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.save()
        canvas.clipPath(shape)
        canvas.drawPath(shape, p)
        canvas.restore()
    }

    private fun badge(bmp: Bitmap, app: AppEntry) {
        val s = bmp.width
        val blank = BitmapDrawable(context.resources, createBitmap(s, s))
        val badged = context.packageManager.getUserBadgedIcon(blank, app.user)
        badged.setBounds(0, 0, s, s)
        badged.draw(Canvas(bmp))
    }

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
