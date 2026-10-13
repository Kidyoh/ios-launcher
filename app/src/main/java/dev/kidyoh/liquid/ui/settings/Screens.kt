package dev.kidyoh.liquid.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.kidyoh.liquid.BuildConfig
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.IconStyle
import dev.kidyoh.liquid.data.LauncherSettings
import dev.kidyoh.liquid.data.SwipeDownAction
import dev.kidyoh.liquid.data.ThemeMode
import dev.kidyoh.liquid.ui.glass.GlassEnv
import dev.kidyoh.liquid.ui.glass.LocalGlass
import dev.kidyoh.liquid.ui.home.AppIconImage
import dev.kidyoh.liquid.ui.home.HomeEnv
import dev.kidyoh.liquid.ui.home.LocalHome
import dev.kidyoh.liquid.ui.theme.Glyphs
import dev.kidyoh.liquid.ui.theme.IOS
import dev.kidyoh.liquid.ui.theme.Inter
import dev.kidyoh.liquid.ui.theme.LocalPalette
import dev.kidyoh.liquid.ui.theme.isDark
import dev.kidyoh.liquid.wallpaper.Presets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun noRipple() = MutableInteractionSource()

/** Everything the Settings app needs from its host activity. */
class SettingsHost(
    val isDefault: Boolean,
    val badgeAccess: Boolean,
    val onMakeDefault: () -> Unit,
    val onBadgeAccess: () -> Unit,
    val onFinishOnboarding: () -> Unit,
    val overlayAccess: Boolean = false,
    val onOverlayAccess: () -> Unit = {},
)

@Composable
fun SettingsApp(start: String?, host: SettingsHost) {
    val app = LiquidApp.instance
    val settings by app.settings.state.collectAsState()
    val first = when {
        start != null -> start
        !settings.onboarded -> "welcome"
        else -> "root"
    }
    var stack by remember { mutableStateOf(listOf(first)) }
    val push = { s: String -> stack = stack + s }
    val pop = { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(stack.size > 1) { pop() }

    val apps by app.apps.byId.collectAsState()
    val wallpaper by app.wallpaper.state.collectAsState()
    val dark = settings.theme.isDark()
    CompositionLocalProvider(
        LocalHome provides HomeEnv(settings, apps, emptyMap(), 48.dp, dark),
        LocalGlass provides GlassEnv(wallpaper, dark, settings.glass),
    ) {
        AnimatedContent(
            targetState = stack,
            transitionSpec = {
                val forward = targetState.size >= initialState.size
                (slideInHorizontally(tween(320)) { if (forward) it else -it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(320)) { if (forward) -it / 3 else it } + fadeOut(tween(320)))
            },
            label = "nav",
        ) { s ->
            when (s.last()) {
                "welcome" -> Onboarding(host.isDefault, onSetDefault = host.onMakeDefault, onSkip = {
                    app.settings.update { it.copy(onboarded = true) }
                    stack = listOf("root")
                })
                "appearance" -> AppearanceScreen(settings, canGoBack = s.size > 1, onBack = pop)
                "wallpaper" -> WallpaperScreen(settings, canGoBack = s.size > 1, onBack = pop)
                "home" -> HomeLayoutScreen(settings, onBack = pop)
                else -> RootScreen(settings, host, push)
            }
        }
    }
}

@Composable
private fun Screen(title: String, back: String? = null, onBack: () -> Unit = {}, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.groupedBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 30.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
            if (back != null) {
                // iOS 26: a round glass back button instead of "< Settings".
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(46.dp)
                        .shadow(if (p.dark) 0.dp else 6.dp, CircleShape)
                        .background(if (p.dark) Color(0xFF2C2C2E) else Color.White, CircleShape)
                        .border(0.6.dp, Color.White.copy(alpha = if (p.dark) 0.12f else 0.6f), CircleShape)
                        .clickable(interactionSource = remember { noRipple() }, indication = null, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Glyphs.ChevronLeft, back, tint = p.label, modifier = Modifier.size(24.dp))
                }
            }
        }
        LargeTitle(title)
        content()
    }
}

