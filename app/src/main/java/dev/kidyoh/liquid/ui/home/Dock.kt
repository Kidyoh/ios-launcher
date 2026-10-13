package dev.kidyoh.liquid.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.DragSource
import dev.kidyoh.liquid.data.LayoutRepository
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.Inter

@Composable
fun Dock(dock: List<String>, controller: HomeController, width: Dp, height: Dp, pad: Dp, modifier: Modifier = Modifier) {
    val env = LocalHome.current
    val density = LocalDensity.current
    val launch = rememberLauncher()
    val icon = env.icon
    val display = controller.dockDisplay(dock)
    val n = display.size.coerceAtLeast(1)
    val slot = (width - pad * 2) / n
    val edit = controller.editMode

    Glass(modifier.size(width, height), corner = 38.dp) {
        display.forEachIndexed { i, id ->
            if (id == null) return@forEachIndexed
            key(id) {
                val to = with(density) { IntOffset((pad + slot * i + (slot - icon) / 2).roundToPx(), ((height - icon) / 2).roundToPx()) }
                val pos by animateIntOffsetAsState(to, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow), label = "dock")
                val app = env.apps[id]
                val item = remember(id) { AppItem(LayoutRepository.newId(), id) }
                IconCell(
                    id = "dock:$id", app = app, label = null, cellWidth = icon, iconTop = 0.dp,
                    editMode = edit, editModeProvider = { controller.editMode },
                    onTap = { b -> if (!controller.editMode && app != null) launch(app, b) },
                    onLongPress = { b -> controller.menu = MenuRequest(item, DragSource.Dock, b, app) },
                    onDragStart = { p, o -> controller.startDrag(item, DragSource.Dock, p, o) },
                    onRemove = { controller.removeRequest = RemoveRequest(item, DragSource.Dock, app) },
                    modifier = Modifier.offset { pos },
                )
            }
        }
    }
}

/** iOS 26 shows a glass "Search" pill above the dock; while swiping pages it turns into page dots. */
@Composable
fun SearchPill(
    pages: Int,
    current: Int,
    showDots: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = Color.White
    Glass(
        modifier
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
            .pointerInput(Unit) { detectTapGestures { onSearch() } },
        corner = Dp.Infinity,
        opacity = 0f,
    ) {
        AnimatedContent(
            showDots,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "pill",
        ) { dots ->
            if (dots) {
                Row(
                    Modifier.padding(horizontal = 12.dp).size(width = (pages * 14).dp + 2.dp, height = 30.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages) { i ->
                        Box(
                            Modifier
                                .padding(horizontal = 3.5.dp)
                                .size(7.dp)
                                .background(if (i == current) fg else fg.copy(alpha = 0.35f), CircleShape)
                        )
                    }
                }
            } else {
                Row(
                    Modifier.padding(horizontal = 14.dp).height(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Glyphs.Search, null, tint = fg, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Search", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg))
                }
            }
        }
    }
}
