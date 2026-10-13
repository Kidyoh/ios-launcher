package dev.kidyoh.liquid.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IconStyle(val label: String) { Default("Default"), Dark("Dark"), Tinted("Tinted"), Clear("Clear") }
enum class ThemeMode(val label: String) { Auto("Automatic"), Light("Light"), Dark("Dark") }
enum class SwipeDownAction(val label: String) { Search("Search"), Notifications("Notifications") }

data class LauncherSettings(
    val iconStyle: IconStyle = IconStyle.Default,
    /** iOS 27-style Liquid Glass slider: 0 = fully clear, 1 = fully tinted. */
    val glass: Float = 0.35f,
    val theme: ThemeMode = ThemeMode.Auto,
    /** "preset:<id>", "photo" or "system". */
    val wallpaper: String = "preset:aurora",
    val dimWallpaperInDark: Boolean = true,
    val columns: Int = 4,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val showSearchPill: Boolean = true,
    val addNewApps: Boolean = true,
    val haptics: Boolean = true,
    val badges: Boolean = true,
    val swipeDown: SwipeDownAction = SwipeDownAction.Search,
    val tint: Int = 0xFFFFB340.toInt(),
    val onboarded: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LauncherSettings> = _state.asStateFlow()
    val value: LauncherSettings get() = _state.value

    fun update(block: (LauncherSettings) -> LauncherSettings) {
        val next = block(_state.value)
        if (next == _state.value) return
        _state.value = next
        save(next)
    }

    private fun load(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            iconStyle = enumOr(prefs.getString("iconStyle", null), d.iconStyle),
            glass = prefs.getFloat("glass", d.glass),
            theme = enumOr(prefs.getString("theme", null), d.theme),
            wallpaper = prefs.getString("wallpaper", d.wallpaper) ?: d.wallpaper,
            dimWallpaperInDark = prefs.getBoolean("dimWallpaperInDark", d.dimWallpaperInDark),
            columns = prefs.getInt("columns", d.columns),
            iconScale = prefs.getFloat("iconScale", d.iconScale),
            showLabels = prefs.getBoolean("showLabels", d.showLabels),
            showSearchPill = prefs.getBoolean("showSearchPill", d.showSearchPill),
            addNewApps = prefs.getBoolean("addNewApps", d.addNewApps),
            haptics = prefs.getBoolean("haptics", d.haptics),
            badges = prefs.getBoolean("badges", d.badges),
            swipeDown = enumOr(prefs.getString("swipeDown", null), d.swipeDown),
            tint = prefs.getInt("tint", d.tint),
            onboarded = prefs.getBoolean("onboarded", d.onboarded),
        )
    }

    private fun save(s: LauncherSettings) = prefs.edit {
        putString("iconStyle", s.iconStyle.name)
        putFloat("glass", s.glass)
        putString("theme", s.theme.name)
        putString("wallpaper", s.wallpaper)
        putBoolean("dimWallpaperInDark", s.dimWallpaperInDark)
        putInt("columns", s.columns)
        putFloat("iconScale", s.iconScale)
        putBoolean("showLabels", s.showLabels)
        putBoolean("showSearchPill", s.showSearchPill)
        putBoolean("addNewApps", s.addNewApps)
        putBoolean("haptics", s.haptics)
        putBoolean("badges", s.badges)
        putString("swipeDown", s.swipeDown.name)
        putInt("tint", s.tint)
        putBoolean("onboarded", s.onboarded)
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
