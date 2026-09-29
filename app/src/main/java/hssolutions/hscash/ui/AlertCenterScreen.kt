package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.util.Calendar

internal enum class FinancialAlertType { GROUPED_OVERDUE, OVERDUE, TODAY, TOMORROW, DATE_REVIEW }

internal data class FinancialAlert(
    val id: String,
    val account: ContaSalva,
    val type: FinancialAlertType,
    val title: String,
    val message: String,
    val relatedAccounts: List<ContaSalva> = emptyList()
)

internal fun buildFinancialAlerts(
    accounts: List<ContaSalva>,
    referenceToday: Calendar = startOfToday()
): List<FinancialAlert> {
    val today = referenceToday.normalizedAlertDate()
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    return accounts
        .asSequence()
        .filter { it.status != ContaStatus.PAGO && it.status != ContaStatus.EXCLUIDO }
        .mapNotNull { account ->
            val due = parseContaDate(account.vencimento)?.normalizedAlertDate()
            if (due == null) {
                val name = account.fornecedor.ifBlank { account.banco.ifBlank { "Conta sem fornecedor" } }
                return@mapNotNull FinancialAlert(
                    id = "DATE_REVIEW:${account.id}",
                    account = account,
                    type = FinancialAlertType.DATE_REVIEW,
                    title = "$name precisa de conferência",
                    message = "Vencimento não reconhecido: ${account.vencimento.ifBlank { "não informado" }}"
                )
            }
            val type = when {
                due.before(today) -> FinancialAlertType.OVERDUE
                due.sameAlertDay(today) -> FinancialAlertType.TODAY
                due.sameAlertDay(tomorrow) -> FinancialAlertType.TOMORROW
                else -> return@mapNotNull null
            }
            val name = account.fornecedor.ifBlank { account.banco }
            val action = if (account.direcao == ContaDirecao.RECEBER) "receber" else "pagar"
            FinancialAlert(
                id = "${type.name}:${account.id}:${account.vencimento}",
                account = account,
                type = type,
                title = when (type) {
                    FinancialAlertType.GROUPED_OVERDUE -> "$name tem contas vencidas"
                    FinancialAlertType.OVERDUE -> "$name esta em atraso"
                    FinancialAlertType.TODAY -> "$name vence hoje"
                    FinancialAlertType.TOMORROW -> "$name vence amanha"
                    FinancialAlertType.DATE_REVIEW -> "$name precisa de conferência"
                },
                message = "${account.valor} para $action • vencimento ${account.vencimento}"
            )
        }
        .toList()
        .let { individual ->
            val overdue = accounts.filter {
                it.status != ContaStatus.PAGO && it.status != ContaStatus.EXCLUIDO &&
                    (parseContaDate(it.vencimento)?.normalizedAlertDate()?.before(today) == true)
            }
            val grouped = buildList {
                overdue.groupBy { it.fornecedor.trim().lowercase() }
                    .filterKeys { it.isNotBlank() }
                    .filterValues { it.size >= 2 }
                    .forEach { (_, items) ->
                        val first = items.first()
                        val name = first.fornecedor.trim()
                        add(FinancialAlert(
                            id = "GROUP:FORNECEDOR:${name.lowercase()}",
                            account = first,
                            type = FinancialAlertType.GROUPED_OVERDUE,
                            title = "Fornecedor: $name",
                            message = "${items.size} contas vencidas • ${formatGroupedTotal(items)} no total",
                            relatedAccounts = items.sortedBy { parseContaDate(it.vencimento)?.timeInMillis }
                        ))
                    }
                overdue.groupBy { it.categoria.trim().ifBlank { "Outros" }.lowercase() }
                    .filterValues { it.size >= 2 }
                    .forEach { (_, items) ->
                        val first = items.first()
                        val category = first.categoria.trim().ifBlank { "Outros" }
                        add(FinancialAlert(
                            id = "GROUP:CATEGORIA:${category.lowercase()}",
                            account = first,
                            type = FinancialAlertType.GROUPED_OVERDUE,
                            title = "Categoria: $category",
                            message = "${items.size} contas vencidas • ${formatGroupedTotal(items)} no total",
                            relatedAccounts = items.sortedBy { parseContaDate(it.vencimento)?.timeInMillis }
                        ))
                    }
            }
            (grouped + individual).sortedWith(compareBy<FinancialAlert> { it.type.ordinal }
                .thenBy { parseContaDate(it.account.vencimento)?.timeInMillis })
        }
}

