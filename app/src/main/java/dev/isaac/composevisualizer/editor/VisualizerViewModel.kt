package dev.isaac.composevisualizer.editor

import androidx.lifecycle.ViewModel
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.NodeKind
import dev.isaac.composevisualizer.model.SampleNodes
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.findNode
import dev.isaac.composevisualizer.model.findParent
import dev.isaac.composevisualizer.model.updateNode
import dev.isaac.composevisualizer.model.withInserted
import dev.isaac.composevisualizer.model.withMoved
import dev.isaac.composevisualizer.model.withRemoved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the editor screen needs to draw itself. Immutable. */
data class EditorUiState(
    val root: Node,
    val selectedId: String?,
) {
    /** Derived on demand, so it can never disagree with the tree. */
    val selectedNode: Node? get() = selectedId?.let { root.findNode(it) }
}

/**
 * Owns the component tree. Survives rotation (not process death yet).
 * The UI observes [uiState] and calls the functions below to change it.
 */
class VisualizerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun select(id: String) {
        _uiState.update { state ->
            if (state.root.findNode(id) != null) state.copy(selectedId = id) else state
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

    /** Adds a new default node of [kind] under [parentId] and selects it. */
    fun addChild(parentId: String, kind: NodeKind) {
        // Created outside update{}: if update retries, the id stays the same.
        val newNode = kind.createDefault()
        _uiState.update { state ->
            val newRoot = state.root.withInserted(parentId, newNode)
            if (newRoot === state.root) {
                state // impossible add (unknown parent, Text, full Surface): do nothing
            } else {
                state.copy(root = newRoot, selectedId = newNode.id)
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
            state.copy(
                root = newRoot,
                selectedId = if (selectionSurvived) selected else parent.id,
            )
        }
    }

    /** Moves [id] among its siblings: delta -1 = up, +1 = down. Selection unchanged. */
    fun moveNode(id: String, delta: Int) {
        _uiState.update { state ->
            val newRoot = state.root.withMoved(id, delta)
            if (newRoot === state.root) state else state.copy(root = newRoot)
        }
    }
}

private fun initialState(): EditorUiState {
    val root = SampleNodes.nowPlayingCard()
    return EditorUiState(root = root, selectedId = root.id)
}