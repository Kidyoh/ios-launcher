package dev.kidyoh.liquid.ui.island

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.ui.center.openNotification
import dev.kidyoh.liquid.ui.home.AppIconImage
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import dev.kidyoh.liquid.ui.widgets.rememberNow
import kotlin.math.max

const val ISLAND_HEIGHT = 36

private val white = Color.White
private val grey = Color(0xFF9A9AA0)
private val textStyle = TextStyle(fontFamily = Inter, color = white)

/** Which layout the island morphs between. Media keeps one key per track so it doesn't re-animate every tick. */
private data class Shape(val kind: String, val expanded: Boolean)

/** The black pill around the front camera, morphing with a springy iOS-style animation. */
@Composable
fun DynamicIsland(island: IslandController, maxWidth: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val mode = island.mode
    val expanded = island.expanded
    val big = expanded || mode is IslandMode.Note
    val corner by animateDpAsState(if (big) 42.dp else (ISLAND_HEIGHT / 2).dp, spring(dampingRatio = 0.75f, stiffness = 400f), label = "corner")
    val shapeKey = Shape(
        when (mode) {
            IslandMode.Idle -> "idle"
            is IslandMode.Media -> "media"
            is IslandMode.Charging -> "charge"
            is IslandMode.Note -> "note:${mode.item.key}"
            is IslandMode.Ringer -> "ring:${mode.silent}"
        },
        expanded,
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.Black)
            .pointerInput(mode, expanded) {
                detectTapGestures(
                    onTap = {
                        when (mode) {
                            is IslandMode.Note -> { openNotification(ctx, mode.item); island.showForTest(IslandMode.Idle) }
                            else -> island.tap()
                        }
                    },
                    onLongPress = { if (mode is IslandMode.Media) island.expanded = true },
                )
            }
    ) {
        AnimatedContent(
            targetState = shapeKey,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 80)) togetherWith fadeOut(tween(80))) using
                    SizeTransform(clip = true) { _, _ -> spring<IntSize>(dampingRatio = 0.7f, stiffness = 380f) }
            },
            label = "island",
        ) { key ->
            val m = island.mode
            when {
                key.kind == "idle" -> Box(Modifier.size(122.dp, ISLAND_HEIGHT.dp))
                key.kind == "media" && m is IslandMode.Media && key.expanded -> MediaExpanded(m, maxWidth)
                key.kind == "media" && m is IslandMode.Media -> MediaCompact(m)
                key.kind == "charge" && m is IslandMode.Charging -> Charging(m)
                key.kind.startsWith("note") && m is IslandMode.Note -> NoteView(m, maxWidth)
                key.kind.startsWith("ring") && m is IslandMode.Ringer -> RingerView(m)
                else -> Box(Modifier.size(122.dp, ISLAND_HEIGHT.dp))
            }
        }
    }
}

