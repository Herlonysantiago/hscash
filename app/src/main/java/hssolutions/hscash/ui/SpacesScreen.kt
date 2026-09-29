package hssolutions.hscash.ui

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hssolutions.hscash.cloud.CloudAuthRepository

private const val PRO_SPACE_LIMIT = 2

@Composable
fun SpacesScreen(
    onBack: () -> Unit,
    onSpaceSelected: () -> Unit,
    onOpenOnlineAccount: () -> Unit,
    onOpenSharing: (String?) -> Unit
) {
    val context = LocalContext.current
    var spaces by remember { mutableStateOf(SpaceStorage.list(context)) }
    var activeId by remember { mutableStateOf(SpaceStorage.activeSpaceId(context)) }
    var editing by remember { mutableStateOf<LocalSpace?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Espaços", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Separe sua vida pessoal, família ou empresa", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Cada espaço é independente", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Contas, relatórios, alertas e recorrências exibem somente o espaço selecionado.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        val cloudUser = CloudAuthRepository.currentUser()
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenOnlineAccount),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(if (cloudUser == null) "Conectar conta online" else "Conta online conectada", fontWeight = FontWeight.Bold)
                    Text(
                        cloudUser?.email ?: "Login, recuperação e futura sincronização",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("Abrir", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }

        if (cloudUser != null) {
            OutlinedButton(onClick = { onOpenSharing(null) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.GroupAdd, contentDescription = null)
                Text("  Convites recebidos")
            }
        }

        spaces.forEach { space ->
            SpaceCard(
                space = space,
                selected = space.id == activeId,
                onSelect = {
                    if (SpaceStorage.select(context, space.id)) {
                        activeId = space.id
                        onSpaceSelected()
                    }
                },
                onEdit = { editing = space },
                onShare = { onOpenSharing(space.id) },
                sharingEnabled = cloudUser != null
            )
        }

        if (spaces.size < PRO_SPACE_LIMIT) {
            Button(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("  Criar novo espaço")
            }
        } else {
            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "O plano Pro permite até 2 espaços. O Business terá espaços ilimitados.", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Limite de 2 espaços do plano Pro")
            }
        }

        Text(
            if (cloudUser == null) "Conecte uma conta online para convidar integrantes." else "Cada integrante só acessa os espaços para os quais recebeu e aceitou um convite.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
    }

    if (creating) {
        SpaceEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, type, icon, color ->
                SpaceStorage.create(context, name, type, icon, color)?.let { created ->
                    spaces = SpaceStorage.list(context)
                    SpaceStorage.select(context, created.id)
                    activeId = created.id
                    creating = false
                    onSpaceSelected()
                }
            }
        )
    }

    editing?.let { space ->
        SpaceEditorDialog(
            initial = space,
            onDismiss = { editing = null },
            onSave = { name, type, icon, color ->
                SpaceStorage.update(context, space.copy(name = name, type = type, icon = icon, color = color))
                spaces = SpaceStorage.list(context)
                editing = null
            }
        )
    }
}

@Composable
private fun SpaceCard(
    space: LocalSpace,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    sharingEnabled: Boolean
) {
    val accent = colorFromHex(space.color)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(spaceIcon(space.icon), contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(space.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(spaceTypeLabel(space.type), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected) Text("Espaço ativo", color = accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = accent)
            if (sharingEnabled) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "Gerenciar integrantes", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar espaço", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SpaceEditorDialog(
    initial: LocalSpace?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: SpaceType.PERSONAL) }
    var icon by remember(initial?.id) { mutableStateOf(initial?.icon ?: "wallet") }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: "#0B3D6E") }
    var showNameError by remember { mutableStateOf(false) }
    val colors = listOf("#0B3D6E", "#1565C0", "#0F766E", "#7C3AED", "#C2410C", "#BE123C")
    val icons = listOf("wallet", "home", "family", "business", "store", "bank")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo espaço" else "Editar espaço", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showNameError = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do espaço") },
                    placeholder = { Text("Ex: Minha casa ou Loja Centro") },
                    isError = showNameError,
                    supportingText = if (showNameError) ({ Text("Informe um nome") }) else null,
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                Text("Tipo", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        SpaceType.PERSONAL to "Pessoal",
                        SpaceType.FAMILY to "Família",
                        SpaceType.COMPANY to "Empresa"
                    ).forEach { (value, label) ->
                        FilterChip(selected = type == value, onClick = { type = value }, label = { Text(label) })
                    }
                }
                Text("Ícone", fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    icons.forEach { value ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (icon == value) colorFromHex(color).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = value },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(spaceIcon(value), contentDescription = null, tint = if (icon == value) colorFromHex(color) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Cor", fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    colors.forEach { value ->
                        Box(
                            modifier = Modifier
                                .size(if (color == value) 40.dp else 34.dp)
                                .clip(CircleShape)
                                .background(colorFromHex(value))
                                .clickable { color = value },
                            contentAlignment = Alignment.Center
                        ) {
                            if (color == value) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) showNameError = true else onSave(name, type, icon, color)
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

internal fun spaceTypeLabel(type: String): String = when (type) {
    SpaceType.FAMILY -> "Família"
    SpaceType.COMPANY -> "Empresa"
    else -> "Pessoal"
}

internal fun spaceIcon(name: String): ImageVector = when (name) {
    "home" -> Icons.Default.Home
    "family" -> Icons.Default.FamilyRestroom
    "business" -> Icons.Default.Business
    "store" -> Icons.Default.Store
    "bank" -> Icons.Default.AccountBalance
    else -> Icons.Default.Wallet
}

internal fun colorFromHex(value: String): Color = runCatching {
    Color(AndroidColor.parseColor(value))
}.getOrDefault(Color(0xFF0B3D6E))
