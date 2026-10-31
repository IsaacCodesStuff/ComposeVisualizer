package dev.isaac.composevisualizer.editor

import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.NodeKind
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadConfigurationTest {

    /** root (surface) -> col -> [t1, t2], with fixed ids unrelated to the sample tree. */
    private fun loadedTree(): Node = SurfaceNode(
        id = "r",
        child = ColumnNode(
            id = "c",
            children = listOf(TextNode(id = "t1", text = "one"), TextNode(id = "t2", text = "two")),
        ),
    )

    // ---- starting state ----------------------------------------------------

    @Test
    fun aFreshViewModelHasNoConfigurationAndIsClean() {
        val state = VisualizerViewModel().uiState.value
        assertNull(state.configId)
        assertNull(state.configName)
        assertFalse(state.isDirty)
    }

    @Test
    fun editingAFreshViewModelMakesItDirty() {
        val vm = VisualizerViewModel()
        val id = vm.uiState.value.root.id
        vm.updateSurface(id) { it.copy(elevation = 5f) }
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun editingBackToTheOriginalValueMakesItCleanAgain() {
        val vm = VisualizerViewModel()
        val id = vm.uiState.value.root.id
        val original = (vm.uiState.value.root as SurfaceNode).elevation
        vm.updateSurface(id) { it.copy(elevation = 5f) }
        vm.updateSurface(id) { it.copy(elevation = original) }
        assertFalse(vm.uiState.value.isDirty)
    }

    // ---- loadConfiguration -------------------------------------------------

    @Test
    fun loadingReplacesTheTreeAndRecordsIdAndName() {
        val vm = VisualizerViewModel()
        val tree = loadedTree()
        vm.loadConfiguration("config-1", "My card", tree)

        val state = vm.uiState.value
        assertSame(tree, state.root)
        assertEquals("config-1", state.configId)
        assertEquals("My card", state.configName)
    }

    @Test
    fun loadingSelectsTheRoot() {
        val vm = VisualizerViewModel()
        val oldChild = (vm.uiState.value.root as SurfaceNode).child!!.id
        vm.select(oldChild)

        vm.loadConfiguration("config-1", "n", loadedTree())

        assertEquals("r", vm.uiState.value.selectedId)
        assertEquals("r", vm.uiState.value.selectedNode?.id)
    }

    @Test
    fun loadingClearsCollapsedBranches() {
        val vm = VisualizerViewModel()
        val col = (vm.uiState.value.root as SurfaceNode).child!!.id
        vm.toggleCollapsed(col)
        assertTrue(vm.uiState.value.collapsedIds.isNotEmpty())

        vm.loadConfiguration("config-1", "n", loadedTree())

        assertTrue(vm.uiState.value.collapsedIds.isEmpty())
    }

    @Test
    fun aJustLoadedConfigurationIsClean() {
        val vm = VisualizerViewModel()
        vm.updateSurface(vm.uiState.value.root.id) { it.copy(elevation = 9f) } // dirty before loading
        vm.loadConfiguration("config-1", "n", loadedTree())
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test
    fun editingALoadedConfigurationMakesItDirty() {
        val vm = VisualizerViewModel()
        vm.loadConfiguration("config-1", "n", loadedTree())
        vm.updateText("t1") { it.copy(text = "changed") }
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun structuralEditsMakeALoadedConfigurationDirty() {
        val vm = VisualizerViewModel()
        vm.loadConfiguration("config-1", "n", loadedTree())
        vm.addChild("c", NodeKind.Text)
        assertTrue(vm.uiState.value.isDirty)

        vm.loadConfiguration("config-1", "n", loadedTree())
        vm.moveNode("t2", -1)
        assertTrue(vm.uiState.value.isDirty)

        vm.loadConfiguration("config-1", "n", loadedTree())
        vm.deleteNode("t1")
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun selectingAndCollapsingDoNotMakeItDirty() {
        val vm = VisualizerViewModel()
        vm.loadConfiguration("config-1", "n", loadedTree())
        vm.select("t2")
        vm.toggleCollapsed("c")
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test
    fun editingStillWorksAfterLoading() {
        val vm = VisualizerViewModel()
        vm.loadConfiguration("config-1", "n", loadedTree())
        vm.updateText("t2") { it.copy(text = "edited") }
        val col = vm.uiState.value.root.let { (it as SurfaceNode).child as ColumnNode }
        assertEquals("edited", (col.children[1] as TextNode).text)
    }

    @Test
    fun loadingTwiceReplacesTheFirstConfiguration() {
        val vm = VisualizerViewModel()
        vm.loadConfiguration("first", "First", loadedTree())
        val second = TextNode(id = "only")
        vm.loadConfiguration("second", "Second", second)

        val state = vm.uiState.value
        assertSame(second, state.root)
        assertEquals("second", state.configId)
        assertEquals("Second", state.configName)
        assertEquals("only", state.selectedId)
        assertFalse(state.isDirty)
    }

    // ---- markSaved ---------------------------------------------------------

    @Test
    fun markSavedRecordsTheConfigurationAndCleansTheState() {
        val vm = VisualizerViewModel()
        vm.updateSurface(vm.uiState.value.root.id) { it.copy(elevation = 3f) }
        assertTrue(vm.uiState.value.isDirty)

        vm.markSaved("config-1", "Saved name", vm.uiState.value.root)

        val state = vm.uiState.value
        assertEquals("config-1", state.configId)
        assertEquals("Saved name", state.configName)
        assertFalse(state.isDirty)
    }

    @Test
    fun editsMadeWhileSavingStayUnsaved() {
        val vm = VisualizerViewModel()
        val snapshot = vm.uiState.value.root // what the save started with
        vm.updateSurface(snapshot.id) { it.copy(elevation = 7f) } // user keeps editing during the save

        vm.markSaved("config-1", "n", snapshot)

        assertTrue("the newer edit was not part of the save", vm.uiState.value.isDirty)
        assertEquals("config-1", vm.uiState.value.configId)
    }

    @Test
    fun markSavedLeavesTreeSelectionAndCollapseAlone() {
        val vm = VisualizerViewModel()
        val col = (vm.uiState.value.root as SurfaceNode).child!!.id
        vm.toggleCollapsed(col) // selects the column, collapses it
        val before = vm.uiState.value

        vm.markSaved("config-1", "n", before.root)

        val after = vm.uiState.value
        assertSame(before.root, after.root)
        assertEquals(before.selectedId, after.selectedId)
        assertEquals(before.collapsedIds, after.collapsedIds)
    }

    // ---- detachConfiguration -----------------------------------------------

    @Test
    fun detachingForgetsTheConfigurationButKeepsTheTree() {
        val vm = VisualizerViewModel()
        vm.loadConfiguration("config-1", "n", loadedTree())
        val tree = vm.uiState.value.root

        vm.detachConfiguration()

        val state = vm.uiState.value
        assertNull(state.configId)
        assertNull(state.configName)
        assertSame(tree, state.root)
        assertTrue("an in-memory-only tree counts as unsaved", state.isDirty)
    }

    @Test
    fun savingAfterDetachingCleansTheStateAgain() {
        val vm = VisualizerViewModel()
        vm.loadConfiguration("config-1", "n", loadedTree())
        vm.detachConfiguration()
        vm.markSaved("config-2", "Again", vm.uiState.value.root)
        assertFalse(vm.uiState.value.isDirty)
        assertEquals("config-2", vm.uiState.value.configId)
    }
}