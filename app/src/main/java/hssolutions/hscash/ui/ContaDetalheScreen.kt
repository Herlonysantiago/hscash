package hssolutions.hscash.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContaDetalheScreen(
    contaId: String,
    onBack: () -> Unit = {},
    onDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var conta by remember(contaId) { mutableStateOf<ContaSalva?>(null) }
    var carregando by remember(contaId) { mutableStateOf(true) }
    var processando by remember { mutableStateOf(false) }
    var fornecedor by remember(conta?.id) { mutableStateOf(conta?.fornecedor.orEmpty()) }
    var categoria by remember(conta?.id) { mutableStateOf(conta?.categoria.orEmpty()) }
    var valor by remember(conta?.id) { mutableStateOf(conta?.valor.orEmpty()) }
    var vencimento by remember(conta?.id) { mutableStateOf(conta?.vencimento.orEmpty()) }
    var clienteWhatsapp by remember(conta?.id) { mutableStateOf(conta?.clienteWhatsapp.orEmpty()) }
    var recorrente by remember(conta?.id) { mutableStateOf(conta?.recorrente ?: false) }
    var recorrencia by remember(conta?.id) { mutableStateOf(conta?.recorrencia ?: "Mensal") }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    var confirmarPagamento by remember { mutableStateOf(false) }
    var dataPagamento by remember { mutableStateOf(formatPaymentDate(System.currentTimeMillis())) }
    var formaPagamento by remember { mutableStateOf("PIX") }
    var observacaoPagamento by remember { mutableStateOf("") }
    var erroPagamento by remember { mutableStateOf<String?>(null) }
    var confirmarExclusaoComprovante by remember { mutableStateOf(false) }
    val isReceber = conta?.direcao == ContaDirecao.RECEBER

    LaunchedEffect(contaId) {
        conta = ContaStorage.buscarContaAsync(context, contaId)
        carregando = false
    }

    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val atual = conta ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                processando = true
                try {
                    val path = withContext(Dispatchers.IO) { ReceiptHelper.saveReceipt(context, atual.id, uri) }
                    if (path != null) {
                        if (atual.comprovantePath != path) {
                            withContext(Dispatchers.IO) { ReceiptHelper.deleteStoredFile(atual.comprovantePath) }
                        }
                        val atualizada = atual.copy(comprovantePath = path)
                        ContaStorage.atualizarContaAsync(context, atualizada)
                        conta = atualizada
                        Toast.makeText(context, "Comprovante anexado", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    processando = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Column {
                Text(
                    text = if (isReceber) "Detalhes do Recebimento" else "Detalhes da Conta",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                conta?.let {
                    Text(
                        text = statusLabel(it),
                        color = Color(statusColorHex(it)),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (carregando) {
            CircularProgressIndicator()
            return@Column
        }

        val contaAtual = conta
        if (contaAtual == null) {
            Text("Conta nao encontrada.", color = Color(0xFFD32F2F))
            return@Column
        }

        val codigoPagamento = contaAtual.codigo.takeIf { contaAtual.direcao == ContaDirecao.PAGAR && !it.startsWith("manual-") }
        val boletoPagamento = codigoPagamento?.let { parseBoleto(it) }
        if (boletoPagamento != null) {
            PaymentCopyCard(
                linhaDigitavel = boletoPagamento.linhaDigitavel,
                codigoBarras = boletoPagamento.codigoBarras
            )
        } else if (codigoPagamento != null) {
            Text(
                text = "Codigo salvo invalido (${codigoPagamento.filter(Char::isDigit).length} digitos). Leia ou digite o boleto novamente.",
                color = Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold
            )
        }

        contaAtual.boletoPdfPath?.let { path ->
            OutlinedButton(
                onClick = { ReceiptHelper.openReceipt(context, path) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Abrir PDF original do boleto")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E5EC))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fornecedor,
                    onValueChange = { fornecedor = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (contaAtual.direcao == ContaDirecao.RECEBER) "Cliente ou origem" else "Fornecedor") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = categoria,
                    onValueChange = { categoria = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Categoria") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = vencimento,
                    onValueChange = {
                        vencimento = it
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (contaAtual.direcao == ContaDirecao.RECEBER) "Data prevista (dd/mm/aaaa)" else "Vencimento (dd/mm/aaaa)") },
                    singleLine = true,
                    isError = erro != null,
                    colors = hsCashOutlinedTextFieldColors()
                )
                if (contaAtual.direcao == ContaDirecao.RECEBER) {
                    OutlinedTextField(
                        value = clienteWhatsapp,
                        onValueChange = { clienteWhatsapp = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("WhatsApp do cliente") },
                        placeholder = { Text("Ex: 11999999999") },
                        singleLine = true,
                        colors = hsCashOutlinedTextFieldColors()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Conta recorrente", fontWeight = FontWeight.Bold)
                    Switch(checked = recorrente, onCheckedChange = { recorrente = it })
                }
                if (recorrente) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Semanal", "Mensal", "Anual").forEach { option ->
                            TextButton(onClick = { recorrencia = option }) {
                                Text(
                                    text = option,
                                    fontWeight = if (recorrencia == option) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                erro?.let {
                    Text(
                        text = it,
                        color = Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (contaAtual.status == ContaStatus.PAGO) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EE)),
                border = BorderStroke(1.dp, Color(0xFF9AD0A6))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (contaAtual.direcao == ContaDirecao.RECEBER) "Recebimento confirmado" else "Pagamento confirmado",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534)
                    )
                    contaAtual.pagoEm?.let { Text("Data: ${formatPaymentDate(it)}", color = Color(0xFF334155)) }
                    contaAtual.formaPagamento?.let { Text("Forma: $it", color = Color(0xFF334155)) }
                    contaAtual.observacaoPagamento?.let { Text("Observação: $it", color = Color(0xFF334155)) }
                    Text(
                        if (contaAtual.comprovantePath != null) "Comprovante anexado" else "Comprovante não anexado (opcional)",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Button(
            onClick = {
                if (!isValidContaDate(vencimento)) {
                    erro = "Digite uma data valida no formato dd/mm/aaaa."
                    return@Button
                }

                val atualizada = contaAtual.copy(
                    fornecedor = fornecedor.trim(),
                    categoria = categoria.ifBlank { "Outros" }.trim(),
                    valor = valor.trim(),
                    vencimento = vencimento.trim(),
                    clienteWhatsapp = if (contaAtual.direcao == ContaDirecao.RECEBER) clienteWhatsapp.trim().takeIf { it.isNotBlank() } else null,
                    recorrente = recorrente,
                    recorrencia = recorrencia
                )
                scope.launch {
                    processando = true
                    try {
                        ContaStorage.atualizarContaAsync(context, atualizada)
                        conta = atualizada
                        Toast.makeText(context, "Conta atualizada", Toast.LENGTH_SHORT).show()
                    } finally {
                        processando = false
                    }
                }
            },
            enabled = !processando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Salvar alteracoes")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (contaAtual.status == ContaStatus.PAGO) {
                        scope.launch {
                            processando = true
                            try {
                                ContaStorage.marcarComoPendenteAsync(context, contaAtual.id)
                                conta = ContaStorage.buscarContaAsync(context, contaAtual.id)
                                Toast.makeText(context, if (contaAtual.direcao == ContaDirecao.RECEBER) "Recebimento voltou para aberto" else "Conta voltou para pendente", Toast.LENGTH_SHORT).show()
                            } finally {
                                processando = false
                            }
                        }
                    } else {
                        dataPagamento = formatPaymentDate(System.currentTimeMillis())
                        formaPagamento = contaAtual.formaPagamento ?: "PIX"
                        observacaoPagamento = contaAtual.observacaoPagamento.orEmpty()
                        erroPagamento = null
                        confirmarPagamento = true
                    }
                },
                enabled = !processando,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (contaAtual.status == ContaStatus.PAGO) {
                        if (contaAtual.direcao == ContaDirecao.RECEBER) "Marcar a receber" else "Marcar pendente"
                    } else {
                        if (contaAtual.direcao == ContaDirecao.RECEBER) "Marcar recebido" else "Marcar pago"
                    }
                )
            }
            OutlinedButton(
                onClick = { confirmarExclusao = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Excluir")
            }
        }

        if (contaAtual.comprovantePath != null) {
            TextButton(
                onClick = { confirmarExclusaoComprovante = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Excluir comprovante", color = Color(0xFFD32F2F))
            }
        }

        if (confirmarPagamento) {
            AlertDialog(
                onDismissRequest = { confirmarPagamento = false },
                title = { Text(if (contaAtual.direcao == ContaDirecao.RECEBER) "Confirmar recebimento" else "Confirmar pagamento") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Confirme os dados de ${if (contaAtual.direcao == ContaDirecao.RECEBER) "recebimento" else "pagamento"} de ${contaAtual.fornecedor.ifBlank { "esta conta" }} no valor de ${contaAtual.valor}.")
                        OutlinedTextField(
                            value = dataPagamento,
                            onValueChange = { dataPagamento = it; erroPagamento = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Data (dd/mm/aaaa)") },
                            singleLine = true,
                            isError = erroPagamento != null,
                            colors = hsCashOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = formaPagamento,
                            onValueChange = { formaPagamento = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (contaAtual.direcao == ContaDirecao.RECEBER) "Forma de recebimento" else "Forma de pagamento") },
                            placeholder = { Text("Ex: PIX, dinheiro ou cartão") },
                            singleLine = true,
                            colors = hsCashOutlinedTextFieldColors()
                        )
                        OutlinedTextField(
                            value = observacaoPagamento,
                            onValueChange = { observacaoPagamento = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Observação (opcional)") },
                            minLines = 2,
                            maxLines = 3,
                            colors = hsCashOutlinedTextFieldColors()
                        )
                        erroPagamento?.let { Text(it, color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall) }
                        Text("O comprovante é opcional e pode ser anexado após a confirmação.", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !processando,
                        onClick = {
                            val pagoEm = parseContaDate(dataPagamento)?.timeInMillis
                            if (pagoEm == null) {
                                erroPagamento = "Informe uma data válida no formato dd/mm/aaaa."
                                return@Button
                            }
                            scope.launch {
                                processando = true
                                try {
                                    ContaStorage.marcarComoPagoAsync(
                                        context = context,
                                        id = contaAtual.id,
                                        pagoEm = pagoEm,
                                        formaPagamento = formaPagamento,
                                        observacaoPagamento = observacaoPagamento
                                    )
                                    conta = ContaStorage.buscarContaAsync(context, contaAtual.id)
                                    confirmarPagamento = false
                                    Toast.makeText(
                                        context,
                                        if (contaAtual.direcao == ContaDirecao.RECEBER) "Recebimento confirmado" else "Pagamento confirmado. Anexe o comprovante se desejar.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    processando = false
                                }
                            }
                        }
                    ) { Text("Confirmar") }
                },
                dismissButton = { TextButton(onClick = { confirmarPagamento = false }) { Text("Cancelar") } }
            )
        }

        if (contaAtual.direcao == ContaDirecao.RECEBER) {
            Button(
                onClick = {
                    val atualizada = contaAtual.copy(
                        fornecedor = fornecedor.trim(),
                        categoria = categoria.ifBlank { "Outros" }.trim(),
                        valor = valor.trim(),
                        vencimento = vencimento.trim(),
                        clienteWhatsapp = clienteWhatsapp.trim().takeIf { it.isNotBlank() },
                        recorrente = recorrente,
                        recorrencia = recorrencia
                    )
                    scope.launch {
                        processando = true
                        try {
                            ContaStorage.atualizarContaAsync(context, atualizada)
                            conta = atualizada
                            WhatsAppHelper.sendCharge(context, atualizada)
                        } finally {
                            processando = false
                        }
                    }
                },
                enabled = !processando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar cobranca no WhatsApp")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { receiptLauncher.launch("*/*") },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text(
                    text = "Anexar comprovante",
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = { contaAtual.comprovantePath?.let { ReceiptHelper.openReceipt(context, it) } },
                enabled = contaAtual.comprovantePath != null,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Text(
                    text = "Abrir comprovante",
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 12.sp
                )
            }
        }

    }

    if (confirmarExclusao) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Mover para lixeira") },
            text = { Text("A conta sera removida da lista principal, mas podera ser restaurada pela lixeira.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            processando = true
                            try {
                                ContaStorage.removerContaAsync(context, contaId)
                                Toast.makeText(context, "Conta movida para a lixeira", Toast.LENGTH_SHORT).show()
                                confirmarExclusao = false
                                onDeleted()
                            } finally {
                                processando = false
                            }
                        }
                    }
                ) {
                    Text("Mover")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarExclusao = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (confirmarExclusaoComprovante) {
        AlertDialog(
            onDismissRequest = { confirmarExclusaoComprovante = false },
            title = { Text("Excluir comprovante?") },
            text = { Text("O arquivo do comprovante sera removido. A conta e o PDF original do boleto serao mantidos.") },
            confirmButton = {
                Button(
                    enabled = !processando,
                    onClick = {
                        val atual = conta
                        if (atual != null) {
                            scope.launch {
                                processando = true
                                try {
                                    val removido = withContext(Dispatchers.IO) {
                                        ReceiptHelper.deleteStoredFile(atual.comprovantePath)
                                    }
                                    if (removido) {
                                        val atualizada = atual.copy(comprovantePath = null)
                                        ContaStorage.atualizarContaAsync(context, atualizada)
                                        conta = atualizada
                                        Toast.makeText(context, "Comprovante excluido", Toast.LENGTH_SHORT).show()
                                    }
                                    confirmarExclusaoComprovante = false
                                } finally {
                                    processando = false
                                }
                            }
                        }
                    }
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusaoComprovante = false }) { Text("Cancelar") } }
        )
    }
}

private fun formatPaymentDate(timestamp: Long): String {
    return SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(timestamp))
}
