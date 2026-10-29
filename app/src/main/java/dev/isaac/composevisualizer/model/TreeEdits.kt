package dev.isaac.composevisualizer.model

// Structural edits: add, remove, move. Built on updateNode() from NodeOps.kt.
// Rule: if an edit is not possible, return the SAME tree instance unchanged.

/** Can this node accept one more child right now? */
fun Node.canAddChild(): Boolean = when (this) {
    is TextNode -> false
    is SurfaceNode -> child == null
    is ColumnNode -> true
}

/** The node whose direct child has [childId]; null for the root or an unknown id. */
fun Node.findParent(childId: String): Node? {
    val children = childNodes()
    if (children.any { it.id == childId }) return this
    for (child in children) {
        child.findParent(childId)?.let { return it }
    }
    return null
}

// ---- One level only: these change a single node's own children. ----

private fun Node.addingChild(newChild: Node): Node = when (this) {
    is TextNode -> this
    is SurfaceNode -> if (child == null) copy(child = newChild) else this
    is ColumnNode -> copy(children = children + newChild)
}

private fun Node.removingChild(childId: String): Node = when (this) {
    is TextNode -> this
    is SurfaceNode -> if (child?.id == childId) copy(child = null) else this
    is ColumnNode ->
        if (children.any { it.id == childId }) {
            copy(children = children.filterNot { it.id == childId })
        } else {
            this
        }
}

private fun Node.movingChild(childId: String, delta: Int): Node = when (this) {
    is ColumnNode -> {
        val from = children.indexOfFirst { it.id == childId }
        val to = from + delta
        if (from < 0 || to !in children.indices) {
            this
        } else {
            // Take the child out, then put it back at its new index.
            copy(children = children.toMutableList().apply { add(to, removeAt(from)) })
        }
    }
    else -> this // Text has no children; Surface has only one.
}

// ---- Whole tree: find the right parent, apply the one-level edit. ----

/** Adds [child] under the node with [parentId]. */
fun Node.withInserted(parentId: String, child: Node): Node =
    updateNode(parentId) { it.addingChild(child) }

/** Removes the node with [id] and its whole subtree. The root cannot be removed. */
fun Node.withRemoved(id: String): Node {
    if (id == this.id) return this
    val parent = findParent(id) ?: return this
    return updateNode(parent.id) { it.removingChild(id) }
}

/** Moves the node with [id] by [delta] positions among its siblings (-1 = up, +1 = down). */
fun Node.withMoved(id: String, delta: Int): Node {
    val parent = findParent(id) ?: return this
    return updateNode(parent.id) { it.movingChild(id, delta) }
}