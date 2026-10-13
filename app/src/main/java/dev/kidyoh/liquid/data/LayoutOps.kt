package dev.kidyoh.liquid.data

/** Where a dragged thing came from. */
sealed interface DragSource {
    data class Page(val page: Int) : DragSource
    data object Dock : DragSource
    data class Folder(val folderId: String) : DragSource
}

/** Where it will land. Page indices are indices into [HomeLayout.pages]. */
sealed interface DropTarget {
    data class PageInsert(val page: Int, val index: Int) : DropTarget
    data class Merge(val page: Int, val targetId: String) : DropTarget
    data class DockInsert(val index: Int) : DropTarget
    data class FolderInsert(val folderId: String, val index: Int) : DropTarget
    data object None : DropTarget
}

/** Pure Home Screen edits, kept free of Android so they are easy to test. */
object LayoutOps {

    fun appIdOf(item: HomeItem): String? = (item as? AppItem)?.app

    fun remove(l: HomeLayout, item: HomeItem, source: DragSource): HomeLayout = when (source) {
        is DragSource.Page -> l.copy(pages = l.pages.map { page -> page.filter { it.id != item.id } })
        DragSource.Dock -> {
            val app = appIdOf(item)
            val i = l.dock.indexOf(app)
            if (i < 0) l else l.copy(dock = l.dock.toMutableList().apply { removeAt(i) })
        }
        is DragSource.Folder -> {
            val app = appIdOf(item)
            l.copy(pages = l.pages.map { page ->
                page.mapNotNull { it ->
                    if (it is FolderItem && it.id == source.folderId) {
                        val rest = it.apps.toMutableList().apply { remove(app) }
                        if (rest.isEmpty()) null else it.copy(apps = rest)
                    } else it
                }
            })
        }
    }

    /** Removes [item] from [source] and puts it at [target]. Falls back to the last page if the target vanished. */
    fun move(l: HomeLayout, item: HomeItem, source: DragSource, target: DropTarget, newId: () -> String, folderName: (List<String>) -> String): HomeLayout {
        if (target == DropTarget.None) return l
        val removed = remove(l, item, source)
        val app = appIdOf(item)
        val placed: HomeLayout? = when (target) {
            is DropTarget.PageInsert -> insertOnPage(removed, target.page, target.index, item)
            is DropTarget.Merge -> if (app == null) null else merge(removed, target.targetId, app, newId, folderName)
            is DropTarget.DockInsert -> if (app == null) null else removed.copy(
                dock = removed.dock.toMutableList().apply { add(target.index.coerceIn(0, size), app) }
            )
            is DropTarget.FolderInsert -> if (app == null) null else folderInsert(removed, target.folderId, target.index, app)
            DropTarget.None -> removed
        }
        return placed ?: insertOnPage(removed, removed.pages.lastIndex.coerceAtLeast(0), Int.MAX_VALUE, item)
    }

    fun insertOnPage(l: HomeLayout, page: Int, index: Int, item: HomeItem): HomeLayout {
        val pages = l.pages.map { it.toMutableList() }.toMutableList()
        while (pages.size <= page) pages.add(mutableListOf())
        val p = pages[page]
        p.add(index.coerceIn(0, p.size), item)
        return l.copy(pages = pages.map { it.toList() })
    }

    private fun merge(l: HomeLayout, targetId: String, app: String, newId: () -> String, folderName: (List<String>) -> String): HomeLayout? {
        var found = false
        val pages = l.pages.map { page ->
            page.map { it ->
                if (it.id != targetId) return@map it
                found = true
                when (it) {
                    is AppItem -> FolderItem(newId(), folderName(listOf(it.app, app)), listOf(it.app, app))
                    is FolderItem -> it.copy(apps = it.apps + app)
                    is WidgetItem -> it
                }
            }
        }
        return if (found) l.copy(pages = pages) else null
    }

    private fun folderInsert(l: HomeLayout, folderId: String, index: Int, app: String): HomeLayout? {
        var found = false
        val pages = l.pages.map { page ->
            page.map { it ->
                if (it is FolderItem && it.id == folderId) {
                    found = true
                    it.copy(apps = it.apps.toMutableList().apply { add(index.coerceIn(0, size), app) })
                } else it
            }
        }
        return if (found) l.copy(pages = pages) else null
    }

    fun removeItem(l: HomeLayout, id: String): HomeLayout =
        l.copy(pages = l.pages.map { page -> page.filter { it.id != id } })

    fun removeAppEverywhere(l: HomeLayout, app: String): HomeLayout = l.copy(
        pages = l.pages.map { page ->
            page.mapNotNull {
                when (it) {
                    is AppItem -> it.takeIf { a -> a.app != app }
                    is FolderItem -> it.copy(apps = it.apps - app).takeIf { f -> f.apps.isNotEmpty() }
                    is WidgetItem -> it
                }
            }
        },
        dock = l.dock - app,
    )

    fun renameFolder(l: HomeLayout, id: String, name: String): HomeLayout = l.copy(
        pages = l.pages.map { page -> page.map { if (it is FolderItem && it.id == id) it.copy(name = name) else it } }
    )

    fun findFolder(l: HomeLayout, id: String): FolderItem? =
        l.pages.asSequence().flatten().filterIsInstance<FolderItem>().firstOrNull { it.id == id }

    fun pageOf(l: HomeLayout, id: String): Int = l.pages.indexOfFirst { page -> page.any { it.id == id } }

    fun onHome(l: HomeLayout, app: String): Boolean = app in l.dock || l.pages.any { page ->
        page.any { (it is AppItem && it.app == app) || (it is FolderItem && app in it.apps) }
    }
}
