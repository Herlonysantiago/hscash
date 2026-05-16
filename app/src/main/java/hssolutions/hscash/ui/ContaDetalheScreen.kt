package hssolutions.hscash.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
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
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Column {
                Text(
                    text = "Detalhes da Conta",
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
                    label = { Text("Fornecedor") },
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
                    label = { Text("Vencimento (dd/mm/aaaa)") },
                    singleLine = true,
                    isError = erro != null
                )
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
                    vencimento = vencimento.trim()
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
                        Toast.makeText(context, "Conta voltou para pendente", Toast.LENGTH_SHORT).show()
                    } else {
                        ContaStorage.marcarComoPago(context, contaAtual.id)
                        conta = ContaStorage.buscarConta(context, contaAtual.id)
                        Toast.makeText(context, "Conta marcada como paga", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (contaAtual.status == ContaStatus.PAGO) "Marcar pendente" else "Marcar pago")
            }
            OutlinedButton(
                onClick = { confirmarExclusao = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Excluir")
            }
        }

        Text("Codigo:", style = MaterialTheme.typography.labelSmall)
        SelectionContainer {
            Text(
                text = contaAtual.codigo,
                color = Color(0xFF0D47A1),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.weight(1f))
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
