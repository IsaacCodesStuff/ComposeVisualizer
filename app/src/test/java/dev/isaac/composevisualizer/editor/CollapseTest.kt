package dev.isaac.composevisualizer.editor

import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.NodeKind
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.flattenVisible
import dev.isaac.composevisualizer.model.pathTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests collapsing, the "selection is never hidden" rule, and pruning. */
class CollapseTest {

    // ---- pure model helpers ----
    // Tree: root(Surface) -> col(Column) -> [a, b]

    private val a = TextNode(id = "a")
    private val b = TextNode(id = "b")
    private val col = ColumnNode(id = "col", children = listOf(a, b))
    private val root = SurfaceNode(id = "root", child = col)

    @Test
    fun flattenVisibleSkipsChildrenOfCollapsedNodes() {
        assertEquals(
            listOf("root", "col", "a", "b"),
            root.flattenVisible(emptySet()).map { it.node.id },
        )
        assertEquals(
            listOf("root", "col"),
            root.flattenVisible(setOf("col")).map { it.node.id },
        )
        assertEquals(
            listOf("root"),
            root.flattenVisible(setOf("root")).map { it.node.id },
        )
    }

    @Test
    fun pathToReturnsRootToNodeChain() {
        assertEquals(listOf("root", "col", "b"), root.pathTo("b")?.map { it.id })
        assertEquals(listOf("root"), root.pathTo("root")?.map { it.id })
        assertNull(root.pathTo("missing"))
    }

    // ---- ViewModel ----

    private fun VisualizerViewModel.columnNode() =
        (uiState.value.root as SurfaceNode).child as ColumnNode

    private val VisualizerViewModel.collapsed get() = uiState.value.collapsedIds
    private val VisualizerViewModel.selectedId get() = uiState.value.selectedId

    @Test
    fun collapsingAnAncestorOfTheSelectionSelectsTheCollapsedNode() {
        val vm = VisualizerViewModel()
        val column = vm.columnNode()
        vm.select(column.children[0].id)

        vm.toggleCollapsed(column.id)

        assertTrue(column.id in vm.collapsed)
        assertEquals(column.id, vm.selectedId)
    }

    @Test
    fun collapseThenExpandRestoresState() {
        val vm = VisualizerViewModel()
        val column = vm.columnNode()

        vm.toggleCollapsed(column.id)
        vm.toggleCollapsed(column.id)

        assertTrue(vm.collapsed.isEmpty())
    }

    @Test
    fun togglingANodeWithoutChildrenIsIgnored() {
        val vm = VisualizerViewModel()
        val before = vm.uiState.value

        vm.toggleCollapsed(vm.columnNode().children[0].id) // a Text node

        assertSame(before, vm.uiState.value)
    }

    @Test
    fun addingToACollapsedParentExpandsIt() {
        val vm = VisualizerViewModel()
        val column = vm.columnNode()
        vm.select(column.id)
        vm.toggleCollapsed(column.id)
        assertTrue(column.id in vm.collapsed)

        vm.addChild(column.id, NodeKind.Text)

        assertFalse(column.id in vm.collapsed)
        assertEquals(3, vm.columnNode().children.size)
        assertEquals(vm.columnNode().children.last().id, vm.selectedId)
    }

    @Test
    fun selectingAHiddenNodeOpensItsAncestors() {
        val vm = VisualizerViewModel()
        val column = vm.columnNode()
        val hiddenChild = column.children[0]
        vm.toggleCollapsed(column.id)

        vm.select(hiddenChild.id)

        assertFalse(column.id in vm.collapsed)
        assertEquals(hiddenChild.id, vm.selectedId)
    }

    @Test
    fun deletingACollapsedNodeForgetsItsCollapsedId() {
        val vm = VisualizerViewModel()
        val column = vm.columnNode()
        vm.select(column.id)
        vm.toggleCollapsed(column.id)

        vm.deleteNode(column.id)

        assertTrue(vm.collapsed.isEmpty())
        assertEquals(vm.uiState.value.root.id, vm.selectedId)
    }
}