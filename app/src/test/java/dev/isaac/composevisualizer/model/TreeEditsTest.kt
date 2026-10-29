package dev.isaac.composevisualizer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests the pure tree functions. No Android needed.
 * Tree under test:   root(Surface) -> col(Column) -> [a(Text), b(Text)]
 */
class TreeEditsTest {

    private val textA = TextNode(id = "a", text = "A")
    private val textB = TextNode(id = "b", text = "B")
    private val column = ColumnNode(id = "col", children = listOf(textA, textB))
    private val root = SurfaceNode(id = "root", child = column)

    private fun columnOf(tree: Node): ColumnNode =
        (tree as SurfaceNode).child as ColumnNode

    // ---- insert ----

    @Test
    fun insertAppendsToColumnAndKeepsSiblings() {
        val result = root.withInserted("col", TextNode(id = "new"))
        val col = columnOf(result)
        assertEquals(listOf("a", "b", "new"), col.children.map { it.id })
        assertSame(textA, col.children[0]) // untouched siblings are the same objects
        assertSame(textB, col.children[1])
    }

    @Test
    fun insertIntoTextIsNoOp() {
        assertSame(root, root.withInserted("a", TextNode(id = "new")))
    }

    @Test
    fun insertIntoFullSurfaceIsNoOp() {
        assertSame(root, root.withInserted("root", TextNode(id = "new")))
    }

    @Test
    fun insertIntoEmptySurfaceSetsChild() {
        val empty = SurfaceNode(id = "s")
        val result = empty.withInserted("s", TextNode(id = "t")) as SurfaceNode
        assertEquals("t", result.child?.id)
    }

    @Test
    fun insertIntoUnknownParentIsNoOp() {
        assertSame(root, root.withInserted("missing", TextNode(id = "new")))
    }

    // ---- remove ----

    @Test
    fun removeDeletesOnlyThatNode() {
        val col = columnOf(root.withRemoved("b"))
        assertEquals(listOf("a"), col.children.map { it.id })
        assertSame(textA, col.children[0])
    }

    @Test
    fun removeSubtreeRemovesDescendantsToo() {
        val result = root.withRemoved("col") as SurfaceNode
        assertNull(result.child)
    }

    @Test
    fun removeRootIsNoOp() {
        assertSame(root, root.withRemoved("root"))
    }

    @Test
    fun removeUnknownIdIsNoOp() {
        assertSame(root, root.withRemoved("missing"))
    }

    // ---- move ----

    @Test
    fun moveUpSwapsWithPreviousSibling() {
        val col = columnOf(root.withMoved("b", -1))
        assertEquals(listOf("b", "a"), col.children.map { it.id })
        assertSame(textA, col.children[1]) // data preserved, just reordered
    }

    @Test
    fun moveDownSwapsWithNextSibling() {
        val col = columnOf(root.withMoved("a", +1))
        assertEquals(listOf("b", "a"), col.children.map { it.id })
    }

    @Test
    fun moveFirstUpAndLastDownAreNoOps() {
        assertSame(root, root.withMoved("a", -1))
        assertSame(root, root.withMoved("b", +1))
    }

    @Test
    fun moveOnlyChildOfSurfaceIsNoOp() {
        assertSame(root, root.withMoved("col", +1))
    }

    // ---- related helpers ----

    @Test
    fun findParentFindsDirectParentOnly() {
        assertEquals("col", root.findParent("a")?.id)
        assertEquals("root", root.findParent("col")?.id)
        assertNull(root.findParent("root"))
        assertNull(root.findParent("missing"))
    }

    @Test
    fun propertyEditKeepsSiblingsAndAncestors() {
        val result = root.updateNode("b") { (it as TextNode).copy(text = "X") }
        val col = columnOf(result)
        assertSame(textA, col.children[0])
        assertEquals("X", (col.children[1] as TextNode).text)
    }
}