package dev.isaac.composevisualizer.persistence

import dev.isaac.composevisualizer.model.Node
import java.util.UUID

/** Creates the id of a new saved configuration. It becomes the file name, never the display name. */
fun newConfigId(): String = UUID.randomUUID().toString()

private val CONFIG_ID_PATTERN =
    Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/**
 * Whether [id] has the exact shape [newConfigId] produces (lowercase UUID).
 * Repositories refuse anything else, which is what keeps ids like `../x` from
 * ever reaching the file system.
 */
fun isValidConfigId(id: String): Boolean = CONFIG_ID_PATTERN.matches(id)

/** A saved configuration as shown in a list. */
data class ConfigSummary(val id: String, val name: String, val modifiedAtMillis: Long)

/** A file that exists but can't be loaded, so the UI can show it and offer to delete it. */
data class UnreadableConfig(val id: String, val problem: String)

sealed interface ListResult {
    /** [saved] is newest first; [unreadable] is sorted by id. */
    data class Listed(val saved: List<ConfigSummary>, val unreadable: List<UnreadableConfig>) : ListResult

    /** The storage folder exists but couldn't be read. */
    data class IoFailure(val message: String) : ListResult
}

/** Every way loading can end. The cases mirror [DecodeResult], plus the two file-level ones. */
sealed interface LoadResult {
    data class Loaded(val id: String, val content: ConfigContent) : LoadResult

    /** Not JSON, not valid UTF-8, too large, or not shaped like format version 1. */
    data class Malformed(val message: String) : LoadResult

    data class UnsupportedVersion(val found: Int) : LoadResult

    data class Invalid(val errors: List<ValidationError>) : LoadResult

    /** There is no file for this id. */
    data object NotFound : LoadResult

    /** The file exists (or should) but the operating system couldn't read it. */
    data class IoFailure(val message: String) : LoadResult
}

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult

    /** The name or tree breaks a validation rule. Nothing was written. */
    data class Invalid(val errors: List<ValidationError>) : SaveResult

    /** The encoded file would exceed [MAX_FILE_BYTES], which loading would refuse. Nothing was written. */
    data class TooLarge(val bytes: Long) : SaveResult

    /**
     * The file system can't replace a file atomically here. Nothing was
     * written and nothing was replaced: there is deliberately no fallback to a
     * non-atomic overwrite.
     */
    data object AtomicMoveUnsupported : SaveResult

    /** The operating system refused the write. The previous version of the file, if any, is intact. */
    data class IoFailure(val message: String) : SaveResult
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    data object NotFound : DeleteResult

    data class IoFailure(val message: String) : DeleteResult
}

/**
 * Where configurations are stored. One configuration per id.
 *
 * Every function is blocking and does file I/O, so call them off the main
 * thread (the library ViewModel will use `Dispatchers.IO`). None of them throws
 * for bad file contents or I/O trouble; those come back as result cases. An id
 * that isn't valid per [isValidConfigId] is a programming error and throws
 * [IllegalArgumentException].
 */
interface ConfigRepository {
    fun list(): ListResult

    fun load(id: String): LoadResult

    /**
     * Validates, then writes. Creates the configuration, or replaces the one with
     * this id. The name is stored exactly as given; trimming is the caller's job.
     */
    fun save(id: String, name: String, root: Node): SaveResult

    fun delete(id: String): DeleteResult
}