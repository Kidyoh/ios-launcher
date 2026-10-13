package dev.kidyoh.liquid.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.LauncherSettings
import dev.kidyoh.liquid.ui.settings.SettingsApp
import dev.kidyoh.liquid.ui.settings.SettingsHost
import dev.kidyoh.liquid.ui.theme.LiquidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Settings and onboarding, rendered on the JVM. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val out = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    private fun render(name: String, start: String?, onboarded: Boolean = true, dark: Boolean = false) {
        val app = LiquidApp.instance
        app.settings.update { LauncherSettings(onboarded = onboarded) }
        app.apps.replaceForTest(FakeApps.create())
        app.wallpaper.ensure("preset:aurora", dark, 786, 1704)
        compose.waitUntil(20_000) { ShadowLooper.idleMainLooper(); app.wallpaper.state.value.image != null }
        compose.setContent { Host(start, dark) }
        repeat(6) {
            compose.mainClock.advanceTimeBy(400)
            Thread.sleep(200)
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(File(out, "$name.png").path)
    }

    @Composable
    private fun Host(start: String?, dark: Boolean) {
        LiquidTheme(dark) {
            SettingsApp(start, SettingsHost(isDefault = false, badgeAccess = false, onMakeDefault = {}, onBadgeAccess = {}, onFinishOnboarding = {}))
        }
    }

    @Test
    fun onboarding() = render("13_onboarding", start = null, onboarded = false)
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class SettingsRootScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun root() {
        val app = LiquidApp.instance
        app.settings.update { LauncherSettings(onboarded = true) }
        app.apps.replaceForTest(FakeApps.create())
        compose.setContent {
            LiquidTheme(false) {
                SettingsApp(null, SettingsHost(isDefault = false, badgeAccess = false, onMakeDefault = {}, onBadgeAccess = {}, onFinishOnboarding = {}))
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(System.getProperty("user.dir"), "build/screenshots/14_settings.png").path)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class AppearanceScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun appearance() {
        val app = LiquidApp.instance
        app.settings.update { LauncherSettings(onboarded = true) }
        app.apps.replaceForTest(FakeApps.create())
        app.wallpaper.ensure("preset:aurora", false, 786, 1704)
        compose.waitUntil(20_000) { ShadowLooper.idleMainLooper(); app.wallpaper.state.value.image != null }
        compose.setContent {
            LiquidTheme(false) {
                SettingsApp("appearance", SettingsHost(isDefault = true, badgeAccess = true, onMakeDefault = {}, onBadgeAccess = {}, onFinishOnboarding = {}))
            }
        }
        repeat(5) { compose.mainClock.advanceTimeBy(400); Thread.sleep(200); compose.waitForIdle() }
        compose.onRoot().captureRoboImage(File(System.getProperty("user.dir"), "build/screenshots/15_icons_and_glass.png").path)
    }
}
