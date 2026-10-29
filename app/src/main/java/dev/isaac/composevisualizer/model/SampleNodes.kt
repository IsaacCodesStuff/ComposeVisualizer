package dev.isaac.composevisualizer.model

/** Hardcoded demo trees. Temporary: a ViewModel will own the real tree later. */
object SampleNodes {
    fun nowPlayingCard(): Node = SurfaceNode(
        color = ColorSpec.Role(ColorRole.PrimaryContainer),
        shape = ShapeSpec.Rounded(20f),
        padding = PaddingSpec.symmetric(horizontal = 20f, vertical = 16f),
        child = ColumnNode(
            spacing = 4f,
            children = listOf(
                TextNode(
                    text = "Now Playing",
                    style = TextStyleRole.TitleMedium,
                    color = ColorSpec.Role(ColorRole.OnPrimaryContainer),
                ),
                TextNode(
                    text = "Artist name",
                    style = TextStyleRole.BodyMedium,
                    color = ColorSpec.Role(ColorRole.OnPrimaryContainer),
                ),
            ),
        ),
    )
}