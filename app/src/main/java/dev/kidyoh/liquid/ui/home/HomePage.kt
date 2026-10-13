package dev.kidyoh.liquid.ui.home

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.DragSource
import dev.kidyoh.liquid.data.DropTarget
import dev.kidyoh.liquid.data.FolderItem
import dev.kidyoh.liquid.data.HomeGrid
import dev.kidyoh.liquid.data.HomeItem
import dev.kidyoh.liquid.data.WidgetItem
import dev.kidyoh.liquid.data.WidgetKind
import dev.kidyoh.liquid.data.span
import dev.kidyoh.liquid.ui.widgets.WidgetCard

/** Grid metrics shared by pages, the dock, folders and the drag overlay. */
data class GridMetrics(val cellW: Dp, val cellH: Dp, val iconTop: Dp)

@Composable
fun HomePage(
    pageIndex: Int,
    items: List<HomeItem>,
    controller: HomeController,
    metrics: GridMetrics,
    modifier: Modifier = Modifier,
) {
    val env = LocalHome.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val launch = rememberLauncher()
    val cols = env.settings.columns
    val display = controller.displayList(pageIndex, items)
    val cells = remember(display, cols) { HomeGrid.pack(display, cols) }
    val dragId = controller.drag?.item?.id
    val target = controller.target
    val edit = controller.editMode
    val source = DragSource.Page(pageIndex)
    val (cellW, cellH, iconTop) = metrics

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(controller) {
                detectTapGestures(
                    onLongPress = {
                        if (env.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.enterEdit()
                    },
                    onTap = { if (controller.editMode) controller.exitEdit() },
                )
            }
    ) {
        display.forEachIndexed { i, item ->
            val cell = cells.getOrNull(i) ?: return@forEachIndexed
            if (item.id == dragId) return@forEachIndexed
            key(item.id) {
                val to = with(density) { IntOffset((cellW * cell.col).roundToPx(), (cellH * cell.row).roundToPx()) }
                val pos by animateIntOffsetAsState(to, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow), label = "slot")
                val place = Modifier.offset { pos }
                val merge = target is DropTarget.Merge && target.targetId == item.id
                when (item) {
                    is AppItem -> {
                        val app = env.apps[item.app]
                        IconCell(
                            id = item.id, app = app, label = app?.label, cellWidth = cellW, iconTop = iconTop,
                            editMode = edit, editModeProvider = { controller.editMode },
                            onTap = { b -> if (!controller.editMode && app != null) launch(app, b) },
                            onLongPress = { b -> controller.menu = MenuRequest(item, source, b, app) },
                            onDragStart = { p, o -> controller.startDrag(item, source, p, o) },
                            onRemove = { controller.removeRequest = RemoveRequest(item, source, app) },
                            modifier = place, highlight = merge,
                        )
                    }
                    is FolderItem -> IconCell(
                        id = item.id, app = null, label = item.name, cellWidth = cellW, iconTop = iconTop,
                        editMode = edit, editModeProvider = { controller.editMode },
                        onTap = { b -> controller.openFolder = FolderOpen(item.id, b) },
                        onLongPress = { b -> controller.menu = MenuRequest(item, source, b, null) },
                        onDragStart = { p, o -> controller.startDrag(item, source, p, o) },
                        onRemove = { controller.removeRequest = RemoveRequest(item, source, null) },
                        modifier = place, highlight = merge,
                        badgeCount = item.apps.sumOf { id -> env.apps[id]?.let { env.badges[it.key.pkg] } ?: 0 },
                        iconContent = { FolderIcon(item, it) },
                    )
                    is WidgetItem -> WidgetCell(item, source, controller, metrics, place)
                }
            }
        }
    }
}

@Composable
fun WidgetCell(item: WidgetItem, source: DragSource, controller: HomeController, metrics: GridMetrics, modifier: Modifier = Modifier) {
    val env = LocalHome.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val icon = env.icon
    val (sx, sy) = item.span(env.settings.columns)
    val (cellW, cellH, iconTop) = metrics
    val w = cellW * (sx - 1) + icon
    val h = cellH * (sy - 1) + icon
    val origin = remember { Ref(Offset.Zero) }
    val body = remember { Ref(Rect.Zero) }
    val edit = controller.editMode
    Box(
        modifier
            .size(cellW * sx, cellH * sy)
            .onGloballyPositioned { origin.value = it.positionInRoot() }
            .itemGestures(
                key = item.id,
                editMode = { controller.editMode },
                onTap = { if (!controller.editMode) openWidgetApp(ctx, item.kind) },
                onLongPress = {
                    if (env.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    controller.menu = MenuRequest(item, source, body.value, null)
                },
                onDragStart = { local -> controller.startDrag(item, source, origin.value + local, body.value.topLeft) },
                onPress = {},
            )
    ) {
        Column(Modifier.offset(x = (cellW - icon) / 2, y = iconTop)) {
            WidgetCard(
                item.kind, item.size, w, h,
                Modifier
                    .onGloballyPositioned { body.value = it.boundsInRoot() }
                    .jiggle(edit, item.id),
            )
            if (env.settings.showLabels) {
                Spacer(Modifier.height(5.dp))
                IconLabel(item.kind.title, w)
            }
        }
        if (edit) {
            MinusBubble(
                { controller.removeRequest = RemoveRequest(item, source, null) },
                Modifier.align(Alignment.TopStart).offset(x = (cellW - icon) / 2 - 7.dp, y = iconTop - 7.dp),
            )
        }
    }
}

fun openWidgetApp(ctx: android.content.Context, kind: WidgetKind) {
    val intent = when (kind) {
        WidgetKind.AnalogClock, WidgetKind.WorldClock -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
        WidgetKind.Calendar, WidgetKind.Month -> Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
        WidgetKind.Battery -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
    }
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
