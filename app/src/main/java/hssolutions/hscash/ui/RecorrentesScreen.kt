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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private enum class RecurrenceFilter { ALL, SUBSCRIPTIONS, ACCOUNTS }

@Composable
fun RecorrentesScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenAccount: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<ContaSalva>>(emptyList()) }
    var filter by remember { mutableStateOf(RecurrenceFilter.ALL) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var ending by remember { mutableStateOf<ContaSalva?>(null) }

    LaunchedEffect(refreshKey) { accounts = ContaStorage.listarContasAsync(context) }

    val recurring = accounts.filter { it.recorrente }
    val groups = recurring.groupBy(::recurrenceSeriesKey)
    val representatives = groups.values.mapNotNull(::nextRecurrenceOccurrence)
        .filter {
            when (filter) {
                RecurrenceFilter.ALL -> true
                RecurrenceFilter.SUBSCRIPTIONS -> it.tipo == "Assinatura"
                RecurrenceFilter.ACCOUNTS -> it.tipo != "Assinatura"
            }
        }.sortedBy { parseContaDate(it.vencimento)?.timeInMillis ?: Long.MAX_VALUE }
    val nextTotal = representatives.sumOf(::moneyToCents)
    val paidThisMonth = recurring.filter(::paidInCurrentMonth).sumOf(::moneyToCents)

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") }
            Column(Modifier.weight(1f)) {
                Text("Recorrentes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Assinaturas e contas que se repetem", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("PRÓXIMAS COBRANÇAS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(formatCents(nextTotal), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text("Pago neste mês: ${formatCents(paidThisMonth)}", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Autorenew, null)
            Text("  Nova assinatura ou conta recorrente")
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = filter == RecurrenceFilter.ALL, onClick = { filter = RecurrenceFilter.ALL }, label = { Text("Todas") }) }
            item { FilterChip(selected = filter == RecurrenceFilter.SUBSCRIPTIONS, onClick = { filter = RecurrenceFilter.SUBSCRIPTIONS }, label = { Text("Assinaturas") }) }
            item { FilterChip(selected = filter == RecurrenceFilter.ACCOUNTS, onClick = { filter = RecurrenceFilter.ACCOUNTS }, label = { Text("Contas") }) }
        }

        if (representatives.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("Nenhuma recorrência neste filtro. Cadastre uma conta e ative Conta recorrente.", Modifier.padding(16.dp))
            }
        } else {
            representatives.forEach { account ->
                RecurrenceCard(
                    account = account,
                    onPay = {
                        scope.launch {
                            ContaStorage.marcarComoPagoAsync(context, account.id)
                            refreshKey++
                        }
                    },
                    onEdit = { onOpenAccount(account.id) },
                    onEnd = { ending = account }
                )
            }
        }
    }

    ending?.let { account ->
        AlertDialog(
            onDismissRequest = { ending = null },
            title = { Text("Encerrar recorrência?") },
            text = { Text("As cobranças futuras pendentes serão removidas. O histórico já pago será preservado.") },
            confirmButton = {
                TextButton(onClick = {
                    ending = null
                    scope.launch { ContaStorage.encerrarRecorrenciaAsync(context, account); refreshKey++ }
                }) { Text("Encerrar") }
            },
            dismissButton = { TextButton(onClick = { ending = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun RecurrenceCard(account: ContaSalva, onPay: () -> Unit, onEdit: () -> Unit, onEnd: () -> Unit) {
    val overdue = account.status == ContaStatus.PENDENTE && (parseContaDate(account.vencimento)?.before(startOfToday()) == true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (overdue) Color(0xFFD08A00) else MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(account.fornecedor.ifBlank { account.categoria }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${if (account.tipo == "Assinatura") "Assinatura" else "Conta recorrente"} • ${recurrenceLabel(account.recorrencia)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(account.valor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            }
            Text("Próxima cobrança: ${account.vencimento}", color = if (overdue) Color(0xFFD08A00) else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (account.status == ContaStatus.PENDENTE) {
                    Button(onClick = onPay, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, null)
                        Text("  Marcar como paga")
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
                IconButton(onClick = onEnd) { Icon(Icons.Default.StopCircle, "Encerrar recorrência") }
            }
        }
    }
}

private fun recurrenceSeriesKey(account: ContaSalva): String = listOf(
    account.spaceId,
    account.codigo,
    account.fornecedor.lowercase(),
    account.valor,
    account.direcao,
    account.recorrencia
).joinToString("|")

private fun nextRecurrenceOccurrence(group: List<ContaSalva>): ContaSalva? {
    val today = startOfToday()
    val pending = group.filter { it.status == ContaStatus.PENDENTE }
    return pending.minByOrNull {
        val date = parseContaDate(it.vencimento) ?: return@minByOrNull Long.MAX_VALUE
        if (date.before(today)) date.timeInMillis else date.timeInMillis
    } ?: group.maxByOrNull { parseContaDate(it.vencimento)?.timeInMillis ?: 0L }
}

private fun paidInCurrentMonth(account: ContaSalva): Boolean {
    if (account.status != ContaStatus.PAGO || account.pagoEm == null) return false
    val paid = Calendar.getInstance().apply { timeInMillis = account.pagoEm }
    val now = Calendar.getInstance()
    return paid.get(Calendar.MONTH) == now.get(Calendar.MONTH) && paid.get(Calendar.YEAR) == now.get(Calendar.YEAR)
}

private fun moneyToCents(account: ContaSalva): Long {
    val normalized = account.valor.replace("R$", "", true).replace(" ", "").replace(" ", "")
        .replace(".", "").replace(',', '.')
    return normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
}

private fun formatCents(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    .format(BigDecimal(cents).movePointLeft(2))
