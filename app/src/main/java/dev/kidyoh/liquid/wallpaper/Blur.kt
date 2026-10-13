package dev.kidyoh.liquid.wallpaper

import android.graphics.Bitmap
import androidx.core.graphics.scale

/** Cheap, good-looking blur: shrink a lot, then three box-blur passes (≈ gaussian). */
object Blur {
    fun frosted(src: Bitmap, factor: Int = 10, radius: Int = 6): Bitmap {
        var w = src.width
        var h = src.height
        var cur = src
        // Halve repeatedly so the down-sampling itself averages pixels instead of skipping them.
        while (w / 2 >= src.width / factor && h / 2 >= 8) {
            w /= 2; h /= 2
            cur = cur.scale(w, h, filter = true)
        }
        val px = IntArray(w * h)
        cur.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxH(px, w, h, radius)
            boxV(px, w, h, radius)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxH(px: IntArray, w: Int, h: Int, r: Int) {
        val row = IntArray(w)
        val div = 2 * r + 1
        for (y in 0 until h) {
            val o = y * w
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val c = px[o + i.coerceIn(0, w - 1)]
                a += c ushr 24; rr += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
            }
            for (x in 0 until w) {
                row[x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = px[o + (x - r).coerceIn(0, w - 1)]
                val inn = px[o + (x + r + 1).coerceIn(0, w - 1)]
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 255) - ((out shr 16) and 255)
                g += ((inn shr 8) and 255) - ((out shr 8) and 255)
                b += (inn and 255) - (out and 255)
            }
            System.arraycopy(row, 0, px, o, w)
        }
    }

    private fun boxV(px: IntArray, w: Int, h: Int, r: Int) {
        val col = IntArray(h)
        val div = 2 * r + 1
        for (x in 0 until w) {
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val c = px[i.coerceIn(0, h - 1) * w + x]
                a += c ushr 24; rr += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
            }
            for (y in 0 until h) {
                col[y] = ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
                val out = px[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = px[(y + r + 1).coerceIn(0, h - 1) * w + x]
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 255) - ((out shr 16) and 255)
                g += ((inn shr 8) and 255) - ((out shr 8) and 255)
                b += (inn and 255) - (out and 255)
            }
            for (y in 0 until h) px[y * w + x] = col[y]
        }
    }
}
