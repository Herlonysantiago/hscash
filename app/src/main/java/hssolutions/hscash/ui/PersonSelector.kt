package hssolutions.hscash.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import hssolutions.hscash.cloud.CloudSpacesRepository

@Composable
fun rememberActiveSpaceMemberNames(): List<String> {
    val context = LocalContext.current
    val cloudId = remember { SpaceStorage.active(context).cloudId }
    var names by remember(cloudId) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(cloudId) {
        names = cloudId?.let { id ->
            runCatching { CloudSpacesRepository.loadAccess(id).members.map { it.name }.distinct() }
                .getOrDefault(emptyList())
        }.orEmpty()
    }
    return names
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSelector(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    members: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (members.isNotEmpty()) expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.menuAnchor(),
            label = { Text(label) },
            placeholder = { Text(if (members.isEmpty()) "Digite um nome" else "Selecione um integrante ou digite") },
            singleLine = true,
            trailingIcon = if (members.isNotEmpty()) {{ ExposedDropdownMenuDefaults.TrailingIcon(expanded) }} else null,
            colors = hsCashOutlinedTextFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            members.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onValueChange(name)
                    expanded = false
                })
            }
            DropdownMenuItem(text = { Text("Outra pessoa...") }, onClick = {
                onValueChange("")
                expanded = false
            })
        }
    }
}
