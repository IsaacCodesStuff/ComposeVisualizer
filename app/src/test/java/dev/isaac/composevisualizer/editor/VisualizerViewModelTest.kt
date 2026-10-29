package dev.isaac.composevisualizer.editor

import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.NodeKind
import dev.isaac.composevisualizer.model.SurfaceNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests the ViewModel's structural actions and selection rules.
 * Sample tree: root(Surface) -> column(Column) -> [title(Text), artist(Text)]
 */
class VisualizerViewModelTest {

    private fun VisualizerViewModel.rootNode() = uiState.value.root as SurfaceNode
    private fun VisualizerViewModel.columnNode() = rootNode().child as ColumnNode
    private val VisualizerViewModel.selectedId get() = uiState.value.selectedId

    @Test
    fun addChildAddsAtEndAndSelectsNewNode() {
        val vm = VisualizerViewModel()
        vm.addChild(vm.columnNode().id, NodeKind.Text)

        val children = vm.columnNode().children
        assertEquals(3, children.size)
        assertEquals(children.last().id, vm.selectedId)
    }

    @Test
    fun addChildToTextIsIgnored() {
        val vm = VisualizerViewModel()
        val before = vm.uiState.value
        vm.addChild(vm.columnNode().children[0].id, NodeKind.Text)
        assertSame(before, vm.uiState.value)
    }

    @Test
    fun deletingSelectedNodeSelectsItsParent() {
        val vm = VisualizerViewModel()
        val title = vm.columnNode().children[0]
        vm.select(title.id)

        vm.deleteNode(title.id)

        assertEquals(1, vm.columnNode().children.size)
        assertEquals(vm.columnNode().id, vm.selectedId)
    }

    @Test
    fun deletingAnotherNodeKeepsSelection() {
        val vm = VisualizerViewModel()
        val (title, artist) = vm.columnNode().children
        vm.select(title.id)

        vm.deleteNode(artist.id)

        assertEquals(title.id, vm.selectedId)
    }

    @Test
    fun deletingAnAncestorOfTheSelectionSelectsTheDeletedNodesParent() {
        val vm = VisualizerViewModel()
        val column = vm.columnNode()
        vm.select(column.children[0].id) // selection is inside the column

        vm.deleteNode(column.id)

        assertEquals(vm.rootNode().id, vm.selectedId)
        assertEquals(null, vm.rootNode().child)
    }

    @Test
    fun deletingTheRootIsIgnored() {
        val vm = VisualizerViewModel()
        val before = vm.uiState.value
        vm.deleteNode(vm.rootNode().id)
        assertSame(before, vm.uiState.value)
    }
}