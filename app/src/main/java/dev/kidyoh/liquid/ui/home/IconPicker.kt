package dev.kidyoh.liquid.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.AppEntry
import dev.kidyoh.liquid.icons.IconPack
import dev.kidyoh.liquid.icons.PackIcon
import dev.kidyoh.liquid.ui.glass.Glass
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter

/** Bottom sheet for picking an app's icon from the iPhone-style set. */
@Composable
fun IconPickerSheet(controller: HomeController) {
    val ink = menuInk()
    AnimatedVisibility(
        controller.iconPicker != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        val app = rememberLatest(controller.iconPicker) ?: return@AnimatedVisibility
        val pack = LiquidApp.instance.iconPack
        val version by pack.version.collectAsState()
        val current = remember(version, app.id) { pack.choice(app.id) }
        val close = { controller.iconPicker = null }
        val choose = { icon: PackIcon?, original: Boolean -> pack.set(app.id, icon, original); close() }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)).pointerInput(Unit) { detectTapGestures { close() } },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Glass(Modifier.fillMaxWidth().fillMaxHeight(0.74f).pointerInput(Unit) { detectTapGestures { } }, corner = 38.dp, opacity = 0.42f) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    item(span = { GridItemSpan(4) }) {
                        Column {
                            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(width = 38.dp, height = 5.dp).background(ink.copy(alpha = 0.25f), CircleShape))
                            }
                            Text("Change Icon", style = TextStyle(fontFamily = Inter, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ink))
                            Text(app.label, style = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = ink.copy(alpha = 0.6f)))
                        }
                    }
                    item { PickerTile("Automatic", current == null, ink, onClick = { choose(null, false) }) { AppIconImage(app, it, plate = false) } }
                    item {
                        PickerTile("Original", current == IconPack.ORIGINAL, ink, onClick = { choose(null, true) }) { size ->
                            val bmp = rememberOriginalIcon(app, size)
                            bmp?.let { Image(it, null, Modifier.size(size)) }
                        }
                    }
                    items(PackIcon.entries) { icon ->
                        PickerTile(icon.title, current == icon.name, ink, onClick = { choose(icon, false) }) { size ->
                            val bmp = rememberPackIcon(icon, size)
                            bmp?.let { Image(it, null, Modifier.size(size)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTile(title: String, selected: Boolean, ink: Color, onClick: () -> Unit, icon: @Composable (Dp) -> Unit) {
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.border(if (selected) 3.dp else 0.dp, if (selected) IOS.Blue else Color.Transparent, RoundedCornerShape(20.dp)).padding(5.dp)
        ) { icon(58.dp) }
        Spacer(Modifier.height(4.dp))
        Text(title, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = ink, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun rememberPackIcon(icon: PackIcon, size: Dp): ImageBitmap? {
    val s = LocalHome.current.settings
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp by produceState<ImageBitmap?>(null, icon, s.iconStyle, px, s.tint) {
        value = LiquidApp.instance.icons.packIcon(icon, s.iconStyle, px, s.tint)
    }
    return bmp
}

@Composable
private fun rememberOriginalIcon(app: AppEntry, size: Dp): ImageBitmap? {
    val s = LocalHome.current.settings
    val density = LocalDensity.current
    val px = with(density) { size.roundToPx() }
    val dpi = (density.density * 160).toInt()
    val bmp by produceState<ImageBitmap?>(null, app.id, s.iconStyle, px) {
        val d = runCatching { app.iconLoader(dpi) }.getOrNull() ?: return@produceState
        value = LiquidApp.instance.icons.plain(d, px, s.iconStyle, s.tint)
    }
    return bmp
}

/** iOS-style rename alert for an app's Home Screen name. */
@Composable
fun RenameDialog(controller: HomeController) {
    val ink = menuInk()
    AnimatedVisibility(controller.renameApp != null, enter = fadeIn() + scaleIn(initialScale = 1.1f), exit = fadeOut() + scaleOut(targetScale = 0.95f)) {
        val app = rememberLatest(controller.renameApp) ?: return@AnimatedVisibility
        val repo = LiquidApp.instance.apps
        var text by remember(app.id) { mutableStateOf(app.label) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(app.id) { runCatching { focus.requestFocus() } }
        val close = { controller.renameApp = null }
        val save = { repo.setLabel(app.id, text); close() }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).imePadding().pointerInput(Unit) { detectTapGestures { close() } },
            contentAlignment = Alignment.Center,
        ) {
            Glass(Modifier.width(300.dp).pointerInput(Unit) { detectTapGestures { } }, corner = 34.dp, opacity = 0.5f) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIconImage(app, 56.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("Rename", style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink))
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.take(30) },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = ink),
                        cursorBrush = SolidColor(IOS.Blue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                        modifier = Modifier.fillMaxWidth().background(ink.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp).focusRequester(focus),
                    )
                    Spacer(Modifier.height(16.dp))
                    AlertButton("Save", IOS.Blue, ink, bold = true, onClick = save)
                    if (repo.customLabel(app.id) != null) {
                        Spacer(Modifier.height(8.dp))
                        AlertButton("Use Original Name", IOS.Red, ink) { repo.setLabel(app.id, null); close() }
                    }
                    Spacer(Modifier.height(8.dp))
                    AlertButton("Cancel", null, ink, onClick = close)
                }
            }
        }
    }
}

@Composable
private fun AlertButton(label: String, color: Color?, ink: Color, bold: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).background(ink.copy(alpha = 0.08f), RoundedCornerShape(50))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium, color = color ?: ink))
        }
    }
}
