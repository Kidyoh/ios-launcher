package dev.kidyoh.liquid.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.AppEntry
import dev.kidyoh.liquid.system.SystemActions
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.home.AppIconImage
import dev.kidyoh.liquid.ui.home.HomeController
import dev.kidyoh.liquid.ui.home.IconLabel
import dev.kidyoh.liquid.ui.home.LocalHome
import dev.kidyoh.liquid.ui.home.Ref
import dev.kidyoh.liquid.ui.home.menuInk
import dev.kidyoh.liquid.ui.home.rememberLauncher
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import kotlinx.coroutines.delay

/** Scores how well an app matches a query; lower is better, null is no match. */
fun matchScore(app: AppEntry, q: String): Int? {
    val label = app.label.lowercase()
    return when {
        label.startsWith(q) -> 0
        label.split(' ', '-', '.').any { it.startsWith(q) } -> 1
        q in label -> 2
        q.length >= 3 && q in app.key.pkg.lowercase() -> 3
        else -> null
    }
}

/** Siri-style suggestions: most used apps, padded with alphabetical ones for a fresh install. */
fun suggestions(apps: List<AppEntry>, count: Int): List<AppEntry> {
    val byId = apps.associateBy { it.id }
    val ranked = LiquidApp.instance.usage.ranked().mapNotNull { byId[it] }
    return (ranked + apps).distinctBy { it.id }.take(count)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp, top = 18.dp),
        style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.92f)),
    )
}

@Composable
fun AppRowGrid(apps: List<AppEntry>, columns: Int = 4, onLaunch: (AppEntry, Rect) -> Unit) {
    val icon = LocalHome.current.icon
    Glass(Modifier.fillMaxWidth(), corner = 30.dp, opacity = 0.05f) {
        Column(Modifier.padding(vertical = 14.dp)) {
            apps.chunked(columns).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        val bounds = remember { Ref(Rect.Zero) }
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLaunch(app, bounds.value) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIconImage(app, icon, Modifier.onGloballyPositioned { bounds.value = it.boundsInRoot() })
                            Spacer(Modifier.height(5.dp))
                            IconLabel(app.label, icon + 18.dp)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun Spotlight(controller: HomeController, topInset: Dp) {
    AnimatedVisibility(
        controller.spotlight,
        enter = fadeIn() + slideInVertically { -it / 10 },
        exit = fadeOut() + slideOutVertically { -it / 10 },
    ) {
        val ctx = LocalContext.current
        val launch = rememberLauncher()
        val keyboard = LocalSoftwareKeyboardController.current
        val all by LiquidApp.instance.apps.apps.collectAsState()
        var query by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(120)
            runCatching { focus.requestFocus() }
        }
        val q = query.trim().lowercase()
        val matches = remember(q, all) {
            if (q.isEmpty()) emptyList()
            else all.mapNotNull { a -> matchScore(a, q)?.let { it to a } }.sortedBy { it.first }.map { it.second }
        }
        val math = remember(query) { MathEval.evaluate(query) }
        val ink = menuInk()
        val open = { app: AppEntry, b: Rect -> launch(app, b); controller.spotlight = false }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { controller.spotlight = false } }
                .imePadding()
        ) {
            Column(Modifier.fillMaxSize().padding(top = topInset + 10.dp, start = 16.dp, end = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glass(Modifier.weight(1f).height(48.dp), corner = Dp.Infinity, opacity = 0.3f) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Glyphs.Search, null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text("Search", style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = ink.copy(alpha = 0.45f)))
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = ink),
                                    cursorBrush = SolidColor(IOS.Blue),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        val top = matches.firstOrNull()
                                        if (top != null) open(top, Rect.Zero)
                                        else if (q.isNotEmpty()) { SystemActions.webSearch(ctx, query); controller.spotlight = false }
                                        keyboard?.hide()
                                    }),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                )
                            }
                            if (query.isNotEmpty()) {
                                Box(
                                    Modifier.size(20.dp).background(ink.copy(alpha = 0.3f), CircleShape)
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { query = "" },
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Glyphs.Close, "Clear", tint = Color.White, modifier = Modifier.size(11.dp)) }
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Glass(
                        Modifier.size(48.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.spotlight = false },
                        corner = Dp.Infinity, opacity = 0.3f,
                    ) { Icon(Glyphs.Close, "Close", tint = ink, modifier = Modifier.align(Alignment.Center).size(18.dp)) }
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { keyboard?.hide() } },
                ) {
                    if (q.isEmpty()) {
                        item { SectionTitle("Siri Suggestions") }
                        item { AppRowGrid(suggestions(all, 8), onLaunch = open) }
                    } else {
                        if (math != null) {
                            item { SectionTitle("Calculator") }
                            item {
                                Glass(Modifier.fillMaxWidth(), corner = 26.dp, opacity = 0.3f) {
                                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                                        Text(query.trim(), style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = ink.copy(alpha = 0.6f)))
                                        Text("= " + MathEval.format(math), style = TextStyle(fontFamily = Inter, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = ink))
                                    }
                                }
                            }
                        }
                        if (matches.isNotEmpty()) {
                            item { SectionTitle("Top Hit") }
                            item {
                                val top = matches.first()
                                val b = remember(top.id) { Ref(Rect.Zero) }
                                Glass(
                                    Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open(top, b.value) },
                                    corner = 26.dp, opacity = 0.3f,
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AppIconImage(top, 52.dp, Modifier.onGloballyPositioned { b.value = it.boundsInRoot() })
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(top.label, style = TextStyle(fontFamily = Inter, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink))
                                            Text(if (top.isWork) "Work app" else "Application", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = ink.copy(alpha = 0.55f)))
                                        }
                                        Text("Open", style = TextStyle(fontFamily = Inter, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = IOS.Blue))
                                    }
                                }
                            }
                            if (matches.size > 1) {
                                item { SectionTitle("Apps") }
                                item { AppRowGrid(matches.drop(1).take(8), onLaunch = open) }
                            }
                        }
                        item { SectionTitle("Search the Web") }
                        item {
                            Glass(Modifier.fillMaxWidth(), corner = 26.dp, opacity = 0.3f) {
                                Column {
                                    listOf(query.trim(), "${query.trim()} near me", "${query.trim()} news").forEachIndexed { i, s ->
                                        if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(0.6.dp).background(ink.copy(alpha = 0.12f)))
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                                    SystemActions.webSearch(ctx, s); controller.spotlight = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 13.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Start,
                                        ) {
                                            Icon(Glyphs.Search, null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Text(s, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = ink), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
