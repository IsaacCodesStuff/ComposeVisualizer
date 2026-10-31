package dev.isaac.composevisualizer.editor

import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.TextNode
import dev.isaac.composevisualizer.persistence.ConfigSummary
import dev.isaac.composevisualizer.persistence.DeleteResult
import dev.isaac.composevisualizer.persistence.ListResult
import dev.isaac.composevisualizer.persistence.LoadResult
import dev.isaac.composevisualizer.persistence.SaveResult
import dev.isaac.composevisualizer.persistence.UnreadableConfig
import dev.isaac.composevisualizer.persistence.ValidationError
import dev.isaac.composevisualizer.persistence.ValidationRule
import dev.isaac.composevisualizer.persistence.isValidConfigId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Drives the `...Now` functions with runBlocking, so no Main dispatcher is needed.
 * The thin `viewModelScope.launch` wrappers around them are covered by manual testing.
 */
class LibraryViewModelTest {

    private val repo = FakeConfigRepository()
    private val vm = LibraryViewModel(repo)

    private val tree: Node = ColumnNode(id = "c", children = listOf(TextNode(id = "t")))

    /** Records what a caller's callback received. */
    private class Received {
        var calls = 0
        var id: String? = null
        var name: String? = null
        var root: Node? = null
    }

    private fun save(id: String?, name: String, root: Node = tree): Received {
        val received = Received()
        runBlocking {
            vm.saveNow(id, name, root) { savedId, savedName, savedRoot ->
                received.calls++
                received.id = savedId
                received.name = savedName
                received.root = savedRoot
            }
        }
        return received
    }

    private fun open(id: String): Received {
        val received = Received()
        runBlocking {
            vm.openNow(id) { openedId, openedName, openedRoot ->
                received.calls++
                received.id = openedId
                received.name = openedName
                received.root = openedRoot
            }
        }
        return received
    }

    private fun delete(id: String): Int {
        var calls = 0
        runBlocking { vm.deleteNow(id) { calls++ } }
        return calls
    }

    private val state get() = vm.state.value

    private fun error(text: String = "x") = ValidationError(ValidationRule.NegativeValue, "n", "f", text)

    // ---- refresh -----------------------------------------------------------

    @Test
    fun startsEmptyAndIdle() {
        assertTrue(state.saved.isEmpty())
        assertTrue(state.unreadable.isEmpty())
        assertFalse(state.isBusy)
        assertNull(state.message)
    }

    @Test
    fun refreshShowsSavedAndUnreadableConfigurations() {
        repo.listOverride = ListResult.Listed(
            saved = listOf(ConfigSummary("a", "Alpha", 5L)),
            unreadable = listOf(UnreadableConfig("b", "broken")),
        )
        runBlocking { vm.refreshNow() }
        assertEquals(listOf("Alpha"), state.saved.map { it.name })
        assertEquals(listOf("b"), state.unreadable.map { it.id })
        assertFalse(state.isBusy)
        assertNull(state.message)
    }

    @Test
    fun aFailedRefreshReportsAnErrorAndKeepsTheOldList() {
        repo.listOverride = ListResult.Listed(listOf(ConfigSummary("a", "Alpha", 1L)), emptyList())
        runBlocking { vm.refreshNow() }

        repo.listOverride = ListResult.IoFailure("storage gone")
        runBlocking { vm.refreshNow() }

        assertEquals(listOf("Alpha"), state.saved.map { it.name })
        assertTrue(state.message!!.isError)
        assertTrue(state.message!!.text.contains("storage gone"))
        assertFalse(state.isBusy)
    }

    // ---- save --------------------------------------------------------------

    @Test
    fun savingANewConfigurationGeneratesAValidId() {
        val received = save(id = null, name = "My card")
        assertEquals(1, received.calls)
        assertTrue(isValidConfigId(received.id!!))
        assertEquals(received.id, repo.saveCalls.single().first)
    }

    @Test
    fun savingTwiceAsNewGivesDifferentIds() {
        val first = save(id = null, name = "A")
        val second = save(id = null, name = "A")
        assertTrue(first.id != second.id)
        assertEquals(2, repo.store.size)
    }

    @Test
    fun savingAnExistingConfigurationKeepsItsId() {
        val first = save(id = null, name = "A")
        val again = save(id = first.id, name = "A", root = TextNode(id = "other"))
        assertEquals(first.id, again.id)
        assertEquals(1, repo.store.size)
        assertEquals("other", repo.store.getValue(first.id!!).second.id)
    }

    @Test
    fun theNameIsTrimmedBeforeSavingAndInTheCallback() {
        val received = save(id = null, name = "   Padded name \n")
        assertEquals("Padded name", repo.saveCalls.single().second)
        assertEquals("Padded name", received.name)
    }

    @Test
    fun theCallbackGetsTheExactTreeThatWasSaved() {
        val received = save(id = null, name = "A")
        assertSame(tree, received.root)
        assertSame(tree, repo.saveCalls.single().third)
    }

    @Test
    fun aSuccessfulSaveRefreshesTheListAndSaysSo() {
        save(id = null, name = "Shown in list")
        assertEquals(listOf("Shown in list"), state.saved.map { it.name })
        val message = state.message!!
        assertFalse(message.isError)
        assertTrue(message.text.contains("Shown in list"))
        assertFalse(state.isBusy)
    }

    @Test
    fun aBlankNameIsRefusedByTheRepositoryAndNothingIsReported() {
        repo.saveOverride = SaveResult.Invalid(
            listOf(ValidationError(ValidationRule.BlankName, null, "name", "Name can't be empty")),
        )
        val received = save(id = null, name = "   ")
        assertEquals(0, received.calls)
        assertTrue(state.message!!.isError)
        assertTrue(state.message!!.text.contains("Name can't be empty"))
    }

    @Test
    fun everyKindOfSaveFailureHasItsOwnMessageAndSkipsTheCallback() {
        val failures = listOf(
            SaveResult.Invalid(listOf(error("padding.top can't be negative"), error("second"))),
            SaveResult.TooLarge(9_999_999L),
            SaveResult.AtomicMoveUnsupported,
            SaveResult.IoFailure("disk full"),
        )
        val messages = failures.map { failure ->
            repo.saveOverride = failure
            val received = save(id = null, name = "n")
            assertEquals("callback must not run for $failure", 0, received.calls)
            assertFalse(state.isBusy)
            val message = state.message!!
            assertTrue(message.isError)
            message.text
        }
        assertEquals("each failure reads differently", messages.size, messages.toSet().size)
        assertTrue(messages[0].contains("padding.top can't be negative") && messages[0].contains("1 more"))
        assertTrue(messages[1].contains("MB"))
        assertTrue(messages[2].contains("can't replace files safely"))
        assertTrue(messages[3].contains("disk full"))
    }

    @Test
    fun aFailedSaveDoesNotChangeTheList() {
        save(id = null, name = "Existing")
        repo.saveOverride = SaveResult.IoFailure("nope")
        save(id = null, name = "Another")
        assertEquals(listOf("Existing"), state.saved.map { it.name })
    }

    // ---- open --------------------------------------------------------------

    @Test
    fun openingHandsTheLoadedConfigurationToTheCallback() {
        val saved = save(id = null, name = "Stored")
        val opened = open(saved.id!!)
        assertEquals(1, opened.calls)
        assertEquals(saved.id, opened.id)
        assertEquals("Stored", opened.name)
        assertSame(tree, opened.root)
        assertNull(state.message)
        assertFalse(state.isBusy)
    }

    @Test
    fun everyKindOfOpenFailureHasItsOwnMessageAndSkipsTheCallback() {
        val failures = listOf(
            LoadResult.Malformed("bad key"),
            LoadResult.UnsupportedVersion(7),
            LoadResult.Invalid(listOf(error("duplicate id"))),
            LoadResult.NotFound,
            LoadResult.IoFailure("permission denied"),
        )
        val messages = failures.map { failure ->
            repo.loadOverride = failure
            val opened = open("any")
            assertEquals("callback must not run for $failure", 0, opened.calls)
            assertFalse(state.isBusy)
            val message = state.message!!
            assertTrue(message.isError)
            message.text
        }
        assertEquals(messages.size, messages.toSet().size)
        assertTrue(messages[0].contains("bad key"))
        assertTrue(messages[1].contains("7"))
        assertTrue(messages[2].contains("duplicate id"))
        assertTrue(messages[3].contains("no longer exists"))
        assertTrue(messages[4].contains("permission denied"))
    }

    @Test
    fun openingAMissingConfigurationRefreshesTheStaleList() {
        val saved = save(id = null, name = "Ghost")
        repo.store.clear() // file vanished behind the app's back
        assertEquals(1, state.saved.size)

        open(saved.id!!)

        assertTrue("list should no longer show the vanished entry", state.saved.isEmpty())
    }

    // ---- delete ------------------------------------------------------------

    @Test
    fun deletingRemovesItAndRefreshesTheList() {
        val saved = save(id = null, name = "Doomed")
        assertEquals(1, delete(saved.id!!))
        assertTrue(state.saved.isEmpty())
        assertFalse(state.message!!.isError)
    }

    @Test
    fun deletingSomethingAlreadyGoneStillCountsAsDeleted() {
        repo.deleteOverride = DeleteResult.NotFound
        assertEquals(1, delete("any"))
    }

    @Test
    fun aFailedDeleteReportsAnErrorAndSkipsTheCallback() {
        repo.deleteOverride = DeleteResult.IoFailure("read-only")
        assertEquals(0, delete("any"))
        assertTrue(state.message!!.isError)
        assertTrue(state.message!!.text.contains("read-only"))
        assertFalse(state.isBusy)
    }

    // ---- busy handling and messages ---------------------------------------

    @Test
    fun aRequestWhileBusyIsIgnored() {
        val second = Received()
        repo.beforeSave = {
            // Runs inside the first save, while the library is busy.
            assertTrue(state.isBusy)
            runBlocking {
                vm.saveNow(null, "second", tree) { _, _, _ -> second.calls++ }
            }
        }
        val first = save(id = null, name = "first")

        assertEquals("only the first save reaches the repository", 1, repo.saveCalls.size)
        assertEquals(1, first.calls)
        assertEquals("the ignored request never calls back", 0, second.calls)
        assertFalse(state.isBusy)
    }

    @Test
    fun aNewOperationClearsTheOldMessage() {
        repo.saveOverride = SaveResult.IoFailure("first failure")
        save(id = null, name = "n")
        assertNotNull(state.message)

        repo.saveOverride = null
        runBlocking { vm.refreshNow() }

        assertNull(state.message)
    }

    @Test
    fun dismissMessageClearsIt() {
        repo.saveOverride = SaveResult.IoFailure("x")
        save(id = null, name = "n")
        assertNotNull(state.message)
        vm.dismissMessage()
        assertNull(state.message)
    }

    // ---- unexpected exceptions ---------------------------------------------

    @Test
    fun anExceptionDuringSaveBecomesAMessageAndNeverEscapes() {
        repo.onCall = { if (it == "save") throw IllegalStateException("boom") }
        val received = save(id = null, name = "n") // would throw here if it escaped

        assertEquals(0, received.calls)
        assertFalse(state.isBusy)
        val message = state.message!!
        assertTrue(message.isError)
        assertTrue(message.text.contains("IllegalStateException") && message.text.contains("boom"))
        assertTrue(message.text.contains("save"))
    }

    @Test
    fun anExceptionDuringOpenBecomesAMessage() {
        repo.onCall = { if (it == "load") throw RuntimeException("disk exploded") }
        val opened = open("any")

        assertEquals(0, opened.calls)
        assertFalse(state.isBusy)
        assertTrue(state.message!!.isError)
        assertTrue(state.message!!.text.contains("disk exploded"))
        assertTrue(state.message!!.text.contains("open"))
    }

    @Test
    fun anExceptionDuringDeleteBecomesAMessage() {
        repo.onCall = { if (it == "delete") throw SecurityException("denied") }
        assertEquals(0, delete("any"))

        assertFalse(state.isBusy)
        assertTrue(state.message!!.isError)
        assertTrue(state.message!!.text.contains("denied"))
        assertTrue(state.message!!.text.contains("delete"))
    }

    @Test
    fun anExceptionDuringRefreshBecomesAMessageAndKeepsTheOldList() {
        save(id = null, name = "Existing")
        repo.onCall = { if (it == "list") throw RuntimeException("cannot list") }

        runBlocking { vm.refreshNow() }

        assertEquals(listOf("Existing"), state.saved.map { it.name })
        assertTrue(state.message!!.isError)
        assertTrue(state.message!!.text.contains("cannot list"))
        assertFalse(state.isBusy)
    }

    @Test
    fun anExceptionWhileRefreshingAfterASuccessfulSaveDoesNotUndoTheSave() {
        repo.onCall = { if (it == "list") throw RuntimeException("cannot list") }
        val received = save(id = null, name = "Kept")

        assertEquals("the save itself succeeded", 1, received.calls)
        assertEquals(1, repo.store.size)
        assertTrue(state.message!!.isError)
        assertFalse(state.isBusy)
    }

    @Test
    fun anExceptionWithoutAMessageStillProducesAReadableMessage() {
        repo.onCall = { if (it == "save") throw RuntimeException() }
        save(id = null, name = "n")
        assertTrue(state.message!!.text.contains("RuntimeException"))
    }

    @Test
    fun aBugInACallbackIsNotMaskedAsAStorageProblem() {
        try {
            runBlocking {
                vm.saveNow(null, "n", tree) { _, _, _ -> throw IllegalStateException("callback bug") }
            }
            fail("a callback's exception should still surface")
        } catch (expected: IllegalStateException) {
            assertEquals("callback bug", expected.message)
        }
        assertFalse("busy flag is still cleared by finally", state.isBusy)
    }

    @Test
    fun cancellationIsNotSwallowed() {
        repo.onCall = { if (it == "save") throw CancellationException("cancelled") }
        try {
            save(id = null, name = "n")
            fail("cancellation must propagate")
        } catch (expected: CancellationException) {
            // good: coroutine cancellation keeps working
        }
        assertFalse(state.isBusy)
        assertNull("cancellation is not an error to show", state.message)
    }

    @Test
    fun seriousErrorsAreNotCaught() {
        repo.onCall = { if (it == "save") throw OutOfMemoryError("pretend") }
        try {
            save(id = null, name = "n")
            fail("an Error must not be swallowed")
        } catch (expected: OutOfMemoryError) {
            // good
        }
        assertFalse(state.isBusy)
    }

    @Test
    fun theLibraryKeepsWorkingAfterAnException() {
        repo.onCall = { if (it == "save") throw RuntimeException("once") }
        save(id = null, name = "first")
        repo.onCall = null

        val received = save(id = null, name = "second")

        assertEquals(1, received.calls)
        assertFalse(state.message!!.isError)
    }
}