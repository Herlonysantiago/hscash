package hssolutions.hscash.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.Normalizer
import java.util.Locale

data class CategoryIdentity(
    val label: String,
    val icon: ImageVector,
    val color: Color
)

fun categoryIdentity(category: String): CategoryIdentity {
    val label = category.ifBlank { "Outros" }
    val key = Normalizer.normalize(label, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.forLanguageTag("pt-BR"))
    val (icon, color) = when {
        "agua" in key -> Icons.Default.WaterDrop to Color(0xFF1976D2)
        "luz" in key || "energia" in key -> Icons.Default.Bolt to Color(0xFFF9A825)
        "internet" in key -> Icons.Default.Wifi to Color(0xFF0288D1)
        "telefone" in key -> Icons.Default.PhoneAndroid to Color(0xFF5E35B1)
        "aluguel recebido" in key -> Icons.Default.Home to Color(0xFF00897B)
        "aluguel" in key -> Icons.Default.Home to Color(0xFF7B1FA2)
        "condominio" in key -> Icons.Default.Apartment to Color(0xFF546E7A)
        "mercado" in key -> Icons.Default.LocalGroceryStore to Color(0xFF2E7D32)
        "transporte" in key -> Icons.Default.DirectionsCar to Color(0xFFEF6C00)
        "saude" in key -> Icons.Default.HealthAndSafety to Color(0xFFD32F2F)
        "educacao" in key -> Icons.Default.School to Color(0xFF3949AB)
        "imposto" in key -> Icons.Default.AccountBalance to Color(0xFF6D4C41)
        "seguro" in key -> Icons.Default.Security to Color(0xFF455A64)
        "assinatura" in key -> Icons.Default.Subscriptions to Color(0xFF8E24AA)
        "lazer" in key -> Icons.Default.Celebration to Color(0xFFE91E63)
        "cartao" in key -> Icons.Default.CreditCard to Color(0xFF1565C0)
        "emprestimo" in key -> Icons.Default.Payments to Color(0xFFAD1457)
        "fornecedor" in key || "estoque" in key || "mercadoria" in key -> Icons.Default.Storefront to Color(0xFF00695C)
        "funcionario" in key || "folha" in key || "pro-labore" in key -> Icons.Default.Person to Color(0xFF5D4037)
        "contabilidade" in key -> Icons.Default.AccountBalance to Color(0xFF455A64)
        "marketing" in key -> Icons.Default.Celebration to Color(0xFFD81B60)
        "sistema" in key || "software" in key -> Icons.Default.Subscriptions to Color(0xFF6A1B9A)
        "manutencao" in key || "terceirizado" in key -> Icons.Default.Work to Color(0xFF546E7A)
        "boleto" in key -> Icons.Default.ReceiptLong to Color(0xFF0D47A1)
        "cliente" in key -> Icons.Default.Person to Color(0xFF00796B)
        "servico" in key -> Icons.Default.Work to Color(0xFF512DA8)
        "venda" in key -> Icons.Default.Storefront to Color(0xFF00838F)
        "salario" in key || "reembolso" in key -> Icons.Default.Payments to Color(0xFF2E7D32)
        else -> Icons.Default.MoreHoriz to Color(0xFF607D8B)
    }
    return CategoryIdentity(label, icon, color)
}
