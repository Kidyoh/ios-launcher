package dev.kidyoh.liquid.icons

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original iPhone-style icons for common built-in apps. Each is an adaptive icon (background + glyph)
 * so the Dark / Tinted / Clear styles work on them too. Glyph coordinates are on a 100×100 grid that
 * maps onto the visible squircle.
 */
enum class PackIcon(val title: String, val top: Int, val bottom: Int, val accent: Int) {
    Phone("Phone", 0xFF65F57E.toInt(), 0xFF0DBE2C.toInt(), 0xFF34C759.toInt()),
    Messages("Messages", 0xFF6EF57D.toInt(), 0xFF12C73A.toInt(), 0xFF34C759.toInt()),
    FaceTime("FaceTime", 0xFF6EF57D.toInt(), 0xFF12C73A.toInt(), 0xFF34C759.toInt()),
    Browser("Browser", 0xFFFFFFFF.toInt(), 0xFFEFEFF4.toInt(), 0xFF0A84FF.toInt()),
    Mail("Mail", 0xFF46B7FF.toInt(), 0xFF0A6CFF.toInt(), 0xFF0A84FF.toInt()),
    Music("Music", 0xFFFF6C86.toInt(), 0xFFFA2D48.toInt(), 0xFFFF375F.toInt()),
    Camera("Camera", 0xFFE8E8ED.toInt(), 0xFFA9A9AF.toInt(), 0xFFAEAEB2.toInt()),
    Photos("Photos", 0xFFFFFFFF.toInt(), 0xFFF4F4F7.toInt(), 0xFFFF9F0A.toInt()),
    Settings("Settings", 0xFFD2D2D7.toInt(), 0xFF8C8C92.toInt(), 0xFF98989D.toInt()),
    Clock("Clock", 0xFF1C1C1E.toInt(), 0xFF000000.toInt(), 0xFFFF9F0A.toInt()),
    Calendar("Calendar", 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF3B30.toInt()),
    Maps("Maps", 0xFFB9F0B0.toInt(), 0xFF71D97F.toInt(), 0xFF30D158.toInt()),
    Weather("Weather", 0xFF4FB0FF.toInt(), 0xFF1D5FDB.toInt(), 0xFF64D2FF.toInt()),
    Notes("Notes", 0xFFFFFFFF.toInt(), 0xFFF6F6F6.toInt(), 0xFFFFD60A.toInt()),
    Reminders("Reminders", 0xFFFFFFFF.toInt(), 0xFFF6F6F6.toInt(), 0xFF0A84FF.toInt()),
    Calculator("Calculator", 0xFF3A3A3C.toInt(), 0xFF1C1C1E.toInt(), 0xFFFF9F0A.toInt()),
    Contacts("Contacts", 0xFFD8D8DD.toInt(), 0xFF9C9CA2.toInt(), 0xFF8E8E93.toInt()),
    Store("App Store", 0xFF2ECBFF.toInt(), 0xFF1A6FFF.toInt(), 0xFF0A84FF.toInt()),
    Files("Files", 0xFFFFFFFF.toInt(), 0xFFF4F4F7.toInt(), 0xFF0A84FF.toInt()),
    Health("Health", 0xFFFFFFFF.toInt(), 0xFFF4F4F7.toInt(), 0xFFFF375F.toInt()),
    Wallet("Wallet", 0xFF2C2C2E.toInt(), 0xFF000000.toInt(), 0xFFFF9F0A.toInt()),
    VoiceMemos("Voice Memos", 0xFF1C1C1E.toInt(), 0xFF000000.toInt(), 0xFFFF453A.toInt()),
    Books("Books", 0xFFFFA64D.toInt(), 0xFFFF7A00.toInt(), 0xFFFF9F0A.toInt()),
    Podcasts("Podcasts", 0xFFD58CFF.toInt(), 0xFF8E3CDB.toInt(), 0xFFBF5AF2.toInt()),
    Video("TV", 0xFF2C2C2E.toInt(), 0xFF000000.toInt(), 0xFF0A84FF.toInt()),
    Translate("Translate", 0xFF2F3A8F.toInt(), 0xFF141A4A.toInt(), 0xFF5E5CE6.toInt());

