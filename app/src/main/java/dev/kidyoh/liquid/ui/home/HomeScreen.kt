package dev.kidyoh.liquid.ui.home

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.FolderItem
import dev.kidyoh.liquid.data.HomeLayout
import dev.kidyoh.liquid.data.SwipeDownAction
import dev.kidyoh.liquid.data.WidgetItem
import dev.kidyoh.liquid.data.span
import dev.kidyoh.liquid.system.Badges
import dev.kidyoh.liquid.ui.glass.GlassEnv
import dev.kidyoh.liquid.ui.glass.LocalBackdrop
import dev.kidyoh.liquid.ui.glass.backdropSource
import dev.kidyoh.liquid.ui.glass.rememberBackdrop
import dev.kidyoh.liquid.ui.glass.LocalGlass
import dev.kidyoh.liquid.ui.center.ControlCenter
import dev.kidyoh.liquid.ui.center.NotificationCenter
import dev.kidyoh.liquid.ui.island.DynamicIsland
import dev.kidyoh.liquid.ui.island.ISLAND_HEIGHT
import dev.kidyoh.liquid.ui.island.IslandController
import dev.kidyoh.liquid.ui.island.IslandOverlay
import dev.kidyoh.liquid.ui.island.cameraCenter
import dev.kidyoh.liquid.ui.library.AppLibrary
import dev.kidyoh.liquid.ui.search.Spotlight
import dev.kidyoh.liquid.ui.theme.isDark
import dev.kidyoh.liquid.ui.today.TodayView
import dev.kidyoh.liquid.ui.widgets.WidgetCard
import dev.kidyoh.liquid.wallpaper.Wallpaper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(controller: HomeController, homePresses: Flow<Unit>) {
    val app = LiquidApp.instance
    val settings by app.settings.state.collectAsState()
    val layoutOrNull by app.layout.layout.collectAsState()
    val apps by app.apps.byId.collectAsState()
    val badges by Badges.counts.collectAsState()
    val wallpaper by app.wallpaper.state.collectAsState()
    val dark = settings.theme.isDark()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        LaunchedEffect(settings.wallpaper, dark, wPx, hPx) { app.wallpaper.ensure(settings.wallpaper, dark, wPx, hPx) }

        val screenW = maxWidth
        val screenH = maxHeight
        val statusTop = with(density) { WindowInsets.statusBarsIgnoringVisibility.getTop(this).toDp() }
        val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

        // ---- Geometry (iPhone proportions) ----
        val cols = settings.columns
        val gridPad = 14.dp
        val cellW = (screenW - gridPad * 2) / cols
        val icon = (cellW * (if (cols >= 5) 0.72f else 0.69f) * settings.iconScale).coerceIn(44.dp, 78.dp)
        val dockH = icon + 30.dp
        val dockSide = 12.dp
        val dockW = screenW - dockSide * 2
        val dockTop = screenH - navBottom - 10.dp - dockH
        val pillH = 30.dp
        val pillTop = dockTop - 12.dp - pillH
        // Leave room above the grid for the Edit / Done buttons, which sit where the status bar is.
        val topReserve = maxOf(statusTop, 46.dp)
        val gridTop = topReserve + 12.dp
        val gridBottom = pillTop - 10.dp
        val gridH = gridBottom - gridTop
        val cellH = gridH / dev.kidyoh.liquid.data.HomeGrid.ROWS
        val labelH = if (settings.showLabels) 23.dp else 0.dp
        val iconTop = ((cellH - icon - labelH) / 2).coerceAtLeast(0.dp)
        val metrics = GridMetrics(cellW, cellH, iconTop)
        val dockPad = 10.dp

        with(density) {
            controller.geometry.apply {
                this.screenW = screenW.toPx()
                gridOrigin = Offset(gridPad.toPx(), gridTop.toPx())
                this.gridW = (screenW - gridPad * 2).toPx()
                this.gridH = gridH.toPx()
                this.cellW = cellW.toPx()
                this.cellH = cellH.toPx()
                this.icon = icon.toPx()
                this.iconTop = iconTop.toPx()
                columns = cols
                edge = 26.dp.toPx()
                dockRect = Rect(dockSide.toPx(), dockTop.toPx(), (dockSide + dockW).toPx(), (dockTop + dockH).toPx())
                this.dockPad = dockPad.toPx()
            }
        }

        val env = HomeEnv(settings, apps, badges, icon, dark)
        val layout = layoutOrNull ?: HomeLayout(emptyList(), emptyList())
        val dragging = controller.drag != null
        val homeCount = layout.pages.size.coerceAtLeast(1) + (if (dragging) 1 else 0)
        val pager = rememberPagerState(initialPage = 1) { homeCount + 2 }

        LaunchedEffect(pager, homeCount) {
            snapshotFlow { pager.currentPage }.collect { p ->
                controller.currentHomePage = (p - 1).coerceIn(0, homeCount - 1)
            }
        }
        LaunchedEffect(Unit) {
            homePresses.collect {
                if (!controller.dismissTop()) pager.animateScrollToPage(1)
            }
        }
        // Hold an icon against the screen edge to flip pages.
        LaunchedEffect(controller.edge) {
            val dir = controller.edge
            if (dir == 0) return@LaunchedEffect
            while (true) {
                delay(550)
                val next = pager.currentPage + dir
                if (next < 1 || next > homeCount) break
                pager.animateScrollToPage(next)
                controller.currentHomePage = next - 1
                controller.refreshTarget()
                if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(450)
            }
        }

        val position by remember(pager) { derivedStateOf { pager.currentPage + pager.currentPageOffsetFraction } }
        val todayAmount = (1f - position).coerceIn(0f, 1f)
        val libraryAmount = (position - homeCount).coerceIn(0f, 1f)
        val overlay = controller.openFolder != null || controller.spotlight || controller.menu != null || controller.widgetGallery ||
            controller.notificationCenter || controller.controlCenter || controller.iconPicker != null || controller.renameApp != null
        val overlayAmount by animateFloatAsState(if (overlay) 1f else 0f, spring(stiffness = 500f), label = "overlay")
        val blurWallpaper = maxOf(todayAmount, libraryAmount, overlayAmount)
        val dim = (if (dark && settings.dimWallpaperInDark) 0.22f else 0f)

        val backdrop = rememberBackdrop()
        CompositionLocalProvider(
            LocalGlass provides GlassEnv(wallpaper, dark, settings.glass),
            LocalHome provides env,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(controller) {
                        // While something is being dragged, the root follows the finger wherever it goes.
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (controller.drag != null) {
                                    if (!controller.settling) {
                                        val c = ev.changes.firstOrNull { it.pressed } ?: ev.changes.firstOrNull()
                                        if (c != null) {
                                            if (c.pressed) controller.moveDrag(c.position) else controller.endDrag()
                                        }
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
            ) {
                // Everything under the floating glass is recorded so the glass can show it, blurred and bent.
                val sinkBehindOverlay = Modifier.graphicsLayer {
                    val r = 28.dp.toPx() * overlayAmount
                    if (Build.VERSION.SDK_INT >= 31 && r > 0.5f) renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    val s = 1f - 0.04f * overlayAmount
                    scaleX = s; scaleY = s
                    alpha = 1f - 0.25f * overlayAmount
                }
                Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                    WallpaperLayer(wallpaper, blurWallpaper, dim, overlayAmount)
                    Box(Modifier.fillMaxSize().then(sinkBehindOverlay)) {
                    HorizontalPager(
                        state = pager,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !dragging,
                        key = { it },
                        modifier = Modifier
                            .fillMaxSize()
                            .swipeDown(controller, { pager.currentPage in 1..homeCount }) { start ->
                                val topZone = with(density) { maxOf(statusTop + 40.dp, 72.dp).toPx() }
                                val w = with(density) { screenW.toPx() }
                                when {
                                    start.y < topZone && start.x > w * 0.6f -> controller.controlCenter = true
                                    start.y < topZone -> controller.notificationCenter = true
                                    settings.swipeDown == SwipeDownAction.Notifications -> controller.notificationCenter = true
                                    else -> controller.spotlight = true
                                }
                            },
                    ) { page ->
                        when {
                            page == 0 -> TodayView(controller, metrics, statusTop, gridPad)
                            page <= homeCount -> {
                                val index = page - 1
                                Box(Modifier.fillMaxSize()) {
                                    HomePage(
                                        index,
                                        layout.pages.getOrNull(index).orEmpty(),
                                        controller,
                                        metrics,
                                        Modifier.offset(gridPad, gridTop).size(screenW - gridPad * 2, gridH),
                                    )
                                }
                            }
                            else -> AppLibrary(controller, statusTop, screenH - dockTop)
                        }
                    }

                    }
                }

                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                Box(Modifier.fillMaxSize().then(sinkBehindOverlay)) {
                    if (settings.showSearchPill || pager.isScrollInProgress || controller.editMode) {
                        val homeVisible = 1f - maxOf(todayAmount, libraryAmount)
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = pillTop)
                                .graphicsLayer { alpha = homeVisible }
                        ) {
                            SearchPill(
                                pages = homeCount,
                                current = controller.currentHomePage,
                                showDots = pager.isScrollInProgress || controller.editMode || !settings.showSearchPill,
                                onSearch = { if (!controller.editMode) controller.spotlight = true },
                            )
                        }
                    }

                    Dock(
                        layout.dock, controller, dockW, dockH, dockPad,
                        Modifier
                            .offset(dockSide, dockTop)
                            .graphicsLayer {
                                alpha = 1f - todayAmount
                                translationY = todayAmount * (dockH + navBottom).toPx()
                            },
                    )
                }

                EditChrome(controller, ((topReserve - 36.dp) / 2).coerceAtLeast(4.dp))
                FolderOverlay(controller, layout, screenW)
                Spotlight(controller, statusTop)
                NotificationCenter(controller, statusTop, navBottom)
                ControlCenter(controller, statusTop)
                ContextMenuOverlay(controller, screenW, screenH)
                WidgetGallery(controller, metrics)
                RemoveDialog(controller)
                IconPickerSheet(controller)
                RenameDialog(controller)
                DragOverlay(controller, metrics)

                // Dynamic Island on the Home Screen (the system-wide overlay takes over when allowed).
                val overlayShowing by IslandOverlay.showing.collectAsState()
                if (settings.dynamicIsland && !overlayShowing) {
                    val islandScope = rememberCoroutineScope()
                    val island = remember { IslandController(ctx.applicationContext, islandScope) }
                    DisposableEffect(island) {
                        island.start()
                        controller.island = island
                        onDispose { island.stop(); controller.island = null }
                    }
                    val view = LocalView.current
                    val cam = remember(view, wPx) { cameraCenter(view) }
                    val centerY = with(density) { cam?.second?.toDp() ?: (statusTop / 2) }
                    val dx = with(density) { cam?.let { (it.first - wPx / 2f).toDp() } ?: 0.dp }
                    if (island.expanded) {
                        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { island.collapse() } })
                    }
                    Box(Modifier.fillMaxWidth().offset(x = dx, y = (centerY - (ISLAND_HEIGHT / 2).dp).coerceAtLeast(8.dp))) {
                        DynamicIsland(island, screenW - 20.dp, Modifier.align(Alignment.TopCenter))
                    }
                }
                }
            }
        }
    }
}

