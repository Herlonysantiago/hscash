package hssolutions.hscash.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

internal data class TodayOverview(
    val overduePayables: List<ContaSalva>,
    val dueTodayPayables: List<ContaSalva>,
    val nextSevenDaysPayables: List<ContaSalva>,
    val overdueReceivables: List<ContaSalva>
) {
    val urgentCount: Int get() = overduePayables.size + dueTodayPayables.size
    val urgentValue: String get() = formatTodayMoney((overduePayables + dueTodayPayables).sumOf(::todayMoneyInCents))
}

internal fun buildTodayOverview(
    contas: List<ContaSalva>,
    referenceToday: Calendar = startOfToday()
): TodayOverview {
    val today = (referenceToday.clone() as Calendar).startAtMidnight()
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val endOfWindow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
    val pending = contas.filter { it.status != ContaStatus.PAGO && it.status != ContaStatus.EXCLUIDO }
    fun dueDate(conta: ContaSalva) = parseContaDate(conta.vencimento)?.startAtMidnight()
    fun sorted(values: List<ContaSalva>) = values.sortedWith(
        compareBy<ContaSalva> { dueDate(it)?.timeInMillis ?: Long.MAX_VALUE }
            .thenBy { it.fornecedor.ifBlank { it.banco } }
    )

    return TodayOverview(
        overduePayables = sorted(pending.filter {
            it.direcao == ContaDirecao.PAGAR && dueDate(it)?.before(today) == true
        }),
        dueTodayPayables = sorted(pending.filter {
            it.direcao == ContaDirecao.PAGAR && dueDate(it)?.isSameDay(today) == true
        }),
        nextSevenDaysPayables = sorted(pending.filter {
            val date = dueDate(it)
            it.direcao == ContaDirecao.PAGAR && date != null &&
                !date.before(tomorrow) && !date.after(endOfWindow)
        }),
        overdueReceivables = sorted(pending.filter {
            it.direcao == ContaDirecao.RECEBER && dueDate(it)?.before(today) == true
        })
    )
}

