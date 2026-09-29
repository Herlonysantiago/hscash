package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun DashboardScreen(
    initialFilter: String? = null,
    onOpenToday: () -> Unit = {},
    onOpenSpaces: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val helpListState = rememberLazyListState()
    val helpTargets = rememberScreenHelpTargets()
    val activeSpace = remember { SpaceStorage.active(context) }
    var contas by remember { mutableStateOf(emptyList<ContaSalva>()) }
    var readAlertIds by remember { mutableStateOf(AppSettings.readAlertIds(context)) }
    var dashboardAllMonths by remember { mutableStateOf(false) }
    var dashboardMonth by remember { mutableStateOf(startOfDay().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    var selectedFilter by remember {
        mutableStateOf(initialFilter?.let { DashboardFilter.fromRouteValue(it) })
    }

    fun carregarContas() {
        scope.launch {
            contas = ContaStorage.listarContasAsync(context)
            readAlertIds = AppSettings.readAlertIds(context)
        }
    }

    LaunchedEffect(Unit) { carregarContas() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                carregarContas()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val resumo = remember(contas, dashboardMonth, dashboardAllMonths) {
        ContaResumo.from(
            contas = contas,
            month = dashboardMonth,
            allMonths = dashboardAllMonths
        )
    }
    val proximasContas = remember(contas, dashboardMonth, dashboardAllMonths) {
        val pagar = contas.filter { it.direcao == ContaDirecao.PAGAR }
        proximasContas(if (dashboardAllMonths) pagar else pagar.filter { it.isInMonth(dashboardMonth) })
    }
    val selectedAccounts = remember(contas, dashboardMonth, dashboardAllMonths, selectedFilter) {
        selectedFilter?.let { filterDashboardAccounts(contas, dashboardMonth, dashboardAllMonths, it) }.orEmpty()
    }
    val unreadAlertCount = remember(contas, readAlertIds) {
        buildFinancialAlerts(contas).count { it.id !in readAlertIds }
    }

    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
          state = helpListState,
          modifier = Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Abrir menu lateral", tint = MaterialTheme.colorScheme.primary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Visao geral das contas cadastradas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                onClick = onOpenSpaces,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colorFromHex(activeSpace.color).copy(alpha = 0.14f)),
                border = BorderStroke(1.dp, colorFromHex(activeSpace.color).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(spaceIcon(activeSpace.icon), contentDescription = null, tint = colorFromHex(activeSpace.color))
                    Column(Modifier.weight(1f)) {
                        Text(activeSpace.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(spaceTypeLabel(activeSpace.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Trocar", color = colorFromHex(activeSpace.color), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Card(
                onClick = onOpenToday,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Today, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Central Hoje", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Vencidas, vencendo hoje e proximos dias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Abrir", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            PeriodSelector(
                selectedMonth = dashboardMonth,
                allMonths = dashboardAllMonths,
                onPreviousMonth = {
                    dashboardMonth = dashboardMonth.copyWithMonthOffset(-1)
                    dashboardAllMonths = false
                    selectedFilter = null
                },
                onNextMonth = {
                    dashboardMonth = dashboardMonth.copyWithMonthOffset(1)
                    dashboardAllMonths = false
                    selectedFilter = null
                },
                onCurrentMonth = {
                    dashboardAllMonths = false
                    selectedFilter = null
                },
                onAllMonths = {
                    dashboardAllMonths = true
                    selectedFilter = null
                },
                monthRowModifier = Modifier.screenHelpTarget(helpTargets, "month_navigation"),
                optionsModifier = Modifier.screenHelpTarget(helpTargets, "period_options")
            )
        }

        if (selectedFilter != null) {
            item {
                DashboardAccountsListHeader(
                    title = selectedFilter!!.title,
                    count = selectedAccounts.size,
                    onBack = { selectedFilter = null }
                )
            }

            if (selectedAccounts.isEmpty()) {
                item {
                    EmptyFilteredCard(selectedFilter!!.emptyMessage)
                }
            } else {
                items(selectedAccounts, key = { it.id }) { conta ->
                    DashboardContaCard(conta)
                }
            }
            return@LazyColumn
        }

        item {
            Box(Modifier.screenHelpTarget(helpTargets, "total_card")) {
                TotalCard(
                    total = resumo.totalValor,
                    quantidade = resumo.totalContas,
                    proximoVencimento = resumo.proximoVencimento,
                    onClick = { selectedFilter = DashboardFilter.TOTAL_PAGAR }
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .screenHelpTarget(helpTargets, "financial_summary"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "A receber",
                    value = resumo.totalReceber,
                    quantity = resumo.totalReceberContas,
                    icon = Icons.Default.AccountBalanceWallet,
                    color = Color(0xFF0B6B2B),
                    modifier = Modifier.weight(1f),
                    onClick = { selectedFilter = DashboardFilter.A_RECEBER }
                )
                MetricCard(
                    title = "Saldo previsto",
                    value = resumo.saldoPrevisto,
                    icon = Icons.Default.AccountBalanceWallet,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .screenHelpTarget(helpTargets, "attention_summary"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Hoje",
                    value = resumo.hojeValor,
                    quantity = resumo.hoje,
                    icon = Icons.Default.Today,
                    color = Color(0xFFF57C00),
                    modifier = Modifier.weight(1f),
                    onClick = { selectedFilter = DashboardFilter.HOJE }
                )
                MetricCard(
                    title = "Todas vencidas",
                    value = resumo.vencidasValor,
                    quantity = resumo.vencidas,
                    icon = Icons.Default.EventBusy,
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f),
                    onClick = { selectedFilter = DashboardFilter.VENCIDAS }
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .screenHelpTarget(helpTargets, "payment_summary"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Pago no mes",
                    value = resumo.totalPagoMes,
                    quantity = resumo.totalPagoContas,
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF388E3C),
                    modifier = Modifier.weight(1f),
                    onClick = { selectedFilter = DashboardFilter.PAGO_MES }
                )
                MetricCard(
                    title = "No prazo",
                    value = resumo.noPrazo.toString(),
                    icon = Icons.Default.EventAvailable,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f),
                    onClick = { selectedFilter = DashboardFilter.NO_PRAZO }
                )
            }
        }

        item {
            SectionTitle("Situacao dos vencimentos")
        }

        item {
            Box(Modifier.screenHelpTarget(helpTargets, "due_current")) {
                StatusContaCard("Vencidas neste mes", resumo.vencidasNoMes, resumo.vencidasNoMesValor, "Atrasos do mes atual", Color(0xFFD32F2F), Icons.Default.ErrorOutline) { selectedFilter = DashboardFilter.VENCIDAS_MES }
            }
        }
        item {
            Box(Modifier.screenHelpTarget(helpTargets, "due_previous")) {
                StatusContaCard("Vencidas anteriores", resumo.vencidasAnteriores, resumo.vencidasAnterioresValor, "Pendencias de meses anteriores", Color(0xFF9B1C1C), Icons.Default.WarningAmber) { selectedFilter = DashboardFilter.VENCIDAS_ANTERIORES }
            }
        }
        item { StatusContaCard("Vencendo Hoje", resumo.hoje, resumo.hojeValor, "Contas para resolver hoje", Color(0xFFF57C00), Icons.Default.Today) { selectedFilter = DashboardFilter.HOJE } }
        item { StatusContaCard("Vence Amanha", resumo.amanha, resumo.amanhaValor, "Planejamento de curto prazo", Color(0xFFFBC02D), Icons.Default.WarningAmber) { selectedFilter = DashboardFilter.AMANHA } }
        item { StatusContaCard("Vence neste Mes", resumo.nesteMes, resumo.nesteMesValor, "Ainda dentro do mes atual", Color(0xFF7B1FA2), Icons.Default.CalendarMonth) { selectedFilter = DashboardFilter.NESTE_MES } }
        item { StatusContaCard("No Prazo", resumo.noPrazo, resumo.noPrazoValor, "Sem urgencia neste momento", Color(0xFF388E3C), Icons.Default.CheckCircle) { selectedFilter = DashboardFilter.NO_PRAZO } }

        item {
            SectionTitle("Proximas contas a pagar")
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
      Row(
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
      ) {
        BadgedBox(
            badge = {
                if (unreadAlertCount > 0) {
                    Badge {
                        Text(if (unreadAlertCount > 99) "99+" else unreadAlertCount.toString())
                    }
                }
            }
        ) {
            IconButton(onClick = onOpenAlerts) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = if (unreadAlertCount > 0) {
                        "$unreadAlertCount alertas novos"
                    } else {
                        "Abrir Central de Alertas"
                    },
                    tint = if (unreadAlertCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
        ScreenHelp(
          screenKey = "dashboard",
          steps = listOf(
              ScreenHelpStep("Mude o mês", "Use as setas < e > para voltar ou avançar um mês.", HelpFocus.TOP_ROW, "month_navigation"),
              ScreenHelpStep("Escolha o período", "Toque em Mês para ver apenas o período selecionado ou em Tudo para reunir todo o histórico.", HelpFocus.TOP_OPTIONS, "period_options"),
              ScreenHelpStep("Total a pagar", "Este resumo mostra o valor, a quantidade de contas abertas e o próximo vencimento. Toque nele para ver as contas.", HelpFocus.TOP, "total_card"),
              ScreenHelpStep("Receitas e saldo", "Estes dois cards mostram quanto você tem a receber e o saldo previsto depois de pagar as contas.", HelpFocus.TOP, "financial_summary"),
              ScreenHelpStep("Atenção do período", "Aqui aparecem as contas que vencem hoje e o total de contas que já estão vencidas.", HelpFocus.TOP, "attention_summary"),
              ScreenHelpStep("Pagamentos e prazo", "Estes cards mostram o que já foi pago no mês e quantas contas continuam dentro do prazo.", HelpFocus.TOP, "payment_summary"),
              ScreenHelpStep("Vencidas neste mês", "Este card reúne a quantidade e o valor das contas atrasadas no mês selecionado.", HelpFocus.TOP, "due_current"),
              ScreenHelpStep("Vencidas anteriores", "Este card separa as pendências de meses anteriores para que nenhum atraso fique escondido.", HelpFocus.TOP, "due_previous")
          ),
          targets = helpTargets,
          onStepChanged = { index ->
              selectedFilter = null
              scope.launch {
                  kotlinx.coroutines.yield()
                  helpListState.animateScrollToItem(listOf(2, 2, 3, 4, 5, 6, 8, 9)[index])
              }
          }
        )
      }
    }
}

@Composable
private fun TotalCard(total: String, quantidade: Int, proximoVencimento: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
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
                    text = "Total a pagar",
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
    quantity: Int? = null,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                quantity?.let { Text("$it conta(s)", fontWeight = FontWeight.Bold, color = color) }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (onClick == null) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(8.dp),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(8.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun PeriodSelector(
    selectedMonth: Calendar,
    allMonths: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onAllMonths: () -> Unit,
    monthRowModifier: Modifier = Modifier,
    optionsModifier: Modifier = Modifier
) {
    val monthLabel = remember(selectedMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("pt-BR"))
            .format(selectedMonth.time)
            .replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = monthRowModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Mes anterior",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (allMonths) "Todos os meses" else monthLabel,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onNextMonth) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "Proximo mes",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row(
                modifier = optionsModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !allMonths,
                    onClick = onCurrentMonth,
                    label = { Text("Mes") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1565C0),
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = allMonths,
                    onClick = onAllMonths,
                    label = { Text("Tudo") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1565C0),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun StatusContaCard(
    titulo: String,
    quantidade: Int,
    valor: String,
    descricao: String,
    cor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(imageVector = icon, contentDescription = null, tint = cor, modifier = Modifier.size(24.dp))
                    Text(quantidade.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cor)
                }
                Text(valor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = cor)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = descricao,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardAccountsListHeader(title: String, count: Int, onBack: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("$count conta(s) encontrada(s) nesta selecao", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onBack) {
                Text("Voltar ao dashboard")
            }
        }
    }
}

@Composable
private fun DashboardContaCard(conta: ContaSalva) {
    val isReceber = conta.direcao == ContaDirecao.RECEBER
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isReceber) Icons.Default.AccountBalanceWallet else Icons.Default.EventAvailable,
                contentDescription = null,
                tint = if (isReceber) Color(0xFF0B6B2B) else Color(0xFF1565C0),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conta.fornecedor.ifBlank { conta.banco },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${if (isReceber) "Receber" else "Pagar"} - ${statusLabel(conta)} - ${conta.vencimento}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = conta.valor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isReceber) Color(0xFF0B6B2B) else Color(0xFFD32F2F)
            )
        }
    }
}

@Composable
private fun EmptyFilteredCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ProximaContaCard(conta: ContaSalva) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Vencimento: ${conta.vencimento}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = "Nenhuma conta com vencimento futuro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

enum class DashboardFilter(val title: String, val emptyMessage: String) {
    TOTAL_PAGAR("Total a pagar", "Nenhuma conta a pagar neste periodo."),
    A_RECEBER("Contas a receber", "Nenhuma conta a receber neste periodo."),
    PAGO_MES("Pagas no mes", "Nenhuma conta paga neste periodo."),
    VENCIDAS("Todas as contas vencidas", "Nenhuma conta vencida."),
    VENCIDAS_MES("Vencidas neste mes", "Nenhuma conta vencida neste mes."),
    VENCIDAS_ANTERIORES("Vencidas anteriores", "Nenhuma conta vencida de meses anteriores."),
    HOJE("Contas para hoje", "Nenhuma conta vence hoje neste periodo."),
    AMANHA("Contas para amanha", "Nenhuma conta vence amanha neste periodo."),
    NESTE_MES("Contas que vencem neste mes", "Nenhuma conta restante neste mes."),
    NO_PRAZO("Contas no prazo", "Nenhuma conta no prazo neste periodo.");

    companion object {
        fun fromRouteValue(value: String): DashboardFilter? {
            return entries.firstOrNull { it.name == value }
        }
    }
}

internal data class ContaResumo(
    val totalContas: Int,
    val totalValor: String,
    val totalReceber: String,
    val totalReceberContas: Int,
    val saldoPrevisto: String,
    val totalPagoMes: String,
    val totalPagoContas: Int,
    val proximoVencimento: String,
    val vencidas: Int,
    val vencidasValor: String,
    val vencidasNoMes: Int,
    val vencidasNoMesValor: String,
    val vencidasAnteriores: Int,
    val vencidasAnterioresValor: String,
    val hoje: Int,
    val hojeValor: String,
    val amanha: Int,
    val amanhaValor: String,
    val nesteMes: Int,
    val nesteMesValor: String,
    val noPrazo: Int,
    val noPrazoValor: String
) {
    companion object {
        fun from(
            contas: List<ContaSalva>,
            month: Calendar,
            allMonths: Boolean,
            referenceToday: Calendar = startOfDay()
        ): ContaResumo {
            val hoje = (referenceToday.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val amanha = (hoje.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val contasPeriodo = if (allMonths) contas else contas.filter { it.isInMonth(month) }
            val pendentes = contasPeriodo.filter { it.status != ContaStatus.PAGO }
            val pendentesPagar = pendentes.filter { it.direcao == ContaDirecao.PAGAR }
            val pendentesReceber = pendentes.filter { it.direcao == ContaDirecao.RECEBER }
            val todasVencidas = contas.filter { conta ->
                conta.direcao == ContaDirecao.PAGAR &&
                    conta.status != ContaStatus.PAGO &&
                    parseVencimento(conta.vencimento)?.before(hoje) == true
            }
            val vencidasNoMes = todasVencidas.count { conta ->
                parseVencimento(conta.vencimento)?.let { sameMonth(it, hoje) } == true
            }
            val vencidasAnteriores = todasVencidas.size - vencidasNoMes
            var venceHoje = 0
            var venceAmanha = 0
            var venceNesteMes = 0
            var noPrazo = 0

            pendentesPagar.forEach { conta ->
                val vencimento = parseVencimento(conta.vencimento)
                when {
                    vencimento == null -> noPrazo += 1
                    vencimento.before(hoje) -> Unit
                    sameDay(vencimento, hoje) -> venceHoje += 1
                    sameDay(vencimento, amanha) -> venceAmanha += 1
                    sameMonth(vencimento, hoje) -> venceNesteMes += 1
                    else -> noPrazo += 1
                }
            }

            val contasVencidasNoMes = todasVencidas.filter { parseVencimento(it.vencimento)?.let { data -> sameMonth(data, hoje) } == true }
            val contasVencidasAnteriores = todasVencidas - contasVencidasNoMes.toSet()
            val contasHoje = pendentesPagar.filter { parseVencimento(it.vencimento)?.let { data -> sameDay(data, hoje) } == true }
            val contasAmanha = pendentesPagar.filter { parseVencimento(it.vencimento)?.let { data -> sameDay(data, amanha) } == true }
            val contasNesteMes = pendentesPagar.filter { conta ->
                parseVencimento(conta.vencimento)?.let { data -> sameMonth(data, hoje) && data.after(amanha) } == true
            }
            val contasNoPrazo = pendentesPagar.filter { conta ->
                val data = parseVencimento(conta.vencimento)
                data == null || (data.after(amanha) && !sameMonth(data, hoje))
            }

            val contasPagasMes = contas
                .filter { it.direcao == ContaDirecao.PAGAR && it.status == ContaStatus.PAGO && if (allMonths) true else paidInMonth(it, month) }
            val pagoMes = contasPagasMes.sumOf { parseValorCentavos(it.valor) }
            val totalPagar = pendentesPagar.sumOf { parseValorCentavos(it.valor) }
            val totalReceber = pendentesReceber.sumOf { parseValorCentavos(it.valor) }

            return ContaResumo(
                totalContas = pendentesPagar.size,
                totalValor = formatMoney(totalPagar),
                totalReceber = formatMoney(totalReceber),
                totalReceberContas = pendentesReceber.size,
                saldoPrevisto = formatMoney(totalReceber - totalPagar),
                totalPagoMes = formatMoney(pagoMes),
                totalPagoContas = contasPagasMes.size,
                proximoVencimento = nextDueDate(pendentesPagar),
                vencidas = todasVencidas.size,
                vencidasValor = formatMoney(todasVencidas.sumOf { parseValorCentavos(it.valor) }),
                vencidasNoMes = vencidasNoMes,
                vencidasNoMesValor = formatMoney(contasVencidasNoMes.sumOf { parseValorCentavos(it.valor) }),
                vencidasAnteriores = vencidasAnteriores,
                vencidasAnterioresValor = formatMoney(contasVencidasAnteriores.sumOf { parseValorCentavos(it.valor) }),
                hoje = venceHoje,
                hojeValor = formatMoney(contasHoje.sumOf { parseValorCentavos(it.valor) }),
                amanha = venceAmanha,
                amanhaValor = formatMoney(contasAmanha.sumOf { parseValorCentavos(it.valor) }),
                nesteMes = venceNesteMes,
                nesteMesValor = formatMoney(contasNesteMes.sumOf { parseValorCentavos(it.valor) }),
                noPrazo = noPrazo,
                noPrazoValor = formatMoney(contasNoPrazo.sumOf { parseValorCentavos(it.valor) })
            )
        }
    }
}

internal fun filterDashboardAccounts(
    contas: List<ContaSalva>,
    month: Calendar,
    allMonths: Boolean,
    filter: DashboardFilter,
    referenceToday: Calendar = startOfDay()
): List<ContaSalva> {
    val hoje = (referenceToday.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val amanha = (hoje.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val contasPeriodo = if (allMonths) contas else contas.filter { it.isInMonth(month) }

    val contasDoFiltro = when (filter) {
        DashboardFilter.VENCIDAS,
        DashboardFilter.VENCIDAS_MES,
        DashboardFilter.VENCIDAS_ANTERIORES -> contas
        else -> contasPeriodo
    }

    return contasDoFiltro
        .filter { conta ->
            val vencimento = parseVencimento(conta.vencimento)
            when (filter) {
                DashboardFilter.TOTAL_PAGAR -> conta.direcao == ContaDirecao.PAGAR && conta.status != ContaStatus.PAGO
                DashboardFilter.A_RECEBER -> conta.direcao == ContaDirecao.RECEBER && conta.status != ContaStatus.PAGO
                DashboardFilter.PAGO_MES -> conta.direcao == ContaDirecao.PAGAR && conta.status == ContaStatus.PAGO && if (allMonths) true else paidInMonth(conta, month)
                DashboardFilter.VENCIDAS -> conta.direcao == ContaDirecao.PAGAR && conta.status != ContaStatus.PAGO && vencimento?.before(hoje) == true
                DashboardFilter.VENCIDAS_MES -> conta.direcao == ContaDirecao.PAGAR && conta.status != ContaStatus.PAGO &&
                    vencimento?.before(hoje) == true && sameMonth(vencimento, hoje)
                DashboardFilter.VENCIDAS_ANTERIORES -> conta.direcao == ContaDirecao.PAGAR && conta.status != ContaStatus.PAGO &&
                    vencimento?.before(hoje) == true && !sameMonth(vencimento, hoje)
                DashboardFilter.HOJE -> conta.direcao == ContaDirecao.PAGAR && conta.status != ContaStatus.PAGO && vencimento?.let { sameDay(it, hoje) } == true
                DashboardFilter.AMANHA -> conta.direcao == ContaDirecao.PAGAR && conta.status != ContaStatus.PAGO && vencimento?.let { sameDay(it, amanha) } == true
                DashboardFilter.NESTE_MES -> conta.direcao == ContaDirecao.PAGAR &&
                    conta.status != ContaStatus.PAGO &&
                    vencimento != null &&
                    sameMonth(vencimento, hoje) &&
                    vencimento.after(amanha)
                DashboardFilter.NO_PRAZO -> conta.direcao == ContaDirecao.PAGAR &&
                    conta.status != ContaStatus.PAGO &&
                    (vencimento == null || vencimento.after(amanha) && !sameMonth(vencimento, hoje))
            }
        }
        .sortedWith(compareBy<ContaSalva> { parseVencimento(it.vencimento)?.timeInMillis ?: Long.MAX_VALUE }.thenBy { conta -> conta.fornecedor.ifBlank { conta.banco } })
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
    return parseContaDate(value)
}

private fun sameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun sameMonth(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
}

private fun paidInMonth(conta: ContaSalva, month: Calendar): Boolean {
    val paidAt = conta.pagoEm ?: return false
    val paidCalendar = Calendar.getInstance().apply { timeInMillis = paidAt }
    return sameMonth(paidCalendar, month)
}

private fun ContaSalva.isInMonth(month: Calendar): Boolean {
    val date = parseVencimento(vencimento) ?: return false
    return sameMonth(date, month)
}

private fun Calendar.copyWithMonthOffset(offset: Int): Calendar {
    return (clone() as Calendar).apply {
        add(Calendar.MONTH, offset)
        set(Calendar.DAY_OF_MONTH, 1)
    }
}

private fun parseValorCentavos(value: String): Long {
    return value.filter(Char::isDigit).toLongOrNull() ?: 0L
}

private fun formatMoney(cents: Long): String {
    val value = BigDecimal(cents).movePointLeft(2)
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)
}
