package dev.kidyoh.liquid.ui.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.kidyoh.liquid.data.WidgetKind
import dev.kidyoh.liquid.data.WidgetSize
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.glass.LocalGlass
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle as JTextStyle
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Text colour that reads on the current glass. */
@Composable
fun onGlass(): Color {
    val g = LocalGlass.current
    return if (g.dark || g.level < 0.3f) Color.White else Color.Black
}

@Composable
fun onGlassSecondary(): Color = onGlass().copy(alpha = 0.6f)

/** Ticks every [periodMs], aligned to the wall clock. */
@Composable
fun rememberNow(periodMs: Long = 1000L): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        val now = System.currentTimeMillis()
        delay(periodMs - now % periodMs)
        value = System.currentTimeMillis()
    }
}

data class BatteryInfo(val level: Float, val charging: Boolean)

@Composable
fun rememberBattery(): State<BatteryInfo> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(BatteryInfo(1f, false)) }
    DisposableEffect(ctx) {
        fun read(i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0 && scale > 0) {
                state.value = BatteryInfo(
                    level / scale.toFloat(),
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read(i)
        }
        read(ContextCompat.registerReceiver(ctx, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    return state
}

/** A widget on its glass card. [width]×[height] is the card size. */
@Composable
fun WidgetCard(kind: WidgetKind, size: WidgetSize, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Glass(modifier.size(width, height), corner = min(width.value, height.value).dp * 0.16f + 4.dp, opacity = 0.04f) {
        when (kind) {
            WidgetKind.AnalogClock -> AnalogClockWidget()
            WidgetKind.WorldClock -> WorldClockWidget()
            WidgetKind.Calendar -> CalendarWidget()
            WidgetKind.Month -> MonthWidget()
            WidgetKind.Battery -> BatteryWidget()
        }
    }
}

private val handColorDay = Color(0xFF1C1C1E)

/** The classic iOS clock face. */
@Composable
fun ClockFace(
    zone: ZoneId,
    modifier: Modifier = Modifier,
    numbers: Boolean = true,
    caption: String? = null,
    night: Boolean? = null,
) {
    val now by rememberNow(1000L)
    val measurer = rememberTextMeasurer()
    val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
    val isNight = night ?: (t.hour < 6 || t.hour >= 19)
    val face = if (isNight) Color(0xFF111113) else Color.White
    val ink = if (isNight) Color.White else handColorDay
    Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(face, r, c)
        // minute ticks
        for (i in 0 until 60) {
            val major = i % 5 == 0
            if (numbers && major) continue
            val a = Math.toRadians(i * 6.0 - 90)
            val outer = r * 0.93f
            val inner = if (major) r * 0.80f else r * 0.88f
            drawLine(
                ink.copy(alpha = if (major) 0.9f else 0.35f),
                Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                strokeWidth = if (major) r * 0.035f else r * 0.018f,
                cap = StrokeCap.Round,
            )
        }
        if (numbers) {
            val style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, color = ink, fontSize = (r * 0.2f).toSp())
            for (h in 1..12) {
                val a = Math.toRadians(h * 30.0 - 90)
                val layout = measurer.measure(h.toString(), style)
                val p = Offset(c.x + cos(a).toFloat() * r * 0.76f, c.y + sin(a).toFloat() * r * 0.76f)
                drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
        caption?.let {
            val style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.55f), fontSize = (r * 0.15f).toSp())
            val layout = measurer.measure(it, style)
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y + r * 0.3f))
        }
        val sec = t.second
        val min = t.minute + sec / 60f
        val hour = (t.hour % 12) + min / 60f
        hand(c, hour * 30f, r * 0.5f, r * 0.075f, ink)
        hand(c, min * 6f, r * 0.8f, r * 0.055f, ink)
        rotate(sec * 6f, c) {
            drawLine(IOS.Orange, Offset(c.x, c.y + r * 0.18f), Offset(c.x, c.y - r * 0.86f), strokeWidth = r * 0.022f, cap = StrokeCap.Round)
        }
        drawCircle(ink, r * 0.06f, c)
        drawCircle(IOS.Orange, r * 0.035f, c)
        drawCircle(face, r * 0.015f, c)
    }
}

private fun DrawScope.hand(c: Offset, degrees: Float, length: Float, width: Float, color: Color) {
    rotate(degrees, c) {
        drawLine(color, c, Offset(c.x, c.y - length * 0.18f), strokeWidth = width * 0.45f, cap = StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y - length * 0.16f), Offset(c.x, c.y - length), strokeWidth = width, cap = StrokeCap.Round)
    }
}

private fun cityName(zone: ZoneId): String =
    zone.id.substringAfterLast('/').replace('_', ' ').let { if (it.length > 12) it.take(11) + "…" else it }

private fun cityCode(zone: ZoneId): String = cityName(zone).filter { it.isLetter() }.take(3).uppercase()

@Composable
private fun AnalogClockWidget() {
    val zone = ZoneId.systemDefault()
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        ClockFace(zone, Modifier.fillMaxSize(), caption = cityCode(zone))
    }
}

@Composable
private fun WorldClockWidget() {
    val local = ZoneId.systemDefault()
    val zones = remember(local) {
        (listOf(local) + listOf("America/New_York", "Europe/London", "Asia/Tokyo", "Australia/Sydney").map { ZoneId.of(it) })
            .distinctBy { ZonedDateTime.now(it).offset }
            .take(4)
    }
    val fg = onGlass()
    val fg2 = onGlassSecondary()
    Row(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        zones.forEach { z ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                ClockFace(z, Modifier.fillMaxWidth(0.86f), numbers = false)
                Spacer(Modifier.height(6.dp))
                Text(cityName(z), style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg), maxLines = 1)
                val diff = (ZonedDateTime.now(z).offset.totalSeconds - ZonedDateTime.now(local).offset.totalSeconds) / 3600
                Text(
                    if (diff == 0) "Local" else "%+dHRS".format(diff),
                    style = TextStyle(fontFamily = Inter, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = fg2),
                )
            }
        }
    }
}

@Composable
private fun CalendarWidget() {
    val locale = LocalConfiguration.current.locales[0]
    val now by rememberNow(60_000L)
    val d = remember(now / 60_000) { LocalDate.now() }
    val fg = onGlass()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            d.dayOfWeek.getDisplayName(JTextStyle.FULL, locale).uppercase(),
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IOS.Red, letterSpacing = 0.4.sp),
        )
        Text(
            d.dayOfMonth.toString(),
            style = TextStyle(fontFamily = Inter, fontSize = 52.sp, fontWeight = FontWeight.Light, color = fg, letterSpacing = (-2).sp),
        )
        Spacer(Modifier.weight(1f))
        Text("No events today", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = onGlassSecondary()))
    }
}

@Composable
private fun MonthWidget() {
    val locale = LocalConfiguration.current.locales[0]
    val now by rememberNow(60_000L)
    val today = remember(now / 60_000) { LocalDate.now() }
    val fg = onGlass()
    val fg2 = onGlassSecondary()
    Row(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.weight(0.9f).fillMaxHeight()) {
            Text(
                today.dayOfWeek.getDisplayName(JTextStyle.FULL, locale).uppercase(),
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IOS.Red, letterSpacing = 0.4.sp),
            )
            Text(
                today.dayOfMonth.toString(),
                style = TextStyle(fontFamily = Inter, fontSize = 50.sp, fontWeight = FontWeight.Light, color = fg, letterSpacing = (-2).sp),
            )
            Spacer(Modifier.weight(1f))
            Text("No more events today", style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg2), maxLines = 2)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1.3f)) {
            Text(
                today.month.getDisplayName(JTextStyle.FULL, locale).uppercase(),
                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IOS.Red, letterSpacing = 0.4.sp),
            )
            Spacer(Modifier.height(4.dp))
            val first = today.withDayOfMonth(1)
            val lead = first.dayOfWeek.value % 7 // Sunday-first grid
            val days = today.lengthOfMonth()
            val cells = (0 until 42).map { i -> (i - lead + 1).takeIf { it in 1..days } }
            val rows = cells.chunked(7).filter { r -> r.any { it != null } }
            val small = TextStyle(fontFamily = Inter, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, style = small.copy(color = fg2), modifier = Modifier.weight(1f))
                }
            }
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day == today.dayOfMonth) {
                                Canvas(Modifier.size(16.dp)) { drawCircle(IOS.Red) }
                            }
                            Text(
                                day?.toString() ?: "",
                                style = small.copy(color = if (day == today.dayOfMonth) Color.White else fg, fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryWidget() {
    val b by rememberBattery()
    val fg = onGlass()
    val track = fg.copy(alpha = 0.16f)
    val color = when {
        b.charging -> IOS.Green
        b.level <= 0.2f -> IOS.Red
        else -> IOS.Green
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(14.dp)) {
        val ring = minOf(maxWidth, maxHeight) * 0.68f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = size.minDimension * 0.11f
                    val inset = sw / 2
                    val s = Size(size.width - sw, size.height - sw)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), s, style = Stroke(sw))
                    drawArc(color, -90f, 360f * b.level, false, Offset(inset, inset), s, style = Stroke(sw, cap = StrokeCap.Round))
                }
                if (b.charging) {
                    Icon(Glyphs.Bolt, null, tint = fg, modifier = Modifier.size(ring * 0.36f))
                } else {
                    Canvas(Modifier.size(ring * 0.22f, ring * 0.38f)) {
                        drawRoundRect(fg, cornerRadius = CornerRadius(size.width * 0.25f), style = Stroke(size.width * 0.14f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${(b.level * 100).toInt()}%",
                style = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = fg),
            )
        }
    }
}
