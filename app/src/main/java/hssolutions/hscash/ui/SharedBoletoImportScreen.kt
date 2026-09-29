package hssolutions.hscash.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal enum class SharedImportConfidence { HIGH, REVIEW, ERROR }

internal data class SharedBoletoPreview(
    val uri: Uri,
    val fileName: String,
    val boleto: BoletoInfo?,
    val confidence: SharedImportConfidence,
    val message: String,
    val pdfStatus: PdfBoletoReadStatus? = null
)

internal data class ConfirmedSharedBoleto(
    val preview: SharedBoletoPreview,
    val supplier: String,
    val value: String,
    val dueDate: String,
    val barcode: String
)

internal object SharedBoletoImporter {
    suspend fun inspect(context: Context, uris: List<Uri>): List<SharedBoletoPreview> =
        withContext(Dispatchers.IO) {
            uris.distinct().map { uri ->
                val fileName = PdfBoletoImporter.fileName(context, uri)
                    ?: uri.lastPathSegment?.substringAfterLast('/')
                    ?: "Arquivo compartilhado"
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                val isPdf = mimeType == "application/pdf" || fileName.endsWith(".pdf", true)
                val pdfInspection = if (isPdf) {
                    runCatching { PdfBoletoImporter.inspectDetailed(context, uri) }.getOrNull()
                } else {
                    null
                }
                val boleto = if (isPdf) {
                    pdfInspection?.boleto
                } else {
                    runCatching { inspectImage(context, uri) }.getOrNull()
                }
                val confidence = confidenceFor(boleto)
                SharedBoletoPreview(
                    uri = uri,
                    fileName = fileName,
                    boleto = boleto,
                    confidence = confidence,
                    message = when (confidence) {
                        SharedImportConfidence.HIGH -> "Codigo validado. Confira os dados antes de salvar."
                        SharedImportConfidence.REVIEW -> "Alguns dados precisam ser preenchidos ou conferidos."
                        SharedImportConfidence.ERROR -> if (isPdf) {
                            pdfInspection?.message ?: "Nao encontramos um codigo de boleto valido neste PDF."
                        } else {
                            "Nao foi possivel ler um codigo de barras valido nesta imagem."
                        }
                    },
                    pdfStatus = pdfInspection?.status
                )
            }
        }

    internal fun confidenceFor(boleto: BoletoInfo?): SharedImportConfidence = when {
        boleto == null -> SharedImportConfidence.ERROR
        parseContaDate(boleto.vencimento) == null || parseMoneyInCents(boleto.valor) <= 0L ->
            SharedImportConfidence.REVIEW
        else -> SharedImportConfidence.HIGH
    }

    private suspend fun inspectImage(context: Context, uri: Uri): BoletoInfo? =
        suspendCancellableCoroutine { continuation ->
            val scanner = BarcodeScanning.getClient()
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
                scanner.close()
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val boleto = barcodes.asSequence()
                        .mapNotNull { it.rawValue }
                        .mapNotNull(::parseBoleto)
                        .firstOrNull()
                    if (continuation.isActive) continuation.resume(boleto)
                    scanner.close()
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                    scanner.close()
                }
            continuation.invokeOnCancellation { runCatching { scanner.close() } }
        }

    internal suspend fun save(context: Context, confirmed: List<ConfirmedSharedBoleto>): PdfImportResult =
        withContext(Dispatchers.IO) {
            var imported = 0
            var duplicates = 0
            val invalid = mutableListOf<String>()

            confirmed.forEach { item ->
                val typedBoleto = item.barcode.takeIf(String::isNotBlank)?.let(::parseBoleto)
                val boleto = item.preview.boleto?.copy(
                    valor = item.value.trim(),
                    vencimento = item.dueDate.trim()
                ) ?: typedBoleto?.copy(
                    valor = item.value.trim(),
                    vencimento = item.dueDate.trim()
                ) ?: manualBoleto(item)

                if (item.supplier.isBlank() ||
                    parseContaDate(item.dueDate) == null || parseMoneyInCents(item.value) <= 0L
                ) {
                    invalid += item.preview.fileName
                    return@forEach
                }

                val saved = ContaStorage.salvarBoleto(
                    context = context,
                    boleto = boleto,
                    fornecedor = item.supplier,
                    categoria = "Boletos importados",
                    saveAfterUpdate = false
                )
                if (!saved) {
                    duplicates += 1
                    return@forEach
                }

                ContaStorage.listarContas(context)
                    .firstOrNull { it.codigo == boleto.codigoBarras && it.vencimento == boleto.vencimento }
                    ?.let { conta ->
                        ReceiptHelper.saveBoletoPdf(context, conta.id, item.preview.uri)?.let { path ->
                            ContaStorage.atualizarConta(
                                context,
                                conta.copy(boletoPdfPath = path),
                                saveAfterUpdate = false
                            )
                        }
                    }
                imported += 1
            }
            if (imported > 0) ContaStorage.finalizarAlteracoesEmLote(context)
            PdfImportResult(imported, duplicates, invalid)
        }

    private fun manualBoleto(item: ConfirmedSharedBoleto): BoletoInfo {
        val id = item.preview.uri.toString().hashCode().toUInt().toString()
        val timestamp = System.currentTimeMillis().toString()
        val code = "pdf-manual-$id-$timestamp"
        return BoletoInfo(
            codigoOriginal = code,
            codigoBarras = code,
            linhaDigitavel = item.barcode.trim(),
            bancoEmissor = item.supplier.trim(),
            valor = item.value.trim(),
            vencimento = item.dueDate.trim(),
            tipo = "Boleto PDF manual",
            observacao = "Dados preenchidos manualmente porque a leitura automatica do PDF falhou."
        )
    }
}

@Composable
fun SharedBoletoImportScreen(
    uris: List<Uri>,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember(uris) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var previews by remember(uris) { mutableStateOf<List<SharedBoletoPreview>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val selected = remember(uris) { mutableStateMapOf<String, Boolean>() }
    val suppliers = remember(uris) { mutableStateMapOf<String, String>() }
    val values = remember(uris) { mutableStateMapOf<String, String>() }
    val dueDates = remember(uris) { mutableStateMapOf<String, String>() }
    val barcodes = remember(uris) { mutableStateMapOf<String, String>() }
    val pdfPasswords = remember(uris) { mutableStateMapOf<String, String>() }
    val unlocking = remember(uris) { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(uris) {
        previews = SharedBoletoImporter.inspect(context, uris)
        previews.forEach { preview ->
            val key = preview.uri.toString()
            selected[key] = preview.boleto != null
            suppliers[key] = preview.fileName.substringBeforeLast('.').ifBlank {
                preview.boleto?.bancoEmissor.orEmpty()
            }
            values[key] = preview.boleto?.valor.orEmpty()
            dueDates[key] = preview.boleto?.vencimento.orEmpty()
            barcodes[key] = preview.boleto?.linhaDigitavel.orEmpty()
            pdfPasswords[key] = ""
            unlocking[key] = false
        }
        loading = false
    }

    val selectedPreviews = previews.filter { selected[it.uri.toString()] == true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !saving) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text("Conferir importacao", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Nada sera salvo sem sua confirmacao", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF1565C0))
                    Spacer(Modifier.height(12.dp))
                    Text("Lendo ${uris.size} arquivo(s)...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            uris.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhum arquivo foi recebido.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(previews, key = { it.uri.toString() }) { preview ->
                        val key = preview.uri.toString()
                        SharedBoletoPreviewCard(
                            preview = preview,
                            checked = selected[key] == true,
                            supplier = suppliers[key].orEmpty(),
                            value = values[key].orEmpty(),
                            dueDate = dueDates[key].orEmpty(),
                            barcode = barcodes[key].orEmpty(),
                            password = pdfPasswords[key].orEmpty(),
                            unlocking = unlocking[key] == true,
                            enabled = !saving,
                            onCheckedChange = { selected[key] = it },
                            onSupplierChange = { suppliers[key] = it },
                            onValueChange = { values[key] = it },
                            onDueDateChange = { dueDates[key] = it },
                            onBarcodeChange = { barcodes[key] = it },
                            onPasswordChange = { pdfPasswords[key] = it },
                            onUnlock = {
                                val password = pdfPasswords[key].orEmpty()
                                if (password.isBlank()) {
                                    errorMessage = "Informe a senha do PDF para tentar desbloquear."
                                } else {
                                    errorMessage = null
                                    unlocking[key] = true
                                    scope.launch {
                                        val inspection = withContext(Dispatchers.IO) {
                                            PdfBoletoImporter.inspectDetailed(context, preview.uri, password)
                                        }
                                        val updatedPreview = preview.copy(
                                            boleto = inspection.boleto,
                                            confidence = SharedBoletoImporter.confidenceFor(inspection.boleto),
                                            message = inspection.message,
                                            pdfStatus = inspection.status
                                        )
                                        previews = previews.map {
                                            if (it.uri == preview.uri) updatedPreview else it
                                        }
                                        if (inspection.boleto != null) {
                                            selected[key] = true
                                            values[key] = inspection.boleto.valor
                                            dueDates[key] = inspection.boleto.vencimento
                                            barcodes[key] = inspection.boleto.linhaDigitavel
                                            suppliers[key] = suppliers[key].orEmpty().ifBlank {
                                                preview.fileName.substringBeforeLast('.').ifBlank { inspection.boleto.bancoEmissor }
                                            }
                                        }
                                        unlocking[key] = false
                                    }
                                }
                            }
                        )
                    }
                }

                errorMessage?.let {
                    Text(
                        text = it,
                        color = Color(0xFFB42318),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Button(
                    onClick = {
                        val confirmed = selectedPreviews.map { preview ->
                            val key = preview.uri.toString()
                            ConfirmedSharedBoleto(
                                preview = preview,
                                supplier = suppliers[key].orEmpty(),
                                value = values[key].orEmpty(),
                                dueDate = dueDates[key].orEmpty(),
                                barcode = barcodes[key].orEmpty()
                            )
                        }
                        val invalidFields = confirmed.any {
                            val typedBarcodeInvalid = it.barcode.isNotBlank() && parseBoleto(it.barcode) == null
                            it.supplier.isBlank() ||
                                parseContaDate(it.dueDate) == null ||
                                parseMoneyInCents(it.value) <= 0L ||
                                typedBarcodeInvalid
                        }
                        if (invalidFields) {
                            errorMessage = "Confira fornecedor, valor, vencimento e código dos itens selecionados."
                        } else {
                            errorMessage = null
                            saving = true
                            scope.launch {
                                val result = SharedBoletoImporter.save(context, confirmed)
                                android.widget.Toast.makeText(
                                    context,
                                    importResultMessage(result),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                                saving = false
                                onFinished()
                            }
                        }
                    },
                    enabled = selectedPreviews.isNotEmpty() && !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(52.dp)
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Salvar selecionados (${selectedPreviews.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedBoletoPreviewCard(
    preview: SharedBoletoPreview,
    checked: Boolean,
    supplier: String,
    value: String,
    dueDate: String,
    barcode: String,
    password: String,
    unlocking: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onSupplierChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDueDateChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit
) {
    val valid = preview.boleto != null
    val needsPassword = preview.pdfStatus == PdfBoletoReadStatus.PASSWORD_PROTECTED && !valid
    val statusColor = when (preview.confidence) {
        SharedImportConfidence.HIGH -> Color(0xFF087443)
        SharedImportConfidence.REVIEW -> Color(0xFF9A6700)
        SharedImportConfidence.ERROR -> Color(0xFFB42318)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
                Icon(
                    imageVector = if (valid) Icons.Default.PictureAsPdf else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = statusColor
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        preview.fileName,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (valid) Icon(Icons.Default.CheckCircle, null, tint = statusColor, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(preview.message, fontSize = 12.sp, color = statusColor)
                    }
                }
            }

            if (valid) {
                OutlinedTextField(
                    value = supplier,
                    onValueChange = onSupplierChange,
                    label = { Text("Fornecedor") },
                    enabled = enabled,
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = { Text("Valor") },
                        enabled = enabled,
                        singleLine = true,
                        colors = hsCashOutlinedTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dueDate,
                        onValueChange = onDueDateChange,
                        label = { Text("Vencimento") },
                        placeholder = { Text("dd/MM/aaaa") },
                        enabled = enabled,
                        singleLine = true,
                        colors = hsCashOutlinedTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "${preview.boleto?.bancoEmissor} • ${preview.boleto?.tipo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    preview.boleto?.linhaDigitavel.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (needsPassword) {
                Text(
                    "Se souber a senha deste PDF, tente desbloquear para importar automaticamente. A senha não será salva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Senha do PDF") },
                        enabled = enabled && !unlocking,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = hsCashOutlinedTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onUnlock,
                        enabled = enabled && !unlocking,
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (unlocking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Tentar")
                        }
                    }
                }
            }
            if (!valid) {
                Text(
                    "Preencha manualmente para salvar a conta. O PDF será guardado como boleto original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = supplier,
                    onValueChange = onSupplierChange,
                    label = { Text("Fornecedor") },
                    enabled = enabled,
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChange,
                        label = { Text("Valor") },
                        enabled = enabled,
                        singleLine = true,
                        colors = hsCashOutlinedTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    DateInputField(
                        value = dueDate,
                        onValueChange = onDueDateChange,
                        label = "Vencimento",
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = barcode,
                    onValueChange = onBarcodeChange,
                    label = { Text("Código de barras/linha digitável (opcional)") },
                    enabled = enabled,
                    minLines = 2,
                    maxLines = 3,
                    colors = hsCashOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun parseMoneyInCents(value: String): Long {
    val normalized = value
        .replace("R$", "", ignoreCase = true)
        .replace(" ", "")
        .replace(".", "")
        .replace(',', '.')
    return normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
}

private fun importResultMessage(result: PdfImportResult): String = buildString {
    append("${result.imported} boleto(s) importado(s)")
    if (result.duplicates > 0) append(" • ${result.duplicates} duplicado(s)")
    if (result.invalid.isNotEmpty()) append(" • ${result.invalid.size} nao importado(s)")
}
