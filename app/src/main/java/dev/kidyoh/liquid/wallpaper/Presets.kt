package dev.kidyoh.liquid.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.createBitmap

/** Soft, flowing "glass ribbon" wallpapers in the spirit of the iOS 26 defaults, drawn procedurally. */
data class WallpaperPreset(
    val id: String,
    val name: String,
    val top: Int,
    val bottom: Int,
    val blobs: List<Int>,
    val ribbon: Pair<Int, Int>,
)

object Presets {
    val all = listOf(
        WallpaperPreset("orbit", "Orbit", 0xFF020A2E.toInt(), 0xFF071B5C.toInt(),
            listOf(0xFF4561E0.toInt(), 0xFF1BA39A.toInt(), 0xFF5EC8FF.toInt()), 0xFF2E7BFF.toInt() to 0xFF5EC8FF.toInt()),
        WallpaperPreset("aurora", "Aurora", 0xFF1B2A6B.toInt(), 0xFF4B1E78.toInt(),
            listOf(0xFF3D7BFF.toInt(), 0xFFB84DFF.toInt(), 0xFF2EE6D6.toInt()), 0xFF6FA8FF.toInt() to 0xFFE07BFF.toInt()),
        WallpaperPreset("bloom", "Bloom", 0xFFFF7EB3.toInt(), 0xFF7A3CFF.toInt(),
            listOf(0xFFFFB2D9.toInt(), 0xFFFF5FA2.toInt(), 0xFF8F6BFF.toInt()), 0xFFFFD1E8.toInt() to 0xFFB18CFF.toInt()),
        WallpaperPreset("sunrise", "Sunrise", 0xFFFFB25B.toInt(), 0xFFFF4E6A.toInt(),
            listOf(0xFFFFE07A.toInt(), 0xFFFF8A3D.toInt(), 0xFFFF5C8A.toInt()), 0xFFFFF0B3.toInt() to 0xFFFF7A59.toInt()),
        WallpaperPreset("ocean", "Ocean", 0xFF0B4F8A.toInt(), 0xFF06243F.toInt(),
            listOf(0xFF1FA2FF.toInt(), 0xFF12D8FA.toInt(), 0xFF2B5BFF.toInt()), 0xFF7FE3FF.toInt() to 0xFF3C7BFF.toInt()),
        WallpaperPreset("lagoon", "Lagoon", 0xFF5EF2C9.toInt(), 0xFF1E7FA8.toInt(),
            listOf(0xFFB5FFE9.toInt(), 0xFF39D2C0.toInt(), 0xFF3A86FF.toInt()), 0xFFE0FFF6.toInt() to 0xFF58B8FF.toInt()),
        WallpaperPreset("forest", "Forest", 0xFF1F5E3B.toInt(), 0xFF0B2A22.toInt(),
            listOf(0xFF4CD07D.toInt(), 0xFF1E9E77.toInt(), 0xFFB8E986.toInt()), 0xFF9BF2B5.toInt() to 0xFF2FBF8F.toInt()),
        WallpaperPreset("ember", "Ember", 0xFF5A0F1E.toInt(), 0xFF1A0508.toInt(),
            listOf(0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFB0124A.toInt()), 0xFFFFB27A.toInt() to 0xFFFF3B5C.toInt()),
        WallpaperPreset("graphite", "Graphite", 0xFF8E8E93.toInt(), 0xFF2C2C2E.toInt(),
            listOf(0xFFD1D1D6.toInt(), 0xFF636366.toInt(), 0xFFAEAEB2.toInt()), 0xFFF2F2F7.toInt() to 0xFF8E8E93.toInt()),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    fun render(p: WallpaperPreset, w: Int, h: Int, dark: Boolean): Bitmap {
        if (p.id == "orbit") return renderOrbit(p, w, h, dark)
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val k = if (dark) 0.42f else 1f
        fun shade(col: Int, alpha: Int = 255) = Color.argb(
            alpha, (Color.red(col) * k).toInt(), (Color.green(col) * k).toInt(), (Color.blue(col) * k).toInt()
        )
        val fw = w.toFloat()
        val fh = h.toFloat()

        c.drawPaint(Paint(Paint.DITHER_FLAG).apply {
            shader = LinearGradient(0f, 0f, fw * 0.3f, fh, shade(p.top), shade(p.bottom), Shader.TileMode.CLAMP)
        })

        // Soft colour pools.
        val spots = listOf(
            Triple(0.15f, 0.18f, 0.75f), Triple(0.9f, 0.45f, 0.8f), Triple(0.3f, 0.88f, 0.85f),
        )
        p.blobs.forEachIndexed { i, col ->
            val (x, y, r) = spots[i % spots.size]
            val radius = fw * r
            c.drawCircle(fw * x, fh * y, radius, Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(fw * x, fh * y, radius, shade(col, 220), shade(col, 0), Shader.TileMode.CLAMP)
            })
        }

        // Two flowing glass ribbons with a bright specular edge.
        fun ribbon(y0: Float, y1: Float, wave: Float, width: Float, from: Int, to: Int, alpha: Int) {
            val path = Path().apply {
                moveTo(-fw * 0.2f, fh * y0)
                cubicTo(fw * 0.25f, fh * (y0 - wave), fw * 0.7f, fh * (y1 + wave), fw * 1.2f, fh * y1)
            }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = fw * width
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(0f, fh * y0, fw, fh * y1, shade(from, alpha), shade(to, alpha), Shader.TileMode.CLAMP)
                maskFilter = BlurMaskFilter(fw * width * 0.28f, BlurMaskFilter.Blur.NORMAL)
            })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = fw * 0.012f
                color = Color.argb(if (dark) 50 else 110, 255, 255, 255)
                maskFilter = BlurMaskFilter(fw * 0.02f, BlurMaskFilter.Blur.NORMAL)
            })
        }
        ribbon(0.62f, 0.30f, 0.16f, 0.42f, p.ribbon.first, p.ribbon.second, 200)
        ribbon(0.82f, 0.58f, 0.10f, 0.26f, p.ribbon.second, p.ribbon.first, 150)

        // A faint top-down light so status bar text always reads.
        c.drawRect(0f, 0f, fw, fh * 0.25f, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, fh * 0.25f, Color.argb(40, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        return bmp
    }

    /** Overlapping glass discs on deep navy with a glowing blue arc, like the iOS 26 default. */
    private fun renderOrbit(p: WallpaperPreset, w: Int, h: Int, dark: Boolean): Bitmap {
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val fw = w.toFloat()
        val fh = h.toFloat()
        val k = if (dark) 0.72f else 1f
        fun col(v: Int, a: Int = 255) = Color.argb(a, (Color.red(v) * k).toInt(), (Color.green(v) * k).toInt(), (Color.blue(v) * k).toInt())
        val paint = { Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG) }

        c.drawPaint(paint().apply { shader = LinearGradient(0f, 0f, fw, fh, col(p.top), col(p.bottom), Shader.TileMode.CLAMP) })

        // Bright cyan light pooling at the bottom-right.
        c.drawCircle(fw * 1.0f, fh * 0.9f, fw * 0.8f, paint().apply {
            shader = RadialGradient(fw * 1.0f, fh * 0.9f, fw * 0.8f, col(0xFF6FD0FF.toInt(), 230), col(0xFF1E5BFF.toInt(), 0), Shader.TileMode.CLAMP)
        })

        // Big blue disc, top-right, lit from the upper left.
        val bx = fw * 0.78f; val by = fh * 0.2f; val br = fw * 0.62f
        c.drawCircle(bx, by, br, paint().apply {
            shader = RadialGradient(bx - br * 0.45f, by - br * 0.1f, br * 1.5f,
                intArrayOf(col(0xFF7C94F0.toInt()), col(0xFF4561E0.toInt()), col(0xFF1C2E8C.toInt())), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawCircle(bx, by, br, paint().apply { style = Paint.Style.STROKE; strokeWidth = fw * 0.004f; color = col(0xFF9DB0FF.toInt(), 150) })

        // Teal disc, bottom-left, overlapping.
        val tx = fw * 0.02f; val ty = fh * 0.66f; val tr = fw * 0.78f
        c.drawCircle(tx, ty, tr, paint().apply {
            shader = RadialGradient(tx + tr * 0.35f, ty - tr * 0.35f, tr * 1.4f,
                intArrayOf(col(0xFF3FD1BE.toInt()), col(0xFF1BA39A.toInt()), col(0xFF0B4A55.toInt())), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        })
        // Soft shadow where the teal disc meets the dark.
        c.drawCircle(tx, ty, tr, paint().apply {
            shader = RadialGradient(tx, ty, tr, intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(120, 0, 0, 10)), floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP)
        })

        // The thin glowing arc sweeping from bottom-left up to the right edge.
        val ax = fw * 1.55f; val ay = fh * 0.62f; val ar = fw * 1.18f
        c.drawCircle(ax, ay, ar, paint().apply {
            style = Paint.Style.STROKE; strokeWidth = fw * 0.03f; color = col(0xFF2E7BFF.toInt(), 120)
            maskFilter = BlurMaskFilter(fw * 0.03f, BlurMaskFilter.Blur.NORMAL)
        })
        c.drawCircle(ax, ay, ar, paint().apply { style = Paint.Style.STROKE; strokeWidth = fw * 0.006f; color = col(0xFF4F9BFF.toInt()) })
        // Inside the arc everything is a little bluer and brighter.
        c.save()
        c.clipPath(Path().apply { addCircle(ax, ay, ar, Path.Direction.CW) })
        c.drawPaint(paint().apply { color = col(0xFF1E5BFF.toInt(), 55) })
        c.restore()

        // Fine grain, like the textured iOS wallpaper.
        val rnd = java.util.Random(7)
        val grain = paint().apply { strokeWidth = 1f }
        repeat((w * h) / 90) {
            grain.color = Color.argb(rnd.nextInt(14), 255, 255, 255)
            c.drawPoint(rnd.nextFloat() * fw, rnd.nextFloat() * fh, grain)
        }
        return bmp
    }
}
