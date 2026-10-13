package dev.kidyoh.liquid

import android.app.Application
import dev.kidyoh.liquid.data.AppRepository
import dev.kidyoh.liquid.data.LayoutRepository
import dev.kidyoh.liquid.data.SettingsStore
import dev.kidyoh.liquid.data.UsageTracker
import dev.kidyoh.liquid.icons.IconPack
import dev.kidyoh.liquid.icons.IconRenderer
import dev.kidyoh.liquid.system.WeatherRepository
import dev.kidyoh.liquid.wallpaper.WallpaperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.kidyoh.liquid.ui.island.IslandOverlay

class LiquidApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: SettingsStore private set
    lateinit var apps: AppRepository private set
    lateinit var layout: LayoutRepository private set
    lateinit var usage: UsageTracker private set
    lateinit var icons: IconRenderer private set
    lateinit var wallpaper: WallpaperRepository private set
    lateinit var weather: WeatherRepository private set
    lateinit var iconPack: IconPack private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        usage = UsageTracker(this)
        icons = IconRenderer(this)
        apps = AppRepository(this, scope)
        layout = LayoutRepository(this, scope, apps, settings)
        wallpaper = WallpaperRepository(this, scope, settings)
        weather = WeatherRepository(this, scope)
        iconPack = IconPack(this, scope, apps, settings)
        icons.pack = iconPack
        scope.launch {
            settings.state.map { Triple(it.dynamicIsland, it.islandEverywhere, it.onboarded) }.distinctUntilChanged().collect {
                IslandOverlay.sync(this@LiquidApp)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_COMPLETE) icons.clear()
    }

    companion object {
        lateinit var instance: LiquidApp
            private set
    }
}
