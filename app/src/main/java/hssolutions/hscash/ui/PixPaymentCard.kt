package hssolutions.hscash.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

@Composable
fun PixPaymentCard(
    payload: String,
    actionLabel: String = "Já paguei",
    showAction: Boolean = true,
    onAction: () -> Unit = {}
) {
    val context = LocalContext.current
    val info = remember(payload) { PixUtils.parse(payload) }
    if (!info.valid) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Pagamento por Pix", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            PixQrCode(payload)
            info.recipient?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            info.key?.let { Text("Chave: $it", style = MaterialTheme.typography.bodySmall) }
            info.amountInCents?.let { Text(PixUtils.moneyText(it), fontWeight = FontWeight.Bold, color = Color(0xFF0B6B2B)) }
            info.txId?.let { Text("TxID: $it", style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = { ClipboardHelper.copy(context, "Pix Copia e Cola", payload) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Copiar código Pix") }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Pix")
                            putExtra(Intent.EXTRA_TEXT, payload)
                        }
                        context.startActivity(Intent.createChooser(share, "Compartilhar Pix"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Compartilhar") }
                if (showAction) {
                    OutlinedButton(onClick = onAction, modifier = Modifier.weight(1f)) { Text(actionLabel) }
                }
            }
            Text(
                "Copiar ou compartilhar não confirma o pagamento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PixQrCode(payload: String, modifier: Modifier = Modifier.size(196.dp)) {
    val image = remember(payload) {
        runCatching {
            val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 720, 720)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            bitmap.asImageBitmap()
        }.getOrNull()
    }
    image?.let { Image(bitmap = it, contentDescription = "QR Code Pix", modifier = modifier) }
}
