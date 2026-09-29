package hssolutions.hscash.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size as AndroidSize
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

enum class PaymentScanMode { BOLETO, PIX }

@Composable
fun BoletosScreen(
    onBack: () -> Unit = {},
    onBoletoFound: (String) -> Unit = {},
    scanMode: PaymentScanMode = PaymentScanMode.BOLETO
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val helpTargets = rememberScreenHelpTargets()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var flashLigado by remember { mutableStateOf(false) }
    var abriuDetalhes by remember { mutableStateOf(false) }
    var mostrarDigitacao by remember { mutableStateOf(false) }
    var codigoManual by remember { mutableStateOf("") }
    var erroManual by remember { mutableStateOf<String?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(scanMode) {
        Log.i("HsCashOrientation", "Abrindo scanner em paisagem fixa: $scanMode")
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        onDispose {
            Log.i("HsCashOrientation", "Fechando scanner e restaurando retrato: $scanMode")
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            camera?.cameraControl?.enableTorch(false)
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setTargetResolution(AndroidSize(1920, 1080))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer(acceptPix = scanMode == PaymentScanMode.PIX) { barcode ->
                                    if (!abriuDetalhes) {
                                        abriuDetalhes = true
                                        vibrate(context)
                                        onBoletoFound(barcode)
                                    }
                                })
                            }

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalyzer
                            )
                            camera?.cameraControl?.setLinearZoom(0.25f)
                        } catch (exc: Exception) {
                            Log.e("Camera", "Falha ao iniciar camera", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = size.width * 0.1f
                val right = size.width * 0.9f
                val top = size.height * 0.28f
                val bottom = size.height * 0.72f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
                drawLine(
                    color = Color.Red,
                    start = Offset(x = size.width * 0.1f, y = size.height / 2),
                    end = Offset(x = size.width * 0.9f, y = size.height / 2),
                    strokeWidth = 8f
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.44f)
                    .align(Alignment.Center)
                    .screenHelpTarget(helpTargets, "scanner_frame")
            )

            ScannerIconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                icon = Icons.Default.ArrowBack,
                description = "Voltar"
            )

            ScreenHelp(
                screenKey = "scanner_boletos",
                darkButton = true,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                steps = listOf(
                    ScreenHelpStep("Enquadre o código", if (scanMode == PaymentScanMode.PIX) "Posicione o QR Code Pix dentro da moldura. A leitura acontece automaticamente." else "Posicione o código de barras dentro da moldura. A leitura acontece automaticamente.", HelpFocus.CENTER, "scanner_frame"),
                    ScreenHelpStep("Use o flash", "Ligue a iluminação quando o código estiver escuro ou com pouco contraste.", HelpFocus.TOP, "scanner_flash"),
                    ScreenHelpStep("Digitação manual", "Se a câmera não conseguir ler, toque no lápis e informe a linha digitável.", HelpFocus.BOTTOM, "scanner_manual")
                ),
                targets = helpTargets
            )

            ScannerIconButton(
                onClick = {
                    val novoEstado = !flashLigado
                    camera?.cameraControl?.enableTorch(novoEstado)
                    flashLigado = novoEstado
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .screenHelpTarget(helpTargets, "scanner_flash"),
                icon = if (flashLigado) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                description = if (flashLigado) "Desligar flash" else "Ligar flash",
                enabled = camera?.cameraInfo?.hasFlashUnit() == true
            )

            ScannerIconButton(
                onClick = { mostrarDigitacao = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .screenHelpTarget(helpTargets, "scanner_manual"),
                icon = Icons.Default.Edit,
                description = "Digitar codigo"
            )
        } else {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Permitir camera")
            }
        }
    }

    if (mostrarDigitacao) {
        AlertDialog(
            onDismissRequest = {
                mostrarDigitacao = false
                erroManual = null
            },
            title = { Text("Digitar codigo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = codigoManual,
                        onValueChange = {
                            codigoManual = it
                            erroManual = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (scanMode == PaymentScanMode.PIX) "Pix Copia e Cola" else "Codigo de barras ou linha digitavel") },
                        singleLine = false,
                        colors = hsCashOutlinedTextFieldColors()
                    )
                    erroManual?.let {
                        Text(
                            text = it,
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedPix = PixUtils.parse(codigoManual)
                        val digits = codigoManual.filter(Char::isDigit)
                        if (scanMode == PaymentScanMode.PIX && !parsedPix.valid) {
                            erroManual = parsedPix.error ?: "Código Pix inválido."
                        } else if (scanMode == PaymentScanMode.BOLETO && parseBoleto(digits) == null) {
                            erroManual = "Codigo invalido: ${digits.length} digitos. Use 47 para boleto bancario, 48 para convenio ou 44 do codigo de barras."
                        } else {
                            mostrarDigitacao = false
                            onBoletoFound(if (scanMode == PaymentScanMode.PIX) parsedPix.payload else digits)
                        }
                    }
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        mostrarDigitacao = false
                        erroManual = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ScannerIconButton(
    onClick: () -> Unit,
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(42.dp),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
            disabledContainerColor = Color.Black.copy(alpha = 0.25f),
            disabledContentColor = Color.White.copy(alpha = 0.45f)
        )
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

@Composable
fun BoletoDetalheScreen(
    codigo: String,
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val boletoInfo = remember(codigo) { parseBoleto(codigo) }
    var fornecedor by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var recorrente by remember { mutableStateOf(false) }
    var recorrencia by remember { mutableStateOf("Mensal") }
    var vencimentoManual by remember { mutableStateOf("") }
    var erroVencimento by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Text(
                text = "Dados do Boleto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (boletoInfo == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.35f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Nao consegui identificar esse codigo como boleto.",
                        color = Color(0xFFB71C1C),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Foram encontrados ${codigo.filter(Char::isDigit).length} digitos. Use 47 digitos para boleto bancario, 48 para convenio/arrecadacao ou 44 do codigo de barras.",
                        color = Color(0xFFB71C1C),
                        style = MaterialTheme.typography.bodySmall
                    )
                    SelectionContainer {
                        Text(text = codigo, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            return@Column
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B6B2B)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Valor do boleto",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Text(
                    text = boletoInfo.valor,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        BoletoInfoRow("Banco emissor", boletoInfo.bancoEmissor, Color(0xFF1565C0))
        BoletoInfoRow("Vencimento", boletoInfo.vencimento, Color(0xFFC62828))
        BoletoInfoRow("Tipo", boletoInfo.tipo, Color(0xFF6A1B9A))
        PaymentCopyCard(
            linhaDigitavel = boletoInfo.linhaDigitavel,
            codigoBarras = boletoInfo.codigoBarras
        )

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
                Text(
                    text = "Organizacao",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = fornecedor,
                    onValueChange = { fornecedor = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fornecedor (opcional)") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                CategorySelector(
                    value = categoria,
                    onValueChange = { categoria = it },
                    modifier = Modifier.fillMaxWidth(),
                    direction = ContaDirecao.PAGAR
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Conta recorrente",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(checked = recorrente, onCheckedChange = { recorrente = it })
                }
                if (recorrente) {
                    RecurrenceSelector(value = recorrencia, onValueChange = { recorrencia = it })
                }
            }
        }

        val precisaInformarVencimento = !isValidVencimento(boletoInfo.vencimento)
        if (precisaInformarVencimento) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                border = BorderStroke(1.dp, Color(0xFFFFA000).copy(alpha = 0.45f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Informe o vencimento antes de salvar",
                        color = Color(0xFF7A4F00),
                        fontWeight = FontWeight.Bold
                    )
                    DateInputField(
                        value = vencimentoManual,
                        onValueChange = {
                            vencimentoManual = it
                            erroVencimento = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = "Vencimento",
                        isError = erroVencimento != null
                    )
                    erroVencimento?.let {
                        Text(
                            text = it,
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        boletoInfo.observacao?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Button(
            onClick = {
                val vencimentoFinal = if (precisaInformarVencimento) {
                    vencimentoManual.trim()
                } else {
                    boletoInfo.vencimento
                }

                if (!isValidVencimento(vencimentoFinal)) {
                    erroVencimento = "Digite uma data valida no formato dd/mm/aaaa."
                    return@Button
                }

                val boletoParaSalvar = boletoInfo.copy(vencimento = vencimentoFinal)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                scope.launch {
                    val salvou = ContaStorage.salvarBoletoAsync(
                        context = context,
                        boleto = boletoParaSalvar,
                        fornecedor = fornecedor,
                        categoria = categoria.ifBlank { "Outros" },
                        recorrente = recorrente,
                        recorrencia = recorrencia
                    )
                    val mensagem = if (salvou) {
                        "Conta salva com sucesso"
                    } else {
                        "Essa conta ja estava salva"
                    }
                    Toast.makeText(context, mensagem, Toast.LENGTH_SHORT).show()
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (precisaInformarVencimento) "Salvar com Vencimento" else "Confirmar e Salvar Conta")
        }
    }
}

@Composable
private fun BoletoInfoRow(label: String, value: String, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun isValidVencimento(value: String): Boolean {
    return try {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply {
            isLenient = false
        }
        formatter.parse(value) != null
    } catch (_: Exception) {
        false
    }
}

private fun vibrate(context: android.content.Context) {
    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(90)
    }
}
