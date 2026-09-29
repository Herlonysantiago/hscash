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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
    initialPix: String = "",
    onBack: () -> Unit = {},
    onScanBarcode: () -> Unit = {},
    onScanPix: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val helpTargets = rememberScreenHelpTargets()
    val isReceber = direcao == ContaDirecao.RECEBER
    val spaceMembers = rememberActiveSpaceMemberNames()
    var fornecedor by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var vencimento by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var subcategoria by remember { mutableStateOf("") }
    var responsavel by remember { mutableStateOf("") }
    var codigoBarras by remember(initialBarcode) { mutableStateOf(initialBarcode.filter(Char::isDigit)) }
    var pixCopiaCola by remember(initialPix) { mutableStateOf(PixUtils.normalize(initialPix)) }
    var pixChave by remember(initialPix) { mutableStateOf(PixUtils.parse(initialPix).key.orEmpty()) }
    var pixRecebedor by remember(initialPix) { mutableStateOf(PixUtils.parse(initialPix).recipient.orEmpty()) }
    var pixTxId by remember(initialPix) { mutableStateOf(PixUtils.parse(initialPix).txId.orEmpty()) }
    var pixAgendado by remember { mutableStateOf(false) }
    var showPixFields by remember(initialPix) { mutableStateOf(initialPix.isNotBlank()) }
    var clienteWhatsapp by remember { mutableStateOf("") }
    var recorrente by remember { mutableStateOf(false) }
    var recorrencia by remember { mutableStateOf("Mensal") }
    var recurrenceKind by remember { mutableStateOf(RecurrenceKind.ACCOUNT) }
    var erro by remember { mutableStateOf<String?>(null) }
    var showClientes by remember { mutableStateOf(false) }
    val clientes = remember { ClienteStorage.listarClientes(context) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(codigoBarras) {
        val boleto = parseBoleto(codigoBarras) ?: return@LaunchedEffect
        if (fornecedor.isBlank()) fornecedor = boleto.bancoEmissor
        if (valor.isBlank()) valor = boleto.valor
        if (vencimento.isBlank() && isValidContaDate(boleto.vencimento)) {
            vencimento = boleto.vencimento
        }
        erro = if (!isValidContaDate(boleto.vencimento) && vencimento.isBlank()) {
            "Código identificado. Informe apenas o vencimento, pois ele não está presente neste tipo de boleto."
        } else {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isReceber) "Cadastro de valor a receber" else "Cadastro manual de conta a pagar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                DateInputField(
                    value = vencimento,
                    onValueChange = {
                        vencimento = it
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_date"),
                    label = if (isReceber) "Data prevista" else "Vencimento"
                )
                CategorySelector(
                    value = categoria,
                    onValueChange = {
                        categoria = it
                        subcategoria = ""
                    },
                    direction = direcao,
                    modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_category")
                )
                SubcategorySelector(
                    category = categoria,
                    value = subcategoria,
                    onValueChange = { subcategoria = it },
                    modifier = Modifier.fillMaxWidth()
                )
                PersonSelector(
                    value = responsavel,
                    onValueChange = { responsavel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Responsável pela conta (opcional)",
                    members = spaceMembers
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

                OutlinedButton(
                    onClick = { showPixFields = !showPixFields },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            showPixFields -> "Ocultar dados Pix"
                            pixCopiaCola.isNotBlank() || pixChave.isNotBlank() -> "Pix configurado • editar"
                            isReceber -> "Adicionar cobrança por Pix (opcional)"
                            else -> "Adicionar pagamento por Pix (opcional)"
                        }
                    )
                }
                if (showPixFields) {
                OutlinedTextField(
                    value = pixCopiaCola,
                    onValueChange = { raw ->
                        pixCopiaCola = raw
                        val info = PixUtils.parse(raw)
                        if (info.valid) {
                            pixChave = info.key ?: pixChave
                            pixRecebedor = info.recipient ?: pixRecebedor
                            pixTxId = info.txId ?: pixTxId
                        }
                        erro = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pix Copia e Cola") },
                    placeholder = { Text("Cole o código ou leia o QR Code") },
                    minLines = 2,
                    maxLines = 4,
                    colors = hsCashOutlinedTextFieldColors()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val pasted = ClipboardHelper.paste(context)
                        if (pasted == null) {
                            erro = "A área de transferência está vazia."
                        } else {
                            pixCopiaCola = pasted
                            val info = PixUtils.parse(pasted)
                            if (info.valid) {
                                pixChave = info.key.orEmpty()
                                pixRecebedor = info.recipient.orEmpty()
                                pixTxId = info.txId.orEmpty()
                            }
                        }
                    }) { Text("Colar Pix") }
                    TextButton(onClick = onScanPix) { Text("Escanear QR Pix") }
                }
                if (pixCopiaCola.isNotBlank()) {
                    val pixInfo = PixUtils.parse(pixCopiaCola)
                    Text(
                        text = if (pixInfo.valid) {
                            buildString {
                                append(if (pixInfo.dynamic) "Pix dinâmico válido" else "Pix válido")
                                pixInfo.amountInCents?.let { append(" • ${PixUtils.moneyText(it)}") }
                                pixInfo.recipient?.let { append(" • $it") }
                            }
                        } else pixInfo.error.orEmpty(),
                        color = if (pixInfo.valid) Color(0xFF0B6B2B) else Color(0xFFD32F2F),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    val typedCents = manualMoneyInCents(valor)
                    if (pixInfo.valid && pixInfo.amountInCents != null && typedCents != null && pixInfo.amountInCents != typedCents) {
                        Text(
                            "Atenção: o Pix vale ${PixUtils.moneyText(pixInfo.amountInCents)}, diferente do valor informado.",
                            color = Color(0xFFF57C00),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                OutlinedTextField(
                    value = pixChave,
                    onValueChange = { pixChave = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isReceber) "Sua chave Pix" else "Chave Pix do recebedor") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = pixRecebedor,
                    onValueChange = { pixRecebedor = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do recebedor") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = pixTxId,
                    onValueChange = { pixTxId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Identificador/TxID (opcional)") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pix agendado no banco", fontWeight = FontWeight.Bold)
                        Text(
                            "O agendamento não confirma que o pagamento foi executado.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = pixAgendado, onCheckedChange = { pixAgendado = it })
                }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "manual_recurrence"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conta recorrente", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = recorrente, onCheckedChange = { recorrente = it })
                }

                if (recorrente) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = recurrenceKind == RecurrenceKind.ACCOUNT,
                            onClick = { recurrenceKind = RecurrenceKind.ACCOUNT },
                            label = { Text("Conta recorrente") }
                        )
                        FilterChip(
                            selected = recurrenceKind == RecurrenceKind.SUBSCRIPTION,
                            onClick = { recurrenceKind = RecurrenceKind.SUBSCRIPTION },
                            label = { Text("Assinatura") }
                        )
                    }
                    RecurrenceSelector(value = recorrencia, onValueChange = { recorrencia = it })
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
                val boletoDigitado = codigoBarras.takeIf(String::isNotBlank)?.let(::parseBoleto)
                val codigoInvalido = codigoBarras.isNotBlank() && boletoDigitado == null
                val fornecedorFinal = fornecedor.ifBlank { boletoDigitado?.bancoEmissor.orEmpty() }
                val valorFinal = valor.ifBlank { boletoDigitado?.valor.orEmpty() }
                val vencimentoFinal = vencimento.takeIf(::isValidContaDate)
                    ?: boletoDigitado?.vencimento?.takeIf(::isValidContaDate)
                    ?: vencimento
                val valorFormatado = formatManualMoney(valorFinal)
                if (boletoDigitado != null) {
                    if (fornecedor.isBlank()) fornecedor = boletoDigitado.bancoEmissor
                    if (valor.isBlank()) valor = boletoDigitado.valor
                    if (vencimento.isBlank() && isValidContaDate(boletoDigitado.vencimento)) {
                        vencimento = boletoDigitado.vencimento
                    }
                }
                when {
                    codigoInvalido -> erro = "O código de barras digitado não é válido. Confira os números."
                    fornecedorFinal.isBlank() -> erro = "Informe uma descricao ou fornecedor."
                    valorFormatado == null -> erro = "Informe um valor valido."
                    !isValidContaDate(vencimentoFinal) -> erro = if (boletoDigitado != null) {
                        "Valor e emissor identificados. Este tipo de código não informa o vencimento; preencha a data para salvar."
                    } else {
                        "Digite uma data valida no formato dd/mm/aaaa."
                    }
                    pixCopiaCola.isNotBlank() && !PixUtils.parse(pixCopiaCola).valid -> erro = PixUtils.parse(pixCopiaCola).error
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        scope.launch {
                            if (pixCopiaCola.isNotBlank() && ContaStorage.existePixDuplicadoAsync(context, pixCopiaCola)) {
                                erro = "Este código Pix já está cadastrado em outra conta."
                                return@launch
                            }
                            val salvou = ContaStorage.salvarContaManualAsync(
                                context = context,
                                fornecedor = fornecedorFinal,
                                valor = valorFormatado,
                                vencimento = vencimentoFinal,
                                categoria = categoria.ifBlank { "Outros" },
                                subcategoria = subcategoria,
                                recorrente = recorrente,
                                recorrencia = recorrencia,
                            direcao = direcao,
                            clienteWhatsapp = if (isReceber) clienteWhatsapp else null,
                            codigoBarras = boletoDigitado?.codigoBarras ?: codigoBarras,
                            banco = boletoDigitado?.bancoEmissor,
                            responsavel = responsavel,
                            pixCopiaCola = pixCopiaCola,
                            pixChave = pixChave,
                            pixRecebedor = pixRecebedor,
                            pixTxId = pixTxId,
                            pixAgendado = pixAgendado,
                            recurrenceKind = recurrenceKind
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
                                Text(cliente.whatsapp.ifBlank { "Sem WhatsApp" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun manualMoneyInCents(input: String): Long? {
    val clean = input.replace("R$", "", ignoreCase = true).replace("\u00A0", "").replace(" ", "")
    val normalized = if (clean.contains(',')) clean.replace(".", "").replace(',', '.') else clean
    return normalized.toBigDecimalOrNull()?.movePointRight(2)?.setScale(0)?.longValueExact()
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
