package dev.isaac.composevisualizer.model

/** The node types a user can add, with sensible starting values. */
enum class NodeKind(val label: String) {
    Text("Text"),
    Surface("Surface"),
    Column("Column");

    /** A fresh node with a new unique id and visible defaults. */
    fun createDefault(): Node = when (this) {
        Text -> TextNode(text = "New text")
        Surface -> SurfaceNode(
            color = ColorSpec.Role(ColorRole.SurfaceContainer),
            shape = ShapeSpec.Rounded(12f),
            padding = PaddingSpec.all(12f),
        )
        Column -> ColumnNode(spacing = 8f)
    }
}