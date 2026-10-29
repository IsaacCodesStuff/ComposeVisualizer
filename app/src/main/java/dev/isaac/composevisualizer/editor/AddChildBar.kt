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
import dev.isaac.composevisualizer.model.displayName

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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(
            if (selected == null) "Add child" else "Add child to ${selected.displayName()}",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NodeKind.entries.forEach { kind ->
                FilledTonalButton(
                    onClick = { onAdd(kind) },
                    enabled = canAdd,
                    modifier = Modifier.weight(1f),
                    // Tighter than the default so three labels fit on a narrow phone.
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(kind.label, maxLines = 1)
                }
            }
        }
        Text(
            text = addHint(selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One line explaining what Add will do, or why it is unavailable. */
private fun addHint(selected: Node?): String = when (selected) {
    null -> "Select a node in the list first."
    is TextNode -> "Text can't contain other nodes. Select a Surface or Column."
    is SurfaceNode ->
        if (selected.child == null) "This Surface's single child slot is empty."
        else "This Surface already has a child. Select a Column to add more."
    is ColumnNode -> "New nodes are added at the end of the Column."
}