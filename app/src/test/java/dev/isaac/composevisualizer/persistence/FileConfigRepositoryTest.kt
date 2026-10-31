package dev.isaac.composevisualizer.persistence

import dev.isaac.composevisualizer.model.ColorSpec
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.PaddingSpec
import dev.isaac.composevisualizer.model.SampleNodes
import dev.isaac.composevisualizer.model.ShapeSpec
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileConfigRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** A folder that does not exist yet, so tests also cover the repository creating it. */
    private val dir: File by lazy { File(tmp.root, "components") }

    private fun repo(atomicMove: AtomicMove = AtomicMove.Default) = FileConfigRepository(dir, atomicMove)

    private fun card(text: String = "Hello"): Node = SurfaceNode(
        id = "root",
        color = ColorSpec.Custom(0xFF336699L),
        shape = ShapeSpec.Rounded(16f),
        padding = PaddingSpec.all(8f),
        child = ColumnNode(id = "col", children = listOf(TextNode(id = "t", text = text))),
    )

    private fun fileOf(id: String) = File(dir, "$id.json")

    private fun tempFiles(): List<String> =
        dir.list()?.filter { it.endsWith(".tmp") }.orEmpty()

    private fun writeRaw(id: String, text: String) {
        dir.mkdirs()
        fileOf(id).writeText(text)
    }

    private fun assertLoaded(expectedName: String, expectedRoot: Node, result: LoadResult) {
        assertTrue("expected Loaded but was $result", result is LoadResult.Loaded)
        val content = (result as LoadResult.Loaded).content
        assertEquals(expectedName, content.name)
        assertEquals(expectedRoot, content.root)
    }

    private fun assertRejectsId(action: () -> Unit) {
        try {
            action()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // good
        }
    }

    // ---- ids ---------------------------------------------------------------

    @Test
    fun generatedIdsAreValidAndUnique() {
        val ids = List(50) { newConfigId() }
        assertTrue(ids.all { isValidConfigId(it) })
        assertEquals(50, ids.toSet().size)
    }

    @Test
    fun onlyLowercaseUuidShapedIdsAreValid() {
        val good = "123e4567-e89b-12d3-a456-426614174000"
        assertTrue(isValidConfigId(good))
        for (bad in listOf(
            "", "abc", "../x", "../../etc/passwd", "a/b", good.uppercase(), "$good.json", " $good", "$good\n",
            good.replace("-", ""), good.dropLast(1), good + "0",
        )) {
            assertFalse("should be invalid: \"$bad\"", isValidConfigId(bad))
        }
    }

    @Test
    fun everyOperationRefusesInvalidIds() {
        val repo = repo()
        assertRejectsId { repo.save("../x", "n", card()) }
        assertRejectsId { repo.load("../x") }
        assertRejectsId { repo.delete("../x") }
        assertRejectsId { repo.save("", "n", card()) }
        assertFalse("nothing may be created for a bad id", dir.exists())
    }

    // ---- save and load -----------------------------------------------------

    @Test
    fun saveThenLoadRoundTrips() {
        val repo = repo()
        val id = newConfigId()
        assertEquals(SaveResult.Saved(id), repo.save(id, "My card", card()))
        assertLoaded("My card", card(), repo.load(id))
    }

    @Test
    fun theFileIsNamedAfterTheIdNotTheName() {
        val repo = repo()
        val id = newConfigId()
        repo.save(id, "Name with / slashes and ../dots", card())
        assertEquals(listOf("$id.json"), dir.list()!!.toList())
    }

    @Test
    fun savedFileIsReadableByTheCodec() {
        val id = newConfigId()
        repo().save(id, "n", card())
        val result = decodeConfig(fileOf(id).readText())
        assertTrue(result is DecodeResult.Success)
    }

    @Test
    fun saveCreatesTheDirectory() {
        assertFalse(dir.exists())
        repo().save(newConfigId(), "n", card())
        assertTrue(dir.isDirectory)
    }

    @Test
    fun aRealisticTreeRoundTrips() {
        val repo = repo()
        val id = newConfigId()
        val tree = SampleNodes.nowPlayingCard()
        repo.save(id, "Sample", tree)
        assertLoaded("Sample", tree, repo.load(id))
    }

    @Test
    fun unicodeNamesAndTextSurviveTheDisk() {
        val repo = repo()
        val id = newConfigId()
        val tree = TextNode(id = "t", text = "日本語 ✓ 🎵")
        repo.save(id, "名前 ✓", tree)
        assertLoaded("名前 ✓", tree, repo.load(id))
    }

    @Test
    fun savingAgainReplacesTheContentAndLeavesNoTempFile() {
        val repo = repo()
        val id = newConfigId()
        repo.save(id, "First", card("one"))
        repo.save(id, "Second", card("two"))
        assertLoaded("Second", card("two"), repo.load(id))
        assertEquals(listOf("$id.json"), dir.list()!!.toList())
    }

    @Test
    fun differentIdsAreIndependent() {
        val repo = repo()
        val a = newConfigId()
        val b = newConfigId()
        repo.save(a, "A", card("a"))
        repo.save(b, "B", card("b"))
        assertLoaded("A", card("a"), repo.load(a))
        assertLoaded("B", card("b"), repo.load(b))
    }

    // ---- save refusals -----------------------------------------------------

    @Test
    fun invalidTreeIsRefusedAndNothingIsWritten() {
        val bad = SurfaceNode(id = "s", padding = PaddingSpec(top = -1f))
        val result = repo().save(newConfigId(), "n", bad)
        assertTrue("was $result", result is SaveResult.Invalid)
        assertEquals(ValidationRule.NegativeValue, (result as SaveResult.Invalid).errors.single().rule)
        assertFalse(dir.exists())
    }

    @Test
    fun invalidNameIsRefusedAndNothingIsWritten() {
        for (name in listOf("", "   ", "a".repeat(MAX_NAME_LENGTH + 1))) {
            assertTrue(repo().save(newConfigId(), name, card()) is SaveResult.Invalid)
        }
        assertFalse(dir.exists())
    }

    @Test
    fun anInvalidSaveLeavesAnExistingFileUntouched() {
        val repo = repo()
        val id = newConfigId()
        repo.save(id, "Good", card("good"))
        val before = fileOf(id).readText()
        assertTrue(repo.save(id, "Bad", SurfaceNode(id = "s", elevation = -1f)) is SaveResult.Invalid)
        assertEquals(before, fileOf(id).readText())
    }

    @Test
    fun aFileThatLoadingWouldRefuseForSizeIsNotWritten() {
        // Wide and shallow: valid by every rule, but far beyond MAX_FILE_BYTES once encoded.
        val wide = ColumnNode(id = "col", children = List(20_000) { TextNode(id = "n$it", text = "x".repeat(40)) })
        val result = repo().save(newConfigId(), "n", wide)
        assertTrue("was $result", result is SaveResult.TooLarge)
        assertTrue((result as SaveResult.TooLarge).bytes > MAX_FILE_BYTES)
        assertFalse(dir.exists())
    }

    // ---- atomic move handling ---------------------------------------------

    @Test
    fun atomicMoveUnsupportedIsReportedAndNeverFallsBack() {
        val id = newConfigId()
        repo().save(id, "Original", card("original"))
        val before = fileOf(id).readText()

        var calls = 0
        val unsupported = repo(AtomicMove { from, to ->
            calls++
            throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "not supported here")
        })

        assertEquals(SaveResult.AtomicMoveUnsupported, unsupported.save(id, "Replacement", card("replacement")))
        assertEquals("exactly one attempt, no retry with another method", 1, calls)
        assertEquals("old file untouched", before, fileOf(id).readText())
        assertEquals("temp file cleaned up", emptyList<String>(), tempFiles())
    }

    @Test
    fun atomicMoveUnsupportedOnAFirstSaveCreatesNoConfiguration() {
        val id = newConfigId()
        val result = repo(AtomicMove { from, to ->
            throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "nope")
        }).save(id, "n", card())
        assertEquals(SaveResult.AtomicMoveUnsupported, result)
        assertFalse(fileOf(id).exists())
        assertEquals(emptyList<String>(), tempFiles())
    }

    @Test
    fun failureDuringTheMoveKeepsTheOldFileAndIsAnIoFailure() {
        val id = newConfigId()
        repo().save(id, "Original", card("original"))
        val before = fileOf(id).readText()

        val failing = repo(AtomicMove { _, _ -> throw IOException("disk on fire") })
        val result = failing.save(id, "Replacement", card("replacement"))

        assertTrue("was $result", result is SaveResult.IoFailure)
        assertTrue((result as SaveResult.IoFailure).message.contains("disk on fire"))
        assertEquals(before, fileOf(id).readText())
        assertEquals(emptyList<String>(), tempFiles())
        assertLoaded("Original", card("original"), repo().load(id))
    }

    @Test
    fun whenTheDirectoryCannotBeCreatedTheResultIsAnIoFailure() {
        // A plain file sits where the folder should be.
        File(tmp.root, "components").writeText("i am a file")
        val result = repo().save(newConfigId(), "n", card())
        assertTrue("was $result", result is SaveResult.IoFailure)
    }

    // ---- load failures -----------------------------------------------------

    @Test
    fun loadingAMissingConfigurationIsNotFound() {
        assertEquals(LoadResult.NotFound, repo().load(newConfigId()))
        dir.mkdirs()
        assertEquals(LoadResult.NotFound, repo().load(newConfigId()))
    }

    @Test
    fun corruptTextIsMalformed() {
        val id = newConfigId()
        for (text in listOf("", "not json", "{\"formatVersion\":1", "[]")) {
            writeRaw(id, text)
            assertTrue("for \"$text\"", repo().load(id) is LoadResult.Malformed)
        }
    }

    @Test
    fun aNewerFormatVersionIsReportedAsUnsupported() {
        val id = newConfigId()
        writeRaw(id, """{"formatVersion":2,"somethingNew":true}""")
        assertEquals(LoadResult.UnsupportedVersion(2), repo().load(id))
    }

    @Test
    fun wellFormedButInvalidContentIsInvalid() {
        val id = newConfigId()
        writeRaw(
            id,
            """{"formatVersion":1,"name":"n","root":{"type":"column","id":"c","children":[{"type":"text","id":"a"},{"type":"text","id":"a"}]}}""",
        )
        val result = repo().load(id)
        assertTrue("was $result", result is LoadResult.Invalid)
        assertEquals(ValidationRule.DuplicateId, (result as LoadResult.Invalid).errors.single().rule)
    }

    @Test
    fun invalidUtf8IsMalformedNotSilentlyRepaired() {
        val id = newConfigId()
        dir.mkdirs()
        // A valid-looking document with a stray 0xFF byte (never valid in UTF-8) in the name.
        val prefix = """{"formatVersion":1,"name":"a""".toByteArray()
        val suffix = """b","root":{"type":"text","id":"a"}}""".toByteArray()
        fileOf(id).writeBytes(prefix + byteArrayOf(0xFF.toByte()) + suffix)
        val result = repo().load(id)
        assertTrue("was $result", result is LoadResult.Malformed)
        assertTrue((result as LoadResult.Malformed).message.contains("UTF-8"))
    }

    @Test
    fun anOversizedFileIsMalformedWithoutBeingParsed() {
        val id = newConfigId()
        dir.mkdirs()
        fileOf(id).writeBytes(ByteArray((MAX_FILE_BYTES + 1).toInt()) { 'a'.code.toByte() })
        val result = repo().load(id)
        assertTrue("was $result", result is LoadResult.Malformed)
        assertTrue((result as LoadResult.Malformed).message.contains("larger"))
    }

    @Test
    fun aFolderWhereTheFileShouldBeIsAnIoFailure() {
        val id = newConfigId()
        fileOf(id).mkdirs()
        assertTrue(repo().load(id) is LoadResult.IoFailure)
    }

    // ---- list --------------------------------------------------------------

    @Test
    fun listingAMissingFolderIsEmptyNotAnError() {
        assertEquals(ListResult.Listed(emptyList(), emptyList()), repo().list())
    }

    @Test
    fun listShowsSavedConfigurationsNewestFirst() {
        val repo = repo()
        val old = newConfigId()
        val middle = newConfigId()
        val fresh = newConfigId()
        repo.save(old, "Old", card())
        repo.save(middle, "Middle", card())
        repo.save(fresh, "Fresh", card())
        fileOf(old).setLastModified(1_000_000L)
        fileOf(middle).setLastModified(2_000_000L)
        fileOf(fresh).setLastModified(3_000_000L)

        val listed = repo.list() as ListResult.Listed
        assertEquals(listOf("Fresh", "Middle", "Old"), listed.saved.map { it.name })
        assertEquals(listOf(fresh, middle, old), listed.saved.map { it.id })
        assertEquals(3_000_000L, listed.saved.first().modifiedAtMillis)
        assertTrue(listed.unreadable.isEmpty())
    }

    @Test
    fun duplicateDisplayNamesAreAllowed() {
        val repo = repo()
        repo.save(newConfigId(), "Same", card("1"))
        repo.save(newConfigId(), "Same", card("2"))
        assertEquals(2, (repo.list() as ListResult.Listed).saved.size)
    }

    @Test
    fun brokenFilesAreListedSeparatelyAndDontHideGoodOnes() {
        val repo = repo()
        val good = newConfigId()
        val broken = newConfigId()
        val future = newConfigId()
        repo.save(good, "Good", card())
        writeRaw(broken, "garbage")
        writeRaw(future, """{"formatVersion":9}""")

        val listed = repo.list() as ListResult.Listed
        assertEquals(listOf(good), listed.saved.map { it.id })
        assertEquals(setOf(broken, future), listed.unreadable.map { it.id }.toSet())
        assertTrue(listed.unreadable.all { it.problem.isNotBlank() })
    }

    @Test
    fun listIgnoresFilesTheAppDidNotWrite() {
        val repo = repo()
        val good = newConfigId()
        repo.save(good, "Good", card())
        File(dir, "notes.txt").writeText("hi")
        File(dir, "x.json").writeText("{}")
        File(dir, "${newConfigId()}.json.tmp").writeText("leftover")
        File(dir, "${newConfigId().uppercase()}.json").writeText("{}")

        val listed = repo.list() as ListResult.Listed
        assertEquals(listOf(good), listed.saved.map { it.id })
        assertTrue(listed.unreadable.isEmpty())
    }

    @Test
    fun listingWhereTheFolderIsReallyAFileIsAnIoFailure() {
        File(tmp.root, "components").writeText("i am a file")
        assertTrue(repo().list() is ListResult.IoFailure)
    }

    // ---- delete ------------------------------------------------------------

    @Test
    fun deleteRemovesTheConfiguration() {
        val repo = repo()
        val id = newConfigId()
        repo.save(id, "n", card())
        assertEquals(DeleteResult.Deleted, repo.delete(id))
        assertEquals(LoadResult.NotFound, repo.load(id))
        assertTrue((repo.list() as ListResult.Listed).saved.isEmpty())
    }

    @Test
    fun deletingAMissingConfigurationIsNotFound() {
        assertEquals(DeleteResult.NotFound, repo().delete(newConfigId()))
        dir.mkdirs()
        assertEquals(DeleteResult.NotFound, repo().delete(newConfigId()))
    }

    @Test
    fun aBrokenFileCanBeDeleted() {
        val id = newConfigId()
        writeRaw(id, "garbage")
        assertEquals(DeleteResult.Deleted, repo().delete(id))
        assertFalse(fileOf(id).exists())
    }

    @Test
    fun deleteOnlyAffectsTheRequestedConfiguration() {
        val repo = repo()
        val keep = newConfigId()
        val drop = newConfigId()
        repo.save(keep, "Keep", card("k"))
        repo.save(drop, "Drop", card("d"))
        repo.delete(drop)
        assertLoaded("Keep", card("k"), repo.load(keep))
    }

    // ---- concurrency -------------------------------------------------------

    @Test
    fun simultaneousSavesToOneIdLeaveOneCompleteFile() {
        val repo = repo()
        val id = newConfigId()
        val contents = List(8) { "version $it" }
        val results = java.util.Collections.synchronizedList(mutableListOf<SaveResult>())

        val threads = contents.map { text ->
            Thread { repeat(10) { results += repo.save(id, text, card(text)) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue("every save should succeed: $results", results.all { it is SaveResult.Saved })
        val loaded = repo.load(id) as LoadResult.Loaded
        assertTrue(loaded.content.name in contents)
        assertEquals(card(loaded.content.name), loaded.content.root)
        assertEquals(listOf("$id.json"), dir.list()!!.toList())
    }
}