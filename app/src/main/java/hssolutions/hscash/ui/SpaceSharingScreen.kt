package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hssolutions.hscash.cloud.CloudAuthRepository
import hssolutions.hscash.cloud.CloudMemberRole
import hssolutions.hscash.cloud.CloudReceivedInvite
import hssolutions.hscash.cloud.CloudSpaceAccess
import hssolutions.hscash.cloud.CloudSpaceAccessMember
import hssolutions.hscash.cloud.CloudSpaceInvite
import hssolutions.hscash.cloud.CloudSpacesRepository
import hssolutions.hscash.cloud.CloudSyncRepository
import hssolutions.hscash.cloud.cloudAuthErrorMessage
import kotlinx.coroutines.launch

@Composable
fun SpaceSharingScreen(localSpaceId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localSpace = remember(localSpaceId) { SpaceStorage.list(context).firstOrNull { it.id == localSpaceId } }
    var access by remember { mutableStateOf<CloudSpaceAccess?>(null) }
    var received by remember { mutableStateOf<List<CloudReceivedInvite>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<CloudSpaceAccessMember?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    fun runAction(success: String, action: suspend () -> Unit) {
        loading = true
        error = null
        message = null
        scope.launch {
            runCatching { action() }
                .onSuccess { message = success; refreshKey++ }
                .onFailure { error = cloudAuthErrorMessage(it) }
            loading = false
        }
    }

    LaunchedEffect(refreshKey, localSpace?.cloudId) {
        if (CloudAuthRepository.currentUser() == null) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            val invitations = CloudSpacesRepository.receivedInvites()
            val spaceAccess = localSpace?.cloudId?.let { CloudSpacesRepository.loadAccess(it) }
            invitations to spaceAccess
        }.onSuccess { (invitations, spaceAccess) ->
            received = invitations
            access = spaceAccess
        }.onFailure { error = cloudAuthErrorMessage(it) }
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
            Column(Modifier.weight(1f)) {
                Text("Compartilhamento", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(localSpace?.name ?: "Convites e espaços compartilhados", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (CloudAuthRepository.currentUser() == null) {
            InfoCard("Conta online necessária", "Entre na conta online antes de enviar ou aceitar convites.")
            return@Column
        }

        if (loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        error?.let { ErrorSharingCard(it) }
        message?.let { SuccessSharingCard(it) }

        Text("Convites recebidos", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        if (received.isEmpty()) {
            InfoCard("Nenhum convite pendente", "Quando alguém compartilhar um espaço com você, ele aparecerá aqui.")
        } else {
            received.forEach { item ->
                ReceivedInviteCard(
                    item = item,
                    enabled = !loading,
                    onAccept = {
                        runAction("Convite aceito e espaço sincronizado.") {
                            CloudSpacesRepository.acceptInvite(item.invite)
                            CloudSyncRepository.sync(context.applicationContext)
                        }
                    },
                    onDecline = { runAction("Convite recusado.") { CloudSpacesRepository.declineInvite(item.invite.id) } }
                )
            }
        }

        if (localSpace != null) {
            Text("Integrantes de ${localSpace.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            if (localSpace.cloudId == null) {
                InfoCard("Sincronize este espaço", "Abra Conta online e sincronize antes de adicionar integrantes.")
            } else {
                access?.let { current ->
                    val me = current.members.firstOrNull { it.isCurrentUser }
                    val canManage = me?.role in setOf(CloudMemberRole.OWNER, CloudMemberRole.ADMIN)
                    current.members.forEach { member ->
                        MemberCard(
                            member = member,
                            canManage = canManage && member.role != CloudMemberRole.OWNER && !member.isCurrentUser,
                            onEdit = { editingMember = member },
                            onRemove = {
                                runAction("Integrante removido.") {
                                    CloudSpacesRepository.removeMember(current.space.id, member.userId)
                                }
                            }
                        )
                    }
                    if (canManage) {
                        Button(onClick = { showInviteDialog = true }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PersonAdd, null)
                            Text("  Convidar integrante")
                        }
                        Text("Convites enviados", fontWeight = FontWeight.Bold)
                        if (current.pendingInvites.isEmpty()) {
                            Text("Nenhum convite aguardando resposta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            current.pendingInvites.forEach { invite ->
                                PendingInviteCard(invite, !loading) {
                                    runAction("Convite cancelado.") { CloudSpacesRepository.cancelInvite(invite.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInviteDialog && access != null) {
        InviteDialog(
            onDismiss = { showInviteDialog = false },
            onInvite = { email, role ->
                showInviteDialog = false
                runAction("Convite enviado para $email.") {
                    CloudSpacesRepository.invite(access!!.space.id, email, role)
                }
            }
        )
    }

    editingMember?.let { member ->
        RoleDialog(
            title = member.name,
            initialRole = member.role,
            onDismiss = { editingMember = null },
            onSave = { role ->
                editingMember = null
                runAction("Permissão atualizada.") {
                    CloudSpacesRepository.updateMemberRole(access!!.space.id, member.userId, role)
                }
            }
        )
    }
}

@Composable
private fun MemberCard(member: CloudSpaceAccessMember, canManage: Boolean, onEdit: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(member.name + if (member.isCurrentUser) " (você)" else "", fontWeight = FontWeight.Bold)
                Text(roleLabel(member.role), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canManage) {
                TextButton(onClick = onEdit) { Text("Permissão") }
                IconButton(onClick = onRemove) { Icon(Icons.Default.DeleteOutline, "Remover integrante") }
            }
        }
    }
}

@Composable
private fun ReceivedInviteCard(item: CloudReceivedInvite, enabled: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.spaceName, fontWeight = FontWeight.Bold)
            Text("Permissão: ${roleLabel(item.invite.role)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, enabled = enabled) { Icon(Icons.Default.Check, null); Text(" Aceitar") }
                OutlinedButton(onClick = onDecline, enabled = enabled) { Icon(Icons.Default.Close, null); Text(" Recusar") }
            }
        }
    }
}

@Composable
private fun PendingInviteCard(invite: CloudSpaceInvite, enabled: Boolean, onCancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(invite.email, fontWeight = FontWeight.Bold)
                Text(roleLabel(invite.role), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onCancel, enabled = enabled) { Text("Cancelar") }
        }
    }
}

@Composable
private fun InviteDialog(onDismiss: () -> Unit, onInvite: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(CloudMemberRole.EDITOR) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convidar integrante") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = false },
                    label = { Text("E-mail") },
                    isError = error,
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                RoleChoices(role) { role = it }
                Text(roleDescription(role), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = {
            if (!email.contains("@") || !email.contains(".")) error = true else onInvite(email.trim(), role)
        }) { Text("Enviar convite") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RoleDialog(title: String, initialRole: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var role by remember { mutableStateOf(initialRole) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissão de $title") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { RoleChoices(role) { role = it }; Text(roleDescription(role)) } },
        confirmButton = { TextButton(onClick = { onSave(role) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RoleChoices(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CloudMemberRole.INVITABLE.forEach { role ->
            FilterChip(selected = selected == role, onClick = { onSelect(role) }, label = { Text(roleLabel(role)) })
        }
    }
}

private fun roleLabel(role: String) = when (role) {
    CloudMemberRole.OWNER -> "Proprietário"
    CloudMemberRole.ADMIN -> "Administrador"
    CloudMemberRole.EDITOR -> "Editor"
    else -> "Visualizador"
}

private fun roleDescription(role: String) = when (role) {
    CloudMemberRole.ADMIN -> "Pode administrar integrantes e editar todas as contas do espaço."
    CloudMemberRole.EDITOR -> "Pode cadastrar, editar e confirmar pagamentos."
    else -> "Pode consultar dados e relatórios, sem realizar alterações."
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body) }
    }
}

@Composable
private fun ErrorSharingCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(message, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun SuccessSharingCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(message, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
