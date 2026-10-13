package dev.kidyoh.liquid

import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.Cell
import dev.kidyoh.liquid.data.HomeGrid
import dev.kidyoh.liquid.data.HomeLayout
import dev.kidyoh.liquid.data.WidgetItem
import dev.kidyoh.liquid.data.WidgetKind
import dev.kidyoh.liquid.data.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGridTest {
    private fun apps(n: Int, prefix: String = "a") = (0 until n).map { AppItem("$prefix$it", "pkg$it/cls#0") }

    @Test
    fun appsFlowRowByRow() {
        val cells = HomeGrid.pack(apps(6), columns = 4)
        assertEquals(Cell(0, 0, 1, 1), cells[0])
        assertEquals(Cell(0, 3, 1, 1), cells[3])
        assertEquals(Cell(1, 1, 1, 1), cells[5])
    }

    @Test
    fun smallWidgetsSitSideBySideAndAppsFlowBelow() {
        val items = listOf(
            WidgetItem("w1", WidgetKind.Calendar, WidgetSize.Small),
            WidgetItem("w2", WidgetKind.AnalogClock, WidgetSize.Small),
        ) + apps(2)
        val cells = HomeGrid.pack(items, columns = 4)
        assertEquals(Cell(0, 0, 2, 2), cells[0])
        assertEquals(Cell(0, 2, 2, 2), cells[1])
        assertEquals(Cell(2, 0, 1, 1), cells[2])
    }

    @Test
    fun appsFillGapsNextToAWidget() {
        val items = listOf(WidgetItem("w", WidgetKind.Calendar, WidgetSize.Small)) + apps(4)
        val cells = HomeGrid.pack(items, columns = 4)
        assertEquals(Cell(0, 2, 1, 1), cells[1])
        assertEquals(Cell(0, 3, 1, 1), cells[2])
        assertEquals(Cell(1, 2, 1, 1), cells[3])
    }

    @Test
    fun overflowIsNull() {
        val cells = HomeGrid.pack(apps(25), columns = 4)
        assertNull(cells[24])
        assertTrue(cells.take(24).all { it != null })
    }

    @Test
    fun normalizeSpillsOverflowOntoNextPageAndDropsEmptyPages() {
        val layout = HomeLayout(pages = listOf(apps(26), apps(1, "b"), emptyList()), dock = emptyList())
        val n = HomeGrid.normalize(layout, columns = 4)
        assertEquals(2, n.pages.size)
        assertEquals(24, n.pages[0].size)
        assertEquals(listOf("a24", "a25", "b0"), n.pages[1].map { it.id })
    }

    @Test
    fun normalizeKeepsOneEmptyPage() {
        val n = HomeGrid.normalize(HomeLayout(emptyList(), emptyList()), columns = 4)
        assertEquals(1, n.pages.size)
    }
}
