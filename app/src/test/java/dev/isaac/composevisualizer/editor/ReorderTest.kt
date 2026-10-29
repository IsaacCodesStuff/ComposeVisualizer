package dev.isaac.composevisualizer.editor

import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.siblingPositionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the "where can this node move?" helper and the ViewModel's move action. */
class ReorderTest {

    // ---- siblingPositionOf (pure model) ----

    private val a = TextNode(id = "a")
    private val b = TextNode(id = "b")
    private val col = ColumnNode(id = "col", children = listOf(a, b))
    private val root = SurfaceNode(id = "root", child = col)

    @Test
    fun siblingPositionReportsIndexAndCount() {
        val first = root.siblingPositionOf("a")!!
        assertEquals(0, first.index)
        assertEquals(2, first.count)
        assertFalse(first.canMoveUp)
        assertTrue(first.canMoveDown)

        val last = root.siblingPositionOf("b")!!
        assertTrue(last.canMoveUp)
        assertFalse(last.canMoveDown)
    }

    @Test
    fun siblingPositionIsNullWhenNodeCannotBeReordered() {
        assertNull(root.siblingPositionOf("root")) // the root
        assertNull(root.siblingPositionOf("col")) // only child of a Surface
        assertNull(root.siblingPositionOf("missing")) // unknown id
    }

    // ---- ViewModel.moveNode ----

    private fun VisualizerViewModel.columnNode() =
        (uiState.value.root as SurfaceNode).child as ColumnNode

    @Test
    fun moveDownSwapsSiblingsAndKeepsSelection() {
        val vm = VisualizerViewModel()
        val (title, artist) = vm.columnNode().children
        vm.select(title.id)

        vm.moveNode(title.id, +1)

        assertEquals(listOf(artist.id, title.id), vm.columnNode().children.map { it.id })
        assertEquals(title.id, vm.uiState.value.selectedId)
        assertSame(artist, vm.columnNode().children[0]) // data of the other sibling untouched
    }

    @Test
    fun moveAtEdgesIsIgnored() {
        val vm = VisualizerViewModel()
        val (title, artist) = vm.columnNode().children
        val before = vm.uiState.value

        vm.moveNode(title.id, -1) // already first
        vm.moveNode(artist.id, +1) // already last

        assertSame(before, vm.uiState.value)
    }

    @Test
    fun moveOfSurfaceChildIsIgnored() {
        val vm = VisualizerViewModel()
        val before = vm.uiState.value

        vm.moveNode(vm.columnNode().id, +1)

        assertSame(before, vm.uiState.value)
    }
}