package dev.isaac.composevisualizer.model

/** The direct children of a node, whatever kind of container it is. */
fun Node.childNodes(): List<Node> = when (this) {
    is TextNode -> emptyList()
    is SurfaceNode -> listOfNotNull(child)
    is ColumnNode -> children
}

/** Depth-first search for the node with this id. */
fun Node.findNode(id: String): Node? {
    if (this.id == id) return this
    for (child in childNodes()) {
        child.findNode(id)?.let { return it }
    }
    return null
}

/**
 * Returns a new tree in which the node with [id] is replaced by
 * transform(node). Everything else is preserved: ancestors are rebuilt
 * with copy(), and untouched subtrees are returned as the *same instance*.
 */
fun Node.updateNode(id: String, transform: (Node) -> Node): Node {
    if (this.id == id) return transform(this)

    return when (this) {
        is TextNode -> this

        is SurfaceNode -> {
            val oldChild = child ?: return this
            val newChild = oldChild.updateNode(id, transform)
            if (newChild === oldChild) this else copy(child = newChild)
        }

        is ColumnNode -> {
            val newChildren = children.map { it.updateNode(id, transform) }
            val unchanged = newChildren.indices.all { newChildren[it] === children[it] }
            if (unchanged) this else copy(children = newChildren)
        }
    }
}

/** A node plus how deep it sits in the tree, for the hierarchy list. */
data class FlatNode(val node: Node, val depth: Int)

/** The whole tree as a flat, depth-first list (parent before children). */
fun Node.flatten(depth: Int = 0): List<FlatNode> =
    listOf(FlatNode(this, depth)) + childNodes().flatMap { it.flatten(depth + 1) }

/** Short label for the hierarchy and the properties header. */
fun Node.displayName(): String = when (this) {
    is TextNode -> {
        val shown = if (text.length > 24) text.take(24) + "…" else text
        "Text · \"$shown\""
    }
    is SurfaceNode -> "Surface"
    is ColumnNode -> "Column"
}