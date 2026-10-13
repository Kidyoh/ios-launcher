package dev.kidyoh.liquid.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.AppEntry
import dev.kidyoh.liquid.data.FolderItem
import dev.kidyoh.liquid.data.IconStyle
import dev.kidyoh.liquid.data.LauncherSettings
import dev.kidyoh.liquid.icons.SquircleShape
import dev.kidyoh.liquid.system.SystemActions
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import kotlin.random.Random

@Immutable
data class HomeEnv(
    val settings: LauncherSettings,
    val apps: Map<String, AppEntry>,
    val badges: Map<String, Int>,
    val icon: Dp,
    val dark: Boolean,
)

val LocalHome = compositionLocalOf<HomeEnv> { error("HomeEnv not provided") }

val LabelStyle = TextStyle(
    fontFamily = Inter,
    fontSize = 12.5.sp,
    fontWeight = FontWeight.Medium,
    color = Color.White,
    textAlign = TextAlign.Center,
    letterSpacing = 0.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 4f),
)

/** A mutable box that doesn't trigger recomposition — for positions read only inside callbacks. */
class Ref<T>(var value: T)

/** Opens an app with the clip-reveal animation from its icon, and records it for suggestions. */
@Composable
fun rememberLauncher(): (AppEntry, Rect?) -> Unit {
    val ctx = LocalContext.current
    val view = LocalView.current
    return remember(ctx, view) {
        { app, bounds ->
            LiquidApp.instance.usage.record(app.id)
            SystemActions.launch(ctx, view, app, bounds?.toAndroidRect())
        }
    }
}

/** The squircle icon bitmap in the current icon style. */
@Composable
fun AppIconImage(app: AppEntry?, size: Dp, modifier: Modifier = Modifier, plate: Boolean = true) {
    val s = LocalHome.current.settings
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(8)
    val renderer = LiquidApp.instance.icons
    val packVersion by LiquidApp.instance.iconPack.version.collectAsState()
    val bmp by produceState(app?.let { renderer.cached(it, s.iconStyle, px, s.tint) }, app?.id, s.iconStyle, px, s.tint, packVersion) {
        if (app != null) value = renderer.icon(app, s.iconStyle, px, s.tint)
    }
    Box(modifier.size(size)) {
        if (s.iconStyle == IconStyle.Clear && plate) {
            Glass(Modifier.matchParentSize(), corner = size * 0.24f, refraction = size > 40.dp)
        }
        bmp?.let { Image(it, contentDescription = app?.label, modifier = Modifier.matchParentSize()) }
    }
}

@Composable
fun IconLabel(text: String, width: Dp, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.width(width),
        style = LabelStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 24.dp)
            .widthIn(min = 24.dp)
            .background(IOS.Red, CircleShape)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "%,d".format(count),
            style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White),
        )
    }
}

/** The grey (–) bubble shown on icons in jiggle mode. */
@Composable
fun MinusBubble(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = LocalHome.current.dark
    Glass(
        modifier
            .size(24.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val up = waitForUpOrCancel()
                    if (up) onClick()
                }
            },
        corner = Dp.Infinity,
        opacity = 0.45f,
        refraction = false,
    ) {
        Icon(
            Glyphs.Minus, null,
            tint = if (dark) Color.White else Color(0xFF1C1C1E),
            modifier = Modifier.align(Alignment.Center).size(14.dp),
        )
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.waitForUpOrCancel(): Boolean {
    while (true) {
        val ev = awaitPointerEvent()
        val c = ev.changes.firstOrNull() ?: return false
        if (!c.pressed) { c.consume(); return true }
        if (c.isConsumed) return false
    }
}

/** iOS-style wiggle for edit mode, with a random phase per item. */
@Composable
fun Modifier.jiggle(active: Boolean, seed: String): Modifier {
    if (!active) return this
    val r = remember(seed) { Random(seed.hashCode()) }
    val dur = remember(seed) { 118 + r.nextInt(36) }
    val start = remember(seed) { r.nextInt(dur) }
    val t = rememberInfiniteTransition(label = "jiggle")
    val angle by t.animateFloat(
        -1.5f, 1.5f,
        infiniteRepeatable(tween(dur, easing = LinearEasing), RepeatMode.Reverse, StartOffset(start)),
        label = "angle",
    )
    val dy by t.animateFloat(
        -0.5f, 0.5f,
        infiniteRepeatable(tween(dur + 41, easing = LinearEasing), RepeatMode.Reverse, StartOffset(start / 2)),
        label = "dy",
    )
    return graphicsLayer {
        rotationZ = angle
        translationY = dy * density
    }
}

/**
 * Touch handling shared by every Home Screen item:
 * tap opens; long-press shows the context menu, and moving afterwards picks the item up;
 * in jiggle mode any movement picks it up straight away.
 */
fun Modifier.itemGestures(
    key: Any,
    editMode: () -> Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onPress: (Boolean) -> Unit,
): Modifier = pointerInput(key) { handleItemGestures(editMode, onTap, onLongPress, onDragStart, onPress) }

private const val UP = 1
private const val CANCEL = 2

private suspend fun PointerInputScope.handleItemGestures(
    editMode: () -> Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onPress: (Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.isConsumed) return@awaitEachGesture
        down.consume()
        val slop = viewConfiguration.touchSlop
        onPress(true)
        try {
            if (editMode()) {
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!c.pressed) {
                        if (!c.isConsumed) { c.consume(); onTap() }
                        return@awaitEachGesture
                    }
                    if ((c.position - down.position).getDistance() > slop) {
                        c.consume()
                        onPress(false)
                        onDragStart(c.position)
                        return@awaitEachGesture
                    }
                }
            }
            val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull CANCEL
                    if (!c.pressed) {
                        if (c.isConsumed) return@withTimeoutOrNull CANCEL
                        c.consume()
                        return@withTimeoutOrNull UP
                    }
                    if (c.isConsumed || (c.position - down.position).getDistance() > slop) return@withTimeoutOrNull CANCEL
                }
                @Suppress("UNREACHABLE_CODE") CANCEL
            }
            when (result) {
                UP -> onTap()
                CANCEL -> Unit
                null -> {
                    onLongPress()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) { c.consume(); break }
                        if ((c.position - down.position).getDistance() > slop * 1.5f) {
                            c.consume()
                            onPress(false)
                            onDragStart(c.position)
                            break
                        }
                    }
                }
            }
        } finally {
            onPress(false)
        }
    }
}

