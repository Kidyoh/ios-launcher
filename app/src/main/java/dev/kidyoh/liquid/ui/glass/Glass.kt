package dev.kidyoh.liquid.ui.glass

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.kidyoh.liquid.wallpaper.Wallpaper
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything a glass surface needs to know about what is behind it. */
@Immutable
data class GlassEnv(
    val wallpaper: Wallpaper,
    val dark: Boolean,
    /** 0 = clear, 1 = tinted (the iOS 27 Liquid Glass slider). */
    val level: Float,
)

val LocalGlass = staticCompositionLocalOf {
    GlassEnv(Wallpaper("", null, null, passthrough = true), dark = false, level = 0.4f)
}

/**
 * A live recording of everything drawn underneath the floating glass (wallpaper, pages, icons…).
 * Glass panels redraw it through a GPU blur + lens shader, so they show what is *really* behind
 * them — icons sliding under the dock, a page blurred behind a folder — not just the wallpaper.
 */
@Stable
class Backdrop(val layer: GraphicsLayer) {
    var origin: Offset = Offset.Zero
        internal set
}

/** Provided only to glass that sits *outside* the recorded content (a layer can't sample itself). */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(): Backdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** Records this content into [backdrop] while drawing it as usual. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * A Liquid Glass panel.
 *
 * With a [LocalBackdrop] on Android 12+ it blurs the real content behind it; on Android 13+ that
 * blur is bent by a lens at the rim with a hint of chromatic split, then tinted and lit with a
 * specular edge. Elsewhere it falls back to a pre-blurred copy of the wallpaper.
 *
 * @param corner corner radius; [Dp.Infinity] makes a capsule.
 * @param opacity extra frost and tint for surfaces that hold text, e.g. menus.
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    opacity: Float = 0f,
    refraction: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val env = LocalGlass.current
    val backdrop = LocalBackdrop.current
    val shape = if (corner == Dp.Infinity) RoundedCornerShape(50) else RoundedCornerShape(corner)
    Box(
        modifier
            .then(GlassElement(env, backdrop, corner, opacity, refraction))
            .clip(shape),
        content = content,
    )
}

private data class GlassElement(
    val env: GlassEnv,
    val backdrop: Backdrop?,
    val corner: Dp,
    val opacity: Float,
    val refraction: Boolean,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(env, backdrop, corner, opacity, refraction)
    override fun update(node: GlassNode) {
        node.env = env; node.backdrop = backdrop; node.corner = corner; node.opacity = opacity; node.refraction = refraction
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "liquidGlass"
    }
}

/** Resolved look of one glass surface. */
private class GlassLook(
    val tint: Color,
    val tintAlpha: Float,
    val blur: Float,
    val highlight: Float,
)

private class GlassNode(
    var env: GlassEnv,
    var backdrop: Backdrop?,
    var corner: Dp,
    var opacity: Float,
    var refraction: Boolean,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var coords: LayoutCoordinates? = null
    private var glassLayer: GraphicsLayer? = null
    private var wallpaperLens: Any? = null // WallpaperLens, untyped so older devices never load it
    private var effectLens: Any? = null    // BackdropLens
    private var effectKey: Any? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        coords = coordinates
        invalidateDraw() // keep the glass lined up with what's behind it while things move
    }

    override fun onDetach() {
        glassLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        glassLayer = null
        effectKey = null
    }

    private fun look(scope: DrawScope): GlassLook = with(scope) {
        val dark = env.dark
        val level = env.level
        GlassLook(
            tint = if (dark) Color(0xFF141418) else Color(0xFFFAFAFC),
            tintAlpha = (lerp(if (dark) 0.08f else 0.06f, if (dark) 0.50f else 0.55f, level) + opacity).coerceIn(0f, 0.92f),
            blur = (lerp(2.5.dp.toPx(), 16.dp.toPx(), level) + opacity * 22.dp.toPx()),
            highlight = if (dark) 0.6f else 1f,
        )
    }

    override fun ContentDrawScope.draw() {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return drawContent()
        val origin = coords?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
        val radius = if (corner == Dp.Infinity) min(w, h) / 2f else min(corner.toPx(), min(w, h) / 2f)
        val look = look(this)
        val b = backdrop
        if (b != null && Build.VERSION.SDK_INT >= 31) {
            drawBackdropGlass(b, origin, radius, look)
        } else {
            drawWallpaperGlass(origin, radius, look)
        }
        drawContent()
    }

    /** Real glass: the live backdrop, blurred on the GPU, lensed and lit. */
    @RequiresApi(31)
    private fun ContentDrawScope.drawBackdropGlass(b: Backdrop, origin: Offset, radius: Float, look: GlassLook) {
        val w = size.width
        val h = size.height
        val layer = glassLayer ?: requireGraphicsContext().createGraphicsLayer().also { glassLayer = it }
        // Extra pixels around the panel so the blur samples real content at the edges.
        val m = ceil(look.blur * 2f + 2f).toInt()
        val lw = ceil(w).toInt() + m * 2
        val lh = ceil(h).toInt() + m * 2
        val bezel = min(22.dp.toPx(), radius * 1.15f).coerceAtLeast(min(8.dp.toPx(), min(w, h) / 2f))
        val bend = min(14.dp.toPx(), bezel * 0.7f)
        val lensOn = refraction && Build.VERSION.SDK_INT >= 33

        val key = listOf(lw, lh, radius, bezel, bend, look.blur, look.tintAlpha, look.tint, look.highlight, lensOn)
        if (key != effectKey) {
            effectKey = key
            layer.renderEffect = if (lensOn) {
                val lens = (effectLens as? BackdropLens) ?: BackdropLens().also { effectLens = it }
                lens.effect(
                    blur = look.blur, w = w, h = h, margin = m.toFloat(), radius = radius, bezel = bezel, bend = bend,
                    rim = 1.5.dp.toPx(), tint = look.tint, tintAlpha = look.tintAlpha, highlight = look.highlight,
                )
            } else if (look.blur > 0.5f) {
                BlurEffect(look.blur, look.blur, TileMode.Clamp)
            } else null
        }
        val rel = origin - b.origin
        layer.record(IntSize(lw, lh)) {
            translate(-rel.x + m, -rel.y + m) { drawLayer(b.layer) }
        }
        if (lensOn) {
            // The shader already cut the shape, tinted it and drew the rim.
            translate(-m.toFloat(), -m.toFloat()) { drawLayer(layer) }
        } else {
            val path = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius))) }
            clipPath(path) {
                translate(-m.toFloat(), -m.toFloat()) { drawLayer(layer) }
                drawRect(look.tint.copy(alpha = look.tintAlpha))
            }
            drawRim(radius, look.highlight)
        }
    }

    /** Fallback: a pre-blurred wallpaper slice lined up with the screen. */
    private fun ContentDrawScope.drawWallpaperGlass(origin: Offset, radius: Float, look: GlassLook) {
        val w = size.width
        val h = size.height
        val blurred = env.wallpaper.blurred
        val sharp = env.wallpaper.image
        val imageAlpha = lerp(0.62f, 1f, env.level).coerceAtLeast(if (opacity > 0f) 0.9f else 0f)
        val tintAlpha = (look.tintAlpha + 0.06f).coerceAtMost(0.92f)
        if (blurred != null && sharp != null && refraction && Build.VERSION.SDK_INT >= 33) {
            val shader = (wallpaperLens as? WallpaperLens) ?: WallpaperLens().also { wallpaperLens = it }
            shader.draw(
                this, blurred.asAndroidBitmap(), sharp.width, sharp.height, origin, radius,
                bezel = min(18.dp.toPx(), radius * 1.2f).coerceAtLeast(6.dp.toPx()),
                bend = 9.dp.toPx(), rim = 1.4.dp.toPx(), tint = look.tint, tintAlpha = tintAlpha,
                imageAlpha = imageAlpha, highlight = look.highlight,
            )
            return
        }
        val path = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius))) }
        clipPath(path) {
            if (blurred != null && sharp != null) {
                drawImage(
                    blurred,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(blurred.width, blurred.height),
                    dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
                    dstSize = IntSize(sharp.width, sharp.height),
                    alpha = imageAlpha,
                    filterQuality = FilterQuality.Low,
                )
            }
            val passthroughBoost = if (blurred == null) 0.18f else 0f
            drawRect(look.tint.copy(alpha = (tintAlpha + passthroughBoost).coerceAtMost(0.94f)))
        }
        drawRim(radius, look.highlight)
    }

    private fun DrawScope.drawRim(radius: Float, highlight: Float) {
        val w = size.width
        val h = size.height
        val sw = 1.2.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.6f * highlight),
                0.45f to Color.White.copy(alpha = 0.06f * highlight),
                1f to Color.White.copy(alpha = 0.3f * highlight),
                start = Offset.Zero, end = Offset(w * 0.6f, h),
            ),
            topLeft = Offset(sw / 2, sw / 2),
            size = androidx.compose.ui.geometry.Size(w - sw, h - sw),
            cornerRadius = CornerRadius(radius - sw / 2),
            style = Stroke(sw),
        )
    }
}

/**
 * Shared AGSL for the lens: signed distance to a rounded rect, its normal, a bezel where the image is
 * pulled inward (so the rim magnifies what's behind it), a slight RGB split there, tint, a soft top sheen,
 * and a specular rim lit from the top-left with a fainter bounce at the bottom-right.
 */
private const val LENS_COMMON = """
uniform float2 size;
uniform float2 margin;
uniform float radius;
uniform float bezel;
uniform float bend;
uniform float rim;
uniform float4 tint;
uniform float highlight;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

float4 finish(float4 c, float2 local, float d, float2 n, float lens) {
    float3 t = tint.rgb * tint.a;
    c = float4(t + c.rgb * (1.0 - tint.a), tint.a + c.a * (1.0 - tint.a));
    float sheen = clamp(1.0 - local.y / size.y, 0.0, 1.0) * 0.04 * highlight;
    float edge = 1.0 - smoothstep(0.0, rim, -d);
    float tl = clamp(dot(n, float2(-0.55, -0.83)), 0.0, 1.0);
    float br = clamp(dot(n, float2(0.55, 0.83)), 0.0, 1.0);
    float spec = pow(tl, 1.6) + 0.55 * pow(br, 1.6);
    float hl = (edge * (0.06 + 0.94 * spec) + lens * 0.07) * highlight + sheen;
    c.a = clamp(c.a + hl, 0.0, 1.0);
    c.rgb = min(c.rgb + float3(hl), float3(c.a));
    float aa = clamp(0.5 - d, 0.0, 1.0);
    return c * aa;
}
"""

private const val LENS_GEOMETRY = """
    float2 hs = size * 0.5;
    float2 p = local - hs;
    float d = sdRoundRect(p, hs, radius);
    if (d > 1.0) { return half4(0.0); }
    float e = 0.75;
    float2 g = float2(
        sdRoundRect(p + float2(e, 0.0), hs, radius) - sdRoundRect(p - float2(e, 0.0), hs, radius),
        sdRoundRect(p + float2(0.0, e), hs, radius) - sdRoundRect(p - float2(0.0, e), hs, radius));
    float2 n = g / max(length(g), 0.0001);
    float depth = clamp(-d / bezel, 0.0, 1.0);
    float lens = 1.0 - depth;
    lens = lens * lens * lens;
    float2 off = n * lens * bend;
    float2 split = n * lens * 2.5;
"""

/** Lens applied as a RenderEffect to the blurred live backdrop. */
@RequiresApi(33)
private class BackdropLens {
    private val shader = RuntimeShader(
        LENS_COMMON + """
uniform shader content;
half4 main(float2 coord) {
    float2 local = coord - margin;
""" + LENS_GEOMETRY + """
    float2 uv = coord - off;
    float4 c = float4(content.eval(uv));
    float rr = float(content.eval(uv - split).r);
    float bb = float(content.eval(uv + split).b);
    c = float4(rr, c.g, bb, c.a);
    return half4(finish(c, local, d, n, lens));
}
"""
    )

    fun effect(
        blur: Float, w: Float, h: Float, margin: Float, radius: Float, bezel: Float, bend: Float,
        rim: Float, tint: Color, tintAlpha: Float, highlight: Float,
    ): RenderEffect {
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("margin", margin, margin)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("bezel", bezel)
        shader.setFloatUniform("bend", bend)
        shader.setFloatUniform("rim", rim)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tintAlpha)
        shader.setFloatUniform("highlight", highlight)
        val lens = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
        val out = if (blur > 0.5f) {
            AndroidRenderEffect.createChainEffect(lens, AndroidRenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP))
        } else lens
        return out.asComposeRenderEffect()
    }
}

/** Lens over a pre-blurred wallpaper bitmap, for glass that sits directly on the wallpaper. */
@RequiresApi(33)
private class WallpaperLens {
    private val shader = RuntimeShader(
        LENS_COMMON + """
uniform shader image;
uniform float imageAlpha;
half4 main(float2 coord) {
    float2 local = coord;
""" + LENS_GEOMETRY + """
    float2 uv = coord - off;
    float4 c = float4(image.eval(uv));
    float rr = float(image.eval(uv - split).r);
    float bb = float(image.eval(uv + split).b);
    c = float4(rr, c.g, bb, c.a) * imageAlpha;
    return half4(finish(c, local, d, n, lens));
}
"""
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var source: android.graphics.Bitmap? = null
    private var bitmapShader: BitmapShader? = null

    fun draw(
        scope: ContentDrawScope,
        bitmap: android.graphics.Bitmap,
        screenW: Int,
        screenH: Int,
        origin: Offset,
        radius: Float,
        bezel: Float,
        bend: Float,
        rim: Float,
        tint: Color,
        tintAlpha: Float,
        imageAlpha: Float,
        highlight: Float,
    ) {
        if (bitmap !== source) {
            source = bitmap
            bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val bs = bitmapShader ?: return
        matrix.reset()
        matrix.setScale(screenW.toFloat() / bitmap.width, screenH.toFloat() / bitmap.height)
        matrix.postTranslate(-origin.x, -origin.y)
        bs.setLocalMatrix(matrix)

        val w = scope.size.width
        val h = scope.size.height
        shader.setInputShader("image", bs)
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("margin", 0f, 0f)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("bezel", bezel)
        shader.setFloatUniform("bend", bend)
        shader.setFloatUniform("rim", rim)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, tintAlpha)
        shader.setFloatUniform("imageAlpha", imageAlpha)
        shader.setFloatUniform("highlight", highlight)
        paint.shader = shader
        scope.drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, w, h, paint) }
    }
}