@Composable
fun AlertCenterScreen(
    onBack: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onPayAccount: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(emptyList<ContaSalva>()) }
    var readIds by remember { mutableStateOf(AppSettings.readAlertIds(context)) }

    fun refresh() {
        scope.launch {
            accounts = ContaStorage.listarTodosEspacosAsync(context)
                .distinctBy { it.id }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val alerts = remember(accounts) { buildFinancialAlerts(accounts) }
    val supplierAlerts = alerts.filter { it.id.startsWith("GROUP:FORNECEDOR:") }
    val categoryAlerts = alerts.filter { alert ->
        alert.id.startsWith("GROUP:CATEGORIA:") &&
            alert.relatedAccounts.map { it.fornecedor.trim().lowercase() }.distinct().size > 1
    }
    val supplierGroupedAccountIds = supplierAlerts.flatMap { it.relatedAccounts }.map { it.id }.toSet()
    val urgentAlerts = alerts.filter {
        it.type != FinancialAlertType.GROUPED_OVERDUE || it.account.id !in supplierGroupedAccountIds
    }
    var expandedGroupedIds by remember { mutableStateOf(emptySet<String>()) }
    val unread = alerts.count { it.id !in readIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
                Column(Modifier.weight(1f)) {
                    Text("Central de alertas", style = MaterialTheme.typography.headlineMedium)
                    Text("${urgentAlerts.size} contas • $unread nao lido(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (unread > 0) {
                    TextButton(onClick = {
                        AppSettings.markAlertsRead(context, alerts.map(FinancialAlert::id))
                        readIds = readIds + alerts.map(FinancialAlert::id)
                    }) { Text("Marcar lidas") }
                }
            }
        }

        if (alerts.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF21A366), modifier = Modifier.size(40.dp))
                        Text("Nenhum alerta financeiro", fontWeight = FontWeight.Bold)
                        Text("Suas contas urgentes aparecerao aqui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (urgentAlerts.isNotEmpty()) {
            item { AlertSectionTitle("Contas individuais", "Vencidas sem agrupamento ou vencendo hoje e amanha") }
            items(urgentAlerts, key = FinancialAlert::id) { alert ->
                FinancialAlertCard(
                    alert = alert,
                    read = alert.id in readIds,
                    onOpen = {
                        AppSettings.markAlertRead(context, alert.id)
                        readIds = readIds + alert.id
                        onOpenAccount(alert.account.id)
                    },
                    onPay = {
                        AppSettings.markAlertRead(context, alert.id)
                        readIds = readIds + alert.id
                        onPayAccount(alert.account.id)
                    }
                )
            }
        }
        if (supplierAlerts.isNotEmpty()) {
            item { AlertSectionTitle("Vencidas por fornecedor", "Toque em um fornecedor para ver as contas") }
            items(supplierAlerts, key = FinancialAlert::id) { alert ->
                GroupedAlertItem(
                    alert = alert,
                    readIds = readIds,
                    expanded = alert.id in expandedGroupedIds,
                    onExpandedChange = { expanded ->
                        AppSettings.markAlertRead(context, alert.id)
                        readIds = readIds + alert.id
                        expandedGroupedIds = if (expanded) expandedGroupedIds + alert.id else expandedGroupedIds - alert.id
                    },
                    onOpenAccount = onOpenAccount,
                    onPayAccount = onPayAccount
                )
            }
        }
        if (categoryAlerts.isNotEmpty()) {
            item { AlertSectionTitle("Vencidas por categoria", "Toque em uma categoria para ver as contas") }
            items(categoryAlerts, key = FinancialAlert::id) { alert ->
                GroupedAlertItem(
                    alert = alert,
                    readIds = readIds,
                    expanded = alert.id in expandedGroupedIds,
                    onExpandedChange = { expanded ->
                        AppSettings.markAlertRead(context, alert.id)
                        readIds = readIds + alert.id
                        expandedGroupedIds = if (expanded) expandedGroupedIds + alert.id else expandedGroupedIds - alert.id
                    },
                    onOpenAccount = onOpenAccount,
                    onPayAccount = onPayAccount
                )
            }
        }
    }
}

