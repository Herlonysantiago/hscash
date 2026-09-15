package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RelatorioScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val contaList = rememberContaListState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val helpTargets = rememberScreenHelpTargets()
    val contas = contaList.contas
    var selectedMonth by remember { mutableStateOf(startOfCurrentMonth()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) contaList.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val monthLabel = remember(selectedMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("pt-BR"))
            .format(selectedMonth.time)
            .replaceFirstChar { it.uppercase() }
    }
    val paidThisMonth = contas.filter {
        it.direcao == ContaDirecao.PAGAR &&
            it.status == ContaStatus.PAGO &&
            paidInMonth(it, selectedMonth)
    }
    val pendingThisMonth = contas.filter {
        it.direcao == ContaDirecao.PAGAR &&
            it.status != ContaStatus.PAGO &&
            dueInMonth(it, selectedMonth)
    }
    val receivedThisMonth = contas.filter {
        it.direcao == ContaDirecao.RECEBER &&
            it.status == ContaStatus.PAGO &&
            paidInMonth(it, selectedMonth)
    }
    val receivableThisMonth = contas.filter {
        it.direcao == ContaDirecao.RECEBER &&
            it.status != ContaStatus.PAGO &&
            dueInMonth(it, selectedMonth)
    }
    val previousMonth = remember(selectedMonth) { selectedMonth.copyWithMonthOffset(-1) }
    val paidPreviousMonth = contas.filter {
        it.direcao == ContaDirecao.PAGAR &&
            it.status == ContaStatus.PAGO &&
            paidInMonth(it, previousMonth)
    }
    val paidTotal = paidThisMonth.sumOf { parseCents(it.valor) }
    val pendingTotal = pendingThisMonth.sumOf { parseCents(it.valor) }
    val receivableTotal = receivableThisMonth.sumOf { parseCents(it.valor) }
    val receivedTotal = receivedThisMonth.sumOf { parseCents(it.valor) }
    val previousPaidTotal = paidPreviousMonth.sumOf { parseCents(it.valor) }
    val byCategory = paidThisMonth.groupBy { it.categoria.ifBlank { "Outros" } }
        .mapValues { entry -> entry.value.sumOf { parseCents(it.valor) } }
        .toList()
        .sortedByDescending { it.second }
    val byPaymentMethod = paidThisMonth.groupBy { it.formaPagamento?.takeIf(String::isNotBlank) ?: "Não informado" }
        .mapValues { entry -> entry.value.sumOf { parseCents(it.valor) } }
        .toList()
        .sortedByDescending { it.second }

    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
          state = listState,
          modifier = Modifier
              .fillMaxSize()
              .background(Color(0xFFF5F7FA))
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color(0xFF102A43))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Relatorio mensal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Text("Pagamentos por categoria no mes selecionado", color = Color(0xFF6B7280))
                }
                IconButton(
                    onClick = { ReportPdfHelper.shareMonthlyReport(context, contas, selectedMonth) },
                    modifier = Modifier.screenHelpTarget(helpTargets, "report_export")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Exportar PDF", tint = Color(0xFF102A43))
                }
            }
        }

        item {
            MonthSelector(
                label = monthLabel,
                onPrevious = { selectedMonth = selectedMonth.copyWithMonthOffset(-1) },
                onNext = { selectedMonth = selectedMonth.copyWithMonthOffset(1) },
                modifier = Modifier.screenHelpTarget(helpTargets, "report_month")
            )
        }

        item {
            SummaryCard("Total pago", formatMoney(paidTotal), Color(0xFF0B6B2B), Modifier.screenHelpTarget(helpTargets, "report_paid"))
        }

        item {
            SummaryCard("Total pendente", formatMoney(pendingTotal), Color(0xFFB71C1C), Modifier.screenHelpTarget(helpTargets, "report_pending"))
        }

        item {
            SummaryCard("A receber", formatMoney(receivableTotal), Color(0xFF1565C0), Modifier.screenHelpTarget(helpTargets, "report_receivable"))
        }

        item {
            SummaryCard("Saldo projetado", formatMoney(receivableTotal - pendingTotal), Color(0xFF5B3FA3))
        }

        item {
            ComparisonCard(
                currentPaid = paidTotal,
                previousPaid = previousPaidTotal,
                received = receivedTotal,
                modifier = Modifier.screenHelpTarget(helpTargets, "report_comparison")
            )
        }

        item {
            Text("Pagamentos por categoria", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
        }

        if (byCategory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.screenHelpTarget(helpTargets, "category_breakdown"),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Nenhum pagamento registrado em $monthLabel.",
                        modifier = Modifier.padding(14.dp),
                        color = Color(0xFF6B7280)
                    )
                }
            }
        } else {
            itemsIndexed(byCategory) { index, (category, cents) ->
                Card(
                    modifier = if (index == 0) Modifier.screenHelpTarget(helpTargets, "category_breakdown") else Modifier,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(category, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text(formatMoney(cents), fontWeight = FontWeight.Bold, color = Color(0xFF0B6B2B))
                    }
                }
            }
        }

        item {
            Text("Formas de pagamento", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
        }
        if (byPaymentMethod.isEmpty()) {
            item { Text("Nenhuma forma de pagamento registrada neste mês.", color = Color(0xFF6B7280)) }
        } else {
            itemsIndexed(byPaymentMethod) { _, (method, cents) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(method, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Text(formatMoney(cents), fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                }
            }
        }
      }
      ScreenHelp(
          screenKey = "relatorio",
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp),
          steps = listOf(
              ScreenHelpStep("Exportar relatório", "Gere e compartilhe um PDF com os totais do mês selecionado.", HelpFocus.TOP, "report_export"),
              ScreenHelpStep("Escolha o mês", "Use as setas para consultar os pagamentos e pendências de outro mês.", HelpFocus.TOP, "report_month"),
              ScreenHelpStep("Total pago", "Mostra o valor das contas efetivamente pagas durante o mês.", HelpFocus.TOP, "report_paid"),
              ScreenHelpStep("Total pendente", "Mostra o valor das contas abertas com vencimento no mês.", HelpFocus.CENTER, "report_pending"),
              ScreenHelpStep("Valores a receber", "Mostra quanto ainda está previsto para entrar no mês selecionado.", HelpFocus.CENTER, "report_receivable"),
              ScreenHelpStep("Comparação mensal", "Compare os pagamentos do mês atual com o mês anterior e acompanhe a variação.", HelpFocus.CENTER, "report_comparison"),
              ScreenHelpStep("Pagamentos por categoria", "Confira como o total pago está distribuído entre as categorias cadastradas.", HelpFocus.BOTTOM, "category_breakdown")
          ),
          targets = helpTargets,
          onStepChanged = { index ->
              scope.launch {
                  listState.animateScrollToItem(listOf(0, 1, 2, 3, 4, 6, 8)[index])
              }
          }
      )
    }
}

@Composable
private fun MonthSelector(label: String, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Mes anterior")
            }
            Text(
                text = label,
                modifier = Modifier.padding(top = 12.dp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Proximo mes")
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.82f))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun ComparisonCard(
    currentPaid: Long,
    previousPaid: Long,
    received: Long,
    modifier: Modifier = Modifier
) {
    val variation = when {
        previousPaid == 0L && currentPaid == 0L -> "Sem alteração"
        previousPaid == 0L -> "Novo movimento neste mês"
        else -> {
            val percent = paymentVariationPercent(currentPaid, previousPaid) ?: 0.0
            val signal = if (percent > 0) "+" else ""
            "$signal${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", percent)}% em relação ao mês anterior"
        }
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFD5DCE5)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comparação mensal", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            Text("Pago neste mês: ${formatMoney(currentPaid)}", color = Color(0xFF334155))
            Text("Pago no mês anterior: ${formatMoney(previousPaid)}", color = Color(0xFF334155))
            Text("Recebido neste mês: ${formatMoney(received)}", color = Color(0xFF334155))
            Text(variation, fontWeight = FontWeight.SemiBold, color = Color(0xFF1565C0))
        }
    }
}

internal fun paymentVariationPercent(currentPaid: Long, previousPaid: Long): Double? {
    if (previousPaid == 0L) return null
    return (currentPaid - previousPaid) * 100.0 / previousPaid
}

private fun parseCents(value: String): Long = value.filter(Char::isDigit).toLongOrNull() ?: 0L

private fun startOfCurrentMonth(): Calendar {
    return Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun Calendar.copyWithMonthOffset(offset: Int): Calendar {
    return (clone() as Calendar).apply {
        add(Calendar.MONTH, offset)
        set(Calendar.DAY_OF_MONTH, 1)
    }
}

private fun paidInMonth(conta: ContaSalva, month: Calendar): Boolean {
    val paidAt = conta.pagoEm
    val paidDate = if (paidAt != null) {
        Calendar.getInstance().apply { timeInMillis = paidAt }
    } else {
        parseContaDate(conta.vencimento)
    } ?: return false
    return sameMonth(paidDate, month)
}

private fun dueInMonth(conta: ContaSalva, month: Calendar): Boolean {
    val dueDate = parseContaDate(conta.vencimento) ?: return false
    return sameMonth(dueDate, month)
}

private fun sameMonth(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
}

private fun formatMoney(cents: Long): String {
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal(cents).movePointLeft(2))
}
