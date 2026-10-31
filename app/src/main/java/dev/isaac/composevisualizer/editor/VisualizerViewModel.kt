package dev.isaac.composevisualizer.editor

import androidx.lifecycle.ViewModel
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.NodeKind
import dev.isaac.composevisualizer.model.SampleNodes
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.childNodes
import dev.isaac.composevisualizer.model.findNode
import dev.isaac.composevisualizer.model.findParent
import dev.isaac.composevisualizer.model.pathTo
import dev.isaac.composevisualizer.model.updateNode
import dev.isaac.composevisualizer.model.withInserted
import dev.isaac.composevisualizer.model.withMoved
import dev.isaac.composevisualizer.model.withRemoved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Everything the editor screen needs to draw itself. Immutable.
 *
 * [collapsedIds] is UI state about the hierarchy list, not part of the
 * component itself. Invariant: the selected node is never hidden inside
 * a collapsed branch (see [withSelectionRevealed]).
 *
 * [configId], [configName] and [savedRoot] describe the saved configuration
 * this tree came from or was last saved as. They are plain data: this class
 * knows nothing about files. [savedRoot] is the tree exactly as it was last
 * saved or loaded, which is what [isDirty] compares against. A brand-new state
 * counts as clean (it starts equal to its own root).
 */
data class EditorUiState(
    val root: Node,
    val selectedId: String?,
    val collapsedIds: Set<String> = emptySet(),
    val configId: String? = null,
    val configName: String? = null,
    val savedRoot: Node? = root,
) {
    /** Derived on demand, so it can never disagree with the tree. */
    val selectedNode: Node? get() = selectedId?.let { root.findNode(it) }

    /**
     * True when the tree differs from the last saved/loaded version, or has no
     * saved version at all (null [savedRoot], e.g. after its file was deleted).
     * Plain value equality: edit something and edit it back, and it's clean again.
     * Unchanged subtrees are shared instances, so the comparison is cheap.
     */
    val isDirty: Boolean get() = savedRoot == null || root != savedRoot
}

/**
 * Owns the component tree. Survives rotation (not process death yet).
 * The UI observes [uiState] and calls the functions below to change it.
 */
class VisualizerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** Selects a node. If it was hidden in a collapsed branch, the branch opens. */
    fun select(id: String) {
        _uiState.update { state ->
            if (state.root.findNode(id) == null) {
                state
            } else {
                state.copy(selectedId = id).withSelectionRevealed()
            }
        }
    }

    /** Collapses or expands a node's children. Nodes without children are ignored. */
    fun toggleCollapsed(id: String) {
        _uiState.update { state ->
            val node = state.root.findNode(id) ?: return@update state
            if (node.childNodes().isEmpty()) return@update state

            if (id in state.collapsedIds) {
                state.copy(collapsedIds = state.collapsedIds - id)
            } else {
                // Collapsing must not leave the selection invisible inside the branch:
                // if it is a descendant of this node, select this node instead.
                val selected = state.selectedId
                val hidesSelection =
                    selected != null && selected != id && node.findNode(selected) != null
                state.copy(
                    collapsedIds = state.collapsedIds + id,
                    selectedId = if (hidesSelection) id else selected,
                )
            }
        }
    }

    // ---- Property edits (Milestone 3) ----

    fun updateText(id: String, change: (TextNode) -> TextNode) =
        edit(id) { node -> if (node is TextNode) change(node) else node }

    fun updateSurface(id: String, change: (SurfaceNode) -> SurfaceNode) =
        edit(id) { node -> if (node is SurfaceNode) change(node) else node }

    fun updateColumn(id: String, change: (ColumnNode) -> ColumnNode) =
        edit(id) { node -> if (node is ColumnNode) change(node) else node }

    private fun edit(id: String, transform: (Node) -> Node) {
        _uiState.update { state ->
            state.copy(root = state.root.updateNode(id, transform))
        }
    }

    // ---- Structural edits (Milestone 4) ----

    /**
     * Adds a new default node of [kind] under [parentId] and selects it.
     * If the parent was collapsed it opens, so the new node is visible.
     */
    fun addChild(parentId: String, kind: NodeKind) {
        // Created outside update{}: if update retries, the id stays the same.
        val newNode = kind.createDefault()
        _uiState.update { state ->
            val newRoot = state.root.withInserted(parentId, newNode)
            if (newRoot === state.root) {
                state // impossible add (unknown parent, Text, full Surface): do nothing
            } else {
                state
                    .copy(root = newRoot, selectedId = newNode.id)
                    .withSelectionRevealed()
            }
        }
    }

    /**
     * Deletes [id] and its subtree. The root cannot be deleted.
     * Selection is kept unless it was inside the deleted subtree;
     * then it moves to the deleted node's parent.
     */
    fun deleteNode(id: String) {
        _uiState.update { state ->
            val parent = state.root.findParent(id) ?: return@update state // root/unknown
            val newRoot = state.root.withRemoved(id)
            if (newRoot === state.root) return@update state

            val selected = state.selectedId
            val selectionSurvived = selected != null && newRoot.findNode(selected) != null
            // Forget collapsed ids of nodes that no longer exist.
            val prunedCollapsed = state.collapsedIds.filterTo(mutableSetOf()) {
                newRoot.findNode(it) != null
            }
            state
                .copy(
                    root = newRoot,
                    selectedId = if (selectionSurvived) selected else parent.id,
                    collapsedIds = prunedCollapsed,
                )
                .withSelectionRevealed()
        }
    }

    /** Moves [id] among its siblings: delta -1 = up, +1 = down. Selection unchanged. */
    fun moveNode(id: String, delta: Int) {
        _uiState.update { state ->
            val newRoot = state.root.withMoved(id, delta)
            if (newRoot === state.root) state else state.copy(root = newRoot)
        }
    }

    // ---- Saved configurations (Milestone 5) ----
    // These only swap plain data in and out of the state. Reading and writing
    // files is someone else's job, so this class needs no Context.

    /**
     * Replaces everything with a configuration that was just loaded: the tree,
     * its id and name, and a clean "nothing changed yet" baseline. Selection
     * resets to the root and every branch is expanded; neither is saved.
     */
    fun loadConfiguration(id: String, name: String, root: Node) {
        _uiState.value = EditorUiState(
            root = root,
            selectedId = root.id,
            configId = id,
            configName = name,
            savedRoot = root,
        )
    }

    /**
     * Records that [savedRoot] was just written as configuration [id]. Pass the
     * tree that was actually saved, not whatever is current: if the user kept
     * editing while the save ran, those newer edits must stay "unsaved".
     * The tree, selection and collapsed state are untouched.
     */
    fun markSaved(id: String, name: String, savedRoot: Node) {
        _uiState.update { it.copy(configId = id, configName = name, savedRoot = savedRoot) }
    }

    /**
     * Forgets the saved configuration without touching the tree, for when its
     * file was deleted. The tree now exists only in memory, so it counts as unsaved.
     */
    fun detachConfiguration() {
        _uiState.update { it.copy(configId = null, configName = null, savedRoot = null) }
    }
}

/**
 * Enforces the invariant "the selected node is never hidden": opens every
 * collapsed ancestor of the selection. Returns the same instance if nothing
 * needed opening, so no-ops stay no-ops.
 */
private fun EditorUiState.withSelectionRevealed(): EditorUiState {
    val id = selectedId ?: return this
    val ancestorIds = root.pathTo(id)?.dropLast(1)?.map { it.id }?.toSet() ?: return this
    val revealed = collapsedIds - ancestorIds
    return if (revealed.size == collapsedIds.size) this else copy(collapsedIds = revealed)
}

private fun initialState(): EditorUiState {
    val root = SampleNodes.nowPlayingCard()
    return EditorUiState(root = root, selectedId = root.id)
}