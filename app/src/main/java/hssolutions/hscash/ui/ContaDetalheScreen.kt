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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp

@Composable
fun ContaDetalheScreen(
    contaId: String,
    onBack: () -> Unit = {},
    onDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    var conta by remember(contaId) { mutableStateOf(ContaStorage.buscarConta(context, contaId)) }
    var fornecedor by remember(conta?.id) { mutableStateOf(conta?.fornecedor.orEmpty()) }
    var categoria by remember(conta?.id) { mutableStateOf(conta?.categoria.orEmpty()) }
    var valor by remember(conta?.id) { mutableStateOf(conta?.valor.orEmpty()) }
    var vencimento by remember(conta?.id) { mutableStateOf(conta?.vencimento.orEmpty()) }
    var recorrente by remember(conta?.id) { mutableStateOf(conta?.recorrente ?: false) }
    var recorrencia by remember(conta?.id) { mutableStateOf(conta?.recorrencia ?: "Mensal") }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    val isReceber = conta?.direcao == ContaDirecao.RECEBER
    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val atual = conta ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val path = ReceiptHelper.saveReceipt(context, atual.id, uri)
            if (path != null) {
                val atualizada = atual.copy(comprovantePath = path)
                ContaStorage.atualizarConta(context, atualizada)
                conta = atualizada
                Toast.makeText(context, "Comprovante anexado", Toast.LENGTH_SHORT).show()
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
                    singleLine = true
                )
                OutlinedTextField(
                    value = categoria,
                    onValueChange = { categoria = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Categoria") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor") },
                    singleLine = true
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
                    isError = erro != null
                )
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
                    recorrente = recorrente,
                    recorrencia = recorrencia
                )
                ContaStorage.atualizarConta(context, atualizada)
                conta = atualizada
                Toast.makeText(context, "Conta atualizada", Toast.LENGTH_SHORT).show()
            },
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
                        ContaStorage.marcarComoPendente(context, contaAtual.id)
                        conta = ContaStorage.buscarConta(context, contaAtual.id)
                        Toast.makeText(context, if (contaAtual.direcao == ContaDirecao.RECEBER) "Recebimento voltou para aberto" else "Conta voltou para pendente", Toast.LENGTH_SHORT).show()
                    } else {
                        ContaStorage.marcarComoPago(context, contaAtual.id)
                        conta = ContaStorage.buscarConta(context, contaAtual.id)
                        Toast.makeText(context, if (contaAtual.direcao == ContaDirecao.RECEBER) "Recebimento marcado como recebido" else "Conta marcada como paga", Toast.LENGTH_SHORT).show()
                    }
                },
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
            title = { Text("Excluir conta") },
            text = { Text("Deseja remover esta conta da lista?") },
            confirmButton = {
                Button(
                    onClick = {
                        ContaStorage.removerConta(context, contaId)
                        Toast.makeText(context, "Conta excluida", Toast.LENGTH_SHORT).show()
                        confirmarExclusao = false
                        onDeleted()
                    }
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarExclusao = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
