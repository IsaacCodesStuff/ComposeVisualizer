package dev.isaac.composevisualizer.persistence

import dev.isaac.composevisualizer.model.ColorSpec
import dev.isaac.composevisualizer.model.ColumnNode
import dev.isaac.composevisualizer.model.Node
import dev.isaac.composevisualizer.model.PaddingSpec
import dev.isaac.composevisualizer.model.ShapeSpec
import dev.isaac.composevisualizer.model.SizeSpec
import dev.isaac.composevisualizer.model.SurfaceNode
import dev.isaac.composevisualizer.model.TextNode

// ---------------------------------------------------------------------------
// Limits. Named so they are easy to find and change in one place later.
// ---------------------------------------------------------------------------

/** Deepest allowed tree, counting the root as depth 1. */
const val MAX_TREE_DEPTH = 64

/** Longest allowed configuration name, in characters, exactly as given. */
const val MAX_NAME_LENGTH = 60

/** Validation stops collecting after this many problems, so a hostile file can't build a huge list. */
const val MAX_REPORTED_ERRORS = 100

private const val ARGB_MAX = 0xFFFFFFFFL

/**
 * Which rule a [ValidationError] broke. Callers and tests branch on this
 * instead of parsing message text.
 */
enum class ValidationRule {
    DuplicateId,
    BlankId,
    NegativeValue,
    NonFiniteValue,
    ArgbOutOfRange,
    TreeTooDeep,
    BlankName,
    NameTooLong,
}

/**
 * One problem found by validation.
 *
 * @param nodeId the offending node, or null for problems that aren't about a node (the name).
 * @param field which property of that node, such as "padding.start", or null.
 * @param message a short human-readable explanation.
 */
data class ValidationError(
    val rule: ValidationRule,
    val nodeId: String?,
    val field: String?,
    val message: String,
)

/**
 * Checks a whole tree and returns every problem found (up to
 * [MAX_REPORTED_ERRORS]), in pre-order. An empty list means the tree is valid.
 *
 * Rules:
 * - Node ids must be unique across the whole tree, and not blank.
 * - Dimensions (fixed sizes, padding, spacing, border width, elevation, corner
 *   radius) must be finite and not negative. There is deliberately no upper
 *   limit in format version 1.
 * - Custom colors must fit in 32 bits: 0..0xFFFFFFFF.
 * - The tree may be at most [MAX_TREE_DEPTH] levels deep.
 *
 * Nothing is corrected or clamped; invalid input is reported, never repaired.
 *
 * The walk is iterative (an explicit stack, not recursion) and never descends
 * past [MAX_TREE_DEPTH], so even an absurdly deep tree can't overflow the call
 * stack and costs bounded work.
 */
fun validateTree(root: Node): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    val seenIds = HashSet<String>()

    // Each entry is a node plus its depth (root = 1). Using the end of the
    // list as the top of the stack gives pre-order if we push children reversed.
    val pending = ArrayDeque<Pair<Node, Int>>()
    pending.addLast(root to 1)

    while (pending.isNotEmpty() && errors.size < MAX_REPORTED_ERRORS) {
        val (node, depth) = pending.removeLast()

        if (depth > MAX_TREE_DEPTH) {
            // Don't inspect or descend into it: one error marks where the limit was crossed.
            errors += ValidationError(
                ValidationRule.TreeTooDeep,
                node.id,
                null,
                "Tree is deeper than the limit of $MAX_TREE_DEPTH levels",
            )
            continue
        }

        checkId(node.id, seenIds, errors)
        NodeChecker(node.id, errors).check(node)

        when (node) {
            is TextNode -> Unit
            is SurfaceNode -> node.child?.let { pending.addLast(it to depth + 1) }
            is ColumnNode -> for (child in node.children.asReversed()) {
                pending.addLast(child to depth + 1)
            }
        }
    }

    return errors.take(MAX_REPORTED_ERRORS)
}

/**
 * Checks a configuration name exactly as given. Callers that want to ignore
 * surrounding spaces should trim *before* validating and saving; this function
 * never alters the name.
 */
fun validateName(name: String): ValidationError? = when {
    name.isBlank() -> ValidationError(
        ValidationRule.BlankName, null, "name", "Name can't be empty",
    )
    name.length > MAX_NAME_LENGTH -> ValidationError(
        ValidationRule.NameTooLong, null, "name",
        "Name is longer than $MAX_NAME_LENGTH characters",
    )
    else -> null
}

private fun checkId(id: String, seenIds: MutableSet<String>, errors: MutableList<ValidationError>) {
    if (id.isBlank()) {
        errors += ValidationError(ValidationRule.BlankId, id, "id", "Node id can't be blank")
    } else if (!seenIds.add(id)) {
        // The first occurrence is fine; every later one is the duplicate.
        errors += ValidationError(ValidationRule.DuplicateId, id, "id", "Node id \"$id\" is used more than once")
    }
}

/** Checks the numeric and color properties of one node, adding problems to [out]. */
private class NodeChecker(
    private val nodeId: String,
    private val out: MutableList<ValidationError>,
) {
    fun check(node: Node) {
        when (node) {
            is TextNode -> {
                color("color", node.color)
                padding("padding", node.padding)
            }
            is SurfaceNode -> {
                size("width", node.width)
                size("height", node.height)
                padding("padding", node.padding)
                color("color", node.color)
                shape("shape", node.shape)
                dimension("borderWidth", node.borderWidth)
                color("borderColor", node.borderColor)
                dimension("elevation", node.elevation)
            }
            is ColumnNode -> {
                size("width", node.width)
                size("height", node.height)
                padding("padding", node.padding)
                dimension("spacing", node.spacing)
            }
        }
    }

    private fun size(field: String, spec: SizeSpec) {
        when (spec) {
            SizeSpec.Wrap -> Unit
            is SizeSpec.Fixed -> dimension(field, spec.value)
        }
    }

    private fun padding(field: String, p: PaddingSpec) {
        dimension("$field.start", p.start)
        dimension("$field.top", p.top)
        dimension("$field.end", p.end)
        dimension("$field.bottom", p.bottom)
    }

    private fun shape(field: String, spec: ShapeSpec) {
        when (spec) {
            ShapeSpec.Rectangle, ShapeSpec.Circle -> Unit
            is ShapeSpec.Rounded -> dimension("$field.radius", spec.radius)
        }
    }

    private fun color(field: String, spec: ColorSpec) {
        when (spec) {
            is ColorSpec.Role -> Unit
            is ColorSpec.Custom -> if (spec.argb !in 0L..ARGB_MAX) {
                out += ValidationError(
                    ValidationRule.ArgbOutOfRange, nodeId, "$field.argb",
                    "$field.argb must be between 0 and 0xFFFFFFFF (was ${spec.argb})",
                )
            }
        }
    }

    /** Finite and not negative. NaN fails every comparison, so finiteness is checked first. */
    private fun dimension(field: String, value: Float) {
        if (!value.isFinite()) {
            out += ValidationError(
                ValidationRule.NonFiniteValue, nodeId, field,
                "$field must be a finite number (was $value)",
            )
        } else if (value < 0f) {
            out += ValidationError(
                ValidationRule.NegativeValue, nodeId, field,
                "$field can't be negative (was $value)",
            )
        }
    }
}