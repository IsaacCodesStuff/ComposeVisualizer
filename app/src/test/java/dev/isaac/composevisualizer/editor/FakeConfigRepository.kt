package dev.isaac.composevisualizer.editor

import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.persistence.ConfigContent
import dev.isaac.composevisualizer.persistence.ConfigRepository
import dev.isaac.composevisualizer.persistence.ConfigSummary
import dev.isaac.composevisualizer.persistence.DeleteResult
import dev.isaac.composevisualizer.persistence.ListResult
import dev.isaac.composevisualizer.persistence.LoadResult
import dev.isaac.composevisualizer.persistence.SaveResult

/**
 * An in-memory [ConfigRepository] for tests, with no files involved. By default
 * it behaves like a working repository; set an `...Override` to script a
 * specific outcome (a failure, say) for one test.
 */
class FakeConfigRepository : ConfigRepository {

    /** Saved configurations by id, in the order they were first saved. */
    val store = linkedMapOf<String, Pair<String, Node>>()

    var listOverride: ListResult? = null
    var loadOverride: LoadResult? = null
    var saveOverride: SaveResult? = null
    var deleteOverride: DeleteResult? = null

    /** Called at the start of every save, before anything is recorded. */
    var beforeSave: (() -> Unit)? = null

    /** Called at the start of every operation, with its name: "list", "load", "save" or "delete". */
    var onCall: ((operation: String) -> Unit)? = null

    /** Every save that reached the repository: id, name, tree. */
    val saveCalls = mutableListOf<Triple<String, String, Node>>()
    var listCalls = 0
        private set

    override fun list(): ListResult {
        onCall?.invoke("list")
        listCalls++
        listOverride?.let { return it }
        val summaries = store.entries.mapIndexed { index, (id, entry) ->
            ConfigSummary(id, entry.first, modifiedAtMillis = index.toLong())
        }
        return ListResult.Listed(summaries.reversed(), emptyList())
    }

    override fun load(id: String): LoadResult {
        onCall?.invoke("load")
        loadOverride?.let { return it }
        val entry = store[id] ?: return LoadResult.NotFound
        return LoadResult.Loaded(id, ConfigContent(entry.first, entry.second))
    }

    override fun save(id: String, name: String, root: Node): SaveResult {
        onCall?.invoke("save")
        beforeSave?.invoke()
        saveCalls += Triple(id, name, root)
        saveOverride?.let { return it }
        store[id] = name to root
        return SaveResult.Saved(id)
    }

    override fun delete(id: String): DeleteResult {
        onCall?.invoke("delete")
        deleteOverride?.let { return it }
        return if (store.remove(id) != null) DeleteResult.Deleted else DeleteResult.NotFound
    }
}