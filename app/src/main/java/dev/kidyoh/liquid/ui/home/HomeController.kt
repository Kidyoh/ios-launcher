package dev.kidyoh.liquid.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.kidyoh.liquid.LiquidApp
import dev.kidyoh.liquid.data.AppCategory
import dev.kidyoh.liquid.data.AppEntry
import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.Cell
import dev.kidyoh.liquid.data.DragSource
import dev.kidyoh.liquid.data.DropTarget
import dev.kidyoh.liquid.data.FolderItem
import dev.kidyoh.liquid.data.HomeGrid
import dev.kidyoh.liquid.data.HomeItem
import dev.kidyoh.liquid.data.HomeLayout
import dev.kidyoh.liquid.data.LayoutOps
import dev.kidyoh.liquid.data.LayoutRepository
import dev.kidyoh.liquid.data.WidgetItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Pixel geometry of the Home Screen, refreshed every composition. All rects are in root coordinates. */
class Geometry {
    var screenW = 0f
    var gridOrigin = Offset.Zero
    var gridW = 0f
    var gridH = 0f
    var cellW = 1f
    var cellH = 1f
    var icon = 1f
    var iconTop = 0f
    var columns = 4
    var edge = 0f
    var dockRect = Rect.Zero
    var dockPad = 0f
    var folderPanel: Rect? = null
    var folderGridOrigin = Offset.Zero
    var folderCellW = 1f
    var folderCellH = 1f
    var folderIconTop = 0f
    var folderPage = 0

    fun cellRect(c: Cell) = Rect(c.col * cellW, c.row * cellH, (c.col + c.spanX) * cellW, (c.row + c.spanY) * cellH)

    /** Top-left of an item's icon (or widget body) in root coordinates. */
    fun visualOrigin(c: Cell) = gridOrigin + Offset(c.col * cellW + (cellW - icon) / 2f, c.row * cellH + iconTop)
}

data class DragInfo(
    val item: HomeItem,
    val source: DragSource,
    /** Pointer position relative to the visual's top-left. */
    val grab: Offset,
    val start: Offset,
)

data class MenuRequest(val item: HomeItem, val source: DragSource, val bounds: Rect, val app: AppEntry?)
data class FolderOpen(val id: String, val from: Rect)
data class RemoveRequest(val item: HomeItem, val source: DragSource, val app: AppEntry?)

@Stable
class HomeController(private val app: LiquidApp, private val scope: CoroutineScope) {
    val geometry = Geometry()

    var editMode by mutableStateOf(false)
        private set
    var openFolder by mutableStateOf<FolderOpen?>(null)
    var menu by mutableStateOf<MenuRequest?>(null)
    var spotlight by mutableStateOf(false)
    var removeRequest by mutableStateOf<RemoveRequest?>(null)
    var widgetGallery by mutableStateOf(false)
    var editMenu by mutableStateOf(false)
    var currentHomePage by mutableIntStateOf(0)

    var drag by mutableStateOf<DragInfo?>(null)
        private set
    var target by mutableStateOf<DropTarget>(DropTarget.None)
        private set
    var edge by mutableIntStateOf(0)
        private set
    var settling by mutableStateOf(false)
        private set
    val dragPos = Animatable(Offset.Zero, Offset.VectorConverter)
    private var lastPointer = Offset.Zero

    private val layout: HomeLayout? get() = app.layout.layout.value

    val anyOverlay: Boolean get() = openFolder != null || menu != null || spotlight || removeRequest != null || widgetGallery

    fun enterEdit() {
        if (!editMode) app.layout.clearHistory()
        editMode = true
        menu = null
    }

    fun exitEdit() {
        editMode = false
        editMenu = false
    }

    /** Back / Home press: close the top-most thing. Returns false if there was nothing to close. */
    fun dismissTop(): Boolean = when {
        removeRequest != null -> { removeRequest = null; true }
        menu != null -> { menu = null; true }
        widgetGallery -> { widgetGallery = false; true }
        editMenu -> { editMenu = false; true }
        spotlight -> { spotlight = false; true }
        openFolder != null -> { openFolder = null; true }
        editMode -> { exitEdit(); true }
        else -> false
    }

    // ---- Drag and drop -------------------------------------------------------------------------

    fun startDrag(item: HomeItem, source: DragSource, pointer: Offset, visualOrigin: Offset) {
        if (drag != null || settling) return
        menu = null
        enterEdit()
        drag = DragInfo(item, source, pointer - visualOrigin, visualOrigin)
        lastPointer = pointer
        scope.launch { dragPos.snapTo(visualOrigin) }
        target = when (source) {
            is DragSource.Page -> {
                val idx = layout?.pages?.getOrNull(source.page)?.indexOfFirst { it.id == item.id } ?: -1
                if (idx >= 0) DropTarget.PageInsert(source.page, idx) else DropTarget.None
            }
            DragSource.Dock -> DropTarget.DockInsert(layout?.dock?.indexOf((item as AppItem).app)?.coerceAtLeast(0) ?: 0)
            is DragSource.Folder -> {
                val f = layout?.let { LayoutOps.findFolder(it, source.folderId) }
                DropTarget.FolderInsert(source.folderId, f?.apps?.indexOf((item as AppItem).app)?.coerceAtLeast(0) ?: 0)
            }
        }
    }

    fun moveDrag(pointer: Offset) {
        val d = drag ?: return
        if (settling) return
        lastPointer = pointer
        scope.launch { dragPos.snapTo(pointer - d.grab) }
        val g = geometry
        val inGrid = pointer.y in g.gridOrigin.y..(g.gridOrigin.y + g.gridH)
        edge = if (openFolder == null && inGrid) when {
            pointer.x < g.edge -> -1
            pointer.x > g.screenW - g.edge -> 1
            else -> 0
        } else 0
        target = resolve(pointer, d)
    }

    /** Called after an edge-flip changed the page, so the target follows. */
    fun refreshTarget() {
        val d = drag ?: return
        target = resolve(lastPointer, d)
    }

