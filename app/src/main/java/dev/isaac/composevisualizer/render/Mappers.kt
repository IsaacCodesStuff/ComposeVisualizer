package dev.isaac.composevisualizer.render

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.isaac.composevisualizer.model.ColorRole
import dev.isaac.composevisualizer.model.ColorSpec
import dev.isaac.composevisualizer.model.ContentAlignment
import dev.isaac.composevisualizer.model.HorizontalAlignmentSpec
import dev.isaac.composevisualizer.model.PaddingSpec
import dev.isaac.composevisualizer.model.ShapeSpec
import dev.isaac.composevisualizer.model.SizeSpec
import dev.isaac.composevisualizer.model.TextStyleRole

// Converts model "specs" (plain data) into real Compose types.
// Keeping all conversions here means the renderer reads cleanly and the
// model never depends on Compose.

internal fun Modifier.widthOf(spec: SizeSpec): Modifier = when (spec) {
    SizeSpec.Wrap -> this
    is SizeSpec.Fixed -> width(spec.value.dp)
}

internal fun Modifier.heightOf(spec: SizeSpec): Modifier = when (spec) {
    SizeSpec.Wrap -> this
    is SizeSpec.Fixed -> height(spec.value.dp)
}

internal fun PaddingSpec.toPaddingValues(): PaddingValues =
    PaddingValues(start = start.dp, top = top.dp, end = end.dp, bottom = bottom.dp)

internal fun ShapeSpec.toShape(): Shape = when (this) {
    ShapeSpec.Rectangle -> RectangleShape
    is ShapeSpec.Rounded -> RoundedCornerShape(radius.dp)
    ShapeSpec.Circle -> CircleShape
}

internal fun ContentAlignment.toAlignment(): Alignment = when (this) {
    ContentAlignment.TopStart -> Alignment.TopStart
    ContentAlignment.TopCenter -> Alignment.TopCenter
    ContentAlignment.TopEnd -> Alignment.TopEnd
    ContentAlignment.CenterStart -> Alignment.CenterStart
    ContentAlignment.Center -> Alignment.Center
    ContentAlignment.CenterEnd -> Alignment.CenterEnd
    ContentAlignment.BottomStart -> Alignment.BottomStart
    ContentAlignment.BottomCenter -> Alignment.BottomCenter
    ContentAlignment.BottomEnd -> Alignment.BottomEnd
}

internal fun HorizontalAlignmentSpec.toAlignment(): Alignment.Horizontal = when (this) {
    HorizontalAlignmentSpec.Start -> Alignment.Start
    HorizontalAlignmentSpec.Center -> Alignment.CenterHorizontally
    HorizontalAlignmentSpec.End -> Alignment.End
}

// The next two are @Composable because they read MaterialTheme, which only
// exists during composition.

@Composable
internal fun ColorSpec.toColor(): Color = when (this) {
    is ColorSpec.Custom -> Color(argb)
    is ColorSpec.Role -> {
        val c = MaterialTheme.colorScheme
        when (role) {
            ColorRole.Surface -> c.surface
            ColorRole.SurfaceContainer -> c.surfaceContainer
            ColorRole.SurfaceVariant -> c.surfaceVariant
            ColorRole.Primary -> c.primary
            ColorRole.OnPrimary -> c.onPrimary
            ColorRole.PrimaryContainer -> c.primaryContainer
            ColorRole.OnPrimaryContainer -> c.onPrimaryContainer
            ColorRole.Secondary -> c.secondary
            ColorRole.SecondaryContainer -> c.secondaryContainer
            ColorRole.OnSecondaryContainer -> c.onSecondaryContainer
            ColorRole.Tertiary -> c.tertiary
            ColorRole.TertiaryContainer -> c.tertiaryContainer
            ColorRole.OnSurface -> c.onSurface
            ColorRole.OnSurfaceVariant -> c.onSurfaceVariant
            ColorRole.Outline -> c.outline
        }
    }
}

@Composable
internal fun TextStyleRole.toTextStyle(): TextStyle {
    val t = MaterialTheme.typography
    return when (this) {
        TextStyleRole.HeadlineMedium -> t.headlineMedium
        TextStyleRole.TitleLarge -> t.titleLarge
        TextStyleRole.TitleMedium -> t.titleMedium
        TextStyleRole.BodyLarge -> t.bodyLarge
        TextStyleRole.BodyMedium -> t.bodyMedium
        TextStyleRole.LabelLarge -> t.labelLarge
        TextStyleRole.LabelMedium -> t.labelMedium
    }
}