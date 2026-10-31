package dev.isaac.composevisualizer.persistence

import dev.isaac.composevisualizer.model.Node
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Largest file this repository will read or write. */
const val MAX_FILE_BYTES = 5L * 1024 * 1024

/**
 * The one step of a save that must be atomic: renaming the finished temporary
 * file over the real one. A separate seam so tests can simulate a file system
 * that can't do it, or one that fails halfway.
 */
fun interface AtomicMove {
    /** Moves [from] to [to], replacing [to], as a single atomic operation or not at all. */
    fun move(from: Path, to: Path)

    companion object {
        val Default = AtomicMove { from, to ->
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

/**
 * Stores each configuration as `<id>.json` inside [directory].
 *
 * Saving writes the complete file to `<id>.json.tmp`, forces it to disk, then
 * atomically renames it over `<id>.json`. A crash or failure at any point
 * leaves either the old file or the new one, never a half-written mix.
 *
 * It takes a plain [File], not an Android `Context`, so it runs in ordinary
 * unit tests. Use a single instance per directory: writes are serialized with
 * a lock inside the instance.
 */
class FileConfigRepository(
    private val directory: File,
    private val atomicMove: AtomicMove = AtomicMove.Default,
) : ConfigRepository {

    private val writeLock = Any()

    override fun list(): ListResult {
        if (!directory.exists()) return ListResult.Listed(emptyList(), emptyList())
        val names = directory.list()
            ?: return ListResult.IoFailure("Can't read the configurations folder")

        val saved = mutableListOf<ConfigSummary>()
        val unreadable = mutableListOf<UnreadableConfig>()
        for (name in names) {
            if (!name.endsWith(FILE_SUFFIX)) continue
            val id = name.removeSuffix(FILE_SUFFIX)
            if (!isValidConfigId(id)) continue // ignores temp files and anything we didn't write

            when (val result = read(id)) {
                is LoadResult.Loaded -> saved += ConfigSummary(
                    id = id,
                    name = result.content.name,
                    modifiedAtMillis = fileFor(id).lastModified(),
                )
                else -> unreadable += UnreadableConfig(id, describe(result))
            }
        }
        saved.sortWith(compareByDescending<ConfigSummary> { it.modifiedAtMillis }.thenBy { it.name })
        unreadable.sortBy { it.id }
        return ListResult.Listed(saved, unreadable)
    }

    override fun load(id: String): LoadResult {
        requireValidId(id)
        return read(id)
    }

    override fun save(id: String, name: String, root: Node): SaveResult {
        requireValidId(id)

        // Never write a file that loading would refuse.
        val errors = listOfNotNull(validateName(name)) + validateTree(root)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val bytes = encodeConfig(name, root).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FILE_BYTES) return SaveResult.TooLarge(bytes.size.toLong())

        synchronized(writeLock) {
            return writeAtomically(id, bytes)
        }
    }

    override fun delete(id: String): DeleteResult {
        requireValidId(id)
        synchronized(writeLock) {
            return try {
                Files.delete(fileFor(id).toPath())
                DeleteResult.Deleted
            } catch (e: NoSuchFileException) {
                DeleteResult.NotFound
            } catch (e: IOException) {
                DeleteResult.IoFailure(messageOf(e))
            }
        }
    }

    // ---- internals ---------------------------------------------------------

    private fun fileFor(id: String) = File(directory, "$id$FILE_SUFFIX")

    private fun tempPathFor(id: String): Path = File(directory, "$id$FILE_SUFFIX$TEMP_SUFFIX").toPath()

    private fun requireValidId(id: String) {
        require(isValidConfigId(id)) { "Not a valid configuration id: \"$id\"" }
    }

    private fun read(id: String): LoadResult {
        val path = fileFor(id).toPath()

        val bytes = try {
            if (Files.size(path) > MAX_FILE_BYTES) {
                return LoadResult.Malformed("File is larger than ${MAX_FILE_BYTES / (1024 * 1024)} MB")
            }
            Files.readAllBytes(path)
        } catch (e: NoSuchFileException) {
            return LoadResult.NotFound
        } catch (e: IOException) {
            return LoadResult.IoFailure(messageOf(e))
        }

        // Strict decoding: invalid UTF-8 is an error, not silently replaced with "?".
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            return LoadResult.Malformed("File is not valid UTF-8 text")
        }

        return when (val decoded = decodeConfig(text)) {
            is DecodeResult.Success -> LoadResult.Loaded(id, decoded.content)
            is DecodeResult.Malformed -> LoadResult.Malformed(decoded.message)
            is DecodeResult.UnsupportedVersion -> LoadResult.UnsupportedVersion(decoded.found)
            is DecodeResult.Invalid -> LoadResult.Invalid(decoded.errors)
        }
    }

    private fun writeAtomically(id: String, bytes: ByteArray): SaveResult {
        val temp = tempPathFor(id)
        val target = fileFor(id).toPath()
        try {
            Files.createDirectories(directory.toPath())
            FileOutputStream(temp.toFile()).use { out ->
                out.write(bytes)
                out.fd.sync() // push the bytes to disk before the rename makes them "the" file
            }
            atomicMove.move(temp, target)
            return SaveResult.Saved(id)
        } catch (e: AtomicMoveNotSupportedException) {
            // Checked before IOException, which it extends. No fallback to a plain overwrite.
            discard(temp)
            return SaveResult.AtomicMoveUnsupported
        } catch (e: IOException) {
            discard(temp)
            return SaveResult.IoFailure(messageOf(e))
        }
    }

    /** Best-effort removal of a leftover temporary file. The real file is never touched. */
    private fun discard(temp: Path) {
        try {
            Files.deleteIfExists(temp)
        } catch (ignored: IOException) {
            // A stray .tmp file is harmless: listing ignores it and the next save overwrites it.
        }
    }

    private fun describe(result: LoadResult): String = when (result) {
        is LoadResult.Loaded -> "OK"
        is LoadResult.Malformed -> result.message
        is LoadResult.UnsupportedVersion -> "Unsupported format version ${result.found}"
        is LoadResult.Invalid -> result.errors.firstOrNull()?.message ?: "Invalid content"
        LoadResult.NotFound -> "File disappeared"
        is LoadResult.IoFailure -> result.message
    }

    private fun messageOf(e: IOException) = e.message ?: e.javaClass.simpleName

    private companion object {
        const val FILE_SUFFIX = ".json"
        const val TEMP_SUFFIX = ".tmp"
    }
}