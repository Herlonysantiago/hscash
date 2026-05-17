package hssolutions.hscash.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.core.content.ContextCompat
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ContaManualScreen(
    direcao: String = ContaDirecao.PAGAR,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val isReceber = direcao == ContaDirecao.RECEBER
    var fornecedor by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var vencimento by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var recorrente by remember { mutableStateOf(false) }
    var recorrencia by remember { mutableStateOf("Mensal") }
    var erro by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Column {
                Text(
                    text = if (isReceber) "Novo Recebimento" else "Nova Conta",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = if (isReceber) "Cadastro de valor a receber" else "Cadastro manual de conta a pagar",
                    color = Color(0xFF6B7280)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fornecedor,
                    onValueChange = {
                        fornecedor = it
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isReceber) "Cliente ou origem" else "Descricao ou fornecedor") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = valor,
                    onValueChange = {
                        valor = it
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor") },
                    placeholder = { Text("Ex: 125,90") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = vencimento,
                    onValueChange = {
                        vencimento = it
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isReceber) "Data prevista (dd/mm/aaaa)" else "Vencimento (dd/mm/aaaa)") },
                    placeholder = { Text("Ex: 25/05/2026") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = categoria,
                    onValueChange = { categoria = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Categoria") },
                    placeholder = { Text(if (isReceber) "Ex: Cliente, Servico, Venda" else "Ex: Aluguel, Luz, Internet") },
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conta recorrente", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Switch(checked = recorrente, onCheckedChange = { recorrente = it })
                }

                if (recorrente) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Semanal", "Mensal", "Anual").forEach { option ->
                            TextButton(onClick = { recorrencia = option }) {
                                Text(
                                    text = option,
                                    color = if (recorrencia == option) Color(0xFF1565C0) else Color(0xFF4B5563),
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
                val valorFormatado = formatManualMoney(valor)
                when {
                    fornecedor.isBlank() -> erro = "Informe uma descricao ou fornecedor."
                    valorFormatado == null -> erro = "Informe um valor valido."
                    !isValidContaDate(vencimento) -> erro = "Digite uma data valida no formato dd/mm/aaaa."
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        val salvou = ContaStorage.salvarContaManual(
                            context = context,
                            fornecedor = fornecedor,
                            valor = valorFormatado,
                            vencimento = vencimento,
                            categoria = categoria.ifBlank { "Outros" },
                            recorrente = recorrente,
                            recorrencia = recorrencia,
                            direcao = direcao
                        )
                        if (salvou) {
                            Toast.makeText(context, if (isReceber) "Recebimento cadastrado" else "Conta cadastrada", Toast.LENGTH_SHORT).show()
                            onSaved()
                        } else {
                            erro = "Nao consegui salvar essa conta. Confira os dados."
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isReceber) "Salvar Recebimento" else "Salvar Conta")
        }
    }
}

private fun formatManualMoney(input: String): String? {
    val clean = input
        .replace("R$", "", ignoreCase = true)
        .replace(" ", "")
        .trim()
    if (clean.isBlank()) return null

    val normalized = if (clean.contains(",")) {
        clean.replace(".", "").replace(",", ".")
    } else {
        clean
    }
    val value = normalized.toBigDecimalOrNull() ?: return null
    if (value <= BigDecimal.ZERO) return null
    return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
}
