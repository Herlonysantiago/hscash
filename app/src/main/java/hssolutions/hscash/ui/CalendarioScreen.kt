package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarioScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var contas by remember { mutableStateOf(ContaStorage.listarContas(context)) }
    val today = remember { startOfToday() }
    var selectedMonth by remember { mutableStateOf(startOfToday()) }
    val monthLabel = remember(selectedMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("pt-BR")).format(selectedMonth.time)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) contas = ContaStorage.listarContas(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pending = contas.filter { it.status != ContaStatus.PAGO }
    val monthAccounts = pending.filter { conta ->
        parseContaDate(conta.vencimento)?.let { sameMonth(it, selectedMonth) } == true
    }
    val countsByDay = monthAccounts
        .mapNotNull { conta ->
            parseContaDate(conta.vencimento)?.get(Calendar.DAY_OF_MONTH)?.let { it to conta }
        }
        .groupBy({ it.first }, { it.second })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Calendario", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(monthLabel.replaceFirstChar { it.uppercase() }, color = Color(0xFF6B7280))
                }
                IconButton(
                    onClick = {
                        selectedMonth = selectedMonth.copyWithMonthOffset(-1)
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Mes anterior")
                }
                IconButton(
                    onClick = {
                        selectedMonth = selectedMonth.copyWithMonthOffset(1)
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Proximo mes")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
                shape = RoundedCornerShape(8.dp)
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val maxDay = selectedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
                    for (day in 1..maxDay) {
                        val count = countsByDay[day]?.size ?: 0
                        val isToday = sameMonth(today, selectedMonth) && day == today.get(Calendar.DAY_OF_MONTH)
                        DayCell(day, count, isToday)
                    }
                }
            }
        }

        item {
            Text(
                "Contas de ${monthLabel.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (monthAccounts.isEmpty()) {
            item {
                Text(
                    "Nenhuma conta neste mes.",
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        items(monthAccounts.sortedBy { parseContaDate(it.vencimento)?.timeInMillis ?: Long.MAX_VALUE }) { conta ->
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(conta.fornecedor.ifBlank { conta.banco }, fontWeight = FontWeight.Bold)
                        Text(
                            "${if (conta.direcao == ContaDirecao.RECEBER) "Receber" else "Pagar"} em ${conta.vencimento}",
                            color = Color(0xFF6B7280)
                        )
                    }
                    Text(
                        conta.valor,
                        color = if (conta.direcao == ContaDirecao.RECEBER) Color(0xFF0B6B2B) else Color(0xFFB71C1C),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun Calendar.copyWithMonthOffset(offset: Int): Calendar {
    return (clone() as Calendar).apply {
        add(Calendar.MONTH, offset)
        set(Calendar.DAY_OF_MONTH, 1)
    }
}

private fun sameMonth(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
}

@Composable
private fun DayCell(day: Int, count: Int, isToday: Boolean) {
    val bg = when {
        count > 0 -> Color(0xFF1565C0)
        isToday -> Color(0xFFE3F2FD)
        else -> Color(0xFFF8FAFC)
    }
    val fg = if (count > 0) Color.White else Color(0xFF111827)
    Column(
        modifier = Modifier
            .size(42.dp)
            .background(bg, CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(day.toString(), color = fg, fontWeight = FontWeight.Bold)
        if (count > 0) Text(count.toString(), color = fg, style = MaterialTheme.typography.labelSmall)
    }
}