@Composable
private fun RootScreen(s: LauncherSettings, host: SettingsHost, push: (String) -> Unit) {
    val app = LiquidApp.instance
    val p = LocalPalette.current
    var confirmReset by remember { mutableStateOf(false) }
    Box {
        Screen("Settings") {
            // Header card, laid out like an iOS 26 Settings detail page.
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(p.card, RoundedCornerShape(30.dp)).padding(20.dp),
            ) {
                Box(
                    Modifier.size(64.dp).clip(dev.kidyoh.liquid.icons.SquircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF4FACFE), Color(0xFF6A5BFF), Color(0xFFE160C9)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Glyphs.Grid, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                Spacer(Modifier.height(14.dp))
                Text("Liquid Launcher", style = TextStyle(fontFamily = Inter, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = p.label))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your Home Screen, made of Liquid Glass: widgets, folders, App Library, Notification Center and Control Center.",
                    style = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = p.secondaryLabel),
                )
                Spacer(Modifier.height(16.dp))
                RowDivider(0.dp)
                Spacer(Modifier.height(14.dp))
                if (host.isDefault) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Default Home app", style = TextStyle(fontFamily = Inter, fontSize = 17.sp, color = p.label), modifier = Modifier.weight(1f))
                        Icon(Glyphs.Check, null, tint = p.accent, modifier = Modifier.size(20.dp))
                    }
                } else {
                    PrimaryButton("Set as Default Home App", Modifier.fillMaxWidth(), onClick = host.onMakeDefault)
                }
            }

            Section("Appearance") {
                SettingRow("Wallpaper", Glyphs.Photo, IOS.Cyan, onClick = { push("wallpaper") })
                RowDivider()
                SettingRow("Icons & Liquid Glass", Glyphs.Glass, IOS.Indigo, value = s.iconStyle.label, onClick = { push("appearance") })
                RowDivider()
                SettingRow("Appearance", Glyphs.Moon, IOS.Indigo, chevron = false)
                Segmented(ThemeMode.entries, s.theme, { it.label }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { m ->
                    app.settings.update { it.copy(theme = m) }
                }
            }

            Section(
                "Dynamic Island",
                footer = if (s.islandEverywhere && !host.overlayAccess) "To show the island over other apps, allow \"Display over other apps\". Music and notifications in the island need notification access."
                else "Opens around your front camera for music, charging, the silent switch and new notifications, then closes by itself. Tap or swipe down on music for controls; swipe up to put it away.",
            ) {
                ToggleRow("Dynamic Island", s.dynamicIsland, Glyphs.Sparkle, Color(0xFF1C1C1E).takeIf { !p.dark } ?: IOS.Indigo) { v ->
                    app.settings.update { it.copy(dynamicIsland = v) }
                }
                if (s.dynamicIsland) {
                    RowDivider()
                    ToggleRow("Hide When Idle", s.islandHideIdle, Glyphs.Focus, IOS.Gray) { v ->
                        app.settings.update { it.copy(islandHideIdle = v) }
                    }
                    RowDivider()
                    ToggleRow("Show Over All Apps", s.islandEverywhere && host.overlayAccess, Glyphs.Mirror, IOS.Blue) { v ->
                        if (v && !host.overlayAccess) host.onOverlayAccess()
                        app.settings.update { it.copy(islandEverywhere = v) }
                    }
                }
            }

            Section("Home Screen") {
                SettingRow("Layout", Glyphs.Grid, IOS.Blue, value = "${s.columns} × 6", onClick = { push("home") })
                RowDivider()
                ToggleRow("Show Search on Home Screen", s.showSearchPill, Glyphs.Search, IOS.Gray) { v -> app.settings.update { it.copy(showSearchPill = v) } }
                RowDivider()
                ToggleRow("Add New Apps to Home Screen", s.addNewApps, Glyphs.Plus, IOS.Green) { v -> app.settings.update { it.copy(addNewApps = v) } }
            }

            Section("Notifications", footer = if (host.badgeAccess) null else "Badges need notification access so Liquid Launcher can count unread notifications. Nothing leaves your phone.") {
                ToggleRow("Badges", s.badges && host.badgeAccess, Glyphs.Bell, IOS.Red) { v ->
                    if (v && !host.badgeAccess) host.onBadgeAccess()
                    app.settings.update { it.copy(badges = v) }
                }
                if (!host.badgeAccess) {
                    RowDivider()
                    SettingRow("Allow Notification Access", titleColor = p.accent, onClick = host.onBadgeAccess)
                }
            }

            Section("Gestures", footer = "Swipe down from the top-left for Notification Center and from the top-right for Control Center, like iPhone.") {
                SettingRow("Swipe Down in the Middle", Glyphs.ArrowDown, IOS.Orange, chevron = false)
                Segmented(SwipeDownAction.entries, s.swipeDown, { it.label }, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) { a ->
                    app.settings.update { it.copy(swipeDown = a) }
                }
                RowDivider()
                ToggleRow("Haptic Feedback", s.haptics, Glyphs.Wave, IOS.Pink) { v -> app.settings.update { it.copy(haptics = v) } }
            }

            Section {
                SettingRow("Reset Home Screen Layout", titleColor = IOS.Red, chevron = false, onClick = { confirmReset = true })
            }
            Section("About") {
                SettingRow("Version", value = BuildConfig.VERSION_NAME, chevron = false)
            }
        }
        if (confirmReset) {
            SimpleAlert(
                "Reset Home Screen?",
                "Your Home Screen will go back to the default layout. Apps stay installed.",
                confirm = "Reset",
                onConfirm = { app.layout.reset(); confirmReset = false },
                onDismiss = { confirmReset = false },
            )
        }
    }
}

