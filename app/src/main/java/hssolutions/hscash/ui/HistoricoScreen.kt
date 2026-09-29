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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoricoScreen(
    onContaClick: (String) -> Unit = {},
    onOpenCategoryEvolution: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val contaList = rememberContaListState()
    val contas = contaList.contas
    val helpTargets = rememberScreenHelpTargets()
    var activities by remember { mutableStateOf<List<ActivityLogItem>>(emptyList()) }

    fun refreshActivities() {
        scope.launch {
            activities = ActivityLogStorage.listarRecentesAsync(context, limit = 20)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                contaList.refresh()
                refreshActivities()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        refreshActivities()
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        TextButton(
            onClick = onOpenCategoryEvolution,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("Ver evolução por categoria")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().screenHelpTarget(helpTargets, "history_list"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activities.isNotEmpty()) {
                item {
                    Text(
                        text = "Movimentações recentes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(activities, key = { it.id }) { activity ->
                    ActivityLogCard(activity = activity, onContaClick = onContaClick)
                }
                item {
                    Text(
                        text = "Contas concluídas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
            }

            if (pagas.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum registro concluido ainda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(pagas, key = { it.id }) { conta ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onContaClick(conta.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append("${if (conta.direcao == ContaDirecao.RECEBER) "Recebido" else "Pago"} - ${conta.categoria}")
                                    conta.subcategoria?.takeIf(String::isNotBlank)?.let { append(" • $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun ActivityLogCard(
    activity: ActivityLogItem,
    onContaClick: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (activity.contaId != null) {
                    Modifier.clickable { onContaClick(activity.contaId) }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = activity.title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            activity.details?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = buildString {
                    append(dateFormat.format(Date(activity.createdAt)))
                    activity.actorName?.let { append(" • $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
