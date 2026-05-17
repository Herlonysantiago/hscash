package hssolutions.hscash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF102A43))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        IconButton(onClick = onFinish) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
        }
        Text(
            text = "HS Cash",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        OnboardingItem("1. Escaneie ou digite o boleto")
        OnboardingItem("2. Confira valor, banco e vencimento")
        OnboardingItem("3. Salve, acompanhe e receba lembretes")
        OnboardingItem("4. Marque como pago e guarde o comprovante")
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Comecar")
        }
    }
}

@Composable
private fun OnboardingItem(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.9f),
        style = MaterialTheme.typography.titleMedium
    )
}
