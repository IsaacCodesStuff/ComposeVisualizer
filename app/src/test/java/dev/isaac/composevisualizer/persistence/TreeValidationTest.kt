package dev.isaac.composevisualizer.persistence

import dev.isaac.composevisualizer.model.ColorRole
import dev.isaac.composevisualizer.model.ColorSpec
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.ContentAlignment
import dev.isaac.composevisualizer.model.HorizontalAlignmentSpec
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.PaddingSpec
import dev.isaac.composevisualizer.model.SampleNodes
import dev.isaac.composevisualizer.model.ShapeSpec
import dev.isaac.composevisualizer.model.SizeSpec
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.model.TextStyleRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeValidationTest {

    // ---- helpers -----------------------------------------------------------

    /** A valid surface to hang one bad property on. */
    private fun surface(
        id: String = "s",
        width: SizeSpec = SizeSpec.Wrap,
        height: SizeSpec = SizeSpec.Wrap,
        padding: PaddingSpec = PaddingSpec.None,
        color: ColorSpec = ColorSpec.Role(ColorRole.Surface),
        shape: ShapeSpec = ShapeSpec.Rectangle,
        borderWidth: Float = 0f,
        borderColor: ColorSpec = ColorSpec.Role(ColorRole.Outline),
        elevation: Float = 0f,
        child: Node? = null,
    ) = SurfaceNode(
        id = id, width = width, height = height, padding = padding, color = color,
        shape = shape, borderWidth = borderWidth, borderColor = borderColor,
        elevation = elevation, child = child,
    )

    /** A chain of [depth] nodes (root = depth 1), built bottom-up with a loop, no recursion. */
    private fun chain(depth: Int): Node {
        var node: Node = TextNode(id = "leaf")
        repeat(depth - 1) { i -> node = surface(id = "s$i", child = node) }
        return node
    }

    private fun rules(root: Node) = validateTree(root).map { it.rule }

    private fun assertSingle(root: Node, rule: ValidationRule, nodeId: String, field: String?) {
        val errors = validateTree(root)
        assertEquals("errors were: $errors", 1, errors.size)
        assertEquals(rule, errors[0].rule)
        assertEquals(nodeId, errors[0].nodeId)
        assertEquals(field, errors[0].field)
    }

    // ---- valid trees -------------------------------------------------------

    @Test
    fun sampleTreeIsValid() {
        assertEquals(emptyList<ValidationError>(), validateTree(SampleNodes.nowPlayingCard()))
    }

    @Test
    fun treeUsingEveryVariantIsValid() {
        val tree = SurfaceNode(
            id = "root",
            width = SizeSpec.Fixed(300f),
            height = SizeSpec.Wrap,
            padding = PaddingSpec.all(8f),
            color = ColorSpec.Custom(0xFF112233L),
            shape = ShapeSpec.Rounded(12f),
            borderWidth = 2f,
            borderColor = ColorSpec.Role(ColorRole.Primary),
            elevation = 6f,
            contentAlignment = ContentAlignment.Center,
            child = ColumnNode(
                id = "col",
                width = SizeSpec.Fixed(200f),
                height = SizeSpec.Fixed(400f),
                spacing = 3f,
                horizontalAlignment = HorizontalAlignmentSpec.End,
                children = listOf(
                    TextNode(id = "t", style = TextStyleRole.TitleLarge, color = ColorSpec.Custom(0x00000000L)),
                    surface(id = "circle", shape = ShapeSpec.Circle),
                    surface(id = "rect", shape = ShapeSpec.Rectangle),
                ),
            ),
        )
        assertEquals(emptyList<ValidationError>(), validateTree(tree))
    }

    @Test
    fun boundaryValuesAreAccepted() {
        val tree = surface(
            id = "root",
            width = SizeSpec.Fixed(0f),
            height = SizeSpec.Fixed(0f),
            padding = PaddingSpec(0f, 0f, 0f, 0f),
            color = ColorSpec.Custom(0L),
            shape = ShapeSpec.Rounded(0f),
            borderWidth = 0f,
            borderColor = ColorSpec.Custom(0xFFFFFFFFL),
            elevation = -0f, // negative zero is zero, not a negative value
            child = ColumnNode(id = "col", spacing = 0f),
        )
        assertEquals(emptyList<ValidationError>(), validateTree(tree))
    }

    @Test
    fun hugeButFiniteValuesAreAccepted() {
        // There is intentionally no upper limit in format version 1.
        val tree = surface(width = SizeSpec.Fixed(1e9f), padding = PaddingSpec.all(Float.MAX_VALUE))
        assertEquals(emptyList<ValidationError>(), validateTree(tree))
    }

    // ---- ids ---------------------------------------------------------------

    @Test
    fun duplicateIdAmongSiblingsIsRejected() {
        val tree = ColumnNode(id = "col", children = listOf(TextNode(id = "a"), TextNode(id = "a")))
        assertSingle(tree, ValidationRule.DuplicateId, "a", "id")
    }

    @Test
    fun duplicateIdAcrossBranchesIsRejected() {
        val tree = ColumnNode(
            id = "col",
            children = listOf(
                surface(id = "left", child = TextNode(id = "x")),
                surface(id = "right", child = TextNode(id = "x")),
            ),
        )
        assertSingle(tree, ValidationRule.DuplicateId, "x", "id")
    }

    @Test
    fun childReusingItsParentsIdIsRejected() {
        val tree = surface(id = "same", child = TextNode(id = "same"))
        assertSingle(tree, ValidationRule.DuplicateId, "same", "id")
    }

    @Test
    fun idUsedThreeTimesReportsTwoDuplicates() {
        val tree = ColumnNode(
            id = "col",
            children = listOf(TextNode(id = "a"), TextNode(id = "a"), TextNode(id = "a")),
        )
        assertEquals(listOf(ValidationRule.DuplicateId, ValidationRule.DuplicateId), rules(tree))
    }

    @Test
    fun blankIdsAreRejected() {
        assertSingle(TextNode(id = ""), ValidationRule.BlankId, "", "id")
        assertSingle(TextNode(id = "   "), ValidationRule.BlankId, "   ", "id")
    }

    // ---- negative values ---------------------------------------------------

    @Test
    fun negativeFixedSizesAreRejected() {
        assertSingle(surface(width = SizeSpec.Fixed(-1f)), ValidationRule.NegativeValue, "s", "width")
        assertSingle(surface(height = SizeSpec.Fixed(-1f)), ValidationRule.NegativeValue, "s", "height")
        val col = ColumnNode(id = "c", width = SizeSpec.Fixed(-5f))
        assertSingle(col, ValidationRule.NegativeValue, "c", "width")
        val col2 = ColumnNode(id = "c", height = SizeSpec.Fixed(-5f))
        assertSingle(col2, ValidationRule.NegativeValue, "c", "height")
    }

    @Test
    fun negativePaddingIsRejectedOnEverySideOfEveryNodeType() {
        val sides = listOf(
            "start" to PaddingSpec(start = -1f),
            "top" to PaddingSpec(top = -1f),
            "end" to PaddingSpec(end = -1f),
            "bottom" to PaddingSpec(bottom = -1f),
        )
        for ((side, padding) in sides) {
            val field = "padding.$side"
            assertSingle(TextNode(id = "t", padding = padding), ValidationRule.NegativeValue, "t", field)
            assertSingle(surface(padding = padding), ValidationRule.NegativeValue, "s", field)
            assertSingle(ColumnNode(id = "c", padding = padding), ValidationRule.NegativeValue, "c", field)
        }
    }

    @Test
    fun negativeSpacingBorderElevationAndRadiusAreRejected() {
        assertSingle(ColumnNode(id = "c", spacing = -0.5f), ValidationRule.NegativeValue, "c", "spacing")
        assertSingle(surface(borderWidth = -1f), ValidationRule.NegativeValue, "s", "borderWidth")
        assertSingle(surface(elevation = -1f), ValidationRule.NegativeValue, "s", "elevation")
        assertSingle(surface(shape = ShapeSpec.Rounded(-4f)), ValidationRule.NegativeValue, "s", "shape.radius")
    }

    @Test
    fun smallestNegativeValueIsStillRejected() {
        assertSingle(
            surface(elevation = -Float.MIN_VALUE),
            ValidationRule.NegativeValue, "s", "elevation",
        )
    }

    // ---- non-finite values -------------------------------------------------

    @Test
    fun nanAndInfinitiesAreRejectedEverywhereANumberLives() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertSingle(surface(width = SizeSpec.Fixed(bad)), ValidationRule.NonFiniteValue, "s", "width")
            assertSingle(TextNode(id = "t", padding = PaddingSpec(top = bad)), ValidationRule.NonFiniteValue, "t", "padding.top")
            assertSingle(ColumnNode(id = "c", spacing = bad), ValidationRule.NonFiniteValue, "c", "spacing")
            assertSingle(surface(borderWidth = bad), ValidationRule.NonFiniteValue, "s", "borderWidth")
            assertSingle(surface(elevation = bad), ValidationRule.NonFiniteValue, "s", "elevation")
            assertSingle(surface(shape = ShapeSpec.Rounded(bad)), ValidationRule.NonFiniteValue, "s", "shape.radius")
        }
    }

    // ---- ARGB --------------------------------------------------------------

    @Test
    fun argbOutsideThirtyTwoBitsIsRejected() {
        for (bad in listOf(-1L, 0x1_0000_0000L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            assertSingle(
                TextNode(id = "t", color = ColorSpec.Custom(bad)),
                ValidationRule.ArgbOutOfRange, "t", "color.argb",
            )
        }
    }

    @Test
    fun argbIsCheckedOnSurfaceColorAndBorderColor() {
        assertSingle(surface(color = ColorSpec.Custom(-1L)), ValidationRule.ArgbOutOfRange, "s", "color.argb")
        assertSingle(surface(borderColor = ColorSpec.Custom(-1L)), ValidationRule.ArgbOutOfRange, "s", "borderColor.argb")
    }

    @Test
    fun argbLimitsThemselvesAreAccepted() {
        assertEquals(emptyList<ValidationError>(), validateTree(TextNode(id = "a", color = ColorSpec.Custom(0L))))
        assertEquals(emptyList<ValidationError>(), validateTree(TextNode(id = "a", color = ColorSpec.Custom(0xFFFFFFFFL))))
    }

    // ---- reporting ---------------------------------------------------------

    @Test
    fun allProblemsAreReportedInPreOrder() {
        val tree = surface(
            id = "root",
            elevation = -1f,
            child = ColumnNode(
                id = "col",
                spacing = -2f,
                children = listOf(
                    TextNode(id = "t1", padding = PaddingSpec(top = -3f)),
                    TextNode(id = "t2", color = ColorSpec.Custom(-1L)),
                ),
            ),
        )
        val errors = validateTree(tree)
        assertEquals(listOf("root", "col", "t1", "t2"), errors.map { it.nodeId })
        assertEquals(
            listOf(
                ValidationRule.NegativeValue,
                ValidationRule.NegativeValue,
                ValidationRule.NegativeValue,
                ValidationRule.ArgbOutOfRange,
            ),
            errors.map { it.rule },
        )
    }

    @Test
    fun oneNodeCanReportSeveralProblems() {
        val tree = surface(id = "s", elevation = -1f, borderWidth = -1f, shape = ShapeSpec.Rounded(Float.NaN))
        assertEquals(
            listOf("elevation", "borderWidth", "shape.radius").sorted(),
            validateTree(tree).map { it.field }.sortedBy { it },
        )
    }

    @Test
    fun errorsAreCappedForHostileInput() {
        val children = List(MAX_REPORTED_ERRORS * 3) { TextNode(id = "n$it", padding = PaddingSpec(top = -1f)) }
        val errors = validateTree(ColumnNode(id = "col", children = children))
        assertEquals(MAX_REPORTED_ERRORS, errors.size)
    }

    @Test
    fun errorMessagesAreNotEmpty() {
        val errors = validateTree(surface(elevation = -1f, color = ColorSpec.Custom(-1L)))
        assertTrue(errors.isNotEmpty() && errors.all { it.message.isNotBlank() })
    }

    // ---- depth -------------------------------------------------------------

    @Test
    fun treeExactlyAtTheDepthLimitIsValid() {
        assertEquals(emptyList<ValidationError>(), validateTree(chain(MAX_TREE_DEPTH)))
    }

    @Test
    fun treeOneLevelOverTheLimitIsRejected() {
        val errors = validateTree(chain(MAX_TREE_DEPTH + 1))
        assertEquals(listOf(ValidationRule.TreeTooDeep), errors.map { it.rule })
    }

    @Test
    fun depthCountsThroughColumnsToo() {
        var node: Node = TextNode(id = "leaf")
        repeat(MAX_TREE_DEPTH) { i -> node = ColumnNode(id = "c$i", children = listOf(node)) }
        // MAX_TREE_DEPTH columns + the leaf = one level too many.
        assertEquals(listOf(ValidationRule.TreeTooDeep), rules(node))
    }

    @Test
    fun absurdlyDeepTreeIsRejectedWithoutOverflowingTheStack() {
        val errors = validateTree(chain(200_000))
        assertEquals(listOf(ValidationRule.TreeTooDeep), errors.map { it.rule })
    }

    @Test
    fun nodesBeyondTheLimitAreNotInspected() {
        // The invalid padding sits below the limit, so only the depth error is reported.
        var node: Node = TextNode(id = "leaf", padding = PaddingSpec(top = -1f))
        repeat(MAX_TREE_DEPTH) { i -> node = surface(id = "s$i", child = node) }
        assertEquals(listOf(ValidationRule.TreeTooDeep), rules(node))
    }

    @Test
    fun onlyTheTooDeepBranchIsReported() {
        val shallow = TextNode(id = "shallow")
        val deep = chain(MAX_TREE_DEPTH) // as a child of the root this lands one level too deep
        val tree = ColumnNode(id = "root", children = listOf(shallow, deep))
        val errors = validateTree(tree)
        assertEquals(listOf(ValidationRule.TreeTooDeep), errors.map { it.rule })
    }

    // ---- names -------------------------------------------------------------

    @Test
    fun ordinaryNamesAreValid() {
        assertNull(validateName("Now playing card"))
        assertNull(validateName("x"))
        assertNull(validateName("日本語の名前"))
    }

    @Test
    fun emptyAndBlankNamesAreRejected() {
        for (bad in listOf("", " ", "   ", "\t\n")) {
            assertEquals(ValidationRule.BlankName, validateName(bad)?.rule)
        }
    }

    @Test
    fun nameLengthLimitIsInclusive() {
        assertNull(validateName("a".repeat(MAX_NAME_LENGTH)))
        assertEquals(ValidationRule.NameTooLong, validateName("a".repeat(MAX_NAME_LENGTH + 1))?.rule)
    }

    @Test
    fun surroundingSpacesAreNotRejectedOrAltered() {
        // Validation never alters or second-guesses the name; trimming is the caller's job.
        assertNull(validateName("  padded  "))
        assertNotNull(validateName(" ".repeat(MAX_NAME_LENGTH + 1) + "x"))
    }
}