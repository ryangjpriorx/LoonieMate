@file:OptIn(ExperimentalMaterial3Api::class)

package looniemate.app.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import looniemate.app.ui.theme.Numbers
import looniemate.engine.Fmt
import looniemate.engine.isDate
import java.time.LocalDate
import looniemate.engine.*

@Composable
fun MoneyField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), hint: String? = null) {
    val ch = marking(onChange)
    OutlinedTextField(value, { v -> ch(v.replace(',', '.').filter { it.isDigit() || it == '.' }) }, modifier, label = { Text(label) }, prefix = { Text("$") },
        singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.merge(Numbers), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = slot(hint), placeholder = { Text("0.00") })
}

@Composable
fun TextFieldRow(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), hint: String? = null, number: Boolean = false, placeholder: String? = null) {
    OutlinedTextField(value, marking(onChange), modifier, label = { Text(label) }, singleLine = true, supportingText = slot(hint),
        placeholder = slot(placeholder), keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default)
}

/** Two decimals with a dot, whatever the phone's language (so 12.50 never becomes 1250). */
fun money2(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

/** Text for an optional field slot. */
fun slot(s: String?): (@Composable () -> Unit)? = s?.let { v -> @Composable { Text(v) } }

private fun toMillis(d: String): Long? = if (isDate(d)) LocalDate.parse(d).toEpochDay() * 86_400_000L else null
private fun fromMillis(ms: Long): String = LocalDate.ofEpochDay(Math.floorDiv(ms, 86_400_000L)).toString()

/** A date field that opens the Android calendar picker. */
@Composable
fun DateField(value: String, onChange0: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), hint: String? = null, clearable: Boolean = false) {
    val onChange = marking(onChange0)
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(if (isDate(value)) Fmt.dLabel(value) + ", " + value.take(4) else "", {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.Event, null) }, supportingText = slot(hint), singleLine = true)
        Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, null) { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = toMillis(value) ?: toMillis(LocalDate.now().toString()))
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChange(fromMillis(it)) }; open = false }) { Text("OK") } },
            dismissButton = { Column { if (clearable) TextButton(onClick = { onChange(""); open = false }) { Text("Clear") } else TextButton(onClick = { open = false }) { Text("Cancel") } } }) {
            DatePicker(state)
        }
    }
}

/** A drop-down list of choices (key to label). */
@Composable
fun SelectField(value: String, options: List<Pair<String, String>>, onChange0: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), hint: String? = null) {
    val onChange = marking(onChange0)
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(options.find { it.first == value }?.second ?: "", {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) }, supportingText = slot(hint), singleLine = true)
        Box(Modifier.matchParentSize().clickable(remember { MutableInteractionSource() }, null) { open = true })
        DropdownMenu(open, { open = false }) {
            options.forEach { (k, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onChange(k); open = false }) }
        }
    }
}