@Composable
private fun SimpleAlert(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { noRipple() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(290.dp).background(p.card, RoundedCornerShape(30.dp))
                .clickable(interactionSource = remember { noRipple() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.label))
            Spacer(Modifier.height(6.dp))
            Text(message, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = p.secondaryLabel, textAlign = TextAlign.Center))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(46.dp).background(p.fill, RoundedCornerShape(50))
                        .clickable(interactionSource = remember { noRipple() }, indication = null, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = p.label)) }
                Box(
                    Modifier.weight(1f).height(46.dp).background(IOS.Red, RoundedCornerShape(50))
                        .clickable(interactionSource = remember { noRipple() }, indication = null, onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) { Text(confirm, style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)) }
            }
        }
    }
}

/** A tiny live Home Screen: wallpaper, icons in the current style and a glass dock. */
@Composable
fun PhonePreview(width: Dp, s: LauncherSettings, modifier: Modifier = Modifier) {
    val wp by LiquidApp.instance.wallpaper.state.collectAsState()
    val all by LiquidApp.instance.apps.apps.collectAsState()
    val dark = LocalHome.current.dark
    val height = width * 2.05f
    val icon = width * 0.16f
    val sample = remember(all) { all.take(12) }
    Box(
        modifier
            .size(width, height)
            .clip(RoundedCornerShape(width * 0.14f))
            .border(5.dp, Color(0xFF1C1C1E), RoundedCornerShape(width * 0.14f))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val img = wp.image
            if (img != null) {
                drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            } else {
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3D5AFE), Color(0xFF8E24AA))))
            }
            if (dark && s.dimWallpaperInDark) drawRect(Color.Black.copy(alpha = 0.22f))
            // Glass dock
            val dh = icon.toPx() + 14.dp.toPx()
            val r = RoundRect(8.dp.toPx(), size.height - dh - 10.dp.toPx(), size.width - 8.dp.toPx(), size.height - 10.dp.toPx(), CornerRadius(18.dp.toPx()))
            val path = Path().apply { addRoundRect(r) }
            clipPath(path) {
                wp.blurred?.let { b ->
                    drawImage(b, srcOffset = IntOffset.Zero, srcSize = IntSize(b.width, b.height), dstSize = IntSize(size.width.toInt(), size.height.toInt()), alpha = lerp(0.62f, 1f, s.glass))
                }
                val tint = if (dark) Color(0xFF16161A) else Color(0xFFF7F7FA)
                drawRect(tint.copy(alpha = lerp(if (dark) 0.1f else 0.14f, if (dark) 0.52f else 0.62f, s.glass)))
            }
            drawRoundRect(Color.White.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(r.left, r.top), Size(r.width, r.height), CornerRadius(18.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        val gap = (width - icon * 4) / 5
        sample.take(8).forEachIndexed { i, a ->
            AppIconImage(a, icon, Modifier.offset(gap + (icon + gap) * (i % 4), 34.dp + (icon + 14.dp) * (i / 4)), plate = s.iconStyle == IconStyle.Clear)
        }
        sample.drop(8).take(4).forEachIndexed { i, a ->
            AppIconImage(a, icon, Modifier.align(Alignment.BottomStart).offset(gap + (icon + gap) * i, -(17.dp)), plate = s.iconStyle == IconStyle.Clear)
        }
    }
}

