package dev.kidyoh.liquid.ui.center

import android.app.ActivityOptions
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.system.Badges
import dev.kidyoh.liquid.system.Controls
import dev.kidyoh.liquid.system.NotificationItem
import dev.kidyoh.liquid.system.SystemActions
import dev.kidyoh.liquid.system.rememberTorch
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.home.AppIconImage
import dev.kidyoh.liquid.ui.home.HomeController
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.Inter
import dev.kidyoh.liquid.ui.widgets.rememberNow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val shadow = Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, 2f), 10f)
private val white = Color.White

/** Swipe down from the top-left: the iOS Lock Screen clock and your notifications on glass. */
@Composable
fun NotificationCenter(controller: HomeController, topInset: Dp, bottomInset: Dp) {
    AnimatedVisibility(
        controller.notificationCenter,
        enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val ctx = LocalContext.current
        val items by Badges.items.collectAsState()
        val access = remember { Badges.hasAccess(ctx) } || items.isNotEmpty()
        val apps by LiquidApp.instance.apps.apps.collectAsState()
        val byPkg = remember(apps) { apps.groupBy { it.key.pkg }.mapValues { it.value.first() } }
        val groups = remember(items) { items.groupBy { it.pkg }.values.toList() }
        var expanded by remember { mutableStateOf(setOf<String>()) }
        val close = { controller.notificationCenter = false }
        val torch = rememberTorch()

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.45f))))
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -70.dp.toPx()) close() }) { _, dy -> total += dy }
                }
        ) {
            LazyColumn(
                contentPadding = PaddingValues(top = topInset + 26.dp, bottom = bottomInset + 120.dp, start = 12.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { LockClock() }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notification Center", style = TextStyle(fontFamily = Inter, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = white, shadow = shadow), modifier = Modifier.weight(1f))
                        if (items.isNotEmpty()) {
                            Glass(
                                Modifier.size(44.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Badges.dismissAll() },
                                corner = Dp.Infinity, opacity = 0.2f,
                            ) { Icon(Glyphs.Close, "Clear all", tint = white, modifier = Modifier.align(Alignment.Center).size(18.dp)) }
                        }
                    }
                }
                when {
                    !access -> item {
                        Glass(
                            Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                Controls.open(ctx, SystemActions.notificationAccessIntent())
                            },
                            corner = 26.dp, opacity = 0.25f,
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Show your notifications here", style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = white))
                                Text("Tap to allow notification access for Liquid Launcher.", style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = white.copy(alpha = 0.8f)))
                            }
                        }
                    }
                    items.isEmpty() -> item {
                        Text(
                            "No Older Notifications",
                            style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = white.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    else -> items(groups, key = { it.first().pkg }) { group ->
                        val pkg = group.first().pkg
                        val open = pkg in expanded || group.size == 1
                        if (open) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.forEach { n ->
                                    SwipeToClear(onClear = { Badges.dismiss(n.key) }) {
                                        NotificationCard(n, byPkg[pkg]) { openNotification(ctx, n); close() }
                                    }
                                }
                            }
                        } else {
                            SwipeToClear(onClear = { group.forEach { Badges.dismiss(it.key) } }) {
                                Box {
                                    // The stack behind, like iOS.
                                    StackPlate(2, group.size > 2)
                                    StackPlate(1, true)
                                    NotificationCard(group.first(), byPkg[pkg], count = group.size) { expanded = expanded + pkg }
                                }
                            }
                        }
                    }
                }
            }
            // Lock Screen-style shortcuts.
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = bottomInset + 24.dp, start = 44.dp, end = 44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundToggle(Glyphs.Flashlight, torch.on.value, Color.White, 62.dp, activeGlyph = Color(0xFF1C1C1E)) { torch.toggle() }
                RoundToggle(Glyphs.Camera, false, Color.White, 62.dp) { close(); Controls.camera(ctx) }
            }
        }
    }
}

fun openNotification(ctx: android.content.Context, n: NotificationItem) {
    val pi = n.intent
    if (pi != null) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(ctx, 0, null, null, null, null, opts.toBundle())
            } else pi.send()
        }.onSuccess { return }
    }
    ctx.packageManager.getLaunchIntentForPackage(n.pkg)?.let { Controls.open(ctx, it) }
}

/** iOS 26 Lock Screen clock: date above big glassy numerals. */
@Composable
fun LockClock() {
    val now by rememberNow(1000L)
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val t = remember(now / 1000) { LocalDateTime.now() }
    val time = t.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm", locale))
    val date = t.format(DateTimeFormatter.ofPattern("EEE MMM d", locale))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(date, style = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = white.copy(alpha = 0.92f), shadow = shadow))
        Box(contentAlignment = Alignment.Center) {
            val big = TextStyle(fontFamily = Inter, fontSize = 104.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-3).sp)
            // Frosted fill…
            Text(time, style = big.copy(brush = Brush.verticalGradient(listOf(white.copy(alpha = 0.78f), white.copy(alpha = 0.42f))), shadow = shadow))
            // …and a bright glass edge.
            Text(time, style = big.copy(color = white.copy(alpha = 0.55f), drawStyle = Stroke(width = 2.5f)))
        }
    }
}

@Composable
private fun StackPlate(depth: Int, show: Boolean) {
    if (!show) return
    Glass(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = (depth * 12).dp)
            .offset(y = (depth * 9).dp)
            .graphicsLayer { alpha = 1f - depth * 0.25f },
        corner = 24.dp, opacity = 0.3f,
    )
}

@Composable
fun NotificationCard(n: NotificationItem, app: dev.kidyoh.liquid.data.AppEntry?, count: Int = 1, onClick: () -> Unit) {
    val now by rememberNow(60_000L)
    Glass(
        Modifier.fillMaxWidth().pointerInput(n.key) { detectTapGestures { onClick() } },
        corner = 26.dp, opacity = 0.32f,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp)) {
                if (n.largeIcon != null) {
                    Image(n.largeIcon.asImageBitmap(), null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    AppIconImage(app, 18.dp, Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp), plate = false)
                } else {
                    AppIconImage(app, 40.dp, plate = false)
                }
                if (count > 1) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).background(white, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("$count", style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.title.ifBlank { app?.label ?: "" },
                        style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = white),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(ago(n.time, now), style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = white.copy(alpha = 0.6f)))
                }
                Text(n.text, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = white.copy(alpha = 0.9f)), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "now", "17m ago", "3h ago", "Yesterday", or a date — like iOS. */
fun ago(time: Long, now: Long): String {
    val m = (now - time) / 60_000
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        m < 48 * 60 -> "Yesterday"
        else -> DateFormat.format("MMM d", time).toString()
    }
}

/** Swipe a card left to clear it. */
@Composable
fun SwipeToClear(onClear: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(offset.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.8f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offset.value < -size.width * 0.35f) {
                                offset.animateTo(-size.width * 1.2f)
                                onClear()
                                offset.snapTo(0f)
                            } else offset.animateTo(0f, spring(dampingRatio = 0.7f))
                        }
                    },
                ) { change, dx ->
                    change.consume()
                    scope.launch { offset.snapTo((offset.value + dx).coerceAtMost(40f)) }
                }
            }
    ) { content() }
}

