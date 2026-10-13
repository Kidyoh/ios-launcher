package dev.kidyoh.liquid.ui.center

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.ThemeMode
import dev.kidyoh.liquid.system.Controls
import dev.kidyoh.liquid.system.rememberNowPlaying
import dev.kidyoh.liquid.system.rememberTorch
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.home.HomeController
import dev.kidyoh.liquid.ui.home.LocalHome
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import dev.kidyoh.liquid.ui.widgets.rememberBattery
import kotlinx.coroutines.delay

private val noRipple = MutableInteractionSource()
private val label = TextStyle(fontFamily = Inter, color = Color.White)

/** iOS 26 Control Center: glass modules, round toggles and tall sliders. Swipe down from the top-right. */
@Composable
fun ControlCenter(controller: HomeController, topInset: Dp) {
    AnimatedVisibility(
        controller.controlCenter,
        enter = fadeIn(spring(stiffness = 500f)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 400f), initialScale = 0.88f, transformOrigin = TransformOrigin(0.85f, 0f)),
        exit = fadeOut() + scaleOut(targetScale = 0.9f, transformOrigin = TransformOrigin(0.85f, 0f)),
    ) {
        val ctx = LocalContext.current
        // Re-read system state every second while open, since most of it changes outside our app.
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
        val wifi = remember(tick) { Controls.wifiOn(ctx) }
        val bt = remember(tick) { Controls.bluetoothOn(ctx) }
        val airplane = remember(tick) { Controls.airplaneOn(ctx) }
        val rotationLocked = remember(tick) { Controls.rotationLocked(ctx) }
        val dnd = remember(tick) { runCatching { Controls.dndOn(ctx) }.getOrDefault(false) }
        val torch = rememberTorch()
        val media by rememberNowPlaying()
        val battery by rememberBattery()
        var brightness by remember { mutableFloatStateOf(Controls.brightness(ctx)) }
        var volume by remember { mutableFloatStateOf(Controls.volume(ctx)) }
        val close = { controller.controlCenter = false }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { close() } }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -60.dp.toPx()) close() }) { _, dy -> total += dy }
                }
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = topInset + 20.dp, start = 30.dp, end = 30.dp)) {
                val gap = 16.dp
                val cell = (maxWidth - gap * 3) / 4
                val block = cell * 2 + gap
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    // Status line
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Glyphs.Wifi, null, tint = Color.White.copy(alpha = if (wifi) 1f else 0.4f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Icon(Glyphs.Bluetooth, null, tint = Color.White.copy(alpha = if (bt) 1f else 0.4f), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.weight(1f))
                        Text("${(battery.level * 100).toInt()}%", style = label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.width(6.dp))
                        BatteryIcon(battery.level, battery.charging)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        // Connectivity module
                        Glass(Modifier.size(block), corner = 38.dp, opacity = 0.05f) {
                            val pad = 14.dp
                            val b = (block - pad * 3) / 2
                            Column(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(pad)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(pad)) {
                                    RoundToggle(Glyphs.Airplane, airplane, IOS.Orange, b) { Controls.openAirplane(ctx) }
                                    RoundToggle(Glyphs.Hotspot, false, IOS.Green, b) { Controls.openInternet(ctx) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(pad)) {
                                    RoundToggle(Glyphs.Wifi, wifi, IOS.Blue, b) { Controls.openWifi(ctx) }
                                    RoundToggle(Glyphs.Bluetooth, bt, IOS.Blue, b) { Controls.openBluetooth(ctx) }
                                }
                            }
                        }
                        // Now Playing module
                        Glass(Modifier.size(block), corner = 38.dp, opacity = 0.05f) {
                            Column(Modifier.fillMaxSize().padding(16.dp)) {
                                Row {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.15f))) {
                                        media.art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Box(
                                        Modifier.size(34.dp).background(Color(0xFFFF375F).copy(alpha = 0.85f), CircleShape)
                                            .clickable(interactionSource = noRipple, indication = null) { Controls.openCast(ctx) },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Glyphs.AirPlay, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                                }
                                Spacer(Modifier.weight(1f))
                                Text(media.title ?: "Not Playing", style = label.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                media.artist?.let { Text(it, style = label.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Spacer(Modifier.height(8.dp))
                                val c = media.controller?.transportControls
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    MediaButton(Glyphs.Previous, 24.dp, c != null) { c?.skipToPrevious() }
                                    MediaButton(if (media.playing) Glyphs.Pause else Glyphs.Play, 30.dp, true) {
                                        if (c == null) Controls.open(ctx, android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_MUSIC))
                                        else if (media.playing) c.pause() else c.play()
                                    }
                                    MediaButton(Glyphs.Next, 24.dp, c != null) { c?.skipToNext() }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                RoundToggle(Glyphs.RotationLock, rotationLocked, Color.White, cell, activeGlyph = IOS.Red) {
                                    Controls.setRotationLocked(ctx, !rotationLocked)
                                    tick++
                                }
                                RoundToggle(Glyphs.Mirror, false, Color.White, cell) { Controls.openCast(ctx) }
                            }
                            // Focus pill
                            Glass(
                                Modifier.size(block, cell).clickable(interactionSource = noRipple, indication = null) { Controls.setDnd(ctx, !dnd); tick++ },
                                corner = Dp.Infinity, opacity = if (dnd) 0.0f else 0.05f,
                            ) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(cell - 20.dp).background(if (dnd) Color.White else Color.White.copy(alpha = 0.18f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Glyphs.Moon, null, tint = if (dnd) IOS.Indigo else Color.White, modifier = Modifier.size(22.dp)) }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Do Not", style = label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                                        Text(if (dnd) "Disturb · On" else "Disturb", style = label.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                                    }
                                }
                            }
                        }
                        GlassSlider(Glyphs.Sun, IOS.Yellow, brightness, cell, block) { v ->
                            brightness = v
                            if (!Controls.setBrightness(ctx, v)) Controls.askWrite(ctx)
                        }
                        GlassSlider(Glyphs.Speaker, IOS.Blue, volume, cell, block) { v ->
                            volume = v
                            Controls.setVolume(ctx, v)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        RoundToggle(Glyphs.Flashlight, torch.on.value, Color.White, cell, activeGlyph = Color(0xFF1C1C1E)) { torch.toggle() }
                        RoundToggle(Glyphs.Camera, false, Color.White, cell) { close(); Controls.camera(ctx) }
                        RoundToggle(Glyphs.Calculator, false, Color.White, cell) { close(); Controls.calculator(ctx) }
                        RoundToggle(Glyphs.Timer, false, Color.White, cell) { close(); Controls.timer(ctx) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        RoundToggle(Glyphs.Alarm, false, Color.White, cell) { close(); Controls.alarms(ctx) }
                        val darkOn = LocalHome.current.dark
                        RoundToggle(Glyphs.Focus, darkOn, Color.White, cell, activeGlyph = Color.Black) {
                            LiquidApp.instance.settings.update { it.copy(theme = if (darkOn) ThemeMode.Light else ThemeMode.Dark) }
                        }
                        RoundToggle(Glyphs.Photo, false, Color.White, cell) {
                            close()
                            ctx.startActivity(android.content.Intent(ctx, dev.kidyoh.liquid.SettingsActivity::class.java).putExtra(dev.kidyoh.liquid.SettingsActivity.EXTRA_SCREEN, "wallpaper"))
                        }
                        RoundToggle(Glyphs.Gear, false, Color.White, cell) { close(); Controls.settings(ctx) }
                    }
                }
            }
        }
    }
}

