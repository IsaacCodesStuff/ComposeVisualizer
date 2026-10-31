package dev.isaac.composevisualizer.persistence

import dev.isaac.composevisualizer.model.Node
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** The only file format version this build can read and write. */
const val FORMAT_VERSION = 1

/**
 * Deepest JSON bracket nesting accepted before parsing even starts.
 *
 * A tree at [MAX_TREE_DEPTH] needs at most about 2 levels per tree level (a
 * column is an object plus a `children` array) plus the envelope and a couple
 * of property objects at the leaf, so this leaves comfortable room for every
 * valid file while stopping hostile input long before it can exhaust the stack.
 */
const val MAX_JSON_NESTING = 2 * MAX_TREE_DEPTH + 8

/** Longest decoder message kept, so a huge broken file can't produce a huge error string. */
private const val MAX_MESSAGE_LENGTH = 300

/**
 * What a saved file contains once it has been read and checked: a name and a
 * tree. The configuration's id is not part of the content; it is the file name
 * and is attached by the repository.
 */
data class ConfigContent(val name: String, val root: Node)

/**
 * The outcome of reading a file. Each failure kind stays distinct so the UI
 * can say something accurate for each.
 */
sealed interface DecodeResult {
    /** The file was readable, supported, and passed every validation rule. */
    data class Success(val content: ConfigContent) : DecodeResult

    /** The text isn't JSON, or isn't shaped like format version 1 (bad key, enum, type, missing field...). */
    data class Malformed(val message: String) : DecodeResult

    /** Well-formed enough to read `formatVersion`, but it isn't a version this build supports. */
    data class UnsupportedVersion(val found: Int) : DecodeResult

    /** Correct shape, but the content breaks a validation rule (see [ValidationRule]). */
    data class Invalid(val errors: List<ValidationError>) : DecodeResult
}

/**
 * The JSON wrapper around a saved tree. Private: the rest of the app only sees
 * [ConfigContent]. None of the three properties has a default, so all are
 * required in the file.
 */
@Serializable
private data class ConfigEnvelope(
    val formatVersion: Int,
    val name: String,
    val root: Node,
)

/**
 * One shared, strict configuration.
 * - Unknown keys are errors (`ignoreUnknownKeys = false`).
 * - Defaults are written out (`encodeDefaults = true`), so a saved file keeps
 *   its meaning even if a default changes in a later version of the app.
 * - No coercion: a bad enum value or a null for a non-null field is an error,
 *   never silently replaced.
 * - NaN and infinities are refused.
 */
private val strictJson = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    prettyPrint = true
    coerceInputValues = false
    isLenient = false
    allowSpecialFloatingPointValues = false
}

/**
 * Writes a configuration as format-version-1 JSON.
 *
 * This does not validate. Callers must validate first ([validateName] and
 * [validateTree]) so a file is never written that [decodeConfig] would refuse.
 *
 * Always goes through the envelope serializer, which is what makes the tree's
 * `"type"` discriminators appear in the output.
 */
fun encodeConfig(name: String, root: Node): String =
    strictJson.encodeToString(ConfigEnvelope.serializer(), ConfigEnvelope(FORMAT_VERSION, name, root))

/**
 * Reads a configuration from JSON text. Never throws for bad input; every
 * problem comes back as a [DecodeResult] case.
 *
 * Order matters:
 * 1. Reject absurd bracket nesting before parsing anything.
 * 2. Parse generic JSON. Failure: [DecodeResult.Malformed].
 * 3. Read `formatVersion` first. A different version is
 *    [DecodeResult.UnsupportedVersion] even if the rest of the file has a
 *    shape this build doesn't know.
 * 4. Decode strictly into the envelope. Failure: [DecodeResult.Malformed].
 * 5. Validate name and tree. Failure: [DecodeResult.Invalid].
 */
fun decodeConfig(text: String): DecodeResult {
    if (nestsDeeperThanAllowed(text)) {
        return DecodeResult.Invalid(
            listOf(
                ValidationError(
                    ValidationRule.TreeTooDeep, null, null,
                    "File is nested deeper than the limit of $MAX_TREE_DEPTH tree levels",
                ),
            ),
        )
    }

    val element = try {
        strictJson.parseToJsonElement(text)
    } catch (e: IllegalArgumentException) { // SerializationException is a subclass
        return DecodeResult.Malformed("Not valid JSON: ${shorten(e.message)}")
    }

    val obj = element as? JsonObject
        ?: return DecodeResult.Malformed("The top level must be a JSON object")

    val versionElement = obj["formatVersion"]
        ?: return DecodeResult.Malformed("Missing \"formatVersion\"")
    val version = (versionElement as? JsonPrimitive)
        ?.takeIf { !it.isString }
        ?.intOrNull
        ?: return DecodeResult.Malformed("\"formatVersion\" must be a whole number")
    if (version != FORMAT_VERSION) return DecodeResult.UnsupportedVersion(version)

    val envelope = try {
        strictJson.decodeFromJsonElement(ConfigEnvelope.serializer(), element)
    } catch (e: IllegalArgumentException) {
        return DecodeResult.Malformed(shorten(e.message))
    }

    val errors = listOfNotNull(validateName(envelope.name)) + validateTree(envelope.root)
    if (errors.isNotEmpty()) return DecodeResult.Invalid(errors)

    return DecodeResult.Success(ConfigContent(envelope.name, envelope.root))
}

private fun shorten(message: String?): String {
    val text = message ?: "Unknown error"
    return if (text.length <= MAX_MESSAGE_LENGTH) text else text.take(MAX_MESSAGE_LENGTH) + "…"
}

/**
 * A cheap single pass that counts `{`/`[` nesting, skipping over string
 * contents so brackets inside text don't count. Stops at the first excess, so
 * a huge hostile file costs at most one scan.
 */
private fun nestsDeeperThanAllowed(text: String): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (c in text) {
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{', '[' -> {
                    depth++
                    if (depth > MAX_JSON_NESTING) return true
                }
                '}', ']' -> if (depth > 0) depth--
            }
        }
    }
    return false
}