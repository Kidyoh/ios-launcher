package dev.kidyoh.liquid.ui.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.BatteryManager
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import dev.kidyoh.liquid.system.Badges
import dev.kidyoh.liquid.system.MediaWatcher
import dev.kidyoh.liquid.system.NotificationItem
import dev.kidyoh.liquid.system.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** What the island is showing. */
sealed interface IslandMode {
    data object Idle : IslandMode
    data class Media(val np: NowPlaying, val accent: Color) : IslandMode
    data class Charging(val level: Int, val charging: Boolean) : IslandMode
    data class Note(val item: NotificationItem) : IslandMode
    data class Ringer(val silent: Boolean) : IslandMode
}

/**
 * Drives the Dynamic Island: short "alerts" (a notification, plugging in, the silent switch) pop out
 * for a few seconds, otherwise it shows what's playing, otherwise it's the plain black pill.
 */
@Stable
class IslandController(private val context: Context, private val scope: CoroutineScope) {
    var mode by mutableStateOf<IslandMode>(IslandMode.Idle)
        private set
    var expanded by mutableStateOf(false)
        private set

    private val media = MediaWatcher(context)
    private var alert: IslandMode? = null
    private var alertJob: Job? = null
    private var accentFor: Bitmap? = null
    private var accent = Color(0xFFFF375F)
    private var lastCharging: Boolean? = null
    private var jobs = mutableListOf<Job>()
    private var collapseJob: Job? = null
    private var pausedSince = 0L
    private var pauseJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                    val bm = c.getSystemService(BatteryManager::class.java)
                    val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
                    val charging = i.action == Intent.ACTION_POWER_CONNECTED
                    if (lastCharging != charging) {
                        lastCharging = charging
                        show(IslandMode.Charging(level, charging), 3500)
                    }
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    // This broadcast is sticky: ignore the copy Android replays when we register.
                    if (isInitialStickyBroadcast) return
                    val m = i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                    show(IslandMode.Ringer(silent = m != AudioManager.RINGER_MODE_NORMAL), 2200)
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        media.start()
        jobs += scope.launch { media.state.collect { refresh() } }
        // New notifications pop out of the island (skipping the ones already there when we started).
        jobs += scope.launch {
            var seen = Badges.items.value.map { it.key }.toSet()
            Badges.items.drop(1).collect { list ->
                val fresh = list.firstOrNull { it.key !in seen && System.currentTimeMillis() - it.time < 15_000 }
                seen = list.map { it.key }.toSet()
                if (fresh != null) show(IslandMode.Note(fresh), 4200)
            }
        }
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        media.stop()
        jobs.forEach { it.cancel() }
        jobs.clear()
        alertJob?.cancel()
        collapseJob?.cancel()
        pauseJob?.cancel()
    }

    /** Tap: media expands / collapses. */
    fun tap() {
        if (expanded) collapse() else expand()
    }

    fun expand() {
        if (mode !is IslandMode.Media) return
        expanded = true
        interacted()
    }

    fun collapse() {
        expanded = false
        collapseJob?.cancel()
    }

    /** Any touch on the island keeps it open a little longer; left alone it closes itself. */
    fun interacted() {
        collapseJob?.cancel()
        if (!expanded) return
        collapseJob = scope.launch {
            delay(AUTO_COLLAPSE_MS)
            expanded = false
        }
    }

    /** Swipe up on a pop-out alert puts it away early. */
    fun dismissAlert() {
        if (alert == null) { collapse(); return }
        alertJob?.cancel()
        alert = null
        refresh()
    }

    /** Screenshot tests: show a given state directly. */
    fun showForTest(m: IslandMode, expand: Boolean = false) {
        alertJob?.cancel()
        alert = null
        mode = m
        expanded = expand && m is IslandMode.Media
    }

    fun show(m: IslandMode, millis: Long) {
        alert = m
        collapse()
        refresh()
        alertJob?.cancel()
        alertJob = scope.launch {
            delay(millis)
            alert = null
            refresh()
        }
    }

    private fun refresh() {
        val np = media.state.value
        mode = alert ?: if (np.active && !pausedTooLong(np)) IslandMode.Media(np, accentOf(np.art)) else IslandMode.Idle
        if (mode !is IslandMode.Media) collapse()
    }

    /** Paused music keeps its place for a while, then the island closes, like an iOS Live Activity. */
    private fun pausedTooLong(np: NowPlaying): Boolean {
        if (np.playing || !np.active) {
            pausedSince = 0L
            pauseJob?.cancel()
            return false
        }
        val now = System.currentTimeMillis()
        if (pausedSince == 0L) {
            pausedSince = now
            pauseJob?.cancel()
            pauseJob = scope.launch {
                delay(PAUSED_LINGER_MS + 50)
                refresh()
            }
        }
        return now - pausedSince >= PAUSED_LINGER_MS
    }

    companion object {
        const val AUTO_COLLAPSE_MS = 6_000L
        const val PAUSED_LINGER_MS = 30_000L
    }

    private fun accentOf(art: Bitmap?): Color {
        if (art == null) return Color(0xFFFF375F)
        if (art === accentFor) return accent
        accentFor = art
        val px = runCatching { art.scale(1, 1, filter = true).getPixel(0, 0) }.getOrDefault(0xFFFF375F.toInt())
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(px, it) }
        hsl[1] = hsl[1].coerceAtLeast(0.55f); hsl[2] = hsl[2].coerceIn(0.55f, 0.7f)
        accent = Color(ColorUtils.HSLToColor(hsl))
        return accent
    }
}
