package dev.kidyoh.liquid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Line icons in the spirit of SF Symbols, drawn on a 24×24 grid. */
object Glyphs {
    private fun glyph(name: String, weight: Float = 2f, fill: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = if (fill) null else SolidColor(Color.Black),
            strokeLineWidth = weight,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
        moveTo(l + rad, t)
        lineTo(r - rad, t); arcTo(rad, rad, 0f, false, true, r, t + rad)
        lineTo(r, b - rad); arcTo(rad, rad, 0f, false, true, r - rad, b)
        lineTo(l + rad, b); arcTo(rad, rad, 0f, false, true, l, b - rad)
        lineTo(l, t + rad); arcTo(rad, rad, 0f, false, true, l + rad, t)
        close()
    }

    val Search = glyph("search", 2.2f) { circle(10.5f, 10.5f, 6.5f); moveTo(15.4f, 15.4f); lineTo(20f, 20f) }
    val Plus = glyph("plus", 2.2f) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    val Minus = glyph("minus", 2.6f) { moveTo(6.5f, 12f); lineTo(17.5f, 12f) }
    val Check = glyph("check", 2.4f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }
    val Close = glyph("close", 2.2f) { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }
    val ChevronRight = glyph("chevron.right", 2.2f) { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) }
    val ChevronLeft = glyph("chevron.left", 2.4f) { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) }
    val Info = glyph("info") { circle(12f, 12f, 9f); moveTo(12f, 11f); lineTo(12f, 16.5f); moveTo(12f, 7.6f); lineTo(12f, 7.7f) }
    val Trash = glyph("trash", 1.8f) {
        moveTo(4f, 6.5f); lineTo(20f, 6.5f)
        moveTo(9.5f, 6.5f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 6.5f)
        moveTo(6f, 6.5f); lineTo(7f, 20f); lineTo(17f, 20f); lineTo(18f, 6.5f)
        moveTo(10f, 10f); lineTo(10.3f, 16.5f); moveTo(14f, 10f); lineTo(13.7f, 16.5f)
    }
    val Pencil = glyph("pencil", 1.8f) {
        moveTo(4f, 20f); lineTo(5f, 15.5f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8.5f, 19f); close()
        moveTo(13.5f, 7f); lineTo(17f, 10.5f)
    }
    val Share = glyph("share", 1.8f) {
        moveTo(8.5f, 10f); lineTo(6f, 10f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 10f); lineTo(15.5f, 10f)
        moveTo(12f, 3.5f); lineTo(12f, 14f); moveTo(8.5f, 7f); lineTo(12f, 3.5f); lineTo(15.5f, 7f)
    }
    val Grid = glyph("square.grid", 1.8f) {
        roundRect(4f, 4f, 10.5f, 10.5f, 2f); roundRect(13.5f, 4f, 20f, 10.5f, 2f)
        roundRect(4f, 13.5f, 10.5f, 20f, 2f); roundRect(13.5f, 13.5f, 20f, 20f, 2f)
    }
    val Widgets = glyph("widgets", 1.8f) {
        roundRect(4f, 4f, 20f, 11f, 2f); roundRect(4f, 13.5f, 10.5f, 20f, 2f); roundRect(13.5f, 13.5f, 20f, 20f, 2f)
    }
    val Photo = glyph("photo", 1.8f) {
        roundRect(3f, 5f, 21f, 19f, 2.5f)
        moveTo(3.5f, 16.5f); lineTo(9f, 11f); lineTo(14f, 16f); lineTo(16f, 14f); lineTo(20.5f, 18.5f)
        circle(16f, 9.3f, 1.5f)
    }
    val Undo = glyph("undo", 2f) {
        moveTo(9f, 14f); lineTo(4.5f, 9.5f); lineTo(9f, 5f)
        moveTo(4.5f, 9.5f); lineTo(14f, 9.5f); arcTo(5f, 5f, 0f, false, true, 14f, 19.5f); lineTo(11f, 19.5f)
    }
    val Redo = glyph("redo", 2f) {
        moveTo(15f, 14f); lineTo(19.5f, 9.5f); lineTo(15f, 5f)
        moveTo(19.5f, 9.5f); lineTo(10f, 9.5f); arcTo(5f, 5f, 0f, false, false, 10f, 19.5f); lineTo(13f, 19.5f)
    }
    val House = glyph("house", 1.8f) {
        moveTo(3.5f, 11.5f); lineTo(12f, 4f); lineTo(20.5f, 11.5f)
        moveTo(6f, 9.5f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(18f, 9.5f)
        moveTo(10f, 20f); lineTo(10f, 14.5f); lineTo(14f, 14.5f); lineTo(14f, 20f)
    }
    val Moon = glyph("moon", fill = true) {
        moveTo(20f, 14.5f); arcTo(8.5f, 8.5f, 0f, true, true, 9.5f, 4f); arcTo(7f, 7f, 0f, false, false, 20f, 14.5f); close()
    }
    val Bell = glyph("bell", 1.8f) {
        moveTo(6f, 16f); lineTo(6f, 11f); arcTo(6f, 6f, 0f, false, true, 18f, 11f); lineTo(18f, 16f); lineTo(19.5f, 18f); lineTo(4.5f, 18f); close()
        moveTo(10f, 20.5f); lineTo(14f, 20.5f)
    }
    val Bolt = glyph("bolt", fill = true) {
        moveTo(13.5f, 2.5f); lineTo(5.5f, 13.5f); lineTo(11.5f, 13.5f); lineTo(10.5f, 21.5f); lineTo(18.5f, 10.5f); lineTo(12.5f, 10.5f); close()
    }
    val Glass = glyph("glass", 1.8f) {
        circle(12f, 12f, 8.5f); moveTo(12f, 3.5f); lineTo(12f, 20.5f)
        moveTo(12f, 7f); lineTo(16f, 7f); moveTo(12f, 11f); lineTo(19f, 11f); moveTo(12f, 15f); lineTo(18f, 15f)
    }
    val Wave = glyph("waveform", 2f) {
        moveTo(4f, 10f); lineTo(4f, 14f); moveTo(8f, 7f); lineTo(8f, 17f); moveTo(12f, 4f); lineTo(12f, 20f)
        moveTo(16f, 8f); lineTo(16f, 16f); moveTo(20f, 10.5f); lineTo(20f, 13.5f)
    }
    val ArrowDown = glyph("arrow.down", 2f) { moveTo(12f, 4f); lineTo(12f, 19f); moveTo(6.5f, 13.5f); lineTo(12f, 19f); lineTo(17.5f, 13.5f) }
    val Text = glyph("textformat", 2f) { moveTo(5.5f, 6f); lineTo(18.5f, 6f); moveTo(12f, 6f); lineTo(12f, 19f) }
    val Lock = glyph("lock", 1.8f) {
        roundRect(5.5f, 10.5f, 18.5f, 20f, 2.5f)
        moveTo(8.5f, 10.5f); lineTo(8.5f, 7.5f); arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 7.5f); lineTo(15.5f, 10.5f)
    }
    val Folder = glyph("folder", 1.8f) {
        moveTo(3.5f, 7f); lineTo(3.5f, 18f); lineTo(20.5f, 18f); lineTo(20.5f, 8.5f); lineTo(11.5f, 8.5f); lineTo(9.5f, 6f); lineTo(3.5f, 6f); close()
    }
    val Gear = glyph("gear", 1.8f) {
        circle(12f, 12f, 3.2f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            moveTo(12f + 6f * kotlin.math.cos(a).toFloat(), 12f + 6f * kotlin.math.sin(a).toFloat())
            lineTo(12f + 8.5f * kotlin.math.cos(a).toFloat(), 12f + 8.5f * kotlin.math.sin(a).toFloat())
        }
    }
    val Sliders = glyph("sliders", 1.8f) {
        moveTo(4f, 7f); lineTo(20f, 7f); moveTo(4f, 17f); lineTo(20f, 17f)
        circle(9f, 7f, 2.2f); circle(15f, 17f, 2.2f)
    }
    val Paint = glyph("paintpalette", 1.8f) {
        moveTo(12f, 3.5f); arcTo(8.5f, 8.5f, 0f, false, false, 12f, 20.5f); arcTo(2f, 2f, 0f, false, false, 13.5f, 17f)
        arcTo(2f, 2f, 0f, false, true, 15.5f, 14f); lineTo(17f, 14f); arcTo(3.5f, 3.5f, 0f, false, false, 20.5f, 10.5f)
        arcTo(8.5f, 7f, 0f, false, false, 12f, 3.5f); close()
        circle(8f, 11f, 0.6f); circle(10.5f, 7.5f, 0.6f); circle(15f, 7.5f, 0.6f)
    }
    val Sparkle = glyph("sparkles", fill = true) {
        moveTo(12f, 2.5f); lineTo(13.8f, 9.2f); lineTo(20.5f, 11f); lineTo(13.8f, 12.8f); lineTo(12f, 19.5f)
        lineTo(10.2f, 12.8f); lineTo(3.5f, 11f); lineTo(10.2f, 9.2f); close()
    }
    val Battery = glyph("battery", 1.8f) {
        roundRect(3f, 7.5f, 19f, 16.5f, 2.5f); moveTo(21f, 10.5f); lineTo(21f, 13.5f)
    }

    val Airplane = glyph("airplane", fill = true) {
        moveTo(21f, 15.8f); lineTo(13.6f, 11.6f); lineTo(13.6f, 5f); arcTo(1.6f, 1.6f, 0f, false, false, 10.4f, 5f)
        lineTo(10.4f, 11.6f); lineTo(3f, 15.8f); lineTo(3f, 17.6f); lineTo(10.4f, 15.3f); lineTo(10.4f, 19f)
        lineTo(8.4f, 20.5f); lineTo(8.4f, 22f); lineTo(12f, 21f); lineTo(15.6f, 22f); lineTo(15.6f, 20.5f)
        lineTo(13.6f, 19f); lineTo(13.6f, 15.3f); lineTo(21f, 17.6f); close()
    }
    val Wifi = glyph("wifi", 2.3f) {
        moveTo(8.9f, 16.2f); arcTo(4.4f, 4.4f, 0f, false, true, 15.1f, 16.2f)
        moveTo(5.8f, 13.1f); arcTo(8.8f, 8.8f, 0f, false, true, 18.2f, 13.1f)
        moveTo(2.7f, 10f); arcTo(13.2f, 13.2f, 0f, false, true, 21.3f, 10f)
        circle(12f, 19.4f, 0.6f)
    }
    val Bluetooth = glyph("bluetooth", 1.9f) {
        moveTo(7f, 7.5f); lineTo(17f, 16.5f); lineTo(12f, 21f); lineTo(12f, 3f); lineTo(17f, 7.5f); lineTo(7f, 16.5f)
    }
    val Hotspot = glyph("hotspot", 2f) {
        circle(12f, 12f, 1.4f)
        moveTo(8.8f, 15.2f); arcTo(4.5f, 4.5f, 0f, true, true, 15.2f, 15.2f)
        moveTo(6f, 18f); arcTo(8.5f, 8.5f, 0f, true, true, 18f, 18f)
    }
    val RotationLock = glyph("lock.rotation", 1.9f) {
        moveTo(19.2f, 13.5f); arcTo(7.4f, 7.4f, 0f, true, true, 16.8f, 6.3f)
        moveTo(17.4f, 3.2f); lineTo(16.8f, 6.3f); lineTo(19.9f, 6.9f)
        roundRect(9.2f, 11.2f, 14.8f, 15.8f, 1f)
        moveTo(10.4f, 11.2f); lineTo(10.4f, 9.8f); arcTo(1.6f, 1.6f, 0f, false, true, 13.6f, 9.8f); lineTo(13.6f, 11.2f)
    }
    val Mirror = glyph("rectangle.on.rectangle", 1.9f) {
        roundRect(3.5f, 4.5f, 16.5f, 13.5f, 2f); roundRect(7.5f, 9.5f, 20.5f, 19.5f, 2f)
    }
    val Sun = glyph("sun", 2f) {
        circle(12f, 12f, 3.8f)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            moveTo(12f + 6.6f * kotlin.math.cos(a).toFloat(), 12f + 6.6f * kotlin.math.sin(a).toFloat())
            lineTo(12f + 8.8f * kotlin.math.cos(a).toFloat(), 12f + 8.8f * kotlin.math.sin(a).toFloat())
        }
    }
    val Speaker = glyph("speaker", 2f) {
        moveTo(3.5f, 9.5f); lineTo(7f, 9.5f); lineTo(11.5f, 5.5f); lineTo(11.5f, 18.5f); lineTo(7f, 14.5f); lineTo(3.5f, 14.5f); close()
        moveTo(15f, 9f); arcTo(4f, 4f, 0f, false, true, 15f, 15f)
        moveTo(17.8f, 6.5f); arcTo(7.5f, 7.5f, 0f, false, true, 17.8f, 17.5f)
    }
    val Flashlight = glyph("flashlight", 1.9f) {
        moveTo(8f, 3f); lineTo(16f, 3f); lineTo(16f, 7f); lineTo(14f, 10f); lineTo(14f, 21f); lineTo(10f, 21f); lineTo(10f, 10f); lineTo(8f, 7f); close()
        moveTo(8f, 7f); lineTo(16f, 7f); moveTo(12f, 13.5f); lineTo(12f, 15f)
    }
    val Camera = glyph("camera", 1.9f) {
        roundRect(3f, 7f, 21f, 19.5f, 2.5f)
        moveTo(8.5f, 7f); lineTo(10f, 4.5f); lineTo(14f, 4.5f); lineTo(15.5f, 7f)
        circle(12f, 13.2f, 3.5f)
    }
    val Calculator = glyph("calculator", 1.9f) {
        roundRect(5f, 3f, 19f, 21f, 2.5f)
        moveTo(8.5f, 7f); lineTo(15.5f, 7f)
        for (r in 0 until 3) for (c in 0 until 3) circle(8.8f + c * 3.2f, 11.5f + r * 3.2f, 0.45f)
    }
    val Timer = glyph("timer", 1.9f) {
        circle(12f, 13.5f, 7.5f); moveTo(12f, 13.5f); lineTo(12f, 9.5f)
        moveTo(10f, 3.5f); lineTo(14f, 3.5f); moveTo(12f, 3.5f); lineTo(12f, 6f)
    }
    val Alarm = glyph("alarm", 1.9f) {
        circle(12f, 13f, 7f); moveTo(12f, 13f); lineTo(12f, 9.3f); moveTo(12f, 13f); lineTo(14.6f, 14.6f)
        moveTo(4.5f, 6f); lineTo(7.2f, 3.6f); moveTo(19.5f, 6f); lineTo(16.8f, 3.6f)
    }
    val Play = glyph("play", fill = true) { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() }
    val Pause = glyph("pause", fill = true) {
        moveTo(6.5f, 4.5f); lineTo(10f, 4.5f); lineTo(10f, 19.5f); lineTo(6.5f, 19.5f); close()
        moveTo(14f, 4.5f); lineTo(17.5f, 4.5f); lineTo(17.5f, 19.5f); lineTo(14f, 19.5f); close()
    }
    val Next = glyph("forward", fill = true) {
        moveTo(2.5f, 6f); lineTo(11.5f, 12f); lineTo(2.5f, 18f); close()
        moveTo(12f, 6f); lineTo(21f, 12f); lineTo(12f, 18f); close()
    }
    val Previous = glyph("backward", fill = true) {
        moveTo(21.5f, 6f); lineTo(12.5f, 12f); lineTo(21.5f, 18f); close()
        moveTo(12f, 6f); lineTo(3f, 12f); lineTo(12f, 18f); close()
    }
    val AirPlay = glyph("airplay", 1.9f) {
        moveTo(7.3f, 16.3f); arcTo(6.6f, 6.6f, 0f, true, true, 16.7f, 16.3f)
        moveTo(8.5f, 21f); lineTo(12f, 16.5f); lineTo(15.5f, 21f); close()
    }
    val Focus = glyph("focus", 1.9f) {
        circle(12f, 12f, 8.5f)
        moveTo(15.5f, 14.8f); arcTo(5.2f, 5.2f, 0f, true, true, 10.2f, 7.1f); arcTo(4.2f, 4.2f, 0f, false, false, 15.5f, 14.8f); close()
    }
}
