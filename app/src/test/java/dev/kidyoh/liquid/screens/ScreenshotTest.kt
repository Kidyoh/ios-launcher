package dev.kidyoh.liquid.screens

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.MainActivity
import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.DragSource
import dev.kidyoh.liquid.data.FolderItem
import dev.kidyoh.liquid.data.IconStyle
import dev.kidyoh.liquid.data.LauncherSettings
import dev.kidyoh.liquid.data.LayoutOps
import dev.kidyoh.liquid.data.ThemeMode
import dev.kidyoh.liquid.ui.home.FolderOpen
import dev.kidyoh.liquid.ui.home.MenuRequest
import dev.kidyoh.liquid.ui.home.RemoveRequest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Renders the launcher on the JVM so it can be looked at without a phone.
 * Run: ./gradlew recordRoborazziDebug --tests '*ScreenshotTest*'   → app/build/screenshots/ (curated copies live in docs/screenshots)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val out = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val app get() = LiquidApp.instance
    private val controller get() = compose.activity.controller!!
    private val fakes = FakeApps.create()
    private fun id(label: String) = fakes.first { it.label == label }.id

    init {
        org.robolectric.shadows.ShadowLog.stream = System.out
    }

    private fun settings(block: (LauncherSettings) -> LauncherSettings) {
        compose.runOnUiThread { app.settings.update { block(LauncherSettings(onboarded = true)) } }
    }

    private fun settle() {
        val want = app.settings.value.wallpaper
        try {
            compose.waitUntil(20_000) {
                // Robolectric's paused main looper doesn't run posted coroutine resumptions on its own.
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                app.wallpaper.state.value.let { it.image != null && it.key.startsWith(want) }
            }
        } catch (e: Throwable) {

            throw AssertionError("wallpaper never became '$want'; state key='${app.wallpaper.state.value.key}' settings=${app.settings.value}", e)
        }
        repeat(6) {
            compose.mainClock.advanceTimeBy(400)
            Thread.sleep(200)
            compose.waitForIdle()
        }
    }

    private fun shot(name: String) {
        settle()
        compose.onRoot().captureRoboImage(File(out, "$name.png").path)
    }

    private fun ui(block: () -> Unit) = compose.runOnUiThread(block)

    @Test
    fun homeScreens() {
        compose.waitUntil(10_000) { app.layout.layout.value != null }
        settings { it }
        ui {
            app.apps.replaceForTest(fakes)
            app.layout.reset()
            app.layout.edit { l ->
                val dock = listOf(id("Phone"), id("Browser"), id("Messages"), id("Music"))
                var next = l.copy(dock = dock)
                dock.forEach { d -> next = next.copy(pages = next.pages.map { p -> p.filter { (it as? AppItem)?.app != d } }) }
                val social = listOf("WhatsApp", "Instagram", "Telegram", "Translate").map(::id)
                social.forEach { s -> next = LayoutOps.removeAppEverywhere(next, s).copy(dock = next.dock) }
                val p0 = next.pages[0].toMutableList()
                p0.add(minOf(10, p0.size), FolderItem("social", "Social", social))
                next.copy(pages = listOf(p0) + next.pages.drop(1))
            }
            app.layout.clearHistory()
        }
        shot("01_home")

        ui { controller.enterEdit() }
        shot("05_edit_mode")
        ui { controller.exitEdit() }

        ui { controller.openFolder = FolderOpen("social", Rect.Zero) }
        shot("06_folder")
        ui { controller.openFolder = null }

        val page0 = app.layout.layout.value!!.pages[0]
        val maps = page0.first { (it as? AppItem)?.app == id("Maps") }
        ui {
            val g = controller.geometry
            controller.menu = MenuRequest(maps, DragSource.Page(0), Rect(g.gridOrigin.x + g.cellW + (g.cellW - g.icon) / 2, g.gridOrigin.y + 3 * g.cellH + g.iconTop, g.gridOrigin.x + g.cellW + (g.cellW + g.icon) / 2, g.gridOrigin.y + 3 * g.cellH + g.iconTop + g.icon), app.apps.byId.value[id("Maps")])
        }
        shot("08_context_menu")
        ui { controller.menu = null }

        ui { controller.removeRequest = RemoveRequest(maps, DragSource.Page(0), app.apps.byId.value[id("Maps")]) }
        shot("09_remove_alert")
        ui { controller.removeRequest = null }

        ui { controller.enterEdit(); controller.widgetGallery = true }
        shot("10_widget_gallery")
        ui { controller.widgetGallery = false; controller.exitEdit() }

        settings { it.copy(theme = ThemeMode.Dark, wallpaper = "preset:ocean") }
        shot("02_home_dark")

        settings { it.copy(iconStyle = IconStyle.Clear, glass = 0.1f, wallpaper = "preset:sunrise") }
        shot("03_home_clear")

        settings { it.copy(iconStyle = IconStyle.Tinted, theme = ThemeMode.Dark, wallpaper = "preset:graphite") }
        shot("04_home_tinted")

        settings { it.copy(iconStyle = IconStyle.Dark, theme = ThemeMode.Dark, wallpaper = "preset:aurora") }
        shot("04b_home_dark_icons")
        settings { it }

        ui { controller.spotlight = true }
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("12*4+2")
        shot("07_spotlight_math")
        ui { controller.spotlight = false }
        settle()
        ui { controller.spotlight = true }
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("m")
        shot("07b_spotlight_apps")
        ui { controller.spotlight = false }

        // Swipe to the App Library (two home pages, then the library).
        repeat(3) { compose.onRoot().performTouchInput { swipeLeft() }; settle() }
        shot("11_app_library")
        repeat(4) { compose.onRoot().performTouchInput { swipeRight() }; settle() }
        shot("12_today_view")
        compose.onRoot().performTouchInput { swipeLeft() }

    }
}
