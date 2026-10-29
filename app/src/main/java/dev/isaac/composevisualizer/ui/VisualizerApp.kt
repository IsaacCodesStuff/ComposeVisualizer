package dev.isaac.composevisualizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.isaac.composevisualizer.editor.EditorUiState
import dev.isaac.composevisualizer.editor.HierarchyPanel
import dev.isaac.composevisualizer.editor.PropertiesPanel
import dev.isaac.composevisualizer.editor.VisualizerViewModel
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.render.PreviewTheme
import dev.isaac.composevisualizer.render.RenderNode

/** Top: component preview. Bottom: Properties / Hierarchy panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualizerApp(viewModel: VisualizerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // While the keyboard is open, give the editor more room.
    val previewWeight = if (WindowInsets.isImeVisible) 0.25f else 0.55f

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            PreviewArea(
                root = state.root,
                modifier = Modifier.weight(previewWeight).fillMaxWidth(),
            )
            EditorPanel(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f - previewWeight).fillMaxWidth(),
            )
        }
    }
}

/** The component keeps its own size, centered, in its own theme, with no device frame. */
@Composable
private fun PreviewArea(root: Node, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        PreviewTheme {
            RenderNode(root)
        }
    }
}

private enum class EditorTab(val title: String) {
    Properties("Properties"),
    Hierarchy("Hierarchy"),
}

@Composable
private fun EditorPanel(
    state: EditorUiState,
    viewModel: VisualizerViewModel,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                EditorTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.title) },
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                when (EditorTab.entries[selectedTab]) {
                    EditorTab.Properties -> PropertiesPanel(
                        node = state.selectedNode,
                        onEditText = viewModel::updateText,
                        onEditSurface = viewModel::updateSurface,
                        onEditColumn = viewModel::updateColumn,
                    )
                    EditorTab.Hierarchy -> HierarchyPanel(
                        root = state.root,
                        selectedId = state.selectedId,
                        selectedNode = state.selectedNode,
                        onSelect = viewModel::select,
                        // Add under whatever is selected at the moment of the tap.
                        onAddChild = { kind ->
                            state.selectedId?.let { viewModel.addChild(it, kind) }
                        },
                        onDelete = viewModel::deleteNode,
                    )
                }
            }
        }
    }
}