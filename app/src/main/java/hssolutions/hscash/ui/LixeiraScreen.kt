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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LixeiraScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val contaList = rememberContaListState(includeDeleted = true)
    val scope = rememberCoroutineScope()
    val contas = contaList.contas
    var excluirDefinitivo by remember { mutableStateOf<ContaSalva?>(null) }
    var processando by remember { mutableStateOf(false) }

    fun reload() {
        contaList.refresh()
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
                    Text("Lixeira", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Contas removidas podem ser restauradas", color = Color(0xFF6B7280))
                }
            }
        }

        if (contas.isEmpty()) {
            item {
                Text("Nenhuma conta na lixeira.", color = Color(0xFF6B7280), modifier = Modifier.padding(8.dp))
            }
        } else {
            items(contas, key = { it.id }) { conta ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conta.fornecedor.ifBlank { conta.banco }, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                                Text("${conta.valor} - ${conta.vencimento}", color = Color(0xFF6B7280))
                            }
                            Text(if (conta.direcao == ContaDirecao.RECEBER) "Receber" else "Pagar", color = Color(0xFF6B7280))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        processando = true
                                        try {
                                            ContaStorage.restaurarContaAsync(context, conta.id)
                                            reload()
                                        } finally {
                                            processando = false
                                        }
                                    }
                                },
                                enabled = !processando
                            ) { Text("Restaurar") }
                            TextButton(onClick = { excluirDefinitivo = conta }) { Text("Excluir definitivo") }
                        }
                    }
                }
            }
        }
    }

    excluirDefinitivo?.let { conta ->
        AlertDialog(
            onDismissRequest = { excluirDefinitivo = null },
            title = { Text("Excluir definitivamente") },
            text = { Text("Essa acao nao pode ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            processando = true
                            try {
                                ContaStorage.removerDefinitivamenteAsync(context, conta.id)
                                excluirDefinitivo = null
                                reload()
                            } finally {
                                processando = false
                            }
                        }
                    },
                    enabled = !processando
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { excluirDefinitivo = null }) { Text("Cancelar") }
            }
        )
    }
}
