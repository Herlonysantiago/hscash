package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RelatorioScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var contas by remember { mutableStateOf(ContaStorage.listarContas(context)) }
    var selectedMonth by remember { mutableStateOf(startOfCurrentMonth()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) contas = ContaStorage.listarContas(context)
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
    val byCategory = paidThisMonth.groupBy { it.categoria.ifBlank { "Outros" } }
        .mapValues { entry -> entry.value.sumOf { parseCents(it.valor) } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Relatorio mensal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Pagamentos por categoria no mes selecionado", color = Color(0xFF6B7280))
                }
                IconButton(onClick = { ReportPdfHelper.shareMonthlyReport(context, contas, selectedMonth) }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Exportar PDF")
                }
            }
        }

        item {
            MonthSelector(
                label = monthLabel,
                onPrevious = { selectedMonth = selectedMonth.copyWithMonthOffset(-1) },
                onNext = { selectedMonth = selectedMonth.copyWithMonthOffset(1) }
            )
        }

        item {
            SummaryCard("Total pago", formatMoney(paidThisMonth.sumOf { parseCents(it.valor) }), Color(0xFF0B6B2B))
        }

        item {
            SummaryCard("Total pendente", formatMoney(pendingThisMonth.sumOf { parseCents(it.valor) }), Color(0xFFB71C1C))
        }

        if (byCategory.isEmpty()) {
            item {
                Card(
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
            items(byCategory) { (category, cents) ->
                Card(
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
    }
}

@Composable
private fun MonthSelector(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Card(
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
private fun SummaryCard(label: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.82f))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        }
    }
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
