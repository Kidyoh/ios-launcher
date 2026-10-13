package dev.kidyoh.liquid.ui.island

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.ui.theme.LiquidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Shows the Dynamic Island over every app, using a small "display over other apps" window that
 * sits around the front camera and resizes with the island.
 */
object IslandOverlay {
    private val _showing = MutableStateFlow(false)
    val showing: StateFlow<Boolean> = _showing.asStateFlow()

    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var island: IslandController? = null
    private var scope: CoroutineScope? = null

    fun canShow(context: Context) = Settings.canDrawOverlays(context)

    /** Adds or removes the overlay to match the settings and permission. */
    fun sync(context: Context) {
        val s = LiquidApp.instance.settings.value
        val want = s.dynamicIsland && s.islandEverywhere && canShow(context)
        if (want && view == null) runCatching { show(context.applicationContext) }.onFailure { hide(context) }
        if (!want && view != null) hide(context)
    }

    private fun show(ctx: Context) {
        val wm = ctx.getSystemService(WindowManager::class.java)
        val density = ctx.resources.displayMetrics.density
        val (screenW, center) = geometry(ctx, wm)
        val sc = MainScope().also { scope = it }
        val ic = IslandController(ctx, sc).also { it.start(); island = it }
        val ow = OverlayOwner().also { it.start(); owner = it }
        val maxWidthDp = (screenW / density - 20f).dp
        val cv = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(ow)
            setViewTreeSavedStateRegistryOwner(ow)
            setContent { LiquidTheme(dark = true) { OverlayIsland(ic, maxWidthDp) } }
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) ic.collapse()
                false
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (center.first - screenW / 2f).roundToInt()
            y = (center.second - ISLAND_HEIGHT * density / 2f).roundToInt().coerceAtLeast((4 * density).roundToInt())
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "Dynamic Island"
        }
        wm.addView(cv, params)
        view = cv
        _showing.value = true
    }

    fun hide(context: Context) {
        view?.let { v -> runCatching { context.applicationContext.getSystemService(WindowManager::class.java).removeView(v) } }
        view = null
        island?.stop(); island = null
        owner?.stop(); owner = null
        scope?.cancel(); scope = null
        _showing.value = false
    }

    /** Screen width and the camera hole's centre (or the middle of the status bar). */
    private fun geometry(ctx: Context, wm: WindowManager): Pair<Int, Pair<Float, Float>> {
        if (Build.VERSION.SDK_INT >= 30) {
            val m = wm.currentWindowMetrics
            val w = m.bounds.width()
            val r = m.windowInsets.displayCutout?.boundingRects?.firstOrNull { it.top < m.bounds.height() / 10 }
            if (r != null) return w to (r.exactCenterX() to r.exactCenterY())
            val bar = m.windowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            return w to (w / 2f to bar / 2f)
        }
        val w = ctx.resources.displayMetrics.widthPixels
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (id > 0) ctx.resources.getDimensionPixelSize(id) else (24 * ctx.resources.displayMetrics.density).toInt()
        return w to (w / 2f to bar / 2f)
    }
}

@Composable
private fun OverlayIsland(island: IslandController, maxWidth: androidx.compose.ui.unit.Dp) {
    // Like iOS, the island steps aside in landscape (videos, games).
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) return
    DynamicIsland(island, maxWidth)
}

/** Minimal lifecycle so Compose can run inside a window that isn't an Activity. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
