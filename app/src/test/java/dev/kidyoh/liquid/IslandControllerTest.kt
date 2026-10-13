package dev.kidyoh.liquid

import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kidyoh.liquid.system.NotificationItem
import dev.kidyoh.liquid.system.NowPlaying
import dev.kidyoh.liquid.ui.island.IslandController
import dev.kidyoh.liquid.ui.island.IslandMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class IslandControllerTest {
    private val song = IslandMode.Media(NowPlaying("Song", "Artist", null, true, null), Color.Red)
    private val note = NotificationItem("k", "pkg", "Title", "Text", 0L, null, null, true)

    @Test
    fun expandedIslandClosesItselfWhenLeftAlone() = runTest {
        val ic = IslandController(ApplicationProvider.getApplicationContext(), this)
        ic.showForTest(song)
        ic.expand()
        assertTrue(ic.expanded)
        advanceTimeBy(IslandController.AUTO_COLLAPSE_MS + 100)
        runCurrent()
        assertFalse(ic.expanded)
    }

    @Test
    fun touchingTheIslandKeepsItOpen() = runTest {
        val ic = IslandController(ApplicationProvider.getApplicationContext(), this)
        ic.showForTest(song)
        ic.expand()
        advanceTimeBy(5_000)
        ic.interacted()
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(ic.expanded)
        advanceTimeBy(1_500)
        runCurrent()
        assertFalse(ic.expanded)
    }

    @Test
    fun onlyMusicExpands() = runTest {
        val ic = IslandController(ApplicationProvider.getApplicationContext(), this)
        ic.showForTest(IslandMode.Charging(50, true))
        ic.expand()
        assertFalse(ic.expanded)
    }

    @Test
    fun alertsCloseThemselvesAndCanBeSwipedAway() = runTest {
        val ic = IslandController(ApplicationProvider.getApplicationContext(), this)
        ic.show(IslandMode.Charging(80, true), 3_500)
        assertTrue(ic.mode is IslandMode.Charging)
        advanceTimeBy(3_600)
        runCurrent()
        assertEquals(IslandMode.Idle, ic.mode)

        ic.show(IslandMode.Note(note), 4_200)
        assertTrue(ic.mode is IslandMode.Note)
        ic.dismissAlert()
        assertEquals(IslandMode.Idle, ic.mode)
    }
}