/** The wallpaper, plus a blurred copy that fades in for Today View, App Library and overlays. */
@Composable
private fun WallpaperLayer(wp: Wallpaper, blur: Float, dim: Float, overlay: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val img = wp.image
        val blurred = wp.blurred
        if (img != null) {
            drawImage(img)
            if (blurred != null && blur > 0.01f) {
                drawImage(
                    blurred,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(blurred.width, blurred.height),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = blur,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                )
            }
        } else if (blur > 0.01f) {
            drawRect(Color.Black.copy(alpha = 0.35f * blur))
        }
        val d = dim + 0.12f * overlay
        if (d > 0f) drawRect(Color.Black.copy(alpha = d))
    }
}

/** The picked-up item following the finger. */
@Composable
private fun DragOverlay(controller: HomeController, metrics: GridMetrics) {
    val d = controller.drag ?: return
    val env = LocalHome.current
    val icon = env.icon
    val lift by animateFloatAsState(if (controller.settling) 1f else 1.1f, tween(180), label = "lift")
    Box(
        Modifier
            .offset { controller.dragPos.value.let { IntOffset(it.x.toInt(), it.y.toInt()) } }
            .graphicsLayer {
                scaleX = lift; scaleY = lift
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
    ) {
        when (val item = d.item) {
            is AppItem -> AppIconImage(env.apps[item.app], icon)
            is FolderItem -> FolderIcon(item, icon)
            is WidgetItem -> {
                val (sx, sy) = item.span(env.settings.columns)
                WidgetCard(item.kind, item.size, metrics.cellW * (sx - 1) + icon, metrics.cellH * (sy - 1) + icon)
            }
        }
    }
}

/** Swipe down on the Home Screen: Spotlight (or the notification shade from the top edge). */
private fun Modifier.swipeDown(controller: HomeController, enabled: () -> Boolean, onSwipe: (Offset) -> Unit): Modifier =
    pointerInput(controller) {
        val trigger = 48.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (controller.editMode || controller.drag != null || !enabled()) return@awaitEachGesture
            var total = Offset.Zero
            var vertical: Boolean? = null
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                if (vertical == null && c.isConsumed) break
                total += c.positionChange()
                if (vertical == null && total.getDistance() > viewConfiguration.touchSlop) {
                    vertical = total.y > 0 && total.y > abs(total.x) * 1.3f
                    if (vertical == false) break
                }
                if (vertical == true) {
                    c.consume()
                    if (total.y > trigger) {
                        onSwipe(down.position)
                        break
                    }
                }
            }
        }
    }
