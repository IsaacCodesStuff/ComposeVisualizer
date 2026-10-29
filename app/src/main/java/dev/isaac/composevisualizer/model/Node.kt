package dev.isaac.composevisualizer.model

import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Creates a fresh unique id for a node. */
fun newNodeId(): String = UUID.randomUUID().toString()

/**
 * One element in a component tree. Sealed, so the compiler knows every
 * subtype and forces `when (node)` to handle all of them.
 */
@Serializable
sealed interface Node {
    val id: String
}

@Serializable
@SerialName("text")
data class TextNode(
    override val id: String = newNodeId(),
    val text: String = "Text",
    val style: TextStyleRole = TextStyleRole.BodyMedium,
    val color: ColorSpec = ColorSpec.Role(ColorRole.OnSurface),
    val padding: PaddingSpec = PaddingSpec.None,
) : Node

/** A Material 3 Surface: background, shape, border, shadow, and one optional child. */
@Serializable
@SerialName("surface")
data class SurfaceNode(
    override val id: String = newNodeId(),
    val width: SizeSpec = SizeSpec.Wrap,
    val height: SizeSpec = SizeSpec.Wrap,
    val padding: PaddingSpec = PaddingSpec.None,
    val color: ColorSpec = ColorSpec.Role(ColorRole.Surface),
    val shape: ShapeSpec = ShapeSpec.Rectangle,
    val borderWidth: Float = 0f,
    val borderColor: ColorSpec = ColorSpec.Role(ColorRole.Outline),
    val elevation: Float = 0f,
    val contentAlignment: ContentAlignment = ContentAlignment.TopStart,
    val child: Node? = null,
) : Node

/** A vertical stack of children, in order. */
@Serializable
@SerialName("column")
data class ColumnNode(
    override val id: String = newNodeId(),
    val width: SizeSpec = SizeSpec.Wrap,
    val height: SizeSpec = SizeSpec.Wrap,
    val padding: PaddingSpec = PaddingSpec.None,
    val spacing: Float = 0f,
    val horizontalAlignment: HorizontalAlignmentSpec = HorizontalAlignmentSpec.Start,
    val children: List<Node> = emptyList(),
) : Node