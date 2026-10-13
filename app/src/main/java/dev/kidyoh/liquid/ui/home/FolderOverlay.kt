package dev.kidyoh.liquid.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.DragSource
import dev.kidyoh.liquid.data.HomeLayout
import dev.kidyoh.liquid.data.LayoutOps
import dev.kidyoh.liquid.data.LayoutRepository
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.theme.Inter

@Composable
fun FolderOverlay(controller: HomeController, layout: HomeLayout, screenW: Dp) {
    val open = controller.openFolder
    val g = controller.geometry
    SideEffect { if (open == null) g.folderPanel = null }
    val shown = rememberLatest(open) ?: return
    val folder = LayoutOps.findFolder(layout, shown.id)
    if (folder == null) {
        SideEffect { if (controller.openFolder?.id == shown.id) controller.openFolder = null }
        return
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(open != null) {
        progress.animateTo(if (open != null) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
    }
    if (open == null && progress.value == 0f) return

    val env = LocalHome.current
    val density = LocalDensity.current
    val launch = rememberLauncher()
    val icon = env.icon
    val panelW = minOf(screenW * 0.84f, 380.dp)
    val padH = 14.dp
    val padV = 20.dp
    val cellW = (panelW - padH * 2) / 3
    val iconTop = 6.dp
    val cellH = iconTop + icon + 5.dp + 16.dp + 14.dp
    val panelH = cellH * 3 + padV * 2
    val display = controller.folderDisplay(folder)
    val pages = display.chunked(9).ifEmpty { listOf(emptyList()) }
    val pager = rememberPagerState { pages.size }
    g.folderPage = pager.currentPage
    var panelCenter by remember { mutableStateOf<Offset?>(null) }
    val from = shown.from
    val source = DragSource.Folder(folder.id)
    val edit = controller.editMode

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { controller.openFolder = null } }
    ) {
        Column(Modifier.align(Alignment.Center).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FolderTitle(folder.name, edit, Modifier.graphicsLayer { alpha = progress.value }) { controller.renameFolder(folder.id, it) }
            Spacer(Modifier.height(24.dp))
            Glass(
                Modifier
                    .size(panelW, panelH)
                    .onGloballyPositioned {
                        val r = it.boundsInRoot()
                        panelCenter = r.center
                        if (controller.openFolder != null && progress.value > 0.9f) {
                            g.folderPanel = r
                            with(density) {
                                g.folderGridOrigin = r.topLeft + Offset(padH.toPx(), padV.toPx())
                                g.folderCellW = cellW.toPx()
                                g.folderCellH = cellH.toPx()
                                g.folderIconTop = iconTop.toPx()
                            }
                        }
                    }
                    .graphicsLayer {
                        val p = progress.value
                        val c = panelCenter
                        if (c != null && from.width > 0f) {
                            val s0 = from.width / size.width
                            val s = lerp(s0, 1f, p)
                            scaleX = s; scaleY = s
                            translationX = (from.center.x - c.x) * (1f - p)
                            translationY = (from.center.y - c.y) * (1f - p)
                        }
                        alpha = (p * 1.6f).coerceAtMost(1f)
                    }
                    .pointerInput(Unit) { detectTapGestures { } },
                corner = 42.dp,
                opacity = 0.08f,
            ) {
                HorizontalPager(pager, userScrollEnabled = controller.drag == null) { page ->
                    Box(Modifier.fillMaxSize().padding(horizontal = padH, vertical = padV)) {
                        pages[page].forEachIndexed { i, id ->
                            if (id == null) return@forEachIndexed
                            key(id) {
                                val to = with(density) { IntOffset((cellW * (i % 3)).roundToPx(), (cellH * (i / 3)).roundToPx()) }
                                val pos by animateIntOffsetAsState(to, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow), label = "fslot")
                                val app = env.apps[id]
                                val item = remember(id) { AppItem(LayoutRepository.newId(), id) }
                                IconCell(
                                    id = "f:${folder.id}:$id", app = app, label = app?.label, cellWidth = cellW, iconTop = iconTop,
                                    editMode = edit, editModeProvider = { controller.editMode },
                                    onTap = { b -> if (!controller.editMode && app != null) { launch(app, b); controller.openFolder = null } },
                                    onLongPress = { b -> controller.menu = MenuRequest(item, source, b, app) },
                                    onDragStart = { p, o -> controller.startDrag(item, source, p, o) },
                                    onRemove = { controller.removeFromHome(item, source) },
                                    modifier = Modifier.offset { pos },
                                )
                            }
                        }
                    }
                }
            }
            if (pages.size > 1) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    repeat(pages.size) { i ->
                        Box(
                            Modifier.padding(horizontal = 4.dp).size(7.dp)
                                .background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.35f), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTitle(name: String, editable: Boolean, modifier: Modifier, onRename: (String) -> Unit) {
    val style = TextStyle(fontFamily = Inter, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, letterSpacing = (-0.5).sp)
    if (!editable) {
        androidx.compose.material3.Text(name, style = style, modifier = modifier.padding(horizontal = 24.dp), maxLines = 1)
        return
    }
    var text by remember(name) { mutableStateOf(name) }
    val focus = LocalFocusManager.current
    BasicTextField(
        value = text,
        onValueChange = { text = it.take(40) },
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onRename(text); focus.clearFocus() }),
        modifier = modifier
            .widthIn(min = 160.dp, max = 320.dp)
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (text != name) onRename(text) } }
}