/**
 * One icon + label as it sits in a grid cell, dock slot or folder.
 * [onTap]/[onLongPress]/[onDragStart] receive the icon's bounds / visual origin in root coordinates.
 */
@Composable
fun IconCell(
    id: String,
    app: AppEntry?,
    label: String?,
    cellWidth: Dp,
    iconTop: Dp,
    editMode: Boolean,
    editModeProvider: () -> Boolean,
    onTap: (Rect) -> Unit,
    onLongPress: (Rect) -> Unit,
    onDragStart: (pointer: Offset, visualOrigin: Offset) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    badgeCount: Int? = null,
    iconContent: @Composable (Dp) -> Unit = { AppIconImage(app, it) },
) {
    val env = LocalHome.current
    val icon = env.icon
    val cellOrigin = remember { Ref(Offset.Zero) }
    val iconBounds = remember { Ref(Rect.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val haptics = env.settings.haptics
    val scale by animateFloatAsState(
        when {
            highlight -> 1.12f
            pressed && !editMode -> 0.94f
            else -> 1f
        },
        spring(dampingRatio = 0.6f, stiffness = 600f), label = "press",
    )

    Box(
        modifier
            .onGloballyPositioned { cellOrigin.value = it.positionInRoot() }
            .itemGestures(
                key = id,
                editMode = editModeProvider,
                onTap = { onTap(iconBounds.value) },
                onLongPress = {
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress(iconBounds.value)
                },
                onDragStart = { local -> onDragStart(cellOrigin.value + local, iconBounds.value.topLeft) },
                onPress = { pressed = it },
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cellWidth)) {
            Spacer(Modifier.height(iconTop))
            Box(
                Modifier
                    .size(icon)
                    .onGloballyPositioned { iconBounds.value = it.boundsInRoot() }
                    .jiggle(editMode, id)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                if (highlight) {
                    Glass(Modifier.requiredSize(icon * 1.18f).align(Alignment.Center), corner = icon * 0.28f, opacity = 0.1f)
                }
                iconContent(icon)
                if (pressed && !editMode) {
                    Box(Modifier.matchParentSize().clip(SquircleShape).background(Color.Black.copy(alpha = 0.28f)))
                }
                val badge = badgeCount ?: app?.let { env.badges[it.key.pkg] } ?: 0
                if (env.settings.badges && badge > 0 && !editMode) {
                    Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp))
                }
                if (editMode && onRemove != null) {
                    MinusBubble(onRemove, Modifier.align(Alignment.TopStart).offset(x = (-7).dp, y = (-7).dp))
                }
            }
            if (label != null && env.settings.showLabels) {
                Spacer(Modifier.height(5.dp))
                IconLabel(label, cellWidth - 4.dp)
            }
        }
    }
}

/** A glass tile with a 3×3 preview of the apps inside. */
@Composable
fun FolderIcon(folder: FolderItem, size: Dp, modifier: Modifier = Modifier) {
    val apps = LocalHome.current.apps
    Glass(modifier.size(size), corner = size * 0.24f, opacity = 0.02f) {
        val pad = size * 0.14f
        val gap = size * 0.06f
        val mini = (size - pad * 2 - gap * 2) / 3
        folder.apps.take(9).forEachIndexed { i, id ->
            val x = pad + (mini + gap) * (i % 3)
            val y = pad + (mini + gap) * (i / 3)
            AppIconImage(apps[id], mini, Modifier.offset(x, y), plate = false)
        }
    }
}

/** Integer offset helper for animated placement. */
fun Offset.round() = IntOffset(x.toInt(), y.toInt())

fun minDp(a: Dp, b: Dp) = if (a < b) a else b
