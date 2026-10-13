package dev.kidyoh.liquid.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.AppCategory
import dev.kidyoh.liquid.data.AppEntry
import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.DragSource
import dev.kidyoh.liquid.data.LayoutRepository
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.home.AppIconImage
import dev.kidyoh.liquid.ui.home.HomeController
import dev.kidyoh.liquid.ui.home.LabelStyle
import dev.kidyoh.liquid.ui.home.MenuRequest
import dev.kidyoh.liquid.ui.home.Ref
import dev.kidyoh.liquid.ui.home.rememberLauncher
import dev.kidyoh.liquid.ui.search.AppRowGrid
import dev.kidyoh.liquid.ui.search.matchScore
import dev.kidyoh.liquid.ui.search.suggestions
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import kotlinx.coroutines.launch

/** The library's long-press menu reuses the Home one; page -1 marks "came from the App Library". */
val LibrarySource = DragSource.Page(-1)

private data class Group(val title: String, val apps: List<AppEntry>, val cluster: Boolean = true)

@Composable
fun AppLibrary(controller: HomeController, topInset: Dp, bottomInset: Dp) {
    val all by LiquidApp.instance.apps.apps.collectAsState()
    val usage by LiquidApp.instance.usage.state.collectAsState()
    var listMode by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Group?>(null) }
    val launch = rememberLauncher()

    val groups = remember(all, usage) {
        val recent = all.sortedByDescending { it.installTime }.take(8)
        buildList {
            add(Group("Suggestions", suggestions(all, 4), cluster = false))
            add(Group("Recently Added", recent))
            AppCategory.entries.forEach { c ->
                val apps = all.filter { it.category == c }
                if (apps.isNotEmpty()) add(Group(c.title, apps))
            }
        }
    }

    AnimatedContent(listMode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "library") { list ->
        if (list) {
            LibraryList(all, topInset, bottomInset, onClose = { listMode = false }, onLaunch = { a, b -> launch(a, b) }, controller = controller)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val side = 22.dp
                val gap = 18.dp
                val box = (maxWidth - side * 2 - gap) / 2
                LazyColumn(
                    contentPadding = PaddingValues(top = topInset + 6.dp, bottom = bottomInset + 16.dp, start = side, end = side),
                ) {
                    item {
                        Glass(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { listMode = true },
                            corner = Dp.Infinity,
                        ) {
                            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Glyphs.Search, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("App Library", style = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = Color.White.copy(alpha = 0.85f)))
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                    items(groups.chunked(2)) { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            row.forEach { g ->
                                CategoryBox(g, box, onLaunch = { a, b -> launch(a, b) }, onExpand = { expanded = g }, controller = controller)
                            }
                        }
                    }
                }
            }
        }
    }

    // A category's full contents, like opening a folder.
    AnimatedVisibility(expanded != null, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
        val g = remember { Ref<Group?>(null) }.also { if (expanded != null) it.value = expanded }.value ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { expanded = null } }
                .padding(top = topInset + 40.dp, start = 20.dp, end = 20.dp),
        ) {
            Column {
                Text(
                    g.title,
                    style = TextStyle(fontFamily = Inter, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                )
                LazyColumn(contentPadding = PaddingValues(bottom = bottomInset)) {
                    item { AppRowGrid(g.apps) { a, b -> launch(a, b); expanded = null } }
                }
            }
        }
    }
}

@Composable
private fun CategoryBox(g: Group, size: Dp, onLaunch: (AppEntry, Rect) -> Unit, onExpand: () -> Unit, controller: HomeController) {
    val pad = 13.dp
    val gap = 11.dp
    val big = (size - pad * 2 - gap) / 2
    val showCluster = g.cluster && g.apps.size > 4
    val heads = if (showCluster) g.apps.take(3) else g.apps.take(4)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Glass(Modifier.size(size), corner = 30.dp, opacity = 0.02f) {
            heads.forEachIndexed { i, app ->
                val b = remember(app.id) { Ref(Rect.Zero) }
                AppIconImage(
                    app, big,
                    Modifier
                        .offset(pad + (big + gap) * (i % 2), pad + (big + gap) * (i / 2))
                        .onGloballyPositioned { b.value = it.boundsInRoot() }
                        .pointerInput(app.id) {
                            detectTapGestures(
                                onTap = { onLaunch(app, b.value) },
                                onLongPress = { controller.menu = MenuRequest(AppItem(LayoutRepository.newId(), app.id), LibrarySource, b.value, app) },
                            )
                        },
                )
            }
            if (showCluster) {
                val mGap = 6.dp
                val mini = (big - mGap) / 2
                Box(
                    Modifier
                        .offset(pad + big + gap, pad + big + gap)
                        .size(big)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onExpand)
                ) {
                    g.apps.drop(3).take(4).forEachIndexed { i, app ->
                        AppIconImage(app, mini, Modifier.offset((mini + mGap) * (i % 2), (mini + mGap) * (i / 2)), plate = false)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(g.title, style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** The alphabetical list with a letter scrubber, reached from the App Library search field. */
@Composable
private fun LibraryList(
    all: List<AppEntry>,
    topInset: Dp,
    bottomInset: Dp,
    onClose: () -> Unit,
    onLaunch: (AppEntry, Rect) -> Unit,
    controller: HomeController,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val q = query.trim().lowercase()
    val shown = remember(q, all) {
        if (q.isEmpty()) all else all.mapNotNull { a -> matchScore(a, q)?.let { it to a } }.sortedBy { it.first }.map { it.second }
    }
    val sections = remember(shown, q) {
        if (q.isNotEmpty()) listOf("" to shown)
        else shown.groupBy { a -> a.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' }
            .toSortedMap(compareBy { if (it == '#') '~' else it })
            .map { (k, v) -> k.toString() to v }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(top = topInset + 6.dp).imePadding()) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Glass(Modifier.weight(1f).height(44.dp), corner = Dp.Infinity, opacity = 0.1f) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyphs.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("App Library", style = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f)))
                        BasicTextField(
                            query, { query = it }, singleLine = true,
                            textStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = Color.White),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Cancel",
                style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 20.dp, end = 34.dp, top = 8.dp, bottom = bottomInset)) {
                sections.forEach { (letter, apps) ->
                    if (letter.isNotEmpty()) item(key = "h$letter") {
                        Text(letter, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f)), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                    }
                    items(apps, key = { it.id }) { app ->
                        val b = remember(app.id) { Ref(Rect.Zero) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pointerInput(app.id) {
                                    detectTapGestures(
                                        onTap = { onLaunch(app, b.value) },
                                        onLongPress = { controller.menu = MenuRequest(AppItem(LayoutRepository.newId(), app.id), LibrarySource, b.value, app) },
                                    )
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIconImage(app, 44.dp, Modifier.onGloballyPositioned { b.value = it.boundsInRoot() })
                            Spacer(Modifier.width(14.dp))
                            Text(app.label, style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = Color.White), maxLines = 1)
                        }
                    }
                }
            }
            if (q.isEmpty()) {
                // Letter scrubber
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    sections.forEach { (letter, _) ->
                        Text(
                            letter,
                            style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IOS.BlueDark),
                            modifier = Modifier
                                .padding(vertical = 1.dp, horizontal = 4.dp)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    var index = 0
                                    for ((l, apps) in sections) {
                                        if (l == letter) break
                                        index += 1 + apps.size
                                    }
                                    scope.launch { listState.scrollToItem(index) }
                                },
                        )
                    }
                }
            }
        }
    }
}