    fun endDrag() {
        val d = drag ?: return
        if (settling) return
        settling = true
        edge = 0
        val t = target
        scope.launch {
            val dest = destination(t, d)
            dragPos.animateTo(dest, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
            if (t != DropTarget.None) {
                app.layout.edit { LayoutOps.move(it, d.item, d.source, t, LayoutRepository::newId, ::folderName) }
            }
            drag = null
            target = DropTarget.None
            settling = false
        }
    }

    fun cancelDrag() {
        if (drag != null && !settling) {
            target = DropTarget.None
            endDrag()
        }
    }

    private fun resolve(pointer: Offset, d: DragInfo): DropTarget {
        val l = layout ?: return DropTarget.None
        val g = geometry
        val isApp = d.item is AppItem

        openFolder?.let { of ->
            val panel = g.folderPanel
            if (panel != null && panel.contains(pointer)) {
                if (!isApp) return DropTarget.None
                val folder = LayoutOps.findFolder(l, of.id) ?: return DropTarget.None
                val app = (d.item as AppItem).app
                val count = folder.apps.count { it != app || d.source != DragSource.Folder(of.id) }
                val local = pointer - g.folderGridOrigin
                val col = (local.x / g.folderCellW).toInt().coerceIn(0, 2)
                val row = (local.y / g.folderCellH).toInt().coerceIn(0, 2)
                return DropTarget.FolderInsert(of.id, (g.folderPage * 9 + row * 3 + col).coerceIn(0, count))
            } else if (d.source is DragSource.Folder) {
                openFolder = null // dragging out of a folder closes it, like iOS
            }
        }

        val dockZone = Rect(g.dockRect.left, g.dockRect.top - g.cellH * 0.15f, g.dockRect.right, g.dockRect.bottom + g.cellH)
        if (dockZone.contains(pointer)) {
            if (!isApp) return DropTarget.None
            val app = (d.item as AppItem).app
            val base = if (d.source == DragSource.Dock) l.dock - app else l.dock
            if (d.source != DragSource.Dock && base.size >= g.columns) return DropTarget.None
            val n = base.size + 1
            val inner = g.dockRect.width - g.dockPad * 2
            val idx = ((pointer.x - g.dockRect.left - g.dockPad) / (inner / n)).toInt().coerceIn(0, base.size)
            return DropTarget.DockInsert(idx)
        }

        val page = currentHomePage
        val items = l.pages.getOrNull(page).orEmpty()
        val base = items.filter { it.id != d.item.id }
        val display = displayList(page, items)
        val cells = HomeGrid.pack(display, g.columns)
        val local = pointer - g.gridOrigin
        if (local.y < 0f || local.y > g.gridH) {
            val t = target
            return if (t is DropTarget.PageInsert && t.page == page) t else DropTarget.PageInsert(page, base.size)
        }
        display.forEachIndexed { i, item ->
            val cell = cells.getOrNull(i) ?: return@forEachIndexed
            val r = g.cellRect(cell)
            if (!r.contains(local)) return@forEachIndexed
            if (item.id == d.item.id) return target // hovering our own placeholder: stay put
            if (isApp && item !is WidgetItem) {
                val c = Offset(r.left + g.cellW / 2f, r.top + g.iconTop + g.icon / 2f)
                if ((local - c).getDistance() < g.icon * 0.36f) return DropTarget.Merge(page, item.id)
            }
            val bi = base.indexOf(item)
            return DropTarget.PageInsert(page, if (local.x < r.center.x) bi else bi + 1)
        }
        return DropTarget.PageInsert(page, base.size)
    }

    private fun destination(t: DropTarget, d: DragInfo): Offset {
        val l = layout ?: return d.start
        val g = geometry
        return when (t) {
            is DropTarget.PageInsert -> {
                val display = displayList(t.page, l.pages.getOrNull(t.page).orEmpty())
                val cells = HomeGrid.pack(display, g.columns)
                val i = display.indexOfFirst { it.id == d.item.id }
                val cell = cells.getOrNull(i) ?: return d.start
                g.visualOrigin(cell)
            }
            is DropTarget.Merge -> {
                val items = displayList(t.page, l.pages.getOrNull(t.page).orEmpty())
                val cells = HomeGrid.pack(items, g.columns)
                val i = items.indexOfFirst { it.id == t.targetId }
                cells.getOrNull(i)?.let { g.visualOrigin(it) } ?: d.start
            }
            is DropTarget.DockInsert -> {
                val app = (d.item as? AppItem)?.app
                val n = (if (d.source == DragSource.Dock) l.dock - app else l.dock).size + 1
                val slot = (g.dockRect.width - g.dockPad * 2) / n
                Offset(g.dockRect.left + g.dockPad + slot * t.index + (slot - g.icon) / 2f, g.dockRect.top + (g.dockRect.height - g.icon) / 2f)
            }
            is DropTarget.FolderInsert -> {
                val slot = t.index - g.folderPage * 9
                if (slot !in 0..8) d.start
                else g.folderGridOrigin + Offset((slot % 3) * g.folderCellW + (g.folderCellW - g.icon) / 2f, (slot / 3) * g.folderCellH + g.folderIconTop)
            }
            DropTarget.None -> d.start
        }
    }

    /** The page as it should look right now, with a gap where the dragged item would land. */
    fun displayList(page: Int, items: List<HomeItem>): List<HomeItem> {
        val d = drag ?: return items
        val base = items.filter { it.id != d.item.id }
        val t = target
        return if (t is DropTarget.PageInsert && t.page == page) base.toMutableList().apply { add(t.index.coerceIn(0, size), d.item) } else base
    }

    /** Dock apps with a null gap for a pending drop. */
    fun dockDisplay(dock: List<String>): List<String?> {
        val d = drag ?: return dock
        val app = (d.item as? AppItem)?.app
        val base: List<String?> = if (d.source == DragSource.Dock) dock - app else dock
        val t = target
        return if (t is DropTarget.DockInsert) base.toMutableList().apply { add(t.index.coerceIn(0, size), null) } else base
    }

    fun folderDisplay(folder: FolderItem): List<String?> {
        val d = drag ?: return folder.apps
        val app = (d.item as? AppItem)?.app
        val base: List<String?> = if (d.source == DragSource.Folder(folder.id)) folder.apps - app else folder.apps
        val t = target
        return if (t is DropTarget.FolderInsert && t.folderId == folder.id) base.toMutableList().apply { add(t.index.coerceIn(0, size), null) } else base
    }

    // ---- Edits ---------------------------------------------------------------------------------

    fun removeFromHome(item: HomeItem, source: DragSource) {
        app.layout.edit { l ->
            when {
                item is FolderItem || item is WidgetItem -> LayoutOps.removeItem(l, item.id)
                else -> LayoutOps.remove(l, item, source)
            }
        }
    }

    fun addToHome(appId: String) {
        app.layout.edit { l ->
            if (LayoutOps.onHome(l, appId)) l
            else LayoutOps.insertOnPage(l, l.pages.lastIndex.coerceAtLeast(0), Int.MAX_VALUE, AppItem(LayoutRepository.newId(), appId))
        }
    }

    fun addWidget(w: WidgetItem) {
        val page = currentHomePage.coerceAtLeast(0)
        app.layout.edit { LayoutOps.insertOnPage(it, page, 0, w) }
    }

    fun renameFolder(id: String, name: String) {
        app.layout.edit { LayoutOps.renameFolder(it, id, name.ifBlank { "Folder" }) }
    }

    private fun folderName(apps: List<String>): String {
        val byId = app.apps.byId.value
        val cats = apps.mapNotNull { byId[it]?.category }.distinct()
        val c = cats.singleOrNull() ?: return "Folder"
        return when (c) {
            AppCategory.Productivity -> "Productivity"
            AppCategory.Information -> "Reading"
            AppCategory.Other -> "Folder"
            else -> c.title
        }
    }
}
