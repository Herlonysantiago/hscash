package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.util.Calendar

private enum class ContaFiltro(val label: String) {
    TODAS("Todas"),
    HOJE("Hoje"),
    SEMANA("Semana"),
    MES("Mes"),
    VENCIDAS("Vencidas"),
    PAGAS("Pagas")
}

@Composable
fun EstoqueScreen(onContaClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var busca by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf(ContaFiltro.TODAS) }
    var contas by remember { mutableStateOf(ContaStorage.listarContas(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                contas = ContaStorage.listarContas(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contasFiltradas = contas
        .filter { it.matchesSearch(busca) }
        .filter { it.matchesFilter(filtro) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Minhas Contas",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${contas.size} cadastradas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )
            }
            IconButton(onClick = { ExportHelper.shareCsv(context, contas) }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Exportar CSV")
            }
        }

        OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            label = { Text("Buscar conta, banco ou categoria...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = MaterialTheme.shapes.medium
        )

        LazyRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ContaFiltro.entries.toList()) { item ->
                FilterChip(
                    selected = filtro == item,
                    onClick = { filtro = item },
                    label = { Text(item.label) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (contasFiltradas.isEmpty()) {
                item {
                    Text(
                        text = if (contas.isEmpty()) {
                            "Nenhuma conta salva ainda."
                        } else {
                            "Nenhuma conta encontrada."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF4B5563),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            items(contasFiltradas, key = { it.id }) { conta ->
                ContaCard(
                    conta = conta,
                    onClick = { onContaClick(conta.id) }
                )
            }
        }
    }
}

@Composable
private fun ContaCard(conta: ContaSalva, onClick: () -> Unit) {
    val statusColor = Color(statusColorHex(conta))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conta.fornecedor.ifBlank { conta.banco },
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    "${conta.categoria} - ${conta.banco}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
                Text(
                    "Vencimento: ${conta.vencimento}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = conta.valor,
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusLabel(conta),
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun ContaSalva.matchesSearch(search: String): Boolean {
    val term = search.trim()
    if (term.isBlank()) return true
    return fornecedor.contains(term, ignoreCase = true) ||
        banco.contains(term, ignoreCase = true) ||
        valor.contains(term, ignoreCase = true) ||
        vencimento.contains(term, ignoreCase = true) ||
        categoria.contains(term, ignoreCase = true) ||
        tipo.contains(term, ignoreCase = true)
}

private fun ContaSalva.matchesFilter(filter: ContaFiltro): Boolean {
    val dueDate = parseContaDate(vencimento)
    val today = startOfToday()
    return when (filter) {
        ContaFiltro.TODAS -> true
        ContaFiltro.PAGAS -> status == ContaStatus.PAGO
        ContaFiltro.VENCIDAS -> isContaVencida(this)
        ContaFiltro.HOJE -> dueDate != null && sameDay(dueDate, today)
        ContaFiltro.SEMANA -> dueDate != null && isInCurrentWeek(dueDate, today)
        ContaFiltro.MES -> dueDate != null &&
            dueDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            dueDate.get(Calendar.MONTH) == today.get(Calendar.MONTH)
    }
}

private fun sameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun isInCurrentWeek(date: Calendar, today: Calendar): Boolean {
    val end = today.clone() as Calendar
    end.add(Calendar.DAY_OF_YEAR, 7)
    return !date.before(today) && !date.after(end)
}
