package dev.isaac.composevisualizer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a node is sized along one axis. */
@Serializable
sealed interface SizeSpec {
    /** Size to content (no size modifier is applied). */
    @Serializable
    @SerialName("wrap")
    data object Wrap : SizeSpec

    /** Exact size in dp. */
    @Serializable
    @SerialName("fixed")
    data class Fixed(val value: Float) : SizeSpec
}

/** Four-sided padding in dp. */
@Serializable
data class PaddingSpec(
    val start: Float = 0f,
    val top: Float = 0f,
    val end: Float = 0f,
    val bottom: Float = 0f,
) {
    companion object {
        val None = PaddingSpec()

        fun all(value: Float) = PaddingSpec(value, value, value, value)

        fun symmetric(horizontal: Float = 0f, vertical: Float = 0f) =
            PaddingSpec(horizontal, vertical, horizontal, vertical)
    }
}

@Serializable
sealed interface ShapeSpec {
    @Serializable
    @SerialName("rectangle")
    data object Rectangle : ShapeSpec

    /** Rounded corners, radius in dp. */
    @Serializable
    @SerialName("rounded")
    data class Rounded(val radius: Float) : ShapeSpec

    /** Fully round ends: a circle if square, a pill if not. */
    @Serializable
    @SerialName("circle")
    data object Circle : ShapeSpec
}

/** Material 3 color roles a component can point at. */
enum class ColorRole {
    Surface,
    SurfaceContainer,
    SurfaceVariant,
    Primary,
    OnPrimary,
    PrimaryContainer,
    OnPrimaryContainer,
    Secondary,
    SecondaryContainer,
    OnSecondaryContainer,
    Tertiary,
    TertiaryContainer,
    OnSurface,
    OnSurfaceVariant,
    Outline,
}

@Serializable
sealed interface ColorSpec {
    /** A color taken from the active Material theme. */
    @Serializable
    @SerialName("role")
    data class Role(val role: ColorRole) : ColorSpec

    /** A fixed color as 0xAARRGGBB. */
    @Serializable
    @SerialName("custom")
    data class Custom(val argb: Long) : ColorSpec
}

/** Material 3 typography roles. */
enum class TextStyleRole {
    HeadlineMedium,
    TitleLarge,
    TitleMedium,
    BodyLarge,
    BodyMedium,
    LabelLarge,
    LabelMedium,
}

/** Where a Surface places its child when the surface is bigger than the child. */
enum class ContentAlignment {
    TopStart, TopCenter, TopEnd,
    CenterStart, Center, CenterEnd,
    BottomStart, BottomCenter, BottomEnd,
}

enum class HorizontalAlignmentSpec { Start, Center, End }