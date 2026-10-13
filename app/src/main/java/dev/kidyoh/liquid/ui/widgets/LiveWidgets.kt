package dev.kidyoh.liquid.ui.widgets

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.system.CalendarEvents
import dev.kidyoh.liquid.system.DayEvent
import dev.kidyoh.liquid.system.Permissions
import dev.kidyoh.liquid.system.WeatherState
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle

/** The sky behind the Weather widget, like iOS: blue by day, deep navy at night, grey when overcast. */
fun weatherSky(code: Int, isDay: Boolean): Brush {
    val cloudy = code in 3..99
    val wet = code >= 51
    val (top, bottom) = when {
        !isDay && cloudy -> Color(0xFF2B3047) to Color(0xFF1A1D2E)
        !isDay -> Color(0xFF14204A) to Color(0xFF2B3F7A)
        wet -> Color(0xFF55657A) to Color(0xFF3A475A)
        cloudy -> Color(0xFF6E86A6) to Color(0xFF4B6385)
        else -> Color(0xFF3D8BEA) to Color(0xFF6CB2F5)
    }
    return Brush.verticalGradient(listOf(top, bottom))
}

private val white = Color.White

@Composable
fun WeatherWidget() {
    val repo = LiquidApp.instance.weather
    val state by repo.state.collectAsState()
    val granted by Permissions.changes.collectAsState()
    LaunchedEffect(granted) { repo.refresh() }
    val now by rememberNow(15 * 60_000L)
    LaunchedEffect(now) { repo.refresh() }

    val w = (state as? WeatherState.Ready)?.weather
    val sky = if (w != null) weatherSky(w.code, w.isDay) else Brush.verticalGradient(listOf(Color(0xFF3D8BEA), Color(0xFF6CB2F5)))
    val base = TextStyle(fontFamily = Inter, color = white)
    Box(Modifier.fillMaxSize().background(sky)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            if (w == null) {
                Text("Weather", style = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.weight(1f))
                Text(
                    when (state) {
                        WeatherState.NeedsPermission -> "Tap to show the weather where you are"
                        WeatherState.Unavailable -> "Weather unavailable"
                        else -> "Loading…"
                    },
                    style = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.place, style = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Canvas(Modifier.size(11.dp)) { locationArrow() }
            }
            Text("${w.temp}°", style = base.copy(fontSize = 48.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp))
            Spacer(Modifier.weight(1f))
            WeatherGlyph(w.code, w.isDay, 20.dp)
            Spacer(Modifier.height(2.dp))
            Text(w.condition, style = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            Text("H:${w.high}° L:${w.low}°", style = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

private fun DrawScope.locationArrow() {
    val s = size.minDimension
    val p = Path().apply {
        moveTo(s, 0f); lineTo(0f, s * 0.45f); lineTo(s * 0.48f, s * 0.52f); lineTo(s * 0.55f, s); close()
    }
    drawPath(p, white)
}

/** Simple SF-style weather symbols. */
@Composable
fun WeatherGlyph(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        fun cloud(color: Color, dx: Float = 0f, dy: Float = 0f, scale: Float = 1f) {
            val k = s * scale
            drawCircle(color, k * 0.22f, Offset(dx + k * 0.38f, dy + k * 0.56f))
            drawCircle(color, k * 0.28f, Offset(dx + k * 0.58f, dy + k * 0.46f))
            drawRoundRect(color, Offset(dx + k * 0.16f, dy + k * 0.52f), Size(k * 0.7f, k * 0.26f), androidx.compose.ui.geometry.CornerRadius(k * 0.13f))
        }
        fun sun(c: Offset, r: Float) {
            drawCircle(IOS.Yellow, r, c)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val o = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                drawLine(IOS.Yellow, c + o * (r * 1.4f), c + o * (r * 1.9f), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
            }
        }
        fun moon(c: Offset, r: Float) {
            val p = Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(c, r))
            }
            val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(r * 0.55f, -r * 0.35f), r * 0.85f)) }
            drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, p, cut), white)
        }
        when (code) {
            0 -> if (isDay) sun(center, s * 0.22f) else moon(center, s * 0.38f)
            1, 2 -> {
                if (isDay) sun(Offset(s * 0.62f, s * 0.34f), s * 0.16f) else moon(Offset(s * 0.64f, s * 0.34f), s * 0.24f)
                cloud(white, dx = -s * 0.08f, dy = s * 0.08f, scale = 0.9f)
            }
            in 51..67, in 80..82 -> {
                cloud(white, dy = -s * 0.14f)
                for (i in 0 until 3) {
                    val x = s * (0.32f + i * 0.18f)
                    drawLine(Color(0xFF64D2FF), Offset(x, s * 0.72f), Offset(x - s * 0.06f, s * 0.92f), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
                }
            }
            in 71..77, 85, 86 -> {
                cloud(white, dy = -s * 0.14f)
                for (i in 0 until 3) drawCircle(white, s * 0.05f, Offset(s * (0.32f + i * 0.18f), s * 0.84f))
            }
            in 95..99 -> {
                cloud(white, dy = -s * 0.14f)
                val p = Path().apply {
                    moveTo(s * 0.55f, s * 0.62f); lineTo(s * 0.42f, s * 0.82f); lineTo(s * 0.52f, s * 0.82f); lineTo(s * 0.45f, s * 0.98f)
                    lineTo(s * 0.64f, s * 0.74f); lineTo(s * 0.54f, s * 0.74f); close()
                }
                drawPath(p, IOS.Yellow)
            }
            45, 48 -> for (i in 0 until 3) {
                drawLine(white, Offset(s * 0.18f, s * (0.38f + i * 0.17f)), Offset(s * 0.82f, s * (0.38f + i * 0.17f)), strokeWidth = s * 0.08f, cap = StrokeCap.Round)
            }
            else -> cloud(white)
        }
    }
}

private operator fun Offset.times(f: Float) = Offset(x * f, y * f)

/** iOS Calendar widget: weekday, date, and the next events with their calendar colours. */
@Composable
fun CalendarEventsWidget() {
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now by rememberNow(60_000L)
    val granted by Permissions.changes.collectAsState()
    val today = remember(now / 60_000) { LocalDate.now() }
    val events by produceState<List<DayEvent>?>(null, now / 300_000, granted) {
        value = withContext(Dispatchers.IO) { CalendarEvents.today(ctx) }
    }
    val fg = onGlass()
    val fg2 = onGlassSecondary()
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 12.dp, top = 15.dp, bottom = 12.dp)) {
        Text(
            today.dayOfWeek.getDisplayName(JTextStyle.FULL, locale).uppercase(),
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IOS.Red, letterSpacing = 0.3.sp),
        )
        Text(
            today.dayOfMonth.toString(),
            style = TextStyle(fontFamily = Inter, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal, color = fg, letterSpacing = (-1.5).sp),
        )
        val list = events
        when {
            list == null && !CalendarEvents.hasPermission(ctx) -> {
                Spacer(Modifier.weight(1f))
                Text("Tap to show your events", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg2))
            }
            list.isNullOrEmpty() -> {
                Spacer(Modifier.weight(1f))
                Text("No more events today", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = fg2))
            }
            else -> {
                Spacer(Modifier.height(2.dp))
                list.take(2).forEachIndexed { i, e ->
                    if (i > 0) Spacer(Modifier.height(4.dp))
                    EventRow(e, CalendarEvents.range(ctx, e))
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: DayEvent, time: String) {
    val base = Color(e.color or 0xFF000000.toInt())
    // Brighten the calendar colour so it reads on dark glass, like iOS does.
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(base.toArgbCompat(), it) }
    hsl[2] = hsl[2].coerceIn(0.62f, 0.72f); hsl[1] = hsl[1].coerceAtLeast(0.7f)
    val bright = Color(ColorUtils.HSLToColor(hsl))
    Row(
        Modifier
            .fillMaxWidth()
            .background(bright.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .height(36.dp)
    ) {
        Box(Modifier.width(3.5.dp).fillMaxHeight().padding(vertical = 4.dp).background(bright, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text(e.title, style = TextStyle(fontFamily = Inter, fontSize = 13.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = bright), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = bright.copy(alpha = 0.85f)), maxLines = 1)
        }
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

/** What tapping a live widget should do when it still needs a permission. Returns true if it asked. */
fun askForWidgetPermission(kind: dev.kidyoh.liquid.data.WidgetKind, ctx: android.content.Context): Boolean = when (kind) {
    dev.kidyoh.liquid.data.WidgetKind.Weather -> if (!LiquidApp.instance.weather.hasPermission()) {
        Permissions.ask(Manifest.permission.ACCESS_COARSE_LOCATION); true
    } else false
    dev.kidyoh.liquid.data.WidgetKind.Calendar, dev.kidyoh.liquid.data.WidgetKind.Month -> if (!CalendarEvents.hasPermission(ctx)) {
        Permissions.ask(Manifest.permission.READ_CALENDAR); true
    } else false
    else -> false
}
