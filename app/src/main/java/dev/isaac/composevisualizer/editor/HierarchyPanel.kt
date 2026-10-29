package dev.isaac.composevisualizer.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.isaac.composevisualizer.model.FlatNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.NodeKind
import dev.isaac.composevisualizer.model.displayName
import dev.isaac.composevisualizer.model.flatten
import dev.isaac.composevisualizer.model.siblingPositionOf

/** Actions on the selection, add controls, and the node tree as a selectable list. */
@Composable
fun HierarchyPanel(
    root: Node,
    selectedId: String?,
    selectedNode: Node?,
    onSelect: (String) -> Unit,
    onAddChild: (NodeKind) -> Unit,
    onMove: (id: String, delta: Int) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Flatten once per tree change, not on every recomposition.
    val rows = remember(root) { root.flatten() }
    // Recomputed only when the tree or the selection changes.
    val position = remember(root, selectedId) {
        selectedId?.let { root.siblingPositionOf(it) }
    }

    Column(modifier = modifier) {
        NodeActionsBar(
            selected = selectedNode,
            isRoot = selectedNode?.id == root.id,
            position = position,
            onMove = onMove,
            onDelete = onDelete,
        )
        AddChildBar(selected = selectedNode, onAdd = onAddChild)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // weight(1f): the list takes the remaining height and scrolls inside it.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // key = id gives every row a stable identity.
            items(rows, key = { it.node.id }) { row ->
                HierarchyRow(
                    row = row,
                    selected = row.node.id == selectedId,
                    onClick = { onSelect(row.node.id) },
                )
            }
        }
    }
}

@Composable
private fun HierarchyRow(row: FlatNode, selected: Boolean, onClick: () -> Unit) {
    val background =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val textColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface

    Text(
        text = row.node.displayName(),
        style = MaterialTheme.typography.bodyMedium,
        color = textColor,
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            // Indent 16dp per level of depth.
            .padding(start = (12 + row.depth * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    )
}