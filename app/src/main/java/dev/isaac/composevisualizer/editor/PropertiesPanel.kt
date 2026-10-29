package dev.isaac.composevisualizer.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.HorizontalAlignmentSpec
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.TextStyleRole
import dev.isaac.composevisualizer.model.displayName

/**
 * "Give me a function that changes a T, and I'll apply it to the latest
 * node." Callers write: onEdit { it.copy(spacing = 8f) }
 */
typealias Edit<T> = ((T) -> T) -> Unit

/** Shows the fields that belong to the selected node's type. */
@Composable
fun PropertiesPanel(
    node: Node?,
    onEditText: (id: String, change: (TextNode) -> TextNode) -> Unit,
    onEditSurface: (id: String, change: (SurfaceNode) -> SurfaceNode) -> Unit,
    onEditColumn: (id: String, change: (ColumnNode) -> ColumnNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (node == null) {
        Text(
            text = "Select a node in the Hierarchy tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // key(node.id): selecting another node throws away all field state
    // and scroll position, so no field ever shows another node's text.
    key(node.id) {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(node.displayName(), style = MaterialTheme.typography.titleMedium)

            // Sealed `when`: exhaustive, so a new node type won't compile
            // until it gets an editor here.
            when (node) {
                is TextNode -> TextProperties(node) { change -> onEditText(node.id, change) }
                is SurfaceNode -> SurfaceProperties(node) { change -> onEditSurface(node.id, change) }
                is ColumnNode -> ColumnProperties(node) { change -> onEditColumn(node.id, change) }
            }
        }
    }
}

@Composable
private fun TextProperties(node: TextNode, onEdit: Edit<TextNode>) {
    SectionTitle("Text")
    TextValueField("Content", node.text, { v -> onEdit { it.copy(text = v) } })
    DropdownField(
        label = "Style",
        options = TextStyleRole.entries,
        selected = node.style,
        onSelected = { s -> onEdit { it.copy(style = s) } },
        modifier = Modifier.fillMaxWidth(),
    )
    ColorSpecField("Color", node.color, { c -> onEdit { it.copy(color = c) } })
    PaddingFields("Padding (dp)", node.padding, { p -> onEdit { it.copy(padding = p) } })
}

@Composable
private fun SurfaceProperties(node: SurfaceNode, onEdit: Edit<SurfaceNode>) {
    SectionTitle("Size")
    SizeField("Width", node.width, { w -> onEdit { it.copy(width = w) } })
    SizeField("Height", node.height, { h -> onEdit { it.copy(height = h) } })
    SectionTitle("Appearance")
    ColorSpecField("Fill", node.color, { c -> onEdit { it.copy(color = c) } })
    ShapeField(node.shape, { s -> onEdit { it.copy(shape = s) } })
    PaddingFields("Padding (dp)", node.padding, { p -> onEdit { it.copy(padding = p) } })
}

@Composable
private fun ColumnProperties(node: ColumnNode, onEdit: Edit<ColumnNode>) {
    SectionTitle("Size")
    SizeField("Width", node.width, { w -> onEdit { it.copy(width = w) } })
    SizeField("Height", node.height, { h -> onEdit { it.copy(height = h) } })
    SectionTitle("Layout")
    NumberField(
        label = "Spacing (dp)",
        value = node.spacing,
        onValueChange = { v -> onEdit { it.copy(spacing = v) } },
        modifier = Modifier.fillMaxWidth(),
    )
    DropdownField(
        label = "Alignment",
        options = HorizontalAlignmentSpec.entries,
        selected = node.horizontalAlignment,
        onSelected = { a -> onEdit { it.copy(horizontalAlignment = a) } },
        modifier = Modifier.fillMaxWidth(),
    )
    PaddingFields("Padding (dp)", node.padding, { p -> onEdit { it.copy(padding = p) } })
}