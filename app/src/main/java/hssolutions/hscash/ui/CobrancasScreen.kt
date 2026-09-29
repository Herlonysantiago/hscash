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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun CobrancasScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contas by remember { mutableStateOf(emptyList<ContaSalva>()) }
    var pixCharge by remember { mutableStateOf<Pair<ContaSalva, String>?>(null) }
    fun carregarContas() {
        scope.launch { contas = ContaStorage.listarContasAsync(context) }
    }
    LaunchedEffect(Unit) { carregarContas() }
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
            .background(MaterialTheme.colorScheme.background)
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
                    Text("${urgentes.size} vencidas ou para hoje", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (cobrancas.isEmpty()) {
            item {
                Text("Nenhuma conta a receber em aberto.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        } else {
            items(cobrancas, key = { it.id }) { conta ->
                CobrancaCard(
                    conta = conta,
                    onCharge = { WhatsAppHelper.sendCharge(context, conta) },
                    onGeneratePix = {
                        chargePixPayload(conta)?.let { pixCharge = conta to it }
                    },
                    onReceived = {
                        scope.launch {
                            ContaStorage.marcarComoPagoAsync(context, conta.id)
                            contas = ContaStorage.listarContasAsync(context)
                        }
                    }
                )
            }
        }
    }

    pixCharge?.let { (conta, payload) ->
        AlertDialog(
            onDismissRequest = { pixCharge = null },
            title = { Text("Cobrança Pix") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${conta.fornecedor.ifBlank { "Cliente" }} • ${conta.valor}")
                    PixPaymentCard(payload = payload, showAction = false)
                }
            },
            confirmButton = { TextButton(onClick = { pixCharge = null }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun CobrancaCard(conta: ContaSalva, onCharge: () -> Unit, onGeneratePix: () -> Unit, onReceived: () -> Unit) {
    val dueDate = parseContaDate(conta.vencimento)
    val today = startOfToday()
    val color = when {
        dueDate == null -> MaterialTheme.colorScheme.onSurfaceVariant
        dueDate.before(today) -> Color(0xFFD32F2F)
        sameDay(dueDate, today) -> Color(0xFFF57C00)
        else -> Color(0xFF0B6B2B)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(conta.fornecedor.ifBlank { "Cliente" }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Previsao: ${conta.vencimento}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(conta.clienteWhatsapp.orEmpty().ifBlank { "WhatsApp nao informado" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(conta.valor, color = color, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCharge) { Text("Cobrar WhatsApp") }
                if (!conta.pixChave.isNullOrBlank() || !conta.pixCopiaCola.isNullOrBlank()) {
                    TextButton(onClick = onGeneratePix) { Text("Gerar Pix") }
                }
                TextButton(onClick = onReceived) { Text("Marcar recebido") }
            }
        }
    }
}

private fun chargePixPayload(conta: ContaSalva): String? {
    conta.pixCopiaCola?.takeIf { PixUtils.parse(it).valid }?.let { return it }
    val key = conta.pixChave?.takeIf(String::isNotBlank) ?: return null
    return runCatching {
        PixUtils.generateStatic(
            key = key,
            recipient = conta.pixRecebedor?.takeIf(String::isNotBlank) ?: conta.fornecedor,
            city = "SAO PAULO",
            amountInCents = PixUtils.moneyToCents(conta.valor),
            txId = conta.pixTxId ?: "HS${conta.id.takeLast(20)}"
        )
    }.getOrNull()
}

private fun sameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}