/** A round glass button; when active it fills with [activeColor] (white tiles get a coloured glyph). */
@Composable
fun RoundToggle(
    glyph: ImageVector,
    active: Boolean,
    activeColor: Color,
    size: Dp,
    activeGlyph: Color = Color.White,
    onClick: () -> Unit,
) {
    val m = Modifier
        .size(size)
        .pointerInput(onClick) { detectTapGestures { onClick() } }
    val tint = if (active) (if (activeColor == Color.White) activeGlyph else Color.White) else Color.White
    if (active) {
        Box(m.background(activeColor, CircleShape), contentAlignment = Alignment.Center) {
            Icon(glyph, null, tint = tint, modifier = Modifier.size(size * 0.4f))
        }
    } else {
        Glass(m, corner = Dp.Infinity, opacity = 0.05f) {
            Icon(glyph, null, tint = tint, modifier = Modifier.align(Alignment.Center).size(size * 0.4f))
        }
    }
}

@Composable
private fun MediaButton(glyph: ImageVector, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        glyph, null,
        tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
        modifier = Modifier.size(size).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
    )
}

/** Tall glass capsule that fills with white from the bottom, like iOS brightness / volume. */
@Composable
fun GlassSlider(glyph: ImageVector, glyphColor: Color, value: Float, width: Dp, height: Dp, onChange: (Float) -> Unit) {
    Glass(
        Modifier
            .size(width, height)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    onChange(1f - (change.position.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) { detectTapGestures { onChange(1f - (it.y / size.height).coerceIn(0f, 1f)) } },
        corner = width / 2, opacity = 0.05f,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).background(Color.White.copy(alpha = 0.92f)))
            Icon(glyph, null, tint = if (value > 0.15f) glyphColor else Color.White, modifier = Modifier.padding(bottom = 18.dp).size(28.dp))
        }
    }
}

@Composable
private fun BatteryIcon(level: Float, charging: Boolean) {
    Box(Modifier.size(26.dp, 13.dp).background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(4.dp)).padding(1.5.dp)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0.05f, 1f))
                .background(if (charging) IOS.Green else if (level <= 0.2f) IOS.Red else Color.White, RoundedCornerShape(3.dp))
        )
    }
}
