package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
fun ContasScreen(
    onContaClick: (String) -> Unit = {},
    onAddConta: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var busca by remember { mutableStateOf("") }
    var filtro by remember { mutableStateOf(ContaFiltro.TODAS) }
    var direcao by remember { mutableStateOf(ContaDirecao.PAGAR) }
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

    val contasDaDirecao = contas.filter { it.direcao == direcao }
    val contasFiltradas = contasDaDirecao
        .filter { it.matchesSearch(busca) }
        .filter { it.matchesFilter(filtro) }
        .sortedWith(compareBy<ContaSalva> { parseContaDate(it.vencimento)?.timeInMillis ?: Long.MAX_VALUE }.thenByDescending { it.criadaEm })
    val isReceber = direcao == ContaDirecao.RECEBER

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F6FA))
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF102A43)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isReceber) "A Receber" else "A Pagar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${contasDaDirecao.size} cadastradas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
                Row {
                    IconButton(onClick = { onAddConta(direcao) }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = if (isReceber) "Adicionar recebimento" else "Adicionar conta",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { ExportHelper.shareCsv(context, contasDaDirecao) }) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "Exportar CSV",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                ContaDirecao.PAGAR to "A pagar",
                ContaDirecao.RECEBER to "A receber"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = direcao == value,
                    onClick = {
                        direcao = value
                        filtro = ContaFiltro.TODAS
                    },
                    label = { Text(label, fontWeight = if (direcao == value) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White,
                        labelColor = Color(0xFF111827),
                        selectedContainerColor = if (value == ContaDirecao.RECEBER) Color(0xFF0B6B2B) else Color(0xFF1565C0),
                        selectedLabelColor = Color.White
                    )
                )
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
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFF111827),
                unfocusedTextColor = Color(0xFF111827),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF1565C0),
                unfocusedBorderColor = Color(0xFFCBD5E1),
                focusedLabelColor = Color(0xFF1565C0),
                unfocusedLabelColor = Color(0xFF4B5563),
                focusedLeadingIconColor = Color(0xFF1565C0),
                unfocusedLeadingIconColor = Color(0xFF4B5563)
            )
        )

        LazyRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ContaFiltro.entries.toList()) { item ->
                FilterChip(
                    selected = filtro == item,
                    onClick = { filtro = item },
                    label = {
                        Text(
                            text = item.label,
                            fontWeight = if (filtro == item) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White,
                        labelColor = Color(0xFF111827),
                        selectedContainerColor = Color(0xFF1565C0),
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filtro == item,
                        borderColor = Color(0xFFCBD5E1),
                        selectedBorderColor = Color(0xFF1565C0)
                    )
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
                        } else if (contasDaDirecao.isEmpty()) {
                            if (isReceber) "Nenhum recebimento cadastrado." else "Nenhuma conta a pagar cadastrada."
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
        border = BorderStroke(1.dp, Color(0xFFD7DEE8)),
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
                    "${if (conta.direcao == ContaDirecao.RECEBER) "Receber" else "Pagar"} - ${conta.categoria} - ${conta.banco}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
                Text(
                    "${if (conta.direcao == ContaDirecao.RECEBER) "Previsao" else "Vencimento"}: ${conta.vencimento}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = conta.valor,
                    color = if (conta.direcao == ContaDirecao.RECEBER) Color(0xFF0B6B2B) else Color(0xFFB71C1C),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                StatusBadge(text = statusLabel(conta), color = statusColor)
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
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
