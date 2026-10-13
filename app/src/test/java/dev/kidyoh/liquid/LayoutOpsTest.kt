package dev.kidyoh.liquid

import dev.kidyoh.liquid.data.AppItem
import dev.kidyoh.liquid.data.DragSource
import dev.kidyoh.liquid.data.DropTarget
import dev.kidyoh.liquid.data.FolderItem
import dev.kidyoh.liquid.data.HomeLayout
import dev.kidyoh.liquid.data.LayoutOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {
    private var n = 0
    private val newId = { "new${n++}" }
    private val name = { _: List<String> -> "Folder" }
    private val a = AppItem("a", "A")
    private val b = AppItem("b", "B")
    private val c = AppItem("c", "C")

    @Test
    fun reorderWithinAPage() {
        val l = HomeLayout(listOf(listOf(a, b, c)), emptyList())
        val out = LayoutOps.move(l, a, DragSource.Page(0), DropTarget.PageInsert(0, 2), newId, name)
        assertEquals(listOf("b", "c", "a"), out.pages[0].map { it.id })
    }

    @Test
    fun dropOnAnAppMakesAFolder() {
        val l = HomeLayout(listOf(listOf(a, b, c)), emptyList())
        val out = LayoutOps.move(l, a, DragSource.Page(0), DropTarget.Merge(0, "b"), newId, name)
        val folder = out.pages[0][0] as FolderItem
        assertEquals(listOf("B", "A"), folder.apps)
        assertEquals(2, out.pages[0].size)
    }

    @Test
    fun dropOnAFolderAddsToIt() {
        val f = FolderItem("f", "Social", listOf("X", "Y"))
        val l = HomeLayout(listOf(listOf(f, a)), emptyList())
        val out = LayoutOps.move(l, a, DragSource.Page(0), DropTarget.Merge(0, "f"), newId, name)
        assertEquals(listOf("X", "Y", "A"), (out.pages[0].single() as FolderItem).apps)
    }

    @Test
    fun dragLastAppOutOfAFolderRemovesTheFolder() {
        val f = FolderItem("f", "F", listOf("A"))
        val l = HomeLayout(listOf(listOf(f, b)), emptyList())
        val out = LayoutOps.move(l, AppItem("tmp", "A"), DragSource.Folder("f"), DropTarget.PageInsert(0, 1), newId, name)
        assertEquals(listOf("B", "A"), out.pages[0].map { (it as AppItem).app })
    }

    @Test
    fun pageToDockAndBack() {
        val l = HomeLayout(listOf(listOf(a, b)), listOf("D1", "D2"))
        val toDock = LayoutOps.move(l, a, DragSource.Page(0), DropTarget.DockInsert(1), newId, name)
        assertEquals(listOf("D1", "A", "D2"), toDock.dock)
        assertEquals(listOf("b"), toDock.pages[0].map { it.id })
        val back = LayoutOps.move(toDock, AppItem("tmp", "D1"), DragSource.Dock, DropTarget.PageInsert(0, 0), newId, name)
        assertEquals(listOf("A", "D2"), back.dock)
        assertEquals("D1", (back.pages[0][0] as AppItem).app)
    }

    @Test
    fun dropOnANewPageCreatesIt() {
        val l = HomeLayout(listOf(listOf(a, b)), emptyList())
        val out = LayoutOps.move(l, a, DragSource.Page(0), DropTarget.PageInsert(1, 0), newId, name)
        assertEquals(2, out.pages.size)
        assertEquals(listOf("a"), out.pages[1].map { it.id })
    }

    @Test
    fun missingTargetFallsBackToLastPageInsteadOfLosingTheApp() {
        val l = HomeLayout(listOf(listOf(a, b)), emptyList())
        val out = LayoutOps.move(l, a, DragSource.Page(0), DropTarget.Merge(0, "gone"), newId, name)
        assertTrue(out.pages.flatten().any { it.id == "a" })
    }

    @Test
    fun removeAppEverywhereCleansDockAndFolders() {
        val f = FolderItem("f", "F", listOf("A"))
        val l = HomeLayout(listOf(listOf(f, a, b)), listOf("A", "B"))
        val out = LayoutOps.removeAppEverywhere(l, "A")
        assertEquals(listOf("b"), out.pages[0].map { it.id })
        assertEquals(listOf("B"), out.dock)
    }
}