@Composable
private fun AppearanceScreen(s: LauncherSettings, canGoBack: Boolean, onBack: () -> Unit) {
    val app = LiquidApp.instance
    val p = LocalPalette.current
    Screen("Icons & Glass", back = if (canGoBack) "Settings" else null, onBack = onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            PhonePreview(170.dp, s)
        }
        Section(footer = "Gives Phone, Messages, your browser, Settings, Camera, Photos and other built-in apps an iPhone-style icon. Long-press any app and choose Change Icon… to pick one yourself.") {
            ToggleRow("iPhone-Style Icons", s.iosIcons, Glyphs.Grid, IOS.Blue) { v -> app.settings.update { it.copy(iosIcons = v) } }
        }
        Section("Icon Style", footer = "Dark puts each icon on black. Tinted turns icons into one colour. Clear makes them frosted glass.") {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconStyle.entries.forEach { style ->
                    val selected = s.iconStyle == style
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(interactionSource = remember { noRipple() }, indication = null) {
                            app.settings.update { it.copy(iconStyle = style) }
                        },
                    ) {
                        StyleSwatch(style, s.tint, selected)
                        Spacer(Modifier.height(6.dp))
                        Text(style.label, style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) p.accent else p.label))
                    }
                }
            }
            if (s.iconStyle == IconStyle.Tinted) {
                RowDivider(16.dp)
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(0xFFFFB340, 0xFFFF6B6B, 0xFFFF7EB6, 0xFFB18CFF, 0xFF64B5FF, 0xFF4CD9A0, 0xFFE8E8ED).forEach { c ->
                        val color = Color(c)
                        val on = s.tint == c.toInt()
                        Box(
                            Modifier.size(30.dp)
                                .border(if (on) 2.5.dp else 0.dp, if (on) p.label else Color.Transparent, CircleShape)
                                .padding(4.dp)
                                .background(color, CircleShape)
                                .clickable(interactionSource = remember { noRipple() }, indication = null) { app.settings.update { it.copy(tint = c.toInt()) } }
                        )
                    }
                }
            }
        }
        Section("Liquid Glass", footer = "Like iOS 27: slide left for a clearer look, or right for more tint and easier reading.") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Clear", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = p.secondaryLabel))
                Spacer(Modifier.weight(1f))
                Text("Tinted", style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = p.secondaryLabel))
            }
            IosSlider(s.glass, { v -> app.settings.update { it.copy(glass = v) } }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            Spacer(Modifier.height(8.dp))
        }
        Section("Wallpaper") {
            ToggleRow("Dark Appearance Dims Wallpaper", s.dimWallpaperInDark, Glyphs.Moon, IOS.Indigo) { v ->
                app.settings.update { it.copy(dimWallpaperInDark = v) }
            }
        }
    }
}

