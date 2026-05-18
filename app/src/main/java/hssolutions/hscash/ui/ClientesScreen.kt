package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ClientesScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var clientes by remember { mutableStateOf(ClienteStorage.listarClientes(context)) }
    var contas by remember { mutableStateOf(ContaStorage.listarContas(context)) }
    var editingCliente by remember { mutableStateOf<ClienteSalvo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    fun reload() {
        clientes = ClienteStorage.listarClientes(context)
        contas = ContaStorage.listarContas(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clientes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Cadastro e cobranca por WhatsApp", color = Color(0xFF6B7280))
                }
                IconButton(onClick = {
                    editingCliente = null
                    showDialog = true
                }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Adicionar cliente")
                }
            }
        }

        if (clientes.isEmpty()) {
            item {
                Text("Nenhum cliente cadastrado.", color = Color(0xFF6B7280), modifier = Modifier.padding(8.dp))
            }
        } else {
            items(clientes, key = { it.id }) { cliente ->
                val pendencias = contas.filter {
                    it.direcao == ContaDirecao.RECEBER &&
                        it.status != ContaStatus.PAGO &&
                        it.fornecedor.equals(cliente.nome, ignoreCase = true)
                }
                ClienteCard(
                    cliente = cliente,
                    pendencias = pendencias,
                    onEdit = {
                        editingCliente = cliente
                        showDialog = true
                    },
                    onCharge = { WhatsAppHelper.sendClientCharge(context, cliente, pendencias) },
                    onDelete = {
                        ClienteStorage.removerCliente(context, cliente.id)
                        reload()
                    }
                )
            }
        }
    }

    if (showDialog) {
        ClienteDialog(
            cliente = editingCliente,
            onDismiss = { showDialog = false },
            onSave = { id, nome, whatsapp, observacao ->
                ClienteStorage.salvarCliente(context, nome, whatsapp, observacao, id)
                showDialog = false
                reload()
            }
        )
    }
}

@Composable
private fun ClienteCard(
    cliente: ClienteSalvo,
    pendencias: List<ContaSalva>,
    onEdit: () -> Unit,
    onCharge: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cliente.nome, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Text(cliente.whatsapp.ifBlank { "WhatsApp nao informado" }, color = Color(0xFF6B7280))
                    if (cliente.observacao.isNotBlank()) {
                        Text(cliente.observacao, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("${pendencias.size} aberto(s)", color = Color(0xFF0B6B2B), fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onCharge, enabled = pendencias.isNotEmpty()) { Text("Cobrar") }
                TextButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}

@Composable
private fun ClienteDialog(
    cliente: ClienteSalvo?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var nome by remember(cliente?.id) { mutableStateOf(cliente?.nome.orEmpty()) }
    var whatsapp by remember(cliente?.id) { mutableStateOf(cliente?.whatsapp.orEmpty()) }
    var observacao by remember(cliente?.id) { mutableStateOf(cliente?.observacao.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (cliente == null) "Novo cliente" else "Editar cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = {
                        nome = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("WhatsApp com DDD") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = observacao,
                    onValueChange = { observacao = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Observacao") }
                )
                error?.let { Text(it, color = Color(0xFFD32F2F)) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nome.isBlank()) {
                        error = "Informe o nome do cliente."
                    } else {
                        onSave(cliente?.id ?: System.currentTimeMillis().toString(), nome, whatsapp, observacao)
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
