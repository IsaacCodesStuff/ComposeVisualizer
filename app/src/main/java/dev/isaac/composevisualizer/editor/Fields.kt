package dev.isaac.composevisualizer.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** Free text. Keeps its own typed text, then reports every change. */
@Composable
fun TextValueField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `by remember { mutableStateOf(...) }` = a local variable that Compose
    // watches. Not keyed on `value`: while typing, this field is the source
    // of truth, which avoids cursor jumps.
    var text by remember { mutableStateOf(value) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A validated number field. Invalid input (not a number, or outside
 * [min]..[max]) shows an error and is NOT sent on, so the preview
 * always holds the last valid value.
 */
@Composable
fun NumberField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1000f,
) {
    var text by remember { mutableStateOf(value.toDisplayString()) }
    val isValid = text.toFloatOrNull()?.let { it in min..max } == true

    val errorText: (@Composable () -> Unit)? =
        if (isValid) null
        else ({ Text("Enter ${min.toDisplayString()} to ${max.toDisplayString()}") })

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            val number = newText.toFloatOrNull()
            if (number != null && number in min..max) onValueChange(number)
        },
        label = { Text(label) },
        singleLine = true,
        isError = !isValid,
        supportingText = errorText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** 12.0 -> "12", 12.5 -> "12.5" */
private fun Float.toDisplayString(): String =
    if (this == toInt().toFloat()) toInt().toString() else toString()

/** A HEX color input (#RRGGBB or #AARRGGBB) with validation. */
@Composable
fun HexColorField(
    label: String,
    argb: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(formatHexColor(argb)) }
    val isValid = parseHexColor(text) != null

    val errorText: (@Composable () -> Unit)? =
        if (isValid) null else ({ Text("Use #RRGGBB or #AARRGGBB") })

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            parseHexColor(newText)?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        isError = !isValid,
        supportingText = errorText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = modifier,
    )
}

/**
 * A button showing "Label: Current" that opens a menu of options.
 * Generic over T, so it works for any enum or list.
 */
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: (T) -> String = { it.toString() },
) {
    var expanded by remember { mutableStateOf(false) }

    // The menu is anchored to the Box it is declared in.
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            // 56dp matches the height of the text fields next to it.
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                text = "$label: ${optionLabel(selected)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}