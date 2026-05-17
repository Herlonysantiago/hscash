package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PaymentCopyCard(
    linhaDigitavel: String,
    codigoBarras: String
) {
    val context = LocalContext.current
    val linhaDigits = linhaDigitavel.filter(Char::isDigit)
    val barrasDigits = codigoBarras.filter(Char::isDigit)
    val linhaValida = linhaDigits.length == 47 || linhaDigits.length == 48
    val barrasValido = barrasDigits.length == 44
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)),
        border = BorderStroke(1.dp, Color(0xFF1565C0).copy(alpha = 0.32f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Linha digitavel para o banco",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF0D47A1),
                fontWeight = FontWeight.Bold
            )
            SelectionContainer {
                Text(
                    text = formatPaymentCode(linhaDigitavel),
                    color = Color(0xFF0D47A1),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = "${linhaDigits.length} digitos",
                color = if (linhaValida) {
                    Color(0xFF0B6B2B)
                } else {
                    Color(0xFFD32F2F)
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = {
                    ClipboardHelper.copy(context, "Linha digitavel", linhaDigitavel)
                },
                enabled = linhaValida,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Copiar linha para o banco", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    ClipboardHelper.copy(context, "Codigo de barras", codigoBarras)
                },
                enabled = barrasValido,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Copiar barras 44 digitos", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatPaymentCode(value: String): String {
    val digits = value.filter(Char::isDigit)
    return when (digits.length) {
        47 -> buildString {
            append(digits.substring(0, 5))
            append(".")
            append(digits.substring(5, 10))
            append(" ")
            append(digits.substring(10, 15))
            append(".")
            append(digits.substring(15, 21))
            append(" ")
            append(digits.substring(21, 26))
            append(".")
            append(digits.substring(26, 32))
            append(" ")
            append(digits.substring(32, 33))
            append(" ")
            append(digits.substring(33, 47))
        }
        48 -> digits.chunked(12).joinToString(" ")
        else -> digits.chunked(5).joinToString(" ")
    }
}
