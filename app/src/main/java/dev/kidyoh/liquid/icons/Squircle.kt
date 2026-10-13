package dev.kidyoh.liquid.icons

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Apple's icon shape is a "continuous corner" rounded square, which a superellipse with
 * exponent ~5 approximates closely. Points are on the unit square [-1, 1].
 */
object Squircle {
    private const val EXPONENT = 5.0
    private const val STEPS = 160

    private val unit: FloatArray by lazy {
        FloatArray(STEPS * 2).also { out ->
            for (i in 0 until STEPS) {
                val t = 2 * PI * i / STEPS
                val c = cos(t)
                val s = sin(t)
                out[i * 2] = (sign(c) * abs(c).pow(2 / EXPONENT)).toFloat()
                out[i * 2 + 1] = (sign(s) * abs(s).pow(2 / EXPONENT)).toFloat()
            }
        }
    }

    fun androidPath(size: Float, left: Float = 0f, top: Float = 0f): android.graphics.Path {
        val r = size / 2f
        return android.graphics.Path().apply {
            for (i in 0 until STEPS) {
                val x = left + r + unit[i * 2] * r
                val y = top + r + unit[i * 2 + 1] * r
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    fun composePath(width: Float, height: Float): Path {
        val rx = width / 2f
        val ry = height / 2f
        return Path().apply {
            for (i in 0 until STEPS) {
                val x = rx + unit[i * 2] * rx
                val y = ry + unit[i * 2 + 1] * ry
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}

object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Squircle.composePath(size.width, size.height))
}
