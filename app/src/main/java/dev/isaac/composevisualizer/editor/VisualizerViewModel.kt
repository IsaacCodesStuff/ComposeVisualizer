package dev.isaac.composevisualizer.editor

import androidx.lifecycle.ViewModel
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.SampleNodes
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.findNode
import dev.isaac.composevisualizer.model.updateNode
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

    // One function per node type. Each checks the node's type at runtime,
    // so the UI can hand us a (TextNode) -> TextNode and stay type-safe.
    fun updateText(id: String, change: (TextNode) -> TextNode) =
        edit(id) { node -> if (node is TextNode) change(node) else node }

    fun updateSurface(id: String, change: (SurfaceNode) -> SurfaceNode) =
        edit(id) { node -> if (node is SurfaceNode) change(node) else node }

    fun updateColumn(id: String, change: (ColumnNode) -> ColumnNode) =
        edit(id) { node -> if (node is ColumnNode) change(node) else node }

    private fun edit(id: String, transform: (Node) -> Node) {
        // update {} re-runs if another thread changed the state meanwhile,
        // so edits are never lost.
        _uiState.update { state ->
            state.copy(root = state.root.updateNode(id, transform))
        }
    }
}

private fun initialState(): EditorUiState {
    val root = SampleNodes.nowPlayingCard()
    return EditorUiState(root = root, selectedId = root.id)
}