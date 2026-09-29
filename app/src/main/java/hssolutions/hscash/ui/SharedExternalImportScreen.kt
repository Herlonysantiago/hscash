package hssolutions.hscash.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SharedExternalType { BOLETO, RECEIPT }

@Composable
fun SharedExternalImportScreen(
    uris: List<Uri>,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    var type by remember(uris) { mutableStateOf<SharedExternalType?>(null) }
    when (type) {
        SharedExternalType.BOLETO -> SharedBoletoImportScreen(
            uris = uris,
            onBack = { type = null },
            onFinished = onFinished
        )
        SharedExternalType.RECEIPT -> SharedReceiptImportScreen(
            uris = uris,
            onBack = { type = null },
            onFinished = onFinished
        )
        null -> SharedExternalTypeChooser(uris.size, onBack) { type = it }
    }
}

@Composable
private fun SharedExternalTypeChooser(
    fileCount: Int,
    onBack: () -> Unit,
    onSelect: (SharedExternalType) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Column {
                Text("Arquivo recebido", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("$fileCount arquivo(s) compartilhado(s) com o HS Cash", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("O que você deseja fazer?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ImportChoiceCard(
            title = "Importar boleto",
            description = "Ler código, valor e vencimento para criar uma nova conta.",
            icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
            onClick = { onSelect(SharedExternalType.BOLETO) }
        )
        ImportChoiceCard(
            title = "Anexar comprovante",
            description = "Escolher uma conta existente e anexar o comprovante do banco. Vencimento não é obrigatório.",
            icon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
            onClick = { onSelect(SharedExternalType.RECEIPT) }
        )
        Text(
            "Nada será salvo nem marcado como pago sem sua confirmação.",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ImportChoiceCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SharedReceiptImportScreen(
    uris: List<Uri>,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val members = rememberActiveSpaceMemberNames()
    var accounts by remember { mutableStateOf<List<ContaSalva>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var selectingUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val selectedAccount = remember(uris) { mutableStateMapOf<String, String>() }
    val markPaid = remember(uris) { mutableStateMapOf<String, Boolean>() }
    val paymentDate = remember(uris) { mutableStateMapOf<String, String>() }
    val paymentMethod = remember(uris) { mutableStateMapOf<String, String>() }
    val paidBy = remember(uris) { mutableStateMapOf<String, String>() }
    val today = remember { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date()) }

    LaunchedEffect(uris) {
        accounts = ContaStorage.listarContasAsync(context)
            .sortedWith(compareBy<ContaSalva> { it.status == ContaStatus.PAGO }.thenBy { parseContaDate(it.vencimento)?.timeInMillis ?: Long.MAX_VALUE })
        uris.forEach { uri ->
            val key = uri.toString()
            markPaid[key] = false
            paymentDate[key] = today
            paymentMethod[key] = "PIX"
            paidBy[key] = ""
        }
        loading = false
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !saving) { Icon(Icons.Default.ArrowBack, "Voltar") }
            Column {
                Text("Anexar comprovante", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Escolha a conta correspondente", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (loading) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Text("Carregando contas...", modifier = Modifier.padding(top = 12.dp))
            }
        } else if (accounts.isEmpty()) {
            Text("Não há contas neste espaço para receber o comprovante.", modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uris, key = Uri::toString) { uri ->
                    val key = uri.toString()
                    val account = accounts.firstOrNull { it.id == selectedAccount[key] }
                    ReceiptImportCard(
                        fileName = sharedFileName(context, uri),
                        account = account,
                        markPaid = markPaid[key] == true,
                        paymentDate = paymentDate[key].orEmpty(),
                        paymentMethod = paymentMethod[key].orEmpty(),
                        paidBy = paidBy[key].orEmpty(),
                        members = members,
                        enabled = !saving,
                        onSelectAccount = { selectingUri = uri },
                        onMarkPaidChange = { markPaid[key] = it },
                        onPaymentDateChange = { paymentDate[key] = it },
                        onPaymentMethodChange = { paymentMethod[key] = it },
                        onPaidByChange = { paidBy[key] = it }
                    )
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Button(
                onClick = {
                    val ids = uris.map { selectedAccount[it.toString()].orEmpty() }
                    when {
                        ids.any(String::isBlank) -> error = "Escolha uma conta para cada comprovante."
                        ids.distinct().size != ids.size -> error = "Escolha contas diferentes para cada arquivo compartilhado."
                        uris.any { markPaid[it.toString()] == true && parseContaDate(paymentDate[it.toString()].orEmpty()) == null } ->
                            error = "Confira a data de pagamento."
                        else -> {
                            error = null
                            saving = true
                            scope.launch {
                                val saved = saveSharedReceipts(
                                    context, uris, accounts, selectedAccount, markPaid,
                                    paymentDate, paymentMethod, paidBy
                                )
                                saving = false
                                Toast.makeText(context, "$saved comprovante(s) anexado(s)", Toast.LENGTH_LONG).show()
                                if (saved > 0) onFinished() else error = "Não foi possível salvar os comprovantes."
                            }
                        }
                    }
                },
                enabled = !saving && uris.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 10.dp))
                Text("Confirmar e anexar (${uris.size})")
            }
        }
    }

    selectingUri?.let { uri ->
        AccountPickerDialog(
            accounts = accounts,
            onDismiss = { selectingUri = null },
            onSelect = { account ->
                selectedAccount[uri.toString()] = account.id
                selectingUri = null
            }
        )
    }
}

@Composable
private fun ReceiptImportCard(
    fileName: String,
    account: ContaSalva?,
    markPaid: Boolean,
    paymentDate: String,
    paymentMethod: String,
    paidBy: String,
    members: List<String>,
    enabled: Boolean,
    onSelectAccount: () -> Unit,
    onMarkPaidChange: (Boolean) -> Unit,
    onPaymentDateChange: (String) -> Unit,
    onPaymentMethodChange: (String) -> Unit,
    onPaidByChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(fileName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = onSelectAccount, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (account == null) "Selecionar conta" else accountLabel(account), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            account?.let {
                Text(
                    "${it.valor} • vencimento ${it.vencimento} • ${statusLabel(it)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = markPaid, onCheckedChange = onMarkPaidChange, enabled = enabled)
                Text(if (account?.direcao == ContaDirecao.RECEBER) "Confirmar recebimento" else "Confirmar pagamento")
            }
            if (markPaid) {
                DateInputField(
                    value = paymentDate,
                    onValueChange = onPaymentDateChange,
                    label = "Data do pagamento",
                    modifier = Modifier.fillMaxWidth(),
                    isError = parseContaDate(paymentDate) == null
                )
                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = onPaymentMethodChange,
                    label = { Text("Forma de pagamento") },
                    placeholder = { Text("PIX, cartão, dinheiro...") },
                    enabled = enabled,
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                PersonSelector(
                    value = paidBy,
                    onValueChange = onPaidByChange,
                    label = "Pago por (opcional)",
                    members = members,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AccountPickerDialog(accounts: List<ContaSalva>, onDismiss: () -> Unit, onSelect: (ContaSalva) -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = accounts.filter { account ->
        search.isBlank() || listOf(account.fornecedor, account.categoria, account.valor, account.vencimento, account.banco)
            .any { it.contains(search.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher conta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Buscar por nome, valor ou data") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = ContaSalva::id) { account ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onSelect(account) }.padding(vertical = 10.dp)
                        ) {
                            Text(accountLabel(account), fontWeight = FontWeight.SemiBold)
                            Text(
                                "${account.valor} • ${account.vencimento} • ${statusLabel(account)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private suspend fun saveSharedReceipts(
    context: Context,
    uris: List<Uri>,
    accounts: List<ContaSalva>,
    selectedAccount: Map<String, String>,
    markPaid: Map<String, Boolean>,
    paymentDate: Map<String, String>,
    paymentMethod: Map<String, String>,
    paidBy: Map<String, String>
): Int = withContext(Dispatchers.IO) {
    var saved = 0
    uris.forEach { uri ->
        val key = uri.toString()
        val account = accounts.firstOrNull { it.id == selectedAccount[key] } ?: return@forEach
        val path = ReceiptHelper.saveReceipt(context, account.id, uri) ?: return@forEach
        val confirmsPayment = markPaid[key] == true
        val paymentMillis = if (confirmsPayment) parseContaDate(paymentDate[key].orEmpty())?.timeInMillis else account.pagoEm
        val updated = account.copy(
            comprovantePath = path,
            status = if (confirmsPayment) ContaStatus.PAGO else account.status,
            pagoEm = paymentMillis,
            formaPagamento = if (confirmsPayment) paymentMethod[key]?.trim()?.takeIf(String::isNotBlank) else account.formaPagamento,
            pagoPor = if (confirmsPayment) paidBy[key]?.trim()?.takeIf(String::isNotBlank) else account.pagoPor,
            pixAgendamentoStatus = if (confirmsPayment && (
                    paymentMethod[key].equals("PIX", true) || !account.pixCopiaCola.isNullOrBlank() || !account.pixChave.isNullOrBlank()
                )) PixAgendamentoStatus.EXECUTADO else account.pixAgendamentoStatus
        )
        ContaStorage.atualizarConta(context, updated, saveAfterUpdate = false)
        if (account.comprovantePath != null && account.comprovantePath != path) {
            ReceiptHelper.deleteStoredFile(account.comprovantePath)
        }
        saved++
    }
    if (saved > 0) ContaStorage.finalizarAlteracoesEmLote(context)
    saved
}

private fun accountLabel(account: ContaSalva): String =
    account.fornecedor.ifBlank { account.categoria.ifBlank { account.banco.ifBlank { "Conta sem nome" } } }

private fun sharedFileName(context: Context, uri: Uri): String =
    PdfBoletoImporter.fileName(context, uri)
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "Comprovante compartilhado"
