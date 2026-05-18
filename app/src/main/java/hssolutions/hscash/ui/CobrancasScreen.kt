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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun CobrancasScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var contas by remember { mutableStateOf(ContaStorage.listarContas(context)) }
    val hoje = startOfToday()
    val cobrancas = remember(contas) {
        contas
            .filter { it.direcao == ContaDirecao.RECEBER && it.status != ContaStatus.PAGO }
            .sortedBy { parseContaDate(it.vencimento)?.timeInMillis ?: Long.MAX_VALUE }
    }
    val urgentes = cobrancas.filter {
        val data = parseContaDate(it.vencimento)
        data != null && !data.after(hoje)
    }

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
                Column {
                    Text("Cobrancas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${urgentes.size} vencidas ou para hoje", color = Color(0xFF6B7280))
                }
            }
        }

        if (cobrancas.isEmpty()) {
            item {
                Text("Nenhuma conta a receber em aberto.", color = Color(0xFF6B7280), modifier = Modifier.padding(8.dp))
            }
        } else {
            items(cobrancas, key = { it.id }) { conta ->
                CobrancaCard(
                    conta = conta,
                    onCharge = { WhatsAppHelper.sendCharge(context, conta) },
                    onReceived = {
                        ContaStorage.marcarComoPago(context, conta.id)
                        contas = ContaStorage.listarContas(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun CobrancaCard(conta: ContaSalva, onCharge: () -> Unit, onReceived: () -> Unit) {
    val dueDate = parseContaDate(conta.vencimento)
    val today = startOfToday()
    val color = when {
        dueDate == null -> Color(0xFF6B7280)
        dueDate.before(today) -> Color(0xFFD32F2F)
        sameDay(dueDate, today) -> Color(0xFFF57C00)
        else -> Color(0xFF0B6B2B)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(conta.fornecedor.ifBlank { "Cliente" }, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Text("Previsao: ${conta.vencimento}", color = Color(0xFF6B7280))
                    Text(conta.clienteWhatsapp.orEmpty().ifBlank { "WhatsApp nao informado" }, color = Color(0xFF6B7280))
                }
                Text(conta.valor, color = color, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCharge) { Text("Cobrar WhatsApp") }
                TextButton(onClick = onReceived) { Text("Marcar recebido") }
            }
        }
    }
}

private fun sameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}