@Composable
private fun Art(m: IslandMode.Media, size: Dp, corner: Dp) {
    val art = m.np.art
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(m.accent.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        if (art != null) Image(art.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Icon(Glyphs.Play, null, tint = white, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
private fun MediaCompact(m: IslandMode.Media) {
    Row(Modifier.size(232.dp, ISLAND_HEIGHT.dp).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(m, 24.dp, 7.dp)
        Spacer(Modifier.weight(1f))
        Waveform(m.accent, m.np.playing, Modifier.size(24.dp, 16.dp))
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun MediaExpanded(m: IslandMode.Media, maxWidth: Dp) {
    val np = m.np
    val c = np.controller?.transportControls
    val now by rememberNow(1000L)
    Column(Modifier.width(maxWidth).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(remember { MutableInteractionSource() }, null) {
                runCatching { np.controller?.sessionActivity?.send() }
            }) { Art(m, 58.dp, 14.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(np.title ?: "Not Playing", style = textStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                np.artist?.let { Text(it, style = textStyle.copy(fontSize = 15.sp, color = grey), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Waveform(m.accent, np.playing, Modifier.size(26.dp, 20.dp))
        }
        Spacer(Modifier.height(16.dp))
        val pos = run { now; np.positionNow() }
        val dur = max(np.duration, 1L)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time(pos), style = textStyle.copy(fontSize = 12.sp, color = grey))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(white.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).background(white))
            }
            Spacer(Modifier.width(10.dp))
            Text(if (np.duration > 0) "-" + time(np.duration - pos) else "--:--", style = textStyle.copy(fontSize = 12.sp, color = grey))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Ctl(Glyphs.Previous, 30.dp) { c?.skipToPrevious() }
            Ctl(if (np.playing) Glyphs.Pause else Glyphs.Play, 38.dp) { if (np.playing) c?.pause() else c?.play() }
            Ctl(Glyphs.Next, 30.dp) { c?.skipToNext() }
        }
    }
}

@Composable
private fun Ctl(glyph: androidx.compose.ui.graphics.vector.ImageVector, size: Dp, onClick: () -> Unit) {
    Icon(glyph, null, tint = white, modifier = Modifier.size(size).clickable(remember { MutableInteractionSource() }, null, onClick = onClick))
}

private fun time(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Bouncing bars tinted with the album art's colour. */
@Composable
fun Waveform(color: Color, playing: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "wave")
    val durations = intArrayOf(420, 300, 520, 360, 460)
    val levels = durations.mapIndexed { i, d ->
        t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(d, easing = LinearEasing), RepeatMode.Reverse, androidx.compose.animation.core.StartOffset(i * 70)), label = "bar$i")
    }
    Canvas(modifier) {
        val n = levels.size
        val gap = size.width / (n * 2 - 1)
        levels.forEachIndexed { i, l ->
            val h = size.height * (if (playing) l.value else 0.3f)
            drawRoundRect(color, Offset(i * gap * 2, (size.height - h) / 2), Size(gap, h), CornerRadius(gap / 2))
        }
    }
}

@Composable
private fun Charging(m: IslandMode.Charging) {
    val green = if (m.charging) IOS.Green else white
    Row(Modifier.size(310.dp, ISLAND_HEIGHT.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (m.charging) "Charging" else "Unplugged", style = textStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        Text("${m.level}%", style = textStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = green))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(30.dp, 15.dp).clip(RoundedCornerShape(4.dp)).background(white.copy(alpha = 0.25f)).padding(2.dp)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((m.level / 100f).coerceIn(0.06f, 1f)).clip(RoundedCornerShape(2.dp)).background(green))
        }
    }
}

@Composable
private fun RingerView(m: IslandMode.Ringer) {
    Row(Modifier.size(236.dp, ISLAND_HEIGHT.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape).background(if (m.silent) IOS.Red else Color(0xFF3A3A3C)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Glyphs.Bell, null, tint = white, modifier = Modifier.size(16.dp))
            if (m.silent) Canvas(Modifier.size(18.dp)) {
                drawLine(white, Offset(2f, 2f), Offset(size.width - 2f, size.height - 2f), strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(if (m.silent) "Silent" else "Ring", style = textStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (m.silent) IOS.Red else white))
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun NoteView(m: IslandMode.Note, maxWidth: Dp) {
    val apps by LiquidApp.instance.apps.apps.collectAsState()
    val app = remember(apps, m.item.pkg) { apps.firstOrNull { it.key.pkg == m.item.pkg } }
    val n = m.item
    Row(Modifier.width(maxWidth).height(80.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp)) {
            if (n.largeIcon != null) {
                Image(n.largeIcon.asImageBitmap(), null, Modifier.size(46.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                AppIconImage(app, 20.dp, Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp), plate = false)
            } else AppIconImage(app, 46.dp, plate = false)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title.ifBlank { app?.label ?: "" }, style = textStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(n.text, style = textStyle.copy(fontSize = 14.sp, color = grey), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Where the front camera is, so the island can sit around it. Returns (centerX, centerY) in px or null. */
fun cameraCenter(view: android.view.View): Pair<Float, Float>? {
    if (Build.VERSION.SDK_INT < 28) return null
    val cutout = view.rootWindowInsets?.displayCutout ?: return null
    val r = cutout.boundingRects.firstOrNull { it.top <= 0 || it.top < view.height / 10 } ?: return null
    return r.exactCenterX() to r.exactCenterY()
}