    fun drawable(): Drawable = AdaptiveIconDrawable(Background(top, bottom), Glyph(this))

    /** Calendar shows today's date, so its cached bitmap changes daily. */
    fun variant(): String = if (this == Calendar) java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR).toString() else ""
}

private class Background(val top: Int, val bottom: Int) : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRect(RectF(b), Paint(Paint.DITHER_FLAG).apply {
            shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
}

/** The glyph layer. In [dark] mode (Dark icon style) white parts take the icon's accent colour, like iOS. */
class Glyph(val icon: PackIcon) : Drawable() {
    var dark = false

    override fun draw(c: Canvas) {
        val b = bounds
        // The visible icon is the middle 72/108 of an adaptive layer.
        val visible = b.width() * 72f / 108f
        c.save()
        c.translate(b.left + (b.width() - visible) / 2f, b.top + (b.height() - visible) / 2f)
        c.scale(visible / 100f, visible / 100f)
        draw(c, icon)
        c.restore()
    }

    private val ink: Int get() = if (dark) icon.accent else Color.WHITE
    private fun fill(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
    private fun stroke(w: Float, col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = w; color = col; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private fun text(size: Float, col: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col; textSize = size; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun draw(c: Canvas, i: PackIcon) {
        when (i) {
            PackIcon.Phone -> c.drawPath(Path().apply {
                moveTo(31f, 22f); cubicTo(23f, 25f, 21f, 41f, 37f, 60f); cubicTo(53f, 78f, 70f, 82f, 77f, 72f)
                cubicTo(79f, 69f, 79f, 66f, 76f, 64f); lineTo(68f, 58f); cubicTo(65f, 56f, 62f, 57f, 60f, 60f); lineTo(58f, 62f)
                cubicTo(51f, 58f, 44f, 51f, 40f, 44f); lineTo(42f, 42f); cubicTo(45f, 40f, 46f, 37f, 44f, 34f); lineTo(38f, 25f)
                cubicTo(36f, 22f, 33f, 21f, 31f, 22f); close()
            }, fill(ink))
            PackIcon.Messages -> {
                c.drawOval(RectF(18f, 24f, 82f, 72f), fill(ink))
                c.drawPath(Path().apply { moveTo(29f, 62f); cubicTo(28f, 70f, 24f, 75f, 19f, 79f); cubicTo(28f, 79f, 36f, 75f, 41f, 69f); close() }, fill(ink))
            }
            PackIcon.FaceTime -> {
                c.drawRoundRect(RectF(16f, 33f, 62f, 67f), 9f, 9f, fill(ink))
                c.drawPath(Path().apply { moveTo(65f, 45f); lineTo(82f, 35f); lineTo(82f, 65f); lineTo(65f, 55f); close() }, fill(ink))
            }
            PackIcon.Browser -> {
                val col = if (dark) icon.accent else 0
                if (!dark) {
                    c.drawCircle(50f, 50f, 38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(0f, 12f, 0f, 88f, 0xFF19D7FF.toInt(), 0xFF1E5FF4.toInt(), Shader.TileMode.CLAMP)
                    })
                } else c.drawCircle(50f, 50f, 38f, stroke(3f, col))
                for (k in 0 until 72) {
                    val a = Math.toRadians(k * 5.0)
                    val r0 = if (k % 6 == 0) 30f else 33f
                    c.drawLine(50f + cos(a).toFloat() * r0, 50f + sin(a).toFloat() * r0, 50f + cos(a).toFloat() * 36f, 50f + sin(a).toFloat() * 36f,
                        stroke(if (k % 6 == 0) 1.6f else 0.8f, if (dark) col else Color.WHITE))
                }
                c.drawPath(Path().apply { moveTo(69f, 31f); lineTo(53.5f, 53.5f); lineTo(46.5f, 46.5f); close() }, fill(0xFFFF3B30.toInt()))
                c.drawPath(Path().apply { moveTo(31f, 69f); lineTo(46.5f, 46.5f); lineTo(53.5f, 53.5f); close() }, fill(if (dark) col else Color.WHITE))
            }
            PackIcon.Mail -> {
                c.drawRoundRect(RectF(17f, 29f, 83f, 71f), 6f, 6f, fill(ink))
                c.drawPath(Path().apply { moveTo(20f, 32f); lineTo(50f, 54f); lineTo(80f, 32f) }, stroke(3.5f, if (dark) Color.BLACK else 0xFF1A78FF.toInt()))
            }
            PackIcon.Music -> {
                c.drawCircle(37f, 69f, 10f, fill(ink)); c.drawCircle(69f, 63f, 10f, fill(ink))
                c.drawLine(46f, 69f, 46f, 30f, stroke(5.5f, ink)); c.drawLine(78f, 63f, 78f, 24f, stroke(5.5f, ink))
                c.drawPath(Path().apply { moveTo(43f, 27f); lineTo(81f, 19f); lineTo(81f, 30f); lineTo(43f, 38f); close() }, fill(ink))
            }
            PackIcon.Camera -> {
                // iOS 26-style lens.
                c.drawCircle(50f, 50f, 33f, fill(if (dark) 0xFF2C2C2E.toInt() else 0xFF8E8E93.toInt()))
                c.drawCircle(50f, 50f, 28f, fill(0xFF0B0B10.toInt()))
                c.drawCircle(50f, 50f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(44f, 44f, 22f, intArrayOf(0xFF3A4C9A.toInt(), 0xFF141A3A.toInt(), 0xFF05060C.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                })
                c.drawOval(RectF(38f, 37f, 52f, 46f), fill(0x99B9D4FF.toInt()))
                c.drawCircle(58f, 57f, 3f, fill(0x66FFFFFF))
            }
            PackIcon.Photos -> {
                val cols = intArrayOf(0xFFFFCC00.toInt(), 0xFFFF9500.toInt(), 0xFFFF3B30.toInt(), 0xFFFF2D55.toInt(), 0xFFAF52DE.toInt(), 0xFF5856D6.toInt(), 0xFF007AFF.toInt(), 0xFF34C759.toInt())
                for (k in 0 until 8) {
                    c.save(); c.rotate(k * 45f, 50f, 50f)
                    c.drawRoundRect(RectF(41f, 14f, 59f, 50f), 9f, 9f, fill(cols[k]).apply { alpha = 215 })
                    c.restore()
                }
            }
            PackIcon.Settings -> {
                val g = if (dark) icon.accent else 0xFF3C3C40.toInt()
                for (k in 0 until 24) { c.save(); c.rotate(k * 15f, 50f, 50f); c.drawRoundRect(RectF(47.5f, 12f, 52.5f, 20f), 1.5f, 1.5f, fill(g)); c.restore() }
                c.drawCircle(50f, 50f, 32f, fill(g))
                c.drawCircle(50f, 50f, 25f, fill(if (dark) Color.BLACK else 0xFFBDBDC2.toInt()))
                c.drawCircle(50f, 50f, 22f, stroke(2.5f, g))
                for (k in 0 until 3) { c.save(); c.rotate(k * 120f, 50f, 50f); c.drawLine(50f, 50f, 50f, 30f, stroke(5f, g)); c.restore() }
                c.drawCircle(50f, 50f, 7f, fill(g))
            }
            PackIcon.Clock -> {
                c.drawCircle(50f, 50f, 38f, fill(if (dark) 0xFF1C1C1E.toInt() else Color.WHITE))
                val h = if (dark) Color.WHITE else Color.BLACK
                for (k in 0 until 12) {
                    val a = Math.toRadians(k * 30.0)
                    c.drawLine(50f + cos(a).toFloat() * 31f, 50f + sin(a).toFloat() * 31f, 50f + cos(a).toFloat() * 35f, 50f + sin(a).toFloat() * 35f, stroke(2f, h))
                }
                c.drawLine(50f, 50f, 34f, 41f, stroke(4.5f, h)); c.drawLine(50f, 50f, 68f, 30f, stroke(3.5f, h))
                c.drawLine(44f, 58f, 61f, 30f, stroke(1.5f, 0xFFFF9500.toInt()))
                c.drawCircle(50f, 50f, 3.2f, fill(0xFFFF9500.toInt()))
            }
            PackIcon.Calendar -> {
                val cal = Calendar.getInstance()
                val day = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
                c.drawText(day, 50f, 27f, text(15f, 0xFFFF3B30.toInt(), bold = true))
                c.drawText(cal.get(Calendar.DAY_OF_MONTH).toString(), 50f, 79f, text(54f, if (dark) Color.WHITE else 0xFF1C1C1E.toInt()))
            }
            PackIcon.Maps -> {
                if (!dark) {
                    c.drawPath(Path().apply { moveTo(-5f, 70f); lineTo(105f, 18f); lineTo(105f, 34f); lineTo(-5f, 86f); close() }, fill(Color.WHITE))
                    c.drawPath(Path().apply { moveTo(56f, -5f); lineTo(70f, -5f); lineTo(44f, 105f); lineTo(30f, 105f); close() }, fill(0xFFFFD66B.toInt()))
                }
                c.drawCircle(50f, 52f, 17f, fill(Color.WHITE))
                c.drawCircle(50f, 52f, 14f, fill(0xFF0A84FF.toInt()))
                c.drawPath(Path().apply { moveTo(50f, 42f); lineTo(58f, 60f); lineTo(50f, 56f); lineTo(42f, 60f); close() }, fill(Color.WHITE))
            }
            PackIcon.Weather -> {
                c.drawCircle(62f, 38f, 15f, fill(0xFFFFD60A.toInt()))
                c.drawCircle(36f, 60f, 13f, fill(ink)); c.drawCircle(54f, 54f, 17f, fill(ink))
                c.drawRoundRect(RectF(22f, 58f, 80f, 74f), 8f, 8f, fill(ink))
            }
            PackIcon.Notes -> {
                c.drawRect(-5f, -5f, 105f, 26f, fill(if (dark) icon.accent else 0xFFFFCC00.toInt()))
                val lines = if (dark) 0xFF48484A.toInt() else 0xFFD1D1D6.toInt()
                for (k in 0 until 4) c.drawLine(10f, 42f + k * 15f, 90f, 42f + k * 15f, stroke(2f, lines))
                for (k in 0 until 9) c.drawCircle(14f + k * 9f, 32f, 1f, fill(lines))
            }
            PackIcon.Reminders -> {
                val dots = intArrayOf(0xFF0A84FF.toInt(), 0xFFFF453A.toInt(), 0xFFFF9F0A.toInt())
                val lines = if (dark) 0xFF48484A.toInt() else 0xFFD1D1D6.toInt()
                for (k in 0 until 3) {
                    val y = 28f + k * 22f
                    c.drawCircle(24f, y, 6.5f, fill(dots[k])); c.drawCircle(24f, y, 6.5f, stroke(1.5f, Color.WHITE))
                    c.drawLine(38f, y, 86f, y, stroke(2f, lines))
                }
            }
            PackIcon.Calculator -> {
                for (k in 0 until 12) {
                    val col = k % 4; val row = k / 4
                    val color = when { col == 3 -> 0xFFFF9F0A.toInt(); row == 0 -> 0xFFA5A5AA.toInt(); else -> 0xFF5A5A5E.toInt() }
                    c.drawCircle(20f + col * 20f, 30f + row * 20f, 8f, fill(color))
                }
            }
            PackIcon.Contacts -> {
                c.drawCircle(50f, 38f, 15f, fill(ink))
                c.drawPath(Path().apply { moveTo(20f, 86f); cubicTo(22f, 62f, 36f, 56f, 50f, 56f); cubicTo(64f, 56f, 78f, 62f, 80f, 86f); close() }, fill(ink))
            }
            PackIcon.Store -> {
                val s = stroke(7f, ink)
                c.drawLine(52f, 22f, 30f, 66f, s); c.drawLine(48f, 22f, 70f, 66f, s)
                c.drawLine(22f, 58f, 78f, 58f, s)
                c.drawLine(28f, 72f, 24f, 79f, s); c.drawLine(72f, 72f, 76f, 79f, s)
            }
            PackIcon.Files -> {
                val blue = if (dark) icon.accent else 0xFF1C8CFF.toInt()
                c.drawRoundRect(RectF(14f, 30f, 86f, 76f), 7f, 7f, fill(blue))
                c.drawRoundRect(RectF(14f, 24f, 44f, 38f), 5f, 5f, fill(blue))
                c.drawRoundRect(RectF(14f, 36f, 86f, 76f), 7f, 7f, fill(if (dark) 0xFF64B5FF.toInt() else 0xFF56AEFF.toInt()))
            }
            PackIcon.Health -> c.drawPath(Path().apply {
                moveTo(50f, 80f); cubicTo(14f, 56f, 15f, 26f, 34f, 24f); cubicTo(43f, 23f, 49f, 30f, 50f, 34f)
                cubicTo(51f, 30f, 57f, 23f, 66f, 24f); cubicTo(85f, 26f, 86f, 56f, 50f, 80f); close()
            }, fill(0xFFFF375F.toInt()))
            PackIcon.Wallet -> {
                val cols = intArrayOf(0xFF34C759.toInt(), 0xFFFFCC00.toInt(), 0xFFFF9500.toInt(), 0xFFFF3B30.toInt())
                cols.forEachIndexed { k, col -> c.drawRoundRect(RectF(16f, 22f + k * 9f, 84f, 48f + k * 9f), 7f, 7f, fill(col)) }
                c.drawRoundRect(RectF(14f, 52f, 86f, 80f), 8f, 8f, fill(0xFFE5D9C7.toInt()))
            }
            PackIcon.VoiceMemos -> {
                val hs = floatArrayOf(10f, 22f, 34f, 18f, 40f, 26f, 14f, 30f, 20f, 8f)
                hs.forEachIndexed { k, hh ->
                    val x = 18f + k * 7.2f
                    c.drawLine(x, 50f - hh / 2, x, 50f + hh / 2, stroke(4f, if (k == 4) 0xFFFF453A.toInt() else Color.WHITE))
                }
            }
            PackIcon.Books -> {
                c.drawPath(Path().apply { moveTo(50f, 32f); cubicTo(38f, 24f, 24f, 24f, 15f, 28f); lineTo(15f, 76f); cubicTo(24f, 72f, 38f, 72f, 50f, 80f); close() }, fill(ink))
                c.drawPath(Path().apply { moveTo(50f, 32f); cubicTo(62f, 24f, 76f, 24f, 85f, 28f); lineTo(85f, 76f); cubicTo(76f, 72f, 62f, 72f, 50f, 80f); close() }, fill(ink))
            }
            PackIcon.Podcasts -> {
                c.drawCircle(50f, 42f, 8f, fill(ink))
                c.drawArc(RectF(30f, 22f, 70f, 62f), 140f, 260f, false, stroke(5f, ink))
                c.drawArc(RectF(18f, 10f, 82f, 74f), 140f, 260f, false, stroke(5f, ink))
                c.drawRoundRect(RectF(45f, 52f, 55f, 84f), 5f, 5f, fill(ink))
            }
            PackIcon.Video -> {
                c.drawRoundRect(RectF(16f, 26f, 84f, 66f), 6f, 6f, stroke(5f, ink))
                c.drawLine(38f, 76f, 62f, 76f, stroke(5f, ink))
                c.drawPath(Path().apply { moveTo(44f, 36f); lineTo(60f, 46f); lineTo(44f, 56f); close() }, fill(ink))
            }
            PackIcon.Translate -> {
                c.drawRoundRect(RectF(14f, 20f, 58f, 56f), 8f, 8f, fill(ink))
                c.drawRoundRect(RectF(42f, 44f, 86f, 80f), 8f, 8f, fill(if (dark) icon.accent else 0xFF5E5CE6.toInt()))
                c.drawText("A", 36f, 46f, text(26f, 0xFF141A4A.toInt(), bold = true))
                c.drawText("文", 64f, 71f, text(24f, Color.WHITE, bold = true))
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