@Composable
private fun StyleSwatch(style: IconStyle, tint: Int, selected: Boolean) {
    val p = LocalPalette.current
    val bg = when (style) {
        IconStyle.Default -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF5AC8FA), Color(0xFF007AFF)))
        IconStyle.Dark -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF0E0E10)))
        IconStyle.Tinted -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2A2A2E), Color(0xFF0B0B0D)))
        IconStyle.Clear -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x22FFFFFF)))
    }
    val glyph = when (style) {
        IconStyle.Default -> Color.White
        IconStyle.Dark -> Color(0xFF5AC8FA)
        IconStyle.Tinted -> Color(tint)
        IconStyle.Clear -> Color.White
    }
    Box(
        Modifier.size(58.dp)
            .border(if (selected) 2.5.dp else 0.dp, if (selected) p.accent else Color.Transparent, RoundedCornerShape(18.dp))
            .padding(4.dp)
            .clip(dev.kidyoh.liquid.icons.SquircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF6A5BFF), Color(0xFFE160C9))))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(Glyphs.Sparkle, null, tint = glyph, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun WallpaperScreen(s: LauncherSettings, canGoBack: Boolean, onBack: () -> Unit) {
    val app = LiquidApp.instance
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dm = ctx.resources.displayMetrics
    val dark = LocalHome.current.dark
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = app.wallpaper.setPhoto(uri, dm.widthPixels, dm.heightPixels)
            if (!ok) Toast.makeText(ctx, "Couldn't use that photo", Toast.LENGTH_SHORT).show()
        }
    }
    val useSystem = { app.settings.update { it.copy(wallpaper = "system") } }
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { useSystem() }
    Screen("Wallpaper", back = if (canGoBack) "Settings" else null, onBack = onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { PhonePreview(170.dp, s) }
        Section("Collections") {
            val density = LocalDensity.current
            Column(Modifier.padding(12.dp)) {
                Presets.all.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { preset ->
                            val thumb by produceState<ImageBitmap?>(null, preset.id, dark) {
                                value = withContext(Dispatchers.Default) {
                                    with(density) { Presets.render(preset, 80.dp.roundToPx(), 170.dp.roundToPx(), dark).asImageBitmap() }
                                }
                            }
                            val selected = s.wallpaper == "preset:${preset.id}"
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(0.47f)
                                        .border(if (selected) 3.dp else 0.dp, if (selected) LocalPalette.current.accent else Color.Transparent, RoundedCornerShape(14.dp))
                                        .padding(if (selected) 4.dp else 0.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(Color.Gray.copy(alpha = 0.2f))
                                        .clickable(interactionSource = remember { noRipple() }, indication = null) {
                                            app.settings.update { it.copy(wallpaper = "preset:${preset.id}") }
                                        }
                                ) { thumb?.let { Image(it, preset.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                                Spacer(Modifier.height(4.dp))
                                Text(preset.name, style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = LocalPalette.current.secondaryLabel))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Section(footer = "System wallpaper uses whatever you set in Android. Live wallpapers show through, but the glass can't blur them.") {
            SettingRow("Choose a Photo…", Glyphs.Photo, IOS.Blue, onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            RowDivider()
            SettingRow("Use System Wallpaper", Glyphs.Gear, IOS.Gray, value = if (s.wallpaper == "system") "On" else null, onClick = {
                val needsPermission = Build.VERSION.SDK_INT < 33 &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storage.launch(Manifest.permission.READ_EXTERNAL_STORAGE) else useSystem()
            })
        }
        Section(footer = "Also uses this wallpaper on the Lock Screen, so unlocking flows straight into your Home Screen.") {
            SettingRow("Set as Lock Screen Too", Glyphs.Lock, IOS.Indigo, chevron = false, onClick = {
                scope.launch {
                    val ok = app.wallpaper.applyToSystem(lock = true)
                    Toast.makeText(ctx, if (ok) "Wallpaper set" else "Open the Home Screen once, then try again", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}

@Composable
private fun HomeLayoutScreen(s: LauncherSettings, onBack: () -> Unit) {
    val app = LiquidApp.instance
    val p = LocalPalette.current
    Screen("Layout", back = "Settings", onBack = onBack) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { PhonePreview(150.dp, s) }
        Section("Grid", footer = "iPhone uses 4 columns. 5 fits more apps on each page.") {
            Segmented(listOf(4, 5), s.columns, { "$it × 6" }, Modifier.padding(16.dp)) { c -> app.settings.update { it.copy(columns = c) } }
        }
        Section("Icon Size") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.Grid, null, tint = p.secondaryLabel, modifier = Modifier.size(14.dp))
                IosSlider(
                    ((s.iconScale - 0.85f) / 0.3f).coerceIn(0f, 1f),
                    { v -> app.settings.update { it.copy(iconScale = 0.85f + v * 0.3f) } },
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Icon(Glyphs.Grid, null, tint = p.secondaryLabel, modifier = Modifier.size(22.dp))
            }
        }
        Section {
            ToggleRow("Show App Names", s.showLabels, Glyphs.Text, IOS.Gray) { v -> app.settings.update { it.copy(showLabels = v) } }
        }
    }
}

