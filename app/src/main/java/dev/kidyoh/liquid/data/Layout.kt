package dev.kidyoh.liquid.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WidgetKind(val title: String, val subtitle: String) {
    AnalogClock("Clock", "Check the current time at a glance."),
    WorldClock("World Clock", "Keep track of time in cities around the world."),
    Calendar("Calendar", "See today's date."),
    Month("Calendar", "See this month and today's date."),
    Battery("Batteries", "Keep an eye on your battery level."),
}

@Serializable
enum class WidgetSize(val spanX: Int, val spanY: Int) {
    Small(2, 2),
    Medium(4, 2),
}

@Serializable
sealed interface HomeItem {
    val id: String
}

@Serializable
@SerialName("app")
data class AppItem(override val id: String, val app: String) : HomeItem

@Serializable
@SerialName("folder")
data class FolderItem(override val id: String, val name: String, val apps: List<String>) : HomeItem

@Serializable
@SerialName("widget")
data class WidgetItem(override val id: String, val kind: WidgetKind, val size: WidgetSize) : HomeItem

@Serializable
data class HomeLayout(
    val pages: List<List<HomeItem>>,
    val dock: List<String>,
)

/** Which grid cells an item covers. */
data class Cell(val row: Int, val col: Int, val spanX: Int, val spanY: Int) {
    val index: Int get() = row * 1000 + col
}

fun HomeItem.span(columns: Int): Pair<Int, Int> = when (this) {
    is WidgetItem -> minOf(size.spanX, columns) to size.spanY
    else -> 1 to 1
}

object HomeGrid {
    const val ROWS = 6

    /**
     * Flows items into a grid the way iOS does: in order, row by row, with each widget taking
     * the first spot it fits. Returns one cell per item, or null once the page is full.
     */
    fun pack(items: List<HomeItem>, columns: Int, rows: Int = ROWS): List<Cell?> {
        val used = Array(rows) { BooleanArray(columns) }
        var full = false
        return items.map { item ->
            if (full) return@map null
            val (sx, sy) = item.span(columns)
            val spot = firstFit(used, sx, sy, columns, rows)
            if (spot == null) {
                full = true
                null
            } else {
                val (r, c) = spot
                for (y in r until r + sy) for (x in c until c + sx) used[y][x] = true
                Cell(r, c, sx, sy)
            }
        }
    }

    fun fits(items: List<HomeItem>, columns: Int, rows: Int = ROWS): Boolean =
        pack(items, columns, rows).all { it != null }

    private fun firstFit(used: Array<BooleanArray>, sx: Int, sy: Int, columns: Int, rows: Int): Pair<Int, Int>? {
        for (r in 0..rows - sy) for (c in 0..columns - sx) {
            var ok = true
            loop@ for (y in r until r + sy) for (x in c until c + sx) if (used[y][x]) { ok = false; break@loop }
            if (ok) return r to c
        }
        return null
    }

    /** Pushes overflow onto following pages (adding pages as needed) and drops empty pages. */
    fun normalize(layout: HomeLayout, columns: Int, rows: Int = ROWS): HomeLayout {
        val pages = layout.pages.map { it.toMutableList() }.toMutableList()
        var i = 0
        while (i < pages.size) {
            val page = pages[i]
            val cells = pack(page, columns, rows)
            val firstOverflow = cells.indexOfFirst { it == null }
            if (firstOverflow >= 0) {
                val spill = page.subList(firstOverflow, page.size).toList()
                repeat(spill.size) { page.removeAt(page.size - 1) }
                if (i + 1 >= pages.size) pages.add(mutableListOf())
                pages[i + 1].addAll(0, spill)
            }
            i++
        }
        val cleaned = pages.filter { it.isNotEmpty() }.ifEmpty { listOf(mutableListOf()) }
        return layout.copy(pages = cleaned.map { it.toList() })
    }
}