@Composable
fun HojeScreen(
    onBack: () -> Unit,
    onContaClick: (String) -> Unit,
    onPayClick: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var contas by remember { mutableStateOf(emptyList<ContaSalva>()) }
    var loading by remember { mutableStateOf(true) }
    var postponeAccount by remember { mutableStateOf<ContaSalva?>(null) }
    var postponedDate by remember { mutableStateOf("") }
    var postponeError by remember { mutableStateOf<String?>(null) }
    var receiptAccount by remember { mutableStateOf<ContaSalva?>(null) }

    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val account = receiptAccount
        receiptAccount = null
        if (uri != null && account != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { ReceiptHelper.saveReceipt(context, account.id, uri) }
                if (path != null) {
                    if (account.comprovantePath != path) {
                        withContext(Dispatchers.IO) { ReceiptHelper.deleteStoredFile(account.comprovantePath) }
                    }
                    ContaStorage.atualizarContaAsync(context, account.copy(comprovantePath = path))
                    contas = ContaStorage.listarContasAsync(context)
                    Toast.makeText(context, "Comprovante anexado sem alterar o pagamento", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Nao foi possivel salvar o comprovante", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun loadAccounts() {
        scope.launch {
            contas = ContaStorage.listarContasAsync(context)
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadAccounts() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) loadAccounts()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overview = remember(contas) { buildTodayOverview(contas) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("Hoje", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("O que precisa da sua atencao agora", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            TodaySummaryCard(
                count = overview.urgentCount,
                value = overview.urgentValue,
                loading = loading
            )
        }

        if (!loading && overview.urgentCount == 0 && overview.nextSevenDaysPayables.isEmpty() && overview.overdueReceivables.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tudo em dia", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("Nao ha contas urgentes ou vencimentos nos proximos sete dias.", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }

        todaySection(
            title = "Vencidas",
            subtitle = "Contas a pagar que ja passaram do vencimento",
            accounts = overview.overduePayables,
            icon = Icons.Default.EventBusy,
            color = Color(0xFFD32F2F),
            onContaClick = onContaClick,
            onPayClick = onPayClick,
            onPostpone = {
                postponeAccount = it
                postponedDate = it.vencimento
                postponeError = null
            },
            onAttach = {
                receiptAccount = it
                receiptLauncher.launch(arrayOf("application/pdf", "image/*"))
            }
        )
        todaySection(
            title = "Vencem hoje",
            subtitle = "Contas que precisam ser resolvidas hoje",
            accounts = overview.dueTodayPayables,
            icon = Icons.Default.Today,
            color = Color(0xFFF57C00),
            onContaClick = onContaClick,
            onPayClick = onPayClick,
            onPostpone = {
                postponeAccount = it
                postponedDate = it.vencimento
                postponeError = null
            },
            onAttach = {
                receiptAccount = it
                receiptLauncher.launch(arrayOf("application/pdf", "image/*"))
            }
        )
        todaySection(
            title = "Proximos 7 dias",
            subtitle = "Vencimentos depois de hoje",
            accounts = overview.nextSevenDaysPayables,
            icon = Icons.Default.EventAvailable,
            color = Color(0xFF1565C0),
            onContaClick = onContaClick,
            onPayClick = onPayClick,
            onPostpone = {
                postponeAccount = it
                postponedDate = it.vencimento
                postponeError = null
            },
            onAttach = {
                receiptAccount = it
                receiptLauncher.launch(arrayOf("application/pdf", "image/*"))
            }
        )
        todaySection(
            title = "A receber em atraso",
            subtitle = "Recebimentos que ainda estao pendentes",
            accounts = overview.overdueReceivables,
            icon = Icons.Default.Payments,
            color = Color(0xFF7B1FA2),
            onContaClick = onContaClick,
            onPayClick = onPayClick,
            onPostpone = {
                postponeAccount = it
                postponedDate = it.vencimento
                postponeError = null
            },
            onAttach = {
                receiptAccount = it
                receiptLauncher.launch(arrayOf("application/pdf", "image/*"))
            }
        )
    }

    postponeAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { postponeAccount = null },
            title = { Text("Adiar conta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Informe o novo vencimento de ${account.fornecedor.ifBlank { account.banco }}.")
                    OutlinedTextField(
                        value = postponedDate,
                        onValueChange = {
                            postponedDate = it
                            postponeError = null
                        },
                        label = { Text("Novo vencimento (dd/mm/aaaa)") },
                        singleLine = true,
                        isError = postponeError != null,
                        colors = hsCashOutlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    postponeError?.let { Text(it, color = Color(0xFFB42318)) }
                    Text(
                        "A conta continuara pendente e os lembretes serao atualizados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!isValidContaDate(postponedDate)) {
                        postponeError = "Digite uma data valida no formato dd/mm/aaaa."
                        return@Button
                    }
                    scope.launch {
                        ContaStorage.atualizarContaAsync(context, account.copy(vencimento = postponedDate.trim()))
                        contas = ContaStorage.listarContasAsync(context)
                        postponeAccount = null
                        Toast.makeText(context, "Vencimento atualizado", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Adiar") }
            },
            dismissButton = {
                TextButton(onClick = { postponeAccount = null }) { Text("Cancelar") }
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.todaySection(
    title: String,
    subtitle: String,
    accounts: List<ContaSalva>,
    icon: ImageVector,
    color: Color,
    onContaClick: (String) -> Unit,
    onPayClick: (String) -> Unit,
    onPostpone: (ContaSalva) -> Unit,
    onAttach: (ContaSalva) -> Unit
) {
    if (accounts.isEmpty()) return
    item(key = "header-$title") {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("$title (${accounts.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                formatTodayMoney(accounts.sumOf(::todayMoneyInCents)),
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
    items(accounts, key = { "$title-${it.id}" }) { conta ->
        TodayAccountCard(
            conta = conta,
            color = color,
            onClick = { onContaClick(conta.id) },
            onPay = { onPayClick(conta.id) },
            onPostpone = { onPostpone(conta) },
            onAttach = { onAttach(conta) }
        )
    }
}

@Composable
private fun TodaySummaryCard(count: Int, value: String, loading: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B3D6E)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Prioridade do dia", color = Color(0xFFD6E9FF))
            Text(
                if (loading) "Carregando..." else "$count conta(s) exigem atencao",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (!loading) Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Inclui contas vencidas e com vencimento hoje.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD6E9FF))
        }
    }
}

@Composable
private fun TodayAccountCard(
    conta: ContaSalva,
    color: Color,
    onClick: () -> Unit,
    onPay: () -> Unit,
    onPostpone: () -> Unit,
    onAttach: () -> Unit
) {
    val identity = categoryIdentity(conta.categoria)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(identity.icon, contentDescription = null, tint = identity.color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    conta.fornecedor.ifBlank { conta.banco },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${statusLabel(conta)} • ${conta.vencimento}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
                Text(identity.label, style = MaterialTheme.typography.bodySmall, color = identity.color)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(conta.valor, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.ChevronRight, contentDescription = "Abrir conta", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onPay, modifier = Modifier.weight(1f)) {
                Text(if (conta.direcao == ContaDirecao.RECEBER) "Receber" else "Pagar")
            }
            TextButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text("Editar") }
            TextButton(onClick = onPostpone, modifier = Modifier.weight(1f)) { Text("Adiar") }
            TextButton(onClick = onAttach, modifier = Modifier.weight(1f)) { Text("Anexar") }
        }
    }
}

private fun Calendar.startAtMidnight(): Calendar = apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun Calendar.isSameDay(other: Calendar): Boolean =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

internal fun todayMoneyInCents(conta: ContaSalva): Long =
    conta.valor.filter(Char::isDigit).toLongOrNull() ?: 0L

private fun formatTodayMoney(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal(cents).movePointLeft(2))
