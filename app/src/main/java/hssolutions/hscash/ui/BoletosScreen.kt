package hssolutions.hscash.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.util.Size
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun BoletosScreen(
    onBack: () -> Unit = {},
    onBoletoFound: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
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

    DisposableEffect(Unit) {
        val orientacaoAnterior = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            if (orientacaoAnterior != null) {
                activity.requestedOrientation = orientacaoAnterior
            }
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
                            .setTargetResolution(Size(1920, 1080))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                                    if (!abriuDetalhes) {
                                        abriuDetalhes = true
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
                drawLine(
                    color = Color.Red,
                    start = Offset(x = size.width * 0.1f, y = size.height / 2),
                    end = Offset(x = size.width * 0.9f, y = size.height / 2),
                    strokeWidth = 8f
                )
            }

            ScannerIconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                icon = Icons.Default.ArrowBack,
                description = "Voltar"
            )

            ScannerIconButton(
                onClick = {
                    val novoEstado = !flashLigado
                    camera?.cameraControl?.enableTorch(novoEstado)
                    flashLigado = novoEstado
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                icon = if (flashLigado) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                description = if (flashLigado) "Desligar flash" else "Ligar flash",
                enabled = camera?.cameraInfo?.hasFlashUnit() == true
            )

            ScannerIconButton(
                onClick = { mostrarDigitacao = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
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
                        label = { Text("Codigo de barras ou linha digitavel") },
                        singleLine = false
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
                        val digits = codigoManual.filter(Char::isDigit)
                        if (parseBoleto(digits) == null) {
                            erroManual = "Confira o codigo. Ele deve ter 44, 47 ou 48 digitos."
                        } else {
                            mostrarDigitacao = false
                            onBoletoFound(digits)
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
    val boletoInfo = remember(codigo) { parseBoleto(codigo) }
    var fornecedor by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var vencimentoManual by remember { mutableStateOf("") }
    var erroVencimento by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
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
                color = Color(0xFF111827)
            )
        }

        if (boletoInfo == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
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
                    SelectionContainer {
                        Text(text = codigo, color = Color(0xFF111827))
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
                Text(
                    text = "Organizacao",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4B5563),
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = fornecedor,
                    onValueChange = { fornecedor = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fornecedor (opcional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = categoria,
                    onValueChange = { categoria = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Categoria") },
                    placeholder = { Text("Ex: Luz, Agua, Internet") },
                    singleLine = true
                )
            }
        }

        val precisaInformarVencimento = !isValidVencimento(boletoInfo.vencimento)
        if (precisaInformarVencimento) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
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
                    OutlinedTextField(
                        value = vencimentoManual,
                        onValueChange = {
                            vencimentoManual = it
                            erroVencimento = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Vencimento (dd/mm/aaaa)") },
                        singleLine = true,
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
                color = Color(0xFF374151)
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Codigo",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4B5563),
                    fontWeight = FontWeight.Bold
                )
                SelectionContainer {
                    Text(
                        text = codigo,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

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

                val salvou = ContaStorage.salvarBoleto(
                    context = context,
                    boleto = boletoParaSalvar,
                    fornecedor = fornecedor,
                    categoria = categoria.ifBlank { "Outros" }
                )
                val mensagem = if (salvou) {
                    "Conta salva com sucesso"
                } else {
                    "Essa conta ja estava salva"
                }
                Toast.makeText(context, mensagem, Toast.LENGTH_SHORT).show()
                onSaved()
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                color = Color(0xFF111827),
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
