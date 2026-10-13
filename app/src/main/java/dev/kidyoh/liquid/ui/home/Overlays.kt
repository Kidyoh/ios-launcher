package dev.kidyoh.liquid.ui.home

import android.content.Intent
import android.content.pm.ShortcutInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.SettingsActivity
import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.DragSource
import dev.kidyoh.liquid.data.FolderItem
import dev.kidyoh.liquid.data.LayoutOps
import dev.kidyoh.liquid.data.LayoutRepository
import dev.kidyoh.liquid.data.WidgetItem
import dev.kidyoh.liquid.data.WidgetKind
import dev.kidyoh.liquid.data.WidgetSize
import dev.kidyoh.liquid.system.SystemActions
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import dev.kidyoh.liquid.ui.widgets.WidgetCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Text colour for content sitting on a menu-style glass panel. */
@Composable
fun menuInk(): Color = if (LocalHome.current.dark) Color.White else Color(0xFF111111)

private val noRipple = MutableInteractionSource()

@Composable
private fun MenuRow(text: String, glyph: ImageVector?, color: Color = menuInk(), image: ImageBitmap? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = color), modifier = Modifier.weight(1f), maxLines = 1)
        when {
            image != null -> Image(image, null, Modifier.size(22.dp))
            glyph != null -> Icon(glyph, null, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun MenuDivider(thick: Boolean = false) {
    val c = if (LocalHome.current.dark) Color.White.copy(alpha = if (thick) 0.08f else 0.12f) else Color.Black.copy(alpha = if (thick) 0.06f else 0.1f)
    Box(Modifier.fillMaxWidth().height(if (thick) 7.dp else 0.6.dp).background(c))
}

/** Keeps the last non-null value around so exit animations have something to draw. */
@Composable
fun <T : Any> rememberLatest(value: T?): T? {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    return last
}

/** Long-press menu: the icon lifts and a glass menu of shortcuts and actions appears. */
@Composable
fun ContextMenuOverlay(controller: HomeController, screenW: Dp, screenH: Dp) {
    val req = controller.menu
    val shown = rememberLatest(req) ?: return
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val visible = req != null
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow))
    }
    if (!visible && progress.value == 0f) return
    val launch = rememberLauncher()
    val home by LiquidApp.instance.layout.layout.collectAsState()
    val app = shown.app
    val shortcuts by produceState<List<Pair<ShortcutInfo, ImageBitmap?>>>(emptyList(), app?.id) {
        if (app != null) {
            value = withContext(Dispatchers.IO) {
                SystemActions.shortcuts(ctx, app).map { s ->
                    s to SystemActions.shortcutIcon(ctx, s)?.let { d ->
                        LiquidApp.instance.icons.plain(d, with(density) { 22.dp.roundToPx() })
                    }
                }
            }
        }
    }

    val b = with(density) { Rect(shown.bounds.left.toDp().value, shown.bounds.top.toDp().value, shown.bounds.right.toDp().value, shown.bounds.bottom.toDp().value) }
    val menuW = 252.dp
    val below = b.bottom < screenH.value * 0.55f
    val menuX = (b.left.dp).coerceIn(14.dp, screenW - menuW - 14.dp)
    val p = progress.value

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { controller.menu = null } }
    ) {
        // The lifted item.
        Box(
            Modifier
                .offset(b.left.dp, b.top.dp)
                .size((b.right - b.left).dp, (b.bottom - b.top).dp)
                .graphicsLayer { val s = 1f + 0.06f * p; scaleX = s; scaleY = s; alpha = p }
        ) {
            when (val item = shown.item) {
                is AppItem -> AppIconImage(shown.app, (b.right - b.left).dp)
                is FolderItem -> FolderIcon(item, (b.right - b.left).dp)
                is WidgetItem -> WidgetCard(item.kind, item.size, (b.right - b.left).dp, (b.bottom - b.top).dp)
            }
        }

        val gap = 12.dp
        Glass(
            Modifier
                .width(menuW)
                .layout { measurable, constraints ->
                    val pl = measurable.measure(constraints)
                    layout(pl.width, pl.height) {
                        val x = menuX.roundToPx()
                        val y = if (below) (b.bottom.dp + gap).roundToPx() else (b.top.dp - gap).roundToPx() - pl.height
                        pl.place(x, y.coerceAtLeast(24.dp.roundToPx()))
                    }
                }
                .graphicsLayer {
                    alpha = p
                    val s = 0.6f + 0.4f * p
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(((b.left.dp - menuX) / menuW).coerceIn(0f, 1f), if (below) 0f else 1f)
                }
                .pointerInput(Unit) { detectTapGestures { } },
            corner = 26.dp,
            opacity = 0.42f,
        ) {
            Column {
                val close = { controller.menu = null }
                if (shortcuts.isNotEmpty()) {
                    shortcuts.forEachIndexed { i, (s, img) ->
                        if (i > 0) MenuDivider()
                        MenuRow((s.shortLabel ?: s.longLabel ?: "").toString(), null, image = img) {
                            close(); SystemActions.startShortcut(ctx, s, null)
                        }
                    }
                    MenuDivider(thick = true)
                }
                when (val item = shown.item) {
                    is AppItem -> if (shown.source == DragSource.Page(-1)) {
                        // Opened from the App Library.
                        val h = home
                        if (h != null && !LayoutOps.onHome(h, item.app)) {
                            MenuRow("Add to Home Screen", Glyphs.Plus) { close(); controller.addToHome(item.app) }
                            MenuDivider()
                        }
                        if (app != null) {
                            MenuRow("Share App", Glyphs.Share) { close(); SystemActions.share(ctx, app) }
                            MenuDivider()
                            MenuRow("App Info", Glyphs.Info) { close(); SystemActions.appInfo(ctx, app) }
                            if (SystemActions.canUninstall(app)) {
                                MenuDivider()
                                MenuRow("Delete App", Glyphs.Trash, color = IOS.Red) { close(); SystemActions.uninstall(ctx, app) }
                            }
                        }
                    } else {
                        MenuRow("Edit Home Screen", Glyphs.Grid) { controller.enterEdit() }
                        if (app != null) {
                            MenuDivider()
                            MenuRow("Share App", Glyphs.Share) { close(); SystemActions.share(ctx, app) }
                            MenuDivider()
                            MenuRow("App Info", Glyphs.Info) { close(); SystemActions.appInfo(ctx, app) }
                        }
                        MenuDivider()
                        MenuRow("Remove App", Glyphs.Minus, color = IOS.Red) {
                            close(); controller.removeRequest = RemoveRequest(item, shown.source, app)
                        }
                    }
                    is FolderItem -> {
                        MenuRow("Open Folder", Glyphs.Folder) { close(); controller.openFolder = FolderOpen(item.id, shown.bounds) }
                        MenuDivider()
                        MenuRow("Edit Home Screen", Glyphs.Grid) { controller.enterEdit() }
                        MenuDivider()
                        MenuRow("Remove Folder", Glyphs.Minus, color = IOS.Red) {
                            close(); controller.removeRequest = RemoveRequest(item, shown.source, null)
                        }
                    }
                    is WidgetItem -> {
                        MenuRow("Edit Home Screen", Glyphs.Grid) { controller.enterEdit() }
                        MenuDivider()
                        MenuRow("Remove Widget", Glyphs.Minus, color = IOS.Red) {
                            close(); controller.removeRequest = RemoveRequest(item, shown.source, null)
                        }
                    }
                }
            }
        }
    }
}

/** iOS 26 style alert with capsule buttons. */
@Composable
fun GlassAlert(
    title: String,
    message: String,
    buttons: List<Triple<String, Color?, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    val ink = menuInk()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Glass(Modifier.width(300.dp).pointerInput(Unit) { detectTapGestures { } }, corner = 34.dp, opacity = 0.5f) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink, textAlign = TextAlign.Center))
                Spacer(Modifier.height(6.dp))
                Text(message, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = ink.copy(alpha = 0.75f), textAlign = TextAlign.Center))
                Spacer(Modifier.height(18.dp))
                buttons.forEachIndexed { i, (label, color, action) ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    val bold = i == buttons.lastIndex
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(ink.copy(alpha = 0.08f), RoundedCornerShape(50))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = action),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = TextStyle(
                                fontFamily = Inter, fontSize = 16.sp,
                                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
                                color = color ?: ink,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RemoveDialog(controller: HomeController) {
    val req = controller.removeRequest
    val ctx = LocalContext.current
    AnimatedVisibility(req != null, enter = fadeIn() + scaleIn(initialScale = 1.1f), exit = fadeOut() + scaleOut(targetScale = 0.95f)) {
        val r = rememberLatest(req) ?: return@AnimatedVisibility
        val close = { controller.removeRequest = null }
        when (val item = r.item) {
            is AppItem -> {
                val name = r.app?.label ?: "App"
                val buttons = buildList<Triple<String, Color?, () -> Unit>> {
                    if (r.app != null && SystemActions.canUninstall(r.app)) {
                        add(Triple("Delete App", IOS.Red) { close(); SystemActions.uninstall(ctx, r.app) })
                    }
                    add(Triple("Remove from Home Screen", IOS.Blue) { close(); controller.removeFromHome(item, r.source) })
                    add(Triple("Cancel", null, close))
                }
                GlassAlert(
                    "Remove “$name”?",
                    "Removing from Home Screen will keep the app in your App Library.",
                    buttons, close,
                )
            }
            is FolderItem -> GlassAlert(
                "Remove “${item.name}”?",
                "The apps inside will stay in your App Library.",
                listOf(
                    Triple("Remove Folder", IOS.Red) { close(); controller.removeFromHome(item, r.source) },
                    Triple("Cancel", null, close),
                ),
                close,
            )
            is WidgetItem -> GlassAlert(
                "Remove “${item.kind.title}” Widget?",
                "Removing this widget will not delete any data.",
                listOf(
                    Triple("Remove", IOS.Red) { close(); controller.removeFromHome(item, r.source) },
                    Triple("Cancel", null, close),
                ),
                close,
            )
        }
    }
}

@Composable
private fun ChromeButton(modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val m = modifier
        .height(36.dp)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    if (accent) {
        Box(m.background(IOS.Blue, RoundedCornerShape(50)).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { content() }
    } else {
        Glass(m, corner = Dp.Infinity, opacity = 0.05f) {
            Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { content() }
        }
    }
}

/** The jiggle-mode toolbar: Edit menu, Undo / Redo and Done. */
@Composable
fun EditChrome(controller: HomeController, top: Dp) {
    val ctx = LocalContext.current
    val history by LiquidApp.instance.layout.history.collectAsState()
    val label = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    AnimatedVisibility(
        controller.editMode && controller.drag == null && !controller.widgetGallery,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        Box(Modifier.fillMaxWidth().padding(top = top, start = 16.dp, end = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChromeButton(onClick = { controller.editMenu = !controller.editMenu }) { Text("Edit", style = label) }
                Spacer(Modifier.width(8.dp))
                ChromeButton(Modifier.width(36.dp), onClick = { LiquidApp.instance.layout.undo() }) {
                    Icon(Glyphs.Undo, "Undo", tint = Color.White.copy(alpha = if (history.first > 0) 1f else 0.35f), modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(6.dp))
                ChromeButton(Modifier.width(36.dp), onClick = { LiquidApp.instance.layout.redo() }) {
                    Icon(Glyphs.Redo, "Redo", tint = Color.White.copy(alpha = if (history.second > 0) 1f else 0.35f), modifier = Modifier.size(17.dp))
                }
            }
            ChromeButton(Modifier.align(Alignment.TopEnd), accent = true, onClick = { controller.exitEdit() }) {
                Text("Done", style = label)
            }
        }
    }
    AnimatedVisibility(
        controller.editMenu && controller.editMode,
        enter = fadeIn() + scaleIn(initialScale = 0.7f, transformOrigin = TransformOrigin(0f, 0f)),
        exit = fadeOut() + scaleOut(targetScale = 0.8f, transformOrigin = TransformOrigin(0f, 0f)),
    ) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { controller.editMenu = false } }) {
            Glass(Modifier.padding(top = top + 44.dp, start = 16.dp).width(230.dp), corner = 24.dp, opacity = 0.42f) {
                Column {
                    MenuRow("Add Widget", Glyphs.Widgets) { controller.editMenu = false; controller.widgetGallery = true }
                    MenuDivider()
                    MenuRow("Customize", Glyphs.Paint) {
                        controller.editMenu = false
                        ctx.startActivity(Intent(ctx, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SCREEN, "appearance"))
                    }
                    MenuDivider()
                    MenuRow("Edit Wallpaper", Glyphs.Photo) {
                        controller.editMenu = false
                        ctx.startActivity(Intent(ctx, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SCREEN, "wallpaper"))
                    }
                }
            }
        }
    }
}

private val galleryEntries = listOf(
    WidgetKind.Weather to WidgetSize.Small,
    WidgetKind.AnalogClock to WidgetSize.Small,
    WidgetKind.WorldClock to WidgetSize.Medium,
    WidgetKind.Calendar to WidgetSize.Small,
    WidgetKind.Month to WidgetSize.Medium,
    WidgetKind.Battery to WidgetSize.Small,
)

/** Bottom sheet listing the built-in widgets. */
@Composable
fun WidgetGallery(controller: HomeController, metrics: GridMetrics) {
    val env = LocalHome.current
    val ink = menuInk()
    AnimatedVisibility(
        controller.widgetGallery,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
                .pointerInput(Unit) { detectTapGestures { controller.widgetGallery = false } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Glass(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .pointerInput(Unit) { detectTapGestures { } },
                corner = 38.dp,
                opacity = 0.4f,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = 38.dp, height = 5.dp).background(ink.copy(alpha = 0.25f), CircleShape))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text("Widgets", style = TextStyle(fontFamily = Inter, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ink), modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .background(ink.copy(alpha = 0.1f), CircleShape)
                                    .clickable(interactionSource = noRipple, indication = null) { controller.widgetGallery = false },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Glyphs.Close, "Close", tint = ink, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    items(galleryEntries) { (kind, size) ->
                        val w = metrics.cellW * (minOf(size.spanX, env.settings.columns) - 1) + env.icon
                        val h = metrics.cellH * (size.spanY - 1) + env.icon
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            WidgetCard(kind, size, w, h)
                            Spacer(Modifier.height(10.dp))
                            Text(kind.title, style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink))
                            Text(kind.subtitle, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = ink.copy(alpha = 0.6f), textAlign = TextAlign.Center))
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .height(40.dp)
                                    .background(IOS.Blue, RoundedCornerShape(50))
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                        controller.addWidget(WidgetItem(LayoutRepository.newId(), kind, size))
                                        controller.widgetGallery = false
                                    }
                                    .padding(horizontal = 22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(Glyphs.Plus, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Widget", style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