@Composable
private fun GroupedAlertItem(
    alert: FinancialAlert,
    readIds: Set<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenAccount: (String) -> Unit,
    onPayAccount: (String) -> Unit
) {
    FinancialAlertCard(
        alert = alert,
        read = alert.id in readIds,
        expanded = expanded,
        onToggle = { onExpandedChange(!expanded) },
        onOpen = {},
        onPay = {},
        onOpenRelated = onOpenAccount,
        onPayRelated = onPayAccount
    )
}

@Composable
private fun AlertSectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FinancialAlertCard(
    alert: FinancialAlert,
    read: Boolean,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    onOpen: () -> Unit,
    onPay: () -> Unit,
    onOpenRelated: (String) -> Unit = {},
    onPayRelated: (String) -> Unit = {}
) {
    val (icon, color) = when (alert.type) {
        FinancialAlertType.GROUPED_OVERDUE -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        FinancialAlertType.OVERDUE -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        FinancialAlertType.TODAY -> Icons.Default.Today to Color(0xFFF59E0B)
        FinancialAlertType.TOMORROW -> Icons.Default.Notifications to MaterialTheme.colorScheme.primary
        FinancialAlertType.DATE_REVIEW -> Icons.Default.Today to MaterialTheme.colorScheme.tertiary
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, if (read) MaterialTheme.colorScheme.outlineVariant else color.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = if (onToggle != null) Modifier.fillMaxWidth().clickable { onToggle() } else Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, null, tint = color)
                Column(Modifier.weight(1f)) {
                    Text(alert.title, fontWeight = FontWeight.Bold)
                    Text(alert.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!read) Text("Nova", color = color, style = MaterialTheme.typography.labelMedium)
                if (onToggle != null) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Expandir detalhes")
                }
            }
            if (expanded && alert.relatedAccounts.isNotEmpty()) {
                alert.relatedAccounts.forEach { related ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(related.fornecedor.ifBlank { related.banco.ifBlank { related.categoria } }, fontWeight = FontWeight.Medium)
                            Text("Vencimento ${related.vencimento} • ${related.valor}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onOpenRelated(related.id) }) { Text("Abrir") }
                        TextButton(onClick = { onPayRelated(related.id) }) { Text("Pagar") }
                    }
                }
            }
            if (alert.type != FinancialAlertType.GROUPED_OVERDUE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Abrir") }
                    Button(onClick = onPay, modifier = Modifier.weight(1f)) {
                        Text(if (alert.account.direcao == ContaDirecao.RECEBER) "Receber" else "Pagar")
                    }
                }
            }
        }
    }
}

private fun Calendar.normalizedAlertDate(): Calendar = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun Calendar.sameAlertDay(other: Calendar): Boolean =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

private fun formatGroupedTotal(accounts: List<ContaSalva>): String {
    val cents = accounts.sumOf { account ->
        val normalized = account.valor.filter { it.isDigit() || it == ',' || it == '.' }
            .replace(".", "").replace(',', '.')
        ((normalized.toDoubleOrNull() ?: 0.0) * 100).toLong()
    }
    return "R$ %.2f".format(java.util.Locale("pt", "BR"), cents / 100.0)
}
