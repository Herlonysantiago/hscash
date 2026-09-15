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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ContaManualScreen(
    direcao: String = ContaDirecao.PAGAR,
    initialBarcode: String = "",
    onBack: () -> Unit = {},
    onScanBarcode: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val helpTargets = rememberScreenHelpTargets()
    val isReceber = direcao == ContaDirecao.RECEBER
    var fornecedor by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var vencimento by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var codigoBarras by remember(initialBarcode) { mutableStateOf(initialBarcode.filter(Char::isDigit)) }
    var clienteWhatsapp by remember { mutableStateOf("") }
    var recorrente by remember { mutableStateOf(false) }
    var recorrencia by remember { mutableStateOf("Mensal") }
    var erro by remember { mutableStateOf<String?>(null) }
    var showClientes by remember { mutableStateOf(false) }
    val clientes = remember { ClienteStorage.listarClientes(context) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Column(modifier = Modifier.weight(1f)) {
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
            ScreenHelp(
                screenKey = if (isReceber) "conta_manual_receber" else "conta_manual_pagar",
                steps = listOf(
                    ScreenHelpStep("Descrição", "Informe quem vai receber o pagamento ou a origem do valor a receber.", HelpFocus.TOP, "manual_supplier"),
                    ScreenHelpStep("Código opcional", "Digite a linha do boleto ou toque em Escanear. Este campo não é obrigatório.", HelpFocus.TOP, "manual_barcode"),
                    ScreenHelpStep("Valor", "Informe o valor usando vírgula ou ponto para separar os centavos.", HelpFocus.CENTER, "manual_value"),
                    ScreenHelpStep("Data", "Informe o vencimento ou a data prevista no formato dia, mês e ano.", HelpFocus.CENTER, "manual_date"),
                    ScreenHelpStep("Categoria", "Use uma categoria consistente para melhorar buscas, orçamento e relatórios.", HelpFocus.CENTER, "manual_category"),
                    ScreenHelpStep("Recorrência", "Ative esta opção para criar repetições semanais, mensais ou anuais.", HelpFocus.BOTTOM, "manual_recurrence"),
                    ScreenHelpStep("Salvar", "Revise os dados e toque aqui para concluir o cadastro.", HelpFocus.BOTTOM, "manual_save")
                ),
                targets = helpTargets,
                onStepChanged = { index ->
                    scope.launch {
                        val fraction = listOf(0f, 0.1f, 0.3f, 0.45f, 0.6f, 0.82f, 1f)[index]
                        scrollState.animateScrollTo((scrollState.maxValue * fraction).toInt())
                    }
                }
            )
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
                if (isReceber && clientes.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showClientes = true }) {
                            Text("Selecionar cliente")
                        }
                        Text(
                            text = if (fornecedor.isBlank()) "Nenhum cliente selecionado" else fornecedor,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = fornecedor,
                    onValueChange = {
                        fornecedor = it
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_supplier"),
                    label = { Text(if (isReceber) "Cliente ou origem" else "Descricao ou fornecedor") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                Row(
                    modifier = Modifier.screenHelpTarget(helpTargets, "manual_barcode"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = codigoBarras,
                        onValueChange = { codigoBarras = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Código de barras (opcional)") },
                        placeholder = { Text("Digite ou escaneie") },
                        singleLine = true,
                        colors = hsCashOutlinedTextFieldColors()
                    )
                    TextButton(onClick = onScanBarcode) { Text("Escanear") }
                }
                OutlinedTextField(
                    value = valor,
                    onValueChange = {
                        valor = it
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_value"),
                    label = { Text("Valor") },
                    placeholder = { Text("Ex: 125,90") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = vencimento,
                    onValueChange = {
                        vencimento = it
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_date"),
                    label = { Text(if (isReceber) "Data prevista (dd/mm/aaaa)" else "Vencimento (dd/mm/aaaa)") },
                    placeholder = { Text("Ex: 25/05/2026") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = categoria,
                    onValueChange = { categoria = it },
                    modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_category"),
                    label = { Text("Categoria") },
                    placeholder = { Text(if (isReceber) "Ex: Cliente, Servico, Venda" else "Ex: Aluguel, Luz, Internet") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                if (isReceber) {
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
                    modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_recurrence"),
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

                        scope.launch {
                            val salvou = ContaStorage.salvarContaManualAsync(
                                context = context,
                                fornecedor = fornecedor,
                                valor = valorFormatado,
                                vencimento = vencimento,
                                categoria = categoria.ifBlank { "Outros" },
                                recorrente = recorrente,
                                recorrencia = recorrencia,
                            direcao = direcao,
                            clienteWhatsapp = if (isReceber) clienteWhatsapp else null,
                            codigoBarras = codigoBarras
                            )
                            if (salvou) {
                                Toast.makeText(context, if (isReceber) "Recebimento cadastrado" else "Conta cadastrada", Toast.LENGTH_SHORT).show()
                                onSaved()
                            } else {
                                erro = "Nao consegui salvar essa conta. Confira os dados."
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_save")
        ) {
            Text(if (isReceber) "Salvar Recebimento" else "Salvar Conta")
        }
    }

    if (showClientes) {
        AlertDialog(
            onDismissRequest = { showClientes = false },
            title = { Text("Selecionar cliente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    clientes.forEach { cliente ->
                        TextButton(
                            onClick = {
                                fornecedor = cliente.nome
                                clienteWhatsapp = cliente.whatsapp
                                if (categoria.isBlank()) categoria = "Cliente"
                                showClientes = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(cliente.nome, fontWeight = FontWeight.Bold)
                                Text(cliente.whatsapp.ifBlank { "Sem WhatsApp" }, color = Color(0xFF6B7280))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClientes = false }) {
                    Text("Fechar")
                }
            }
        )
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
