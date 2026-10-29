package dev.isaac.composevisualizer.render

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode

/**
 * Renders any node as real Compose UI. Recursive: containers call
 * RenderNode again for each child.
 */
@Composable
fun RenderNode(node: Node, modifier: Modifier = Modifier) {
    // `when` on a sealed type must be exhaustive. Add a new Node subtype
    // and this stops compiling until you handle it here.
    when (node) {
        is TextNode -> RenderText(node, modifier)
        is SurfaceNode -> RenderSurface(node, modifier)
        is ColumnNode -> RenderColumn(node, modifier)
    }
}

@Composable
private fun RenderText(node: TextNode, modifier: Modifier) {
    Text(
        text = node.text,
        style = node.style.toTextStyle(),
        color = node.color.toColor(),
        modifier = modifier.padding(node.padding.toPaddingValues()),
    )
}

@Composable
private fun RenderSurface(node: SurfaceNode, modifier: Modifier) {
    val border = if (node.borderWidth > 0f) {
        BorderStroke(node.borderWidth.dp, node.borderColor.toColor())
    } else {
        null
    }

    Surface(
        modifier = modifier.widthOf(node.width).heightOf(node.height),
        shape = node.shape.toShape(),
        color = node.color.toColor(),
        shadowElevation = node.elevation.dp,
        border = border,
    ) {
        // Surface forces its content to at least the surface's size. This Box
        // takes that size, adds the inner padding, and lets the child keep its
        // own size and sit where contentAlignment says.
        Box(
            modifier = Modifier.padding(node.padding.toPaddingValues()),
            contentAlignment = node.contentAlignment.toAlignment(),
        ) {
            node.child?.let { RenderNode(it) }
        }
    }
}

@Composable
private fun RenderColumn(node: ColumnNode, modifier: Modifier) {
    Column(
        modifier = modifier
            .widthOf(node.width)
            .heightOf(node.height)
            .padding(node.padding.toPaddingValues()),
        verticalArrangement = Arrangement.spacedBy(node.spacing.dp),
        horizontalAlignment = node.horizontalAlignment.toAlignment(),
    ) {
        node.children.forEach { child ->
            // key() ties each child's identity to its node id, so reordering
            // moves children instead of recreating them.
            key(child.id) { RenderNode(child) }
        }
    }
}