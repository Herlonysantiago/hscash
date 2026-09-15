package hssolutions.hscash.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class HelpStep(
    val title: String,
    val description: String,
    val details: List<String>,
    val icon: ImageVector,
    val accent: Color
)

private val helpSteps = listOf(
    HelpStep(
        "Bem-vindo ao HS Cash",
        "Organize contas a pagar e a receber em um só lugar.",
        listOf("Acompanhe vencimentos", "Receba lembretes", "Mantenha boletos e comprovantes organizados"),
        Icons.Default.AccountBalanceWallet,
        Color(0xFF1565C0)
    ),
    HelpStep(
        "Entenda o Dashboard",
        "Veja rapidamente sua situação financeira no período selecionado.",
        listOf("Valores e quantidades a pagar e receber", "Contas vencidas do mês e anteriores", "Próximos vencimentos e saldo previsto"),
        Icons.Default.Dashboard,
        Color(0xFF0B6B2B)
    ),
    HelpStep(
        "Cadastre e importe contas",
        "Use cadastro manual, câmera, código de barras ou importe vários PDFs.",
        listOf("O código de barras é opcional no cadastro manual", "PDFs importados ficam guardados como boleto original", "Confira os dados encontrados antes de utilizar o código"),
        Icons.Default.CameraAlt,
        Color(0xFF7B1FA2)
    ),
    HelpStep(
        "Encontre e organize",
        "A tela Contas oferece busca, filtros, categorias e seleção múltipla.",
        listOf("Busque por fornecedor, banco, valor ou categoria", "Filtre por hoje, semana, mês, vencidas ou pagas", "Selecione várias contas para ações em lote"),
        Icons.Default.Search,
        Color(0xFF00695C)
    ),
    HelpStep(
        "Pagamento e comprovante",
        "Uma conta só fica paga depois da sua confirmação.",
        listOf("Abra a conta e toque em Marcar pago", "Anexe, abra, substitua ou exclua o comprovante", "O PDF original do boleto não é um comprovante"),
        Icons.Default.CheckCircle,
        Color(0xFF388E3C)
    ),
    HelpStep(
        "Recorrência e lembretes",
        "Automatize despesas frequentes e evite atrasos.",
        listOf("Escolha recorrência semanal, mensal ou anual", "Configure dias e horários das notificações", "Consulte todos os vencimentos no calendário"),
        Icons.Default.Notifications,
        Color(0xFFF57C00)
    ),
    HelpStep(
        "Relatórios, segurança e backup",
        "Proteja seus dados e acompanhe a evolução das contas.",
        listOf("Gere relatórios mensais em PDF", "Exporte e restaure backups", "Use senha, biometria e o widget da tela inicial"),
        Icons.Default.Backup,
        Color(0xFF455A64)
    ),
    HelpStep(
        "Precisa de ajuda?",
        "Você pode abrir este tutorial novamente pelo menu Mais.",
        listOf("Criado por HS Solutions", "Contato: herlonys97@gmail.com", "Sua opinião ajuda a melhorar o HS Cash"),
        Icons.Default.Email,
        Color(0xFF1565C0)
    )
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    val step = helpSteps[page]
    val isLast = page == helpSteps.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Guia do HS Cash", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF102A43))
            TextButton(onClick = onFinish) { Text("Fechar") }
        }

        Text("Passo ${page + 1} de ${helpSteps.size}", color = Color(0xFF64748B), style = MaterialTheme.typography.labelLarge)

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(88.dp).background(step.accent.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(step.icon, contentDescription = null, tint = step.accent, modifier = Modifier.size(46.dp))
                }
                Spacer(Modifier.size(24.dp))
                Text(step.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF111827), textAlign = TextAlign.Center)
                Text(step.description, modifier = Modifier.padding(top = 10.dp), color = Color(0xFF475569), textAlign = TextAlign.Center)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    step.details.forEach { detail ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 7.dp).size(7.dp).background(step.accent, CircleShape))
                            Text(detail, color = Color(0xFF334155), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (isLast) {
                    OutlinedButton(
                        onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:herlonys97@gmail.com"))) }
                        },
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Text(" Enviar e-mail")
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            helpSteps.indices.forEach { index ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (index == page) 10.dp else 7.dp)
                        .background(if (index == page) Color(0xFF1565C0) else Color(0xFFCBD5E1), CircleShape)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (page > 0) page-- else onFinish() },
                modifier = Modifier.weight(1f)
            ) { Text(if (page == 0) "Pular" else "Voltar") }
            Button(
                onClick = { if (isLast) onFinish() else page++ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) { Text(if (isLast) "Começar a usar" else "Avançar") }
        }
    }
}
