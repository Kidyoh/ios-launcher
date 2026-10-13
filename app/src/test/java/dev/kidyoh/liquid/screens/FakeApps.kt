package dev.kidyoh.liquid.screens

import android.content.ComponentName
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import dev.kidyoh.liquid.data.AppCategory
import dev.kidyoh.liquid.data.AppEntry
import dev.kidyoh.liquid.data.AppKey

/** Stand-ins for installed apps, with simple but recognisable adaptive icons. */
object FakeApps {
    enum class G { Handset, Compass, Bubble, Note, Camera, Flower, Envelope, Pin, Cloud, Lines, Date, Clock, Gear, Play, Tube, Ring, Insta, Waves, N, Plane, Keys, Folder, Heart, Person, Stripes, Bag, Book, Mic }

    private data class Spec(val label: String, val pkg: String, val top: Int, val bottom: Int, val glyph: G, val fg: Int, val cat: AppCategory)

    private val specs = listOf(
        Spec("Phone", "com.android.dialer", 0xFF5BF675.toInt(), 0xFF0CBD2A.toInt(), G.Handset, Color.WHITE, AppCategory.Utilities),
        Spec("Browser", "com.android.chrome", 0xFF2DCBFF.toInt(), 0xFF1A73FF.toInt(), G.Compass, Color.WHITE, AppCategory.Information),
        Spec("Messages", "com.google.android.apps.messaging", 0xFF6EF57D.toInt(), 0xFF12C73A.toInt(), G.Bubble, Color.WHITE, AppCategory.Social),
        Spec("Music", "com.apple.music", 0xFFFF6B86.toInt(), 0xFFFA2D48.toInt(), G.Note, Color.WHITE, AppCategory.Entertainment),
        Spec("Camera", "com.android.camera", 0xFFE5E5EA.toInt(), 0xFFAEAEB2.toInt(), G.Camera, 0xFF1C1C1E.toInt(), AppCategory.Creativity),
        Spec("Photos", "com.google.android.apps.photos", Color.WHITE, 0xFFF2F2F7.toInt(), G.Flower, 0, AppCategory.Creativity),
        Spec("Mail", "com.google.android.gm", 0xFF3FB4FF.toInt(), 0xFF0A6CFF.toInt(), G.Envelope, Color.WHITE, AppCategory.Productivity),
        Spec("Maps", "com.google.android.apps.maps", 0xFF8EE6A0.toInt(), 0xFF33C27A.toInt(), G.Pin, 0xFFFF3B30.toInt(), AppCategory.Travel),
        Spec("Weather", "com.weather", 0xFF4AA8FF.toInt(), 0xFF1C5BD9.toInt(), G.Cloud, Color.WHITE, AppCategory.Information),
        Spec("Notes", "com.google.android.keep", 0xFFFFF6C2.toInt(), 0xFFFFE066.toInt(), G.Lines, 0xFFB38600.toInt(), AppCategory.Productivity),
        Spec("Calendar", "com.google.android.calendar", Color.WHITE, Color.WHITE, G.Date, 0xFFFF3B30.toInt(), AppCategory.Productivity),
        Spec("Clock", "com.google.android.deskclock", 0xFF1C1C1E.toInt(), Color.BLACK, G.Clock, Color.WHITE, AppCategory.Utilities),
        Spec("Settings", "com.android.settings", 0xFFC7C7CC.toInt(), 0xFF8E8E93.toInt(), G.Gear, 0xFF3A3A3C.toInt(), AppCategory.Utilities),
        Spec("Play Store", "com.android.vending", Color.WHITE, 0xFFF2F2F7.toInt(), G.Play, 0xFF00C26E.toInt(), AppCategory.Utilities),
        Spec("YouTube", "com.google.android.youtube", Color.WHITE, Color.WHITE, G.Tube, 0xFFFF0033.toInt(), AppCategory.Entertainment),
        Spec("WhatsApp", "com.whatsapp", 0xFF5EF08A.toInt(), 0xFF1EBE57.toInt(), G.Ring, Color.WHITE, AppCategory.Social),
        Spec("Instagram", "com.instagram.android", 0xFFFEC053.toInt(), 0xFF8A3AB9.toInt(), G.Insta, Color.WHITE, AppCategory.Social),
        Spec("Spotify", "com.spotify.music", 0xFF191414.toInt(), 0xFF121212.toInt(), G.Waves, 0xFF1ED760.toInt(), AppCategory.Entertainment),
        Spec("Netflix", "com.netflix.mediaclient", 0xFF141414.toInt(), Color.BLACK, G.N, 0xFFE50914.toInt(), AppCategory.Entertainment),
        Spec("Telegram", "org.telegram.messenger", 0xFF37BBFE.toInt(), 0xFF007DBB.toInt(), G.Plane, Color.WHITE, AppCategory.Social),
        Spec("Calculator", "com.android.calculator2", 0xFF3A3A3C.toInt(), 0xFF1C1C1E.toInt(), G.Keys, 0xFFFF9F0A.toInt(), AppCategory.Utilities),
        Spec("Files", "com.android.documentsui", 0xFF5AC8FA.toInt(), 0xFF007AFF.toInt(), G.Folder, Color.WHITE, AppCategory.Utilities),
        Spec("Health", "com.google.android.apps.fitness", Color.WHITE, 0xFFF2F2F7.toInt(), G.Heart, 0xFFFF2D55.toInt(), AppCategory.Other),
        Spec("Contacts", "com.android.contacts", 0xFFD1D1D6.toInt(), 0xFF9A9AA0.toInt(), G.Person, Color.WHITE, AppCategory.Utilities),
        Spec("Wallet", "com.google.android.apps.walletnfcrel", 0xFF2C2C2E.toInt(), Color.BLACK, G.Stripes, 0, AppCategory.Productivity),
        Spec("Shop", "com.amazon.mShop", 0xFFFFB84D.toInt(), 0xFFFF8A00.toInt(), G.Bag, Color.WHITE, AppCategory.Other),
        Spec("Books", "com.google.android.apps.books", 0xFFFFA64D.toInt(), 0xFFFF7A00.toInt(), G.Book, Color.WHITE, AppCategory.Information),
        Spec("Podcasts", "com.google.android.apps.podcasts", 0xFFD58CFF.toInt(), 0xFF8E3CDB.toInt(), G.Mic, Color.WHITE, AppCategory.Entertainment),
        Spec("Gmail Go", "com.google.android.gm.lite", 0xFFFF7B6B.toInt(), 0xFFE53935.toInt(), G.Envelope, Color.WHITE, AppCategory.Productivity),
        Spec("Drive", "com.google.android.apps.docs", 0xFFFFE27A.toInt(), 0xFF34A853.toInt(), G.Play, Color.WHITE, AppCategory.Productivity),
        Spec("Meet", "com.google.android.apps.meetings", 0xFF00AC47.toInt(), 0xFF00832D.toInt(), G.Camera, Color.WHITE, AppCategory.Productivity),
        Spec("Translate", "com.google.android.apps.translate", 0xFF4285F4.toInt(), 0xFF1A5BD6.toInt(), G.Bubble, Color.WHITE, AppCategory.Utilities),
    )

    fun create(): List<AppEntry> {
        val user = Process.myUserHandle()
        return specs.mapIndexed { i, s ->
            AppEntry(
                key = AppKey(s.pkg, "${s.pkg}.Main", 0),
                label = s.label,
                component = ComponentName(s.pkg, "${s.pkg}.Main"),
                user = user,
                category = s.cat,
                installTime = 1_700_000_000_000L + i * 86_400_000L,
                isWork = false,
                flags = 0,
                iconLoader = { AdaptiveIconDrawable(Bg(s.top, s.bottom), Fg(s.glyph, s.fg)) },
            )
        }.sortedBy { it.label }
    }

    private class Bg(val top: Int, val bottom: Int) : Drawable() {
        override fun draw(c: Canvas) {
            val b = bounds
            c.drawRect(RectF(b), Paint().apply {
                shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            })
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
    }

    /** Draws the glyph in the adaptive icon's 66/108 safe zone. */
    private class Fg(val g: G, val color: Int) : Drawable() {
        override fun draw(c: Canvas) {
            val b = bounds
            val s = b.width() * 66f / 108f
            val ox = b.left + (b.width() - s) / 2f
            val oy = b.top + (b.height() - s) / 2f
            c.save()
            c.translate(ox, oy)
            c.scale(s / 100f, s / 100f)
            glyph(c)
            c.restore()
        }

        private fun fill(col: Int = color) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = col }
        private fun stroke(w: Float, col: Int = color) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w; this.color = col; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }

        private fun glyph(c: Canvas) {
            when (g) {
                G.Handset -> {
                    val p = Path().apply {
                        moveTo(28f, 18f); cubicTo(18f, 22f, 16f, 40f, 34f, 62f); cubicTo(52f, 82f, 72f, 86f, 80f, 74f)
                        lineTo(70f, 62f); lineTo(60f, 66f); cubicTo(50f, 60f, 42f, 52f, 36f, 42f); lineTo(40f, 32f); close()
                    }
                    c.drawPath(p, fill())
                }
                G.Compass -> {
                    c.drawCircle(50f, 50f, 36f, stroke(4f))
                    val p = Path().apply { moveTo(68f, 32f); lineTo(54f, 54f); lineTo(32f, 68f); lineTo(46f, 46f); close() }
                    c.drawPath(p, fill())
                    c.drawPath(Path().apply { moveTo(68f, 32f); lineTo(54f, 54f); lineTo(46f, 46f); close() }, fill(0xFFFF3B30.toInt()))
                }
                G.Bubble -> {
                    c.drawOval(RectF(14f, 20f, 86f, 76f), fill())
                    c.drawPath(Path().apply { moveTo(26f, 64f); lineTo(18f, 84f); lineTo(42f, 72f); close() }, fill())
                }
                G.Note -> {
                    c.drawCircle(36f, 70f, 11f, fill()); c.drawCircle(70f, 62f, 11f, fill())
                    c.drawLine(46f, 70f, 46f, 26f, stroke(6f)); c.drawLine(80f, 62f, 80f, 18f, stroke(6f))
                    c.drawLine(46f, 26f, 80f, 18f, stroke(10f))
                }
                G.Camera -> {
                    c.drawRoundRect(RectF(14f, 30f, 86f, 78f), 10f, 10f, fill())
                    c.drawRoundRect(RectF(36f, 22f, 64f, 34f), 4f, 4f, fill())
                    c.drawCircle(50f, 54f, 15f, fill(0xFF8E8E93.toInt())); c.drawCircle(50f, 54f, 10f, fill(0xFF1C1C1E.toInt()))
                }
                G.Flower -> {
                    val cols = intArrayOf(0xFFFFCC00.toInt(), 0xFFFF9500.toInt(), 0xFFFF3B30.toInt(), 0xFFAF52DE.toInt(), 0xFF007AFF.toInt(), 0xFF34C759.toInt(), 0xFF5AC8FA.toInt(), 0xFFFF2D55.toInt())
                    for (i in 0 until 8) {
                        c.save(); c.rotate(i * 45f, 50f, 50f)
                        c.drawOval(RectF(42f, 10f, 58f, 50f), fill(cols[i]).apply { alpha = 220 })
                        c.restore()
                    }
                }
                G.Envelope -> {
                    c.drawRoundRect(RectF(14f, 26f, 86f, 74f), 6f, 6f, fill())
                    c.drawPath(Path().apply { moveTo(16f, 30f); lineTo(50f, 56f); lineTo(84f, 30f) }, stroke(5f, 0xFF0A6CFF.toInt()))
                }
                G.Pin -> {
                    c.drawCircle(50f, 40f, 22f, fill())
                    c.drawPath(Path().apply { moveTo(32f, 50f); lineTo(50f, 86f); lineTo(68f, 50f); close() }, fill())
                    c.drawCircle(50f, 40f, 9f, fill(Color.WHITE))
                }
                G.Cloud -> {
                    c.drawCircle(64f, 34f, 14f, fill(0xFFFFD60A.toInt()))
                    c.drawCircle(38f, 58f, 16f, fill()); c.drawCircle(58f, 52f, 20f, fill()); c.drawRoundRect(RectF(22f, 58f, 82f, 74f), 8f, 8f, fill())
                }
                G.Lines -> {
                    c.drawRect(0f, 0f, 100f, 24f, fill(0xFFFFCC00.toInt()))
                    for (i in 0 until 4) c.drawLine(18f, 40f + i * 14f, 82f, 40f + i * 14f, stroke(3f, 0xFFD1D1D6.toInt()))
                }
                G.Date -> {
                    c.drawText("MON", 50f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = 16f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD })
                    c.drawText("13", 50f, 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF1C1C1E.toInt(); textSize = 52f; textAlign = Paint.Align.CENTER })
                }
                G.Clock -> {
                    c.drawCircle(50f, 50f, 40f, fill(Color.WHITE))
                    c.drawLine(50f, 50f, 50f, 24f, stroke(5f, Color.BLACK)); c.drawLine(50f, 50f, 70f, 58f, stroke(5f, Color.BLACK))
                    c.drawLine(50f, 58f, 50f, 16f, stroke(2f, 0xFFFF9500.toInt()))
                }
                G.Gear -> {
                    for (i in 0 until 12) { c.save(); c.rotate(i * 30f, 50f, 50f); c.drawRoundRect(RectF(45f, 8f, 55f, 24f), 2f, 2f, fill()); c.restore() }
                    c.drawCircle(50f, 50f, 30f, fill()); c.drawCircle(50f, 50f, 12f, fill(0xFFC7C7CC.toInt()))
                }
                G.Play -> c.drawPath(Path().apply { moveTo(28f, 16f); lineTo(82f, 50f); lineTo(28f, 84f); close() }, fill())
                G.Tube -> {
                    c.drawRoundRect(RectF(8f, 24f, 92f, 76f), 18f, 18f, fill())
                    c.drawPath(Path().apply { moveTo(42f, 36f); lineTo(64f, 50f); lineTo(42f, 64f); close() }, fill(Color.WHITE))
                }
                G.Ring -> {
                    c.drawCircle(50f, 48f, 32f, stroke(7f))
                    c.drawPath(Path().apply { moveTo(26f, 70f); lineTo(18f, 86f); lineTo(36f, 78f) }, stroke(7f))
                }
                G.Insta -> {
                    c.drawRoundRect(RectF(16f, 16f, 84f, 84f), 20f, 20f, stroke(7f)); c.drawCircle(50f, 50f, 16f, stroke(7f)); c.drawCircle(70f, 30f, 4f, fill())
                }
                G.Waves -> {
                    c.drawCircle(50f, 50f, 42f, fill())
                    for (i in 0 until 3) c.drawArc(RectF(24f + i * 4, 30f + i * 14, 76f - i * 4, 60f + i * 14), 200f, 140f, false, stroke(7f - i, Color.BLACK))
                }
                G.N -> c.drawText("N", 50f, 86f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = 96f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD })
                G.Plane -> c.drawPath(Path().apply { moveTo(14f, 48f); lineTo(86f, 20f); lineTo(70f, 82f); lineTo(50f, 64f); lineTo(40f, 78f); lineTo(38f, 58f); close() }, fill())
                G.Keys -> for (i in 0 until 9) {
                    c.drawCircle(24f + (i % 3) * 26f, 24f + (i / 3) * 26f, 10f, fill(if (i % 3 == 2) color else 0xFFA5A5AA.toInt()))
                }
                G.Folder -> {
                    c.drawRoundRect(RectF(12f, 28f, 88f, 80f), 8f, 8f, fill())
                    c.drawRoundRect(RectF(12f, 20f, 46f, 36f), 6f, 6f, fill())
                }
                G.Heart -> c.drawPath(Path().apply {
                    moveTo(50f, 84f); cubicTo(10f, 56f, 12f, 22f, 34f, 20f); cubicTo(44f, 19f, 50f, 28f, 50f, 32f)
                    cubicTo(50f, 28f, 56f, 19f, 66f, 20f); cubicTo(88f, 22f, 90f, 56f, 50f, 84f); close()
                }, fill())
                G.Person -> { c.drawCircle(50f, 36f, 16f, fill()); c.drawOval(RectF(22f, 58f, 78f, 98f), fill()) }
                G.Stripes -> {
                    val cols = intArrayOf(0xFF34C759.toInt(), 0xFFFFCC00.toInt(), 0xFFFF9500.toInt(), 0xFFFF3B30.toInt())
                    cols.forEachIndexed { i, col -> c.drawRoundRect(RectF(14f, 22f + i * 12f, 86f, 44f + i * 12f), 6f, 6f, fill(col)) }
                    c.drawRoundRect(RectF(14f, 62f, 86f, 84f), 6f, 6f, fill(0xFF3A3A3C.toInt()))
                }
                G.Bag -> {
                    c.drawRoundRect(RectF(18f, 34f, 82f, 86f), 8f, 8f, fill())
                    c.drawArc(RectF(34f, 16f, 66f, 50f), 180f, 180f, false, stroke(6f))
                }
                G.Book -> {
                    c.drawPath(Path().apply { moveTo(50f, 30f); cubicTo(38f, 22f, 22f, 22f, 14f, 26f); lineTo(14f, 78f); cubicTo(22f, 74f, 38f, 74f, 50f, 82f); close() }, fill())
                    c.drawPath(Path().apply { moveTo(50f, 30f); cubicTo(62f, 22f, 78f, 22f, 86f, 26f); lineTo(86f, 78f); cubicTo(78f, 74f, 62f, 74f, 50f, 82f); close() }, fill())
                }
                G.Mic -> {
                    c.drawRoundRect(RectF(38f, 14f, 62f, 58f), 12f, 12f, fill())
                    c.drawArc(RectF(26f, 30f, 74f, 72f), 0f, 180f, false, stroke(5f)); c.drawLine(50f, 72f, 50f, 86f, stroke(5f))
                }
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
