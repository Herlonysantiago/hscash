package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoricoScreen(onContaClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val contaList = rememberContaListState()
    val contas = contaList.contas
    val helpTargets = rememberScreenHelpTargets()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                contaList.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pagas = contas.filter { it.status == ContaStatus.PAGO }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "history_header"),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Historico", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            ScreenHelp(
                screenKey = "historico",
                steps = listOf(
                    ScreenHelpStep("Contas concluídas", "Aqui ficam pagamentos e recebimentos que já foram confirmados.", HelpFocus.TOP, "history_header"),
                    ScreenHelpStep("Consulte os detalhes", "Toque em um registro para abrir seus dados, comprovantes ou retorná-lo para pendente.", HelpFocus.CENTER, "history_list")
                ),
                targets = helpTargets
            )
        }
        Text(
            text = "Pagamentos e recebimentos concluidos",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6B7280),
            modifier = Modifier.padding(top = 2.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().screenHelpTarget(helpTargets, "history_list"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pagas.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum registro concluido ainda.",
                        color = Color(0xFF4B5563)
                    )
                }
            }

            items(pagas, key = { it.id }) { conta ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onContaClick(conta.id) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conta.fornecedor.ifBlank { conta.banco },
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = "${if (conta.direcao == ContaDirecao.RECEBER) "Recebido" else "Pago"} - ${conta.categoria}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280)
                            )
                            val dataConfirmacao = conta.pagoEm?.let {
                                SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(it))
                            } ?: conta.vencimento
                            Text(
                                text = buildString {
                                    append("Confirmado em $dataConfirmacao")
                                    conta.formaPagamento?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280)
                            )
                        }
                        Text(
                            text = conta.valor,
                            color = if (conta.direcao == ContaDirecao.RECEBER) Color(0xFF0B6B2B) else Color(0xFF388E3C),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
