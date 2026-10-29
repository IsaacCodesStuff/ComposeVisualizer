package dev.isaac.composevisualizer.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.SiblingPosition
import dev.isaac.composevisualizer.model.childNodes
import dev.isaac.composevisualizer.model.displayName
import dev.isaac.composevisualizer.model.flatten

/**
 * Actions on the selected node: its name, Up, Down, and Delete.
 * Stateless apart from the delete-confirmation dialog.
 */
@Composable
fun NodeActionsBar(
    selected: Node?,
    isRoot: Boolean,
    position: SiblingPosition?,
    onMove: (id: String, delta: Int) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Non-null = the confirmation dialog is open for that node.
    var pendingDelete by remember { mutableStateOf<Node?>(null) }

    val canDelete = selected != null && !isRoot
    val label = when {
        selected == null -> "Nothing selected"
        isRoot -> "${selected.displayName()} · root"
        else -> selected.displayName()
    }

    // Tight padding so label + three buttons fit on a narrow phone.
    val compact = PaddingValues(horizontal = 8.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { selected?.let { onMove(it.id, -1) } },
            enabled = position?.canMoveUp == true,
            contentPadding = compact,
        ) {
            Text("↑ Up")
        }
        TextButton(
            onClick = { selected?.let { onMove(it.id, +1) } },
            enabled = position?.canMoveDown == true,
            contentPadding = compact,
        ) {
            Text("↓ Down")
        }
        TextButton(
            onClick = {
                selected?.let { node ->
                    // Leaves go immediately; containers with content ask first.
                    if (node.childNodes().isEmpty()) onDelete(node.id) else pendingDelete = node
                }
            },
            enabled = canDelete,
            contentPadding = compact,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Delete")
        }
    }

    pendingDelete?.let { node ->
        // flatten() includes the node itself, so subtract 1 for "nested" nodes.
        val nestedCount = node.flatten().size - 1
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${node.displayName()}?") },
            text = {
                Text(
                    "This also deletes $nestedCount nested " +
                            "node${if (nestedCount == 1) "" else "s"}. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(node.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}