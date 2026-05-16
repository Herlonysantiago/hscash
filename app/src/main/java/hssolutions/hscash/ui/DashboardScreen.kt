package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var contas by remember { mutableStateOf(ContaStorage.listarContas(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                contas = ContaStorage.listarContas(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val resumo = remember(contas) { ContaResumo.from(contas) }
    val proximasContas = remember(contas) { proximasContas(contas) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = "Visao geral das contas cadastradas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )
            }
        }

        item {
            TotalCard(
                total = resumo.totalValor,
                quantidade = resumo.totalContas,
                proximoVencimento = resumo.proximoVencimento
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Hoje",
                    value = resumo.hoje.toString(),
                    icon = Icons.Default.Today,
                    color = Color(0xFFF57C00),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Vencidas",
                    value = resumo.vencidas.toString(),
                    icon = Icons.Default.EventBusy,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Pago no mes",
                    value = resumo.totalPagoMes,
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF388E3C),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "No prazo",
                    value = resumo.noPrazo.toString(),
                    icon = Icons.Default.EventAvailable,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionTitle("Situacao dos vencimentos")
        }

        item { StatusContaCard("Vencidas", resumo.vencidas.toString(), "Exigem atencao imediata", Color(0xFFD32F2F), Icons.Default.ErrorOutline) }
        item { StatusContaCard("Vencendo Hoje", resumo.hoje.toString(), "Contas para resolver hoje", Color(0xFFF57C00), Icons.Default.Today) }
        item { StatusContaCard("Vence Amanha", resumo.amanha.toString(), "Planejamento de curto prazo", Color(0xFFFBC02D), Icons.Default.WarningAmber) }
        item { StatusContaCard("Vence neste Mes", resumo.nesteMes.toString(), "Ainda dentro do mes atual", Color(0xFF7B1FA2), Icons.Default.CalendarMonth) }
        item { StatusContaCard("No Prazo", resumo.noPrazo.toString(), "Sem urgencia neste momento", Color(0xFF388E3C), Icons.Default.CheckCircle) }

        item {
            SectionTitle("Proximas contas")
        }

        if (proximasContas.isEmpty()) {
            item {
                EmptyStateCard()
            }
        } else {
            items(proximasContas, key = { it.id }) { conta ->
                ProximaContaCard(conta)
            }
        }
    }
}

@Composable
private fun TotalCard(total: String, quantidade: Int, proximoVencimento: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF102A43)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Total em aberto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = total,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TotalMeta("Contas", quantidade.toString())
                TotalMeta("Proximo vencimento", proximoVencimento)
            }
        }
    }
}

@Composable
private fun TotalMeta(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.72f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF111827),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun StatusContaCard(
    titulo: String,
    quantidade: String,
    descricao: String,
    cor: Color,
    icon: ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(46.dp)
                    .background(cor, RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = descricao,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
            Text(
                text = quantidade,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = cor
            )
        }
    }
}

@Composable
private fun ProximaContaCard(conta: ContaSalva) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.EventAvailable,
                contentDescription = null,
                tint = Color(0xFF1565C0),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conta.banco,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = "Vencimento: ${conta.vencimento}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
            Text(
                text = conta.valor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F)
            )
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "Nenhuma conta com vencimento futuro.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6B7280),
            modifier = Modifier.padding(16.dp)
        )
    }
}

private data class ContaResumo(
    val totalContas: Int,
    val totalValor: String,
    val totalPagoMes: String,
    val proximoVencimento: String,
    val vencidas: Int,
    val hoje: Int,
    val amanha: Int,
    val nesteMes: Int,
    val noPrazo: Int
) {
    companion object {
        fun from(contas: List<ContaSalva>): ContaResumo {
            val hoje = startOfDay()
            val amanha = startOfDay().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val pendentes = contas.filter { it.status != ContaStatus.PAGO }
            var vencidas = 0
            var venceHoje = 0
            var venceAmanha = 0
            var venceNesteMes = 0
            var noPrazo = 0

            pendentes.forEach { conta ->
                val vencimento = parseVencimento(conta.vencimento)
                when {
                    vencimento == null -> noPrazo += 1
                    vencimento.before(hoje) -> vencidas += 1
                    sameDay(vencimento, hoje) -> venceHoje += 1
                    sameDay(vencimento, amanha) -> venceAmanha += 1
                    sameMonth(vencimento, hoje) -> venceNesteMes += 1
                    else -> noPrazo += 1
                }
            }

            val pagoMes = contas
                .filter { it.status == ContaStatus.PAGO && paidThisMonth(it, hoje) }
                .sumOf { parseValorCentavos(it.valor) }

            return ContaResumo(
                totalContas = pendentes.size,
                totalValor = formatMoney(pendentes.sumOf { parseValorCentavos(it.valor) }),
                totalPagoMes = formatMoney(pagoMes),
                proximoVencimento = nextDueDate(pendentes),
                vencidas = vencidas,
                hoje = venceHoje,
                amanha = venceAmanha,
                nesteMes = venceNesteMes,
                noPrazo = noPrazo
            )
        }
    }
}

private fun proximasContas(contas: List<ContaSalva>): List<ContaSalva> {
    val hoje = startOfDay()
    return contas
        .filter { it.status != ContaStatus.PAGO }
        .mapNotNull { conta ->
            val vencimento = parseVencimento(conta.vencimento) ?: return@mapNotNull null
            if (vencimento.before(hoje)) return@mapNotNull null
            conta to vencimento
        }
        .sortedBy { it.second.timeInMillis }
        .take(3)
        .map { it.first }
}

private fun nextDueDate(contas: List<ContaSalva>): String {
    val next = proximasContas(contas).firstOrNull()
    return next?.vencimento ?: "Sem data"
}

private fun startOfDay(): Calendar {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun parseVencimento(value: String): Calendar? {
    return try {
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).parse(value)
            ?: return null
        Calendar.getInstance().apply { time = date }
    } catch (_: Exception) {
        null
    }
}

private fun sameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun sameMonth(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
}

private fun paidThisMonth(conta: ContaSalva, today: Calendar): Boolean {
    val paidAt = conta.pagoEm ?: return false
    val paidCalendar = Calendar.getInstance().apply { timeInMillis = paidAt }
    return sameMonth(paidCalendar, today)
}

private fun parseValorCentavos(value: String): Long {
    return value.filter(Char::isDigit).toLongOrNull() ?: 0L
}

private fun formatMoney(cents: Long): String {
    val value = BigDecimal(cents).movePointLeft(2)
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)
}
