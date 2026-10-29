package dev.isaac.composevisualizer.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.isaac.composevisualizer.model.ColorRole
import dev.isaac.composevisualizer.model.ColorSpec
import dev.isaac.composevisualizer.model.PaddingSpec
import dev.isaac.composevisualizer.model.ShapeSpec
import dev.isaac.composevisualizer.model.SizeSpec

// Editors for the model's "spec" types. Each is a pure function of its input:
// it shows `spec` and reports a NEW spec through onChange.

private enum class SizeKind { Wrap, Fixed }

@Composable
fun SizeField(
    label: String,
    spec: SizeSpec,
    onChange: (SizeSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = when (spec) {
        SizeSpec.Wrap -> SizeKind.Wrap
        is SizeSpec.Fixed -> SizeKind.Fixed
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DropdownField(
            label = label,
            options = SizeKind.entries,
            selected = kind,
            onSelected = { picked ->
                onChange(
                    when (picked) {
                        SizeKind.Wrap -> SizeSpec.Wrap
                        SizeKind.Fixed -> SizeSpec.Fixed(100f)
                    },
                )
            },
            modifier = Modifier.weight(1f),
        )
        // Only a fixed size has a number to edit.
        if (spec is SizeSpec.Fixed) {
            NumberField(
                label = "$label (dp)",
                value = spec.value,
                onValueChange = { onChange(SizeSpec.Fixed(it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun PaddingFields(
    title: String,
    spec: PaddingSpec,
    onChange: (PaddingSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Start", spec.start, { onChange(spec.copy(start = it)) }, Modifier.weight(1f))
            NumberField("Top", spec.top, { onChange(spec.copy(top = it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("End", spec.end, { onChange(spec.copy(end = it)) }, Modifier.weight(1f))
            NumberField("Bottom", spec.bottom, { onChange(spec.copy(bottom = it)) }, Modifier.weight(1f))
        }
    }
}

private enum class ShapeKind { Rectangle, Rounded, Circle }

@Composable
fun ShapeField(
    spec: ShapeSpec,
    onChange: (ShapeSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = when (spec) {
        ShapeSpec.Rectangle -> ShapeKind.Rectangle
        is ShapeSpec.Rounded -> ShapeKind.Rounded
        ShapeSpec.Circle -> ShapeKind.Circle
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DropdownField(
            label = "Shape",
            options = ShapeKind.entries,
            selected = kind,
            onSelected = { picked ->
                onChange(
                    when (picked) {
                        ShapeKind.Rectangle -> ShapeSpec.Rectangle
                        ShapeKind.Rounded -> ShapeSpec.Rounded(16f)
                        ShapeKind.Circle -> ShapeSpec.Circle
                    },
                )
            },
            modifier = Modifier.weight(1f),
        )
        if (spec is ShapeSpec.Rounded) {
            NumberField(
                label = "Radius (dp)",
                value = spec.radius,
                onValueChange = { onChange(ShapeSpec.Rounded(it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class ColorKind { Role, Custom }

/** A color is either a theme role (dropdown) or a custom HEX value. */
@Composable
fun ColorSpecField(
    label: String,
    spec: ColorSpec,
    onChange: (ColorSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = when (spec) {
        is ColorSpec.Role -> ColorKind.Role
        is ColorSpec.Custom -> ColorKind.Custom
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DropdownField(
            label = label,
            options = ColorKind.entries,
            selected = kind,
            onSelected = { picked ->
                onChange(
                    when (picked) {
                        ColorKind.Role -> ColorSpec.Role(ColorRole.Primary)
                        ColorKind.Custom -> ColorSpec.Custom(0xFF6750A4L)
                    },
                )
            },
            modifier = Modifier.weight(1f),
        )
        when (spec) {
            is ColorSpec.Role -> DropdownField(
                label = "Role",
                options = ColorRole.entries,
                selected = spec.role,
                onSelected = { onChange(ColorSpec.Role(it)) },
                modifier = Modifier.weight(1f),
            )
            is ColorSpec.Custom -> HexColorField(
                label = "Hex",
                argb = spec.argb,
                onValueChange = { onChange(ColorSpec.Custom(it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}