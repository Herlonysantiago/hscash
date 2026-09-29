package hssolutions.hscash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

private val recurrencePresets = listOf(
    "Semanal" to "1 semana",
    "Mensal" to "1 mês",
    "Bimestral" to "2 meses",
    "Trimestral" to "3 meses",
    "Semestral" to "6 meses",
    "Anual" to "12 meses"
)

@Composable
fun RecurrenceSelector(value: String, onValueChange: (String) -> Unit) {
    val isPreset = recurrencePresets.any { it.first == value }
    var custom by remember(value) { mutableStateOf(!isPreset) }
    var customAmount by remember(value) {
        mutableStateOf(Regex("A cada (\\d+)").find(value)?.groupValues?.getOrNull(1) ?: "")
    }
    var customUnit by remember(value) {
        mutableStateOf(
            when {
                value.contains("dia", true) -> "dias"
                value.contains("semana", true) -> "semanas"
                value.contains("ano", true) -> "anos"
                else -> "meses"
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Frequência", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recurrencePresets) { (stored, label) ->
                FilterChip(
                    selected = !custom && value == stored,
                    onClick = { custom = false; onValueChange(stored) },
                    label = { Text(label) }
                )
            }
            item {
                FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Personalizado") })
            }
        }
        if (custom) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = customAmount,
                    onValueChange = { raw ->
                        customAmount = raw.filter(Char::isDigit).take(3)
                        customAmount.toIntOrNull()?.takeIf { it > 0 }?.let { onValueChange("A cada $it $customUnit") }
                    },
                    label = { Text("A cada") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.weight(0.42f)
                )
                LazyRow(Modifier.weight(0.58f).padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("dias", "semanas", "meses", "anos")) { unit ->
                        FilterChip(
                            selected = customUnit == unit,
                            onClick = {
                                customUnit = unit
                                customAmount.toIntOrNull()?.takeIf { it > 0 }?.let { onValueChange("A cada $it $unit") }
                            },
                            label = { Text(unit) }
                        )
                    }
                }
            }
        }
        Text("Próxima cobrança calculada a partir da data informada.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
