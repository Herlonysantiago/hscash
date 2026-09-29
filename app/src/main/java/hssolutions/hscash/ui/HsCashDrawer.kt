package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import hssolutions.hscash.cloud.CloudUser
import hssolutions.hscash.cloud.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import java.text.DateFormat
import java.util.Date

data class DrawerDestination(
    val label: String,
    val icon: ImageVector,
    val route: String,
    val section: String
)

@Composable
fun HsCashDrawerContent(
    refreshKey: Int,
    destinations: List<DrawerDestination>,
    onNavigate: (String) -> Unit,
    onSelectSpace: (String) -> Unit,
    onManageSpaces: () -> Unit,
    onOpenAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var user by remember { mutableStateOf(CloudAuthRepository.currentUser()) }
    var profileName by remember { mutableStateOf("") }
    var spaces by remember { mutableStateOf(SpaceStorage.list(context)) }
    var activeSpaceId by remember { mutableStateOf(SpaceStorage.activeSpaceId(context)) }
    var unreadCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        SupabaseProvider.client?.auth?.sessionStatus?.collect { status ->
            user = (status as? SessionStatus.Authenticated)?.session?.user?.let {
                CloudUser(it.id, it.email.orEmpty())
            }
        }
    }

    LaunchedEffect(refreshKey, user?.id) {
        spaces = SpaceStorage.list(context)
        activeSpaceId = SpaceStorage.activeSpaceId(context)
        unreadCount = buildFinancialAlerts(ContaStorage.listarContasAsync(context))
            .count { it.id !in AppSettings.readAlertIds(context) }
        profileName = if (user == null) {
            ""
        } else {
            runCatching { CloudAuthRepository.loadProfile().nome }.getOrDefault("")
        }
    }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.88f).fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("H", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                }
                Text("HS Cash", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            AccountDrawerCard(
                user = user,
                profileName = profileName,
                onClick = onOpenAccount
            )

            DrawerSectionTitle("ESPAÇOS")
            spaces.forEach { space ->
                SpaceDrawerRow(
                    space = space,
                    active = space.id == activeSpaceId,
                    onSelect = {
                        activeSpaceId = space.id
                        onSelectSpace(space.id)
                    },
                    onEdit = onManageSpaces
                )
            }
            DrawerActionRow(
                label = "Gerenciar espaços",
                icon = Icons.Default.ManageAccounts,
                onClick = onManageSpaces,
                outlined = true
            )

            destinations.groupBy { it.section }.forEach { (section, sectionDestinations) ->
                DrawerSectionTitle(section)
                sectionDestinations.forEach { destination ->
                    DrawerActionRow(
                        label = destination.label,
                        icon = destination.icon,
                        badgeCount = if (destination.route == "alertas") unreadCount else 0,
                        onClick = { onNavigate(destination.route) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            val lastSyncAt = AppSettings.lastCloudSyncAt(context)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (lastSyncAt > 0L) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (lastSyncAt > 0L) Color(0xFF168A49) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (lastSyncAt > 0L) {
                        "Sincronizado em ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSyncAt))}"
                    } else {
                        "Ainda não sincronizado"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (user != null) {
                DrawerActionRow(label = "Sair da conta", icon = Icons.Default.Logout, onClick = onSignOut)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AccountDrawerCard(user: CloudUser?, profileName: String, onClick: () -> Unit) {
    val connected = user != null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(
                    if (connected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (profileName.ifBlank { user?.email.orEmpty() }).firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (connected) "Conta conectada" else "Modo offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (connected) profileName.ifBlank { user!!.email.substringBefore('@') } else "Conectar conta online",
                    fontWeight = FontWeight.Bold,
                    color = if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                if (connected) {
                    Text(user!!.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                }
            }
            Icon(
                if (connected) Icons.Default.Edit else Icons.Default.ChevronRight,
                contentDescription = if (connected) "Editar conta online" else "Conectar conta",
                tint = if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SpaceDrawerRow(space: LocalSpace, active: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    val accent = colorFromHex(space.color)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = if (active) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) accent else MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                Icon(spaceIcon(space.icon), null, tint = Color.White, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(space.name, fontWeight = FontWeight.Bold)
                Text(
                    if (active) "Ativo" else spaceTypeLabel(space.type),
                    color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar ${space.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DrawerActionRow(
    label: String,
    icon: ImageVector,
    badgeCount: Int = 0,
    outlined: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (outlined) Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        if (badgeCount > 0) {
            Badge(containerColor = MaterialTheme.colorScheme.error) {
                Text(if (badgeCount > 99) "99+" else badgeCount.toString())
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun defaultDrawerDestinations(): List<DrawerDestination> = listOf(
    DrawerDestination("Hoje", Icons.Default.CalendarMonth, "hoje", "FINANCEIRO"),
    DrawerDestination("Recorrentes", Icons.Default.History, "recorrentes", "FINANCEIRO"),
    DrawerDestination("Contas", Icons.Default.Payments, "contas", "FINANCEIRO"),
    DrawerDestination("Boletos", Icons.Default.QrCodeScanner, "boletos", "FINANCEIRO"),
    DrawerDestination("Histórico", Icons.Default.History, "historico", "FINANCEIRO"),
    DrawerDestination("Calendário", Icons.Default.CalendarMonth, "calendario", "FINANCEIRO"),
    DrawerDestination("Relatórios", Icons.Default.Description, "relatorio", "FINANCEIRO"),
    DrawerDestination("Central de alertas", Icons.Default.Notifications, "alertas", "FINANCEIRO"),
    DrawerDestination("Clientes", Icons.Default.Groups, "clientes", "CLIENTES E COBRANÇAS"),
    DrawerDestination("Cobranças", Icons.Default.Send, "cobrancas", "CLIENTES E COBRANÇAS"),
    DrawerDestination("Configurações", Icons.Default.Settings, "configuracoes", "APLICATIVO"),
    DrawerDestination("Lixeira", Icons.Default.DeleteOutline, "lixeira", "APLICATIVO"),
    DrawerDestination("Ajuda e tutorial", Icons.Default.HelpOutline, "onboarding", "APLICATIVO")
)
