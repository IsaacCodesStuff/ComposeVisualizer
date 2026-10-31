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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigFormatTest {

    // ---- helpers -----------------------------------------------------------

    /** Wraps node JSON in a version-1 envelope. Pieces are raw JSON text so tests can break them. */
    private fun doc(root: String, name: String = "\"Card\"", version: String = "1") =
        """{"formatVersion":$version,"name":$name,"root":$root}"""

    private val textJson = """{"type":"text","id":"a"}"""

    /** Every node type and every spec variant, with non-default values. */
    private fun everyVariantTree(): Node = SurfaceNode(
        id = "root",
        width = SizeSpec.Fixed(300f),
        height = SizeSpec.Wrap,
        padding = PaddingSpec(1f, 2f, 3f, 4f),
        color = ColorSpec.Custom(0xFF112233L),
        shape = ShapeSpec.Rounded(12.5f),
        borderWidth = 2f,
        borderColor = ColorSpec.Role(ColorRole.Primary),
        elevation = 6f,
        contentAlignment = ContentAlignment.BottomEnd,
        child = ColumnNode(
            id = "col",
            width = SizeSpec.Fixed(200f),
            height = SizeSpec.Fixed(400f),
            padding = PaddingSpec.all(5f),
            spacing = 3f,
            horizontalAlignment = HorizontalAlignmentSpec.End,
            children = listOf(
                TextNode(
                    id = "t",
                    text = "Hello",
                    style = TextStyleRole.TitleLarge,
                    color = ColorSpec.Custom(0x00000000L),
                    padding = PaddingSpec.symmetric(horizontal = 7f),
                ),
                SurfaceNode(id = "circle", shape = ShapeSpec.Circle),
                SurfaceNode(id = "rect", shape = ShapeSpec.Rectangle, child = null),
                ColumnNode(id = "empty"),
            ),
        ),
    )

    private fun chain(depth: Int): Node {
        var node: Node = TextNode(id = "leaf")
        repeat(depth - 1) { i -> node = SurfaceNode(id = "s$i", child = node) }
        return node
    }

    private fun columnChain(depth: Int): Node {
        var node: Node = TextNode(id = "leaf")
        repeat(depth - 1) { i -> node = ColumnNode(id = "c$i", children = listOf(node)) }
        return node
    }

    private fun roundTrip(name: String, root: Node): DecodeResult = decodeConfig(encodeConfig(name, root))

    private fun assertSuccess(expectedName: String, expectedRoot: Node, result: DecodeResult) {
        assertTrue("expected Success but was $result", result is DecodeResult.Success)
        val content = (result as DecodeResult.Success).content
        assertEquals(expectedName, content.name)
        assertEquals(expectedRoot, content.root)
    }

    private fun assertMalformed(text: String) {
        val result = decodeConfig(text)
        assertTrue("expected Malformed but was $result", result is DecodeResult.Malformed)
        assertTrue((result as DecodeResult.Malformed).message.isNotBlank())
    }

    private fun invalidRules(text: String): List<ValidationRule> {
        val result = decodeConfig(text)
        assertTrue("expected Invalid but was $result", result is DecodeResult.Invalid)
        return (result as DecodeResult.Invalid).errors.map { it.rule }
    }

    // ---- round trips -------------------------------------------------------

    @Test
    fun sampleTreeRoundTrips() {
        val tree = SampleNodes.nowPlayingCard()
        assertSuccess("Now playing", tree, roundTrip("Now playing", tree))
    }

    @Test
    fun treeUsingEveryVariantRoundTrips() {
        val tree = everyVariantTree()
        assertSuccess("All", tree, roundTrip("All", tree))
    }

    @Test
    fun editedTreeRoundTrips() {
        val edited = (SampleNodes.nowPlayingCard() as SurfaceNode).copy(
            color = ColorSpec.Custom(0xFFAA5500L),
            shape = ShapeSpec.Circle,
            width = SizeSpec.Fixed(123f),
        )
        assertSuccess("Edited", edited, roundTrip("Edited", edited))
    }

    @Test
    fun everyNodeTypeCanBeTheRoot() {
        for (root in listOf(TextNode(id = "t"), SurfaceNode(id = "s"), ColumnNode(id = "c"))) {
            assertSuccess("n", root, roundTrip("n", root))
        }
    }

    @Test
    fun namesRoundTripExactly() {
        val tree = TextNode(id = "t")
        for (name in listOf("  padded  ", "quote \" and \\ slash", "日本語 ✓", "line\nbreak", "{[ brackets ]}")) {
            assertSuccess(name, tree, roundTrip(name, tree))
        }
    }

    @Test
    fun awkwardFloatsAreRestoredExactly() {
        val values = listOf(0.1f, 1e-7f, 123456.79f, 3.4028235e38f, Float.MIN_VALUE, 16777217f, 0.3f)
        for (v in values) {
            val tree = SurfaceNode(
                id = "s",
                width = SizeSpec.Fixed(v),
                padding = PaddingSpec(v, v, v, v),
                shape = ShapeSpec.Rounded(v),
                elevation = v,
                borderWidth = v,
            )
            val result = roundTrip("n", tree)
            assertSuccess("n", tree, result)
            val back = (result as DecodeResult.Success).content.root as SurfaceNode
            assertEquals(v.toRawBits(), (back.shape as ShapeSpec.Rounded).radius.toRawBits())
        }
    }

    @Test
    fun argbLimitsAreRestoredExactly() {
        for (argb in listOf(0L, 1L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL)) {
            val tree = TextNode(id = "t", color = ColorSpec.Custom(argb))
            assertSuccess("n", tree, roundTrip("n", tree))
        }
    }

    // ---- what the output looks like ---------------------------------------

    @Test
    fun outputContainsEnvelopeDiscriminatorsAndIds() {
        val text = encodeConfig("Card", SampleNodes.nowPlayingCard().let { it as SurfaceNode }.copy(id = "my-root"))
        assertTrue(text, text.contains("\"formatVersion\": 1"))
        assertTrue(text, text.contains("\"name\": \"Card\""))
        assertTrue(text, text.contains("\"type\": \"surface\""))
        assertTrue(text, text.contains("\"type\": \"column\""))
        assertTrue(text, text.contains("\"type\": \"text\""))
        assertTrue(text, text.contains("\"id\": \"my-root\""))
    }

    @Test
    fun defaultValuesAreWrittenOut() {
        val text = encodeConfig("n", TextNode(id = "t")) // everything else is a default
        assertTrue(text, text.contains("\"style\""))
        assertTrue(text, text.contains("\"padding\""))
        assertTrue(text, text.contains("\"text\": \"Text\""))
    }

    @Test
    fun outputIsPrettyPrinted() {
        assertTrue(encodeConfig("n", TextNode(id = "t")).contains("\n"))
    }

    // ---- defaults may be omitted when decoding ----------------------------

    @Test
    fun propertiesWithDefaultsMayBeOmitted() {
        assertSuccess("Card", TextNode(id = "a"), decodeConfig(doc(textJson)))
    }

    @Test
    fun missingNodeIdGetsAGeneratedOne() {
        // id has a default in the model, so under "defaults may be omitted" a missing id is accepted.
        val result = decodeConfig(doc("""{"type":"text"}"""))
        assertTrue("was $result", result is DecodeResult.Success)
        val id = ((result as DecodeResult.Success).content.root).id
        assertFalse(id.isBlank())
    }

    // ---- malformed ---------------------------------------------------------

    @Test
    fun textThatIsNotJsonIsMalformed() {
        for (text in listOf("", "   ", "not json", "{", """{"formatVersion":1""", "{}}", doc(textJson) + " trailing")) {
            assertMalformed(text)
        }
    }

    @Test
    fun topLevelThatIsNotAnObjectIsMalformed() {
        for (text in listOf("[]", "42", "null", "\"text\"", "true")) assertMalformed(text)
    }

    @Test
    fun commentsAndTrailingCommasAreRejected() {
        assertMalformed("// hi\n" + doc(textJson))
        assertMalformed("""{"formatVersion":1,"name":"n","root":{"type":"text","id":"a",}}""")
    }

    @Test
    fun missingEnvelopeFieldsAreMalformed() {
        assertMalformed("""{"name":"n","root":$textJson}""") // no formatVersion
        assertMalformed("""{"formatVersion":1,"root":$textJson}""") // no name
        assertMalformed("""{"formatVersion":1,"name":"n"}""") // no root
    }

    @Test
    fun missingRequiredNestedFieldsAreMalformed() {
        // Fixed.value, Rounded.radius, Role.role and Custom.argb have no defaults.
        assertMalformed(doc("""{"type":"surface","id":"a","width":{"type":"fixed"}}"""))
        assertMalformed(doc("""{"type":"surface","id":"a","shape":{"type":"rounded"}}"""))
        assertMalformed(doc("""{"type":"text","id":"a","color":{"type":"role"}}"""))
        assertMalformed(doc("""{"type":"text","id":"a","color":{"type":"custom"}}"""))
    }

    @Test
    fun missingOrUnknownTypeDiscriminatorIsMalformed() {
        assertMalformed(doc("""{"id":"a"}"""))
        assertMalformed(doc("""{"type":"button","id":"a"}"""))
        assertMalformed(doc("""{"type":"Text","id":"a"}""")) // case matters
        assertMalformed(doc("""{"type":"surface","id":"a","shape":{"type":"hexagon"}}"""))
    }

    @Test
    fun unknownKeysAreMalformedAtEveryLevel() {
        assertMalformed("""{"formatVersion":1,"name":"n","root":$textJson,"extra":1}""")
        assertMalformed(doc("""{"type":"text","id":"a","extra":1}"""))
        assertMalformed(doc("""{"type":"text","id":"a","padding":{"start":1,"extra":2}}"""))
        assertMalformed(doc("""{"type":"surface","id":"a","width":{"type":"fixed","value":1,"extra":2}}"""))
    }

    @Test
    fun unknownEnumValuesAreMalformed() {
        assertMalformed(doc("""{"type":"text","id":"a","style":"Gigantic"}"""))
        assertMalformed(doc("""{"type":"text","id":"a","style":"bodymedium"}""")) // case matters
        assertMalformed(doc("""{"type":"text","id":"a","color":{"type":"role","role":"Pink"}}"""))
        assertMalformed(doc("""{"type":"surface","id":"a","contentAlignment":"Middle"}"""))
        assertMalformed(doc("""{"type":"column","id":"a","horizontalAlignment":"Left"}"""))
    }

    @Test
    fun wrongValueTypesAreMalformed() {
        assertMalformed(doc("""{"type":"text","id":"a","text":5}"""))
        assertMalformed(doc("""{"type":"text","id":"a","padding":{"start":"wide"}}"""))
        assertMalformed(doc("""{"type":"text","id":"a","padding":{"start":null}}"""))
        assertMalformed(doc("""{"type":"column","id":"a","children":{}}"""))
        assertMalformed(doc("""{"type":"text","id":"a","color":{"type":"custom","argb":"red"}}"""))
        assertMalformed(doc(textJson, name = "5"))
        assertMalformed(doc(textJson, name = "null"))
    }

    @Test
    fun nanAndInfinityAreMalformed() {
        assertMalformed(doc("""{"type":"surface","id":"a","shape":{"type":"rounded","radius":NaN}}"""))
        assertMalformed(doc("""{"type":"surface","id":"a","shape":{"type":"rounded","radius":Infinity}}"""))
        assertMalformed(doc("""{"type":"surface","id":"a","shape":{"type":"rounded","radius":"NaN"}}"""))
    }

    // ---- versions ----------------------------------------------------------

    @Test
    fun otherVersionsAreUnsupportedAndReportedAsSuch() {
        for (v in listOf(0, 2, 3, 99, -1)) {
            val result = decodeConfig(doc(textJson, version = v.toString()))
            assertEquals(DecodeResult.UnsupportedVersion(v), result)
        }
    }

    @Test
    fun unsupportedVersionIsReportedEvenWhenTheRestHasAnUnknownShape() {
        val result = decodeConfig("""{"formatVersion":2,"layers":[{"anything":true}]}""")
        assertEquals(DecodeResult.UnsupportedVersion(2), result)
    }

    @Test
    fun unusableVersionValuesAreMalformedNotUnsupported() {
        for (v in listOf("\"1\"", "1.0", "1.5", "true", "null", "[]", "{}", "9999999999")) {
            assertMalformed(doc(textJson, version = v))
        }
    }

    // ---- invalid (well-formed, breaks a rule) ------------------------------

    @Test
    fun duplicateIdsAreInvalid() {
        val col = """{"type":"column","id":"c","children":[$textJson,$textJson]}"""
        assertEquals(listOf(ValidationRule.DuplicateId), invalidRules(doc(col)))
    }

    @Test
    fun negativeValuesAreInvalid() {
        assertEquals(
            listOf(ValidationRule.NegativeValue),
            invalidRules(doc("""{"type":"text","id":"a","padding":{"start":-1}}""")),
        )
        assertEquals(
            listOf(ValidationRule.NegativeValue),
            invalidRules(doc("""{"type":"surface","id":"a","shape":{"type":"rounded","radius":-4}}""")),
        )
        assertEquals(
            listOf(ValidationRule.NegativeValue),
            invalidRules(doc("""{"type":"column","id":"a","spacing":-0.5}""")),
        )
        assertEquals(
            listOf(ValidationRule.NegativeValue),
            invalidRules(doc("""{"type":"surface","id":"a","width":{"type":"fixed","value":-10}}""")),
        )
    }

    @Test
    fun argbOutOfRangeIsInvalid() {
        for (bad in listOf("-1", "4294967296", "9223372036854775807")) {
            val node = """{"type":"text","id":"a","color":{"type":"custom","argb":$bad}}"""
            assertEquals(listOf(ValidationRule.ArgbOutOfRange), invalidRules(doc(node)))
        }
    }

    @Test
    fun badNamesAreInvalid() {
        assertEquals(listOf(ValidationRule.BlankName), invalidRules(doc(textJson, name = "\"\"")))
        assertEquals(listOf(ValidationRule.BlankName), invalidRules(doc(textJson, name = "\"   \"")))
        val tooLong = "\"" + "a".repeat(MAX_NAME_LENGTH + 1) + "\""
        assertEquals(listOf(ValidationRule.NameTooLong), invalidRules(doc(textJson, name = tooLong)))
    }

    @Test
    fun nameAndTreeProblemsAreReportedTogether() {
        val node = """{"type":"text","id":"a","padding":{"top":-1}}"""
        assertEquals(
            listOf(ValidationRule.BlankName, ValidationRule.NegativeValue),
            invalidRules(doc(node, name = "\"\"")),
        )
    }

    @Test
    fun invalidResultCarriesTheOffendingNode() {
        val result = decodeConfig(doc("""{"type":"text","id":"bad-one","padding":{"end":-3}}"""))
        val error = (result as DecodeResult.Invalid).errors.single()
        assertEquals("bad-one", error.nodeId)
        assertEquals("padding.end", error.field)
    }

    // ---- depth -------------------------------------------------------------

    @Test
    fun surfaceChainAtTheDepthLimitRoundTrips() {
        val tree = chain(MAX_TREE_DEPTH)
        assertSuccess("n", tree, roundTrip("n", tree))
    }

    @Test
    fun columnChainAtTheDepthLimitRoundTrips() {
        // Columns nest the most JSON per tree level, so this proves MAX_JSON_NESTING leaves room.
        val tree = columnChain(MAX_TREE_DEPTH)
        assertSuccess("n", tree, roundTrip("n", tree))
    }

    @Test
    fun treeOneLevelTooDeepIsInvalid() {
        val text = encodeConfig("n", chain(MAX_TREE_DEPTH + 1)) // encoding is fine; decoding refuses
        assertEquals(listOf(ValidationRule.TreeTooDeep), invalidRules(text))
    }

    @Test
    fun hugeNestingIsRejectedWithoutCrashing() {
        assertEquals(listOf(ValidationRule.TreeTooDeep), invalidRules("[".repeat(100_000)))
        val deepObjects = "{\"a\":".repeat(100_000)
        assertEquals(listOf(ValidationRule.TreeTooDeep), invalidRules(deepObjects))
    }

    @Test
    fun bracketsInsideStringsDoNotCountAsNesting() {
        val name = "\"" + "[".repeat(MAX_JSON_NESTING * 2) + "\""
        // Too long a name, but crucially not rejected for nesting.
        assertEquals(listOf(ValidationRule.NameTooLong), invalidRules(doc(textJson, name = name)))
    }

    @Test
    fun escapedQuotesInsideStringsDoNotConfuseTheNestingScan() {
        val node = """{"type":"text","id":"a","text":"quote \" then [[[[[[ more \\"}"""
        val result = decodeConfig(doc(node))
        assertTrue("was $result", result is DecodeResult.Success)
    }
}