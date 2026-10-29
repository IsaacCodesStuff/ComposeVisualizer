package dev.isaac.composevisualizer.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.isaac.composevisualizer.model.FlatNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.NodeKind
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.childNodes
import dev.isaac.composevisualizer.model.displayName
import dev.isaac.composevisualizer.model.flatten
import dev.isaac.composevisualizer.model.flattenVisible
import dev.isaac.composevisualizer.model.siblingPositionOf

/** Actions on the selection, add controls, and the collapsible node tree. */
@Composable
fun HierarchyPanel(
    root: Node,
    selectedId: String?,
    selectedNode: Node?,
    collapsedIds: Set<String>,
    onSelect: (String) -> Unit,
    onToggleCollapsed: (String) -> Unit,
    onAddChild: (NodeKind) -> Unit,
    onMove: (id: String, delta: Int) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only the rows that are currently visible (not inside a collapsed branch).
    val rows = remember(root, collapsedIds) { root.flattenVisible(collapsedIds) }
    val position = remember(root, selectedId) {
        selectedId?.let { root.siblingPositionOf(it) }
    }
    val listState = rememberLazyListState()

    // Keep the selected row on screen. Runs when the selection or the rows change
    // (new node added, node moved, panel reopened) but only scrolls if needed.
    LaunchedEffect(selectedId, rows) {
        val index = rows.indexOfFirst { it.node.id == selectedId }
        if (index < 0) return@LaunchedEffect

        withFrameNanos { } // wait one frame so the list has been laid out
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(index)
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

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(rows, key = { it.node.id }) { row ->
                HierarchyRow(
                    row = row,
                    selected = row.node.id == selectedId,
                    collapsed = row.node.id in collapsedIds,
                    onSelect = { onSelect(row.node.id) },
                    onToggle = { onToggleCollapsed(row.node.id) },
                )
            }
        }
    }
}

@Composable
private fun HierarchyRow(
    row: FlatNode,
    selected: Boolean,
    collapsed: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
) {
    val node = row.node
    val hasChildren = node.childNodes().isNotEmpty()

    val background =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val textColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface

    // Small hints: how much a collapsed node hides, or that a container is empty
    // (an empty Column is invisible in the preview, so the list says so).
    val hint = when {
        collapsed && hasChildren -> "  +${node.flatten().size - 1} hidden"
        node !is TextNode && !hasChildren -> "  (empty)"
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            // Indent 16dp per level of depth.
            .padding(start = (4 + row.depth * 16).dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The toggle area is always 40dp wide so labels line up; it only
        // reacts to taps (and shows a chevron) when the node has children.
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(if (hasChildren) Modifier.clickable(onClick = onToggle) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (hasChildren) {
                Text(
                    text = if (collapsed) "▸" else "▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
        }
        Text(
            text = node.displayName() + hint,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}