package dev.isaac.composevisualizer.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.persistence.ConfigRepository
import dev.isaac.composevisualizer.persistence.ConfigSummary
import dev.isaac.composevisualizer.persistence.DeleteResult
import dev.isaac.composevisualizer.persistence.ListResult
import dev.isaac.composevisualizer.persistence.LoadResult
import dev.isaac.composevisualizer.persistence.MAX_FILE_BYTES
import dev.isaac.composevisualizer.persistence.SaveResult
import dev.isaac.composevisualizer.persistence.UnreadableConfig
import dev.isaac.composevisualizer.persistence.ValidationError
import dev.isaac.composevisualizer.persistence.newConfigId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A short note for the person using the app: what happened, and whether it went wrong. */
data class LibraryMessage(val text: String, val isError: Boolean)

/** What the Save / Open screens need. Immutable. */
data class LibraryUiState(
    val saved: List<ConfigSummary> = emptyList(),
    val unreadable: List<UnreadableConfig> = emptyList(),
    /** True while a file operation runs; the UI disables its buttons. */
    val isBusy: Boolean = false,
    val message: LibraryMessage? = null,
)

/**
 * Runs the file operations of the saved-configurations library off the main
 * thread and reports what happened. It never touches the editor's tree
 * itself: results are handed to the caller through callbacks, so the editor
 * ViewModel stays free of persistence.
 *
 * Only one operation runs at a time; a request made while busy is ignored
 * (the UI disables its buttons, this is the safety net).
 *
 * Each public function launches a coroutine that calls the matching `...Now`
 * function. The logic lives in the `...Now` functions so tests can call them
 * directly and wait for the result, without needing a Main dispatcher.
 */
class LibraryViewModel(private val repository: ConfigRepository) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Reloads the list of saved configurations. */
    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    /**
     * Saves [root] under [name]. [id] null means "a new configuration": a fresh
     * id is generated. The name is trimmed first. On success [onSaved] gets the
     * id, the trimmed name and the exact tree that was written.
     */
    fun save(id: String?, name: String, root: Node, onSaved: (id: String, name: String, root: Node) -> Unit) {
        viewModelScope.launch { saveNow(id, name, root, onSaved) }
    }

    /** Loads configuration [id]. On success [onOpened] gets its id, name and tree. */
    fun open(id: String, onOpened: (id: String, name: String, root: Node) -> Unit) {
        viewModelScope.launch { openNow(id, onOpened) }
    }

    /** Deletes configuration [id]. [onDeleted] runs if it is gone afterwards (deleted now or already missing). */
    fun delete(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch { deleteNow(id, onDeleted) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    // ---- the logic ---------------------------------------------------------

    internal suspend fun refreshNow() = exclusive {
        refreshList()
    }

    internal suspend fun saveNow(
        id: String?,
        name: String,
        root: Node,
        onSaved: (id: String, name: String, root: Node) -> Unit,
    ) = exclusive {
        val trimmed = name.trim()
        val target = id ?: newConfigId()
        val result = guarded { repository.save(target, trimmed, root) }.getOrElse {
            postMessage(unexpected("save", it), isError = true)
            return@exclusive
        }
        if (result is SaveResult.Saved) {
            onSaved(target, trimmed, root)
            postMessage("Saved \"$trimmed\"", isError = false)
            refreshList()
        } else {
            postMessage(describe(result), isError = true)
        }
    }

    internal suspend fun openNow(
        id: String,
        onOpened: (id: String, name: String, root: Node) -> Unit,
    ) = exclusive {
        val result = guarded { repository.load(id) }.getOrElse {
            postMessage(unexpected("open the configuration", it), isError = true)
            return@exclusive
        }
        when (result) {
            is LoadResult.Loaded -> onOpened(result.id, result.content.name, result.content.root)
            else -> {
                postMessage(describe(result), isError = true)
                // The list may be out of date (for example the file was removed outside the app).
                if (result is LoadResult.NotFound) refreshList()
            }
        }
    }

    internal suspend fun deleteNow(id: String, onDeleted: () -> Unit) = exclusive {
        val result = guarded { repository.delete(id) }.getOrElse {
            postMessage(unexpected("delete the configuration", it), isError = true)
            return@exclusive
        }
        when (result) {
            DeleteResult.Deleted, DeleteResult.NotFound -> {
                onDeleted()
                postMessage("Deleted", isError = false)
                refreshList()
            }
            is DeleteResult.IoFailure ->
                postMessage("Couldn't delete the file: ${result.message}", isError = true)
        }
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * Runs [block] only if nothing else is running, and always clears the busy
     * flag afterwards, even if [block] throws.
     */
    private suspend fun exclusive(block: suspend () -> Unit) {
        if (!tryStart()) return
        try {
            block()
        } finally {
            _state.update { it.copy(isBusy = false) }
        }
    }

    private fun tryStart(): Boolean {
        var started = false
        _state.update { state ->
            started = false // update() may re-run this lambda; only the final run counts
            if (state.isBusy) {
                state
            } else {
                started = true
                state.copy(isBusy = true, message = null)
            }
        }
        return started
    }

    /**
     * Runs a blocking repository call on the IO dispatcher and turns an
     * unexpected exception into a failed [Result], so it can't escape into
     * `viewModelScope` and crash the app. The repository reports every expected
     * problem as a result value; this is the safety net for the rest.
     *
     * Cancellation is deliberately NOT caught: [CancellationException] is how
     * coroutines stop, and must keep propagating. Only [Exception]s are caught,
     * never [Error]s (out of memory and the like), which shouldn't be hidden.
     */
    private suspend fun <T> guarded(call: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(call())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Reloads the list into the state, or posts a message if even that fails. */
    private suspend fun refreshList() {
        val listing = guarded { repository.list() }.getOrElse {
            postMessage(unexpected("read the saved configurations", it), isError = true)
            return
        }
        applyListing(listing)
    }

    private fun applyListing(listing: ListResult) {
        when (listing) {
            is ListResult.Listed -> _state.update {
                it.copy(saved = listing.saved, unreadable = listing.unreadable)
            }
            is ListResult.IoFailure ->
                postMessage("Couldn't read the saved configurations: ${listing.message}", isError = true)
        }
    }

    private fun postMessage(text: String, isError: Boolean) {
        _state.update { it.copy(message = LibraryMessage(text, isError)) }
    }
}

// ---- wording of failures (plain functions, so the texts are easy to find and change) ----

private fun describe(result: SaveResult): String = when (result) {
    is SaveResult.Saved -> "Saved"
    is SaveResult.Invalid -> "Can't save: ${firstProblem(result.errors)}"
    is SaveResult.TooLarge ->
        "Can't save: the configuration is larger than the ${MAX_FILE_BYTES / (1024 * 1024)} MB limit"
    SaveResult.AtomicMoveUnsupported ->
        "Nothing was saved: this device's storage can't replace files safely"
    is SaveResult.IoFailure -> "Couldn't save: ${result.message}"
}

private fun describe(result: LoadResult): String = when (result) {
    is LoadResult.Loaded -> "Opened"
    is LoadResult.Malformed -> "This file isn't a valid configuration: ${result.message}"
    is LoadResult.UnsupportedVersion ->
        "This configuration uses format version ${result.found}, which this version of the app can't read"
    is LoadResult.Invalid -> "This configuration has invalid content: ${firstProblem(result.errors)}"
    LoadResult.NotFound -> "That configuration no longer exists"
    is LoadResult.IoFailure -> "Couldn't read the file: ${result.message}"
}

private fun unexpected(action: String, error: Throwable): String {
    val detail = error.message?.let { ": $it" } ?: ""
    return "Couldn't $action: unexpected error (${error.javaClass.simpleName}$detail)"
}

private fun firstProblem(errors: List<ValidationError>): String {
    val first = errors.firstOrNull()?.message ?: "unknown problem"
    val more = errors.size - 1
    return if (more > 0) "$first (and $more more)" else first
}