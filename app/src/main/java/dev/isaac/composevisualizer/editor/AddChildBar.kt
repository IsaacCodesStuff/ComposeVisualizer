package dev.isaac.composevisualizer.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.NodeKind
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.canAddChild

/**
 * "Add child" controls: one button per node type. Stateless: it shows
 * what it is given and reports taps through [onAdd].
 */
@Composable
fun AddChildBar(
    selected: Node?,
    onAdd: (NodeKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The model decides what is allowed; the UI only reflects it.
    val canAdd = selected?.canAddChild() == true
    val disabledReason = if (canAdd) null else addDisabledReason(selected)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NodeKind.entries.forEach { kind ->
                FilledTonalButton(
                    onClick = { onAdd(kind) },
                    enabled = canAdd,
                    modifier = Modifier.weight(1f),
                    // Tighter than the default so three labels fit on a narrow phone.
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("+ ${kind.label}", maxLines = 1)
                }
            }
        }
        // Only shown when something is wrong, so it costs no space otherwise.
        if (disabledReason != null) {
            Text(
                text = disabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Why Add is unavailable for this selection. */
private fun addDisabledReason(selected: Node?): String = when (selected) {
    null -> "Select a node in the list first."
    is TextNode -> "Text can't contain other nodes. Select a Surface or Column."
    is SurfaceNode -> "This Surface already has a child. Select a Column to add more."
    is ColumnNode -> "" // never disabled, so never reached
}