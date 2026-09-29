package hssolutions.hscash.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

internal enum class ReportView(val label: String) {
    PAID("Pagos"),
    PENDING("Pendentes"),
    EXPECTED("Previstos"),
    RECEIVED("Recebidos")
}

enum class ReportSection(val label: String) {
    OVERVIEW("Visão geral"),
    CATEGORY_EVOLUTION("Por categoria")
}

internal enum class EvolutionDateBasis(val label: String) {
    DUE_DATE("Vencimento"),
    PAYMENT_DATE("Pagamento")
}

internal data class CategoryMonthEvolution(
    val month: Calendar,
    val cents: Long,
    val count: Int,
    val pendingCents: Long = 0L,
    val pendingCount: Int = 0,
    val paidAccounts: List<ContaSalva> = emptyList(),
    val pendingAccounts: List<ContaSalva> = emptyList()
)

internal data class CategoryReportItem(
    val category: String,
    val cents: Long,
    val count: Int,
    val percentage: Float,
    val accounts: List<ContaSalva>
)

internal fun categoryReport(accounts: List<ContaSalva>): List<CategoryReportItem> {
    val total = accounts.sumOf { parseReportCents(it.valor) }
    return accounts
        .groupBy { it.categoria.ifBlank { "Outros" } }
        .map { (category, categoryAccounts) ->
            val cents = categoryAccounts.sumOf { parseReportCents(it.valor) }
            CategoryReportItem(
                category = category,
                cents = cents,
                count = categoryAccounts.size,
                percentage = if (total > 0L) cents * 100f / total else 0f,
                accounts = categoryAccounts
            )
        }
        .sortedByDescending(CategoryReportItem::cents)
}

@Composable
fun RelatorioScreen(
    onBack: () -> Unit = {},
    initialSection: ReportSection = ReportSection.OVERVIEW
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val contaList = rememberContaListState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val helpTargets = rememberScreenHelpTargets()
    val contas = contaList.contas
    var selectedMonth by remember { mutableStateOf(startOfCurrentMonth()) }
    var reportView by remember { mutableStateOf(ReportView.PAID) }
    var selectedCategory by remember { mutableStateOf<CategoryReportItem?>(null) }
    var reportSection by remember { mutableStateOf(initialSection) }
    var evolutionPeriod by remember { mutableStateOf(6) }
    var evolutionDateBasis by remember { mutableStateOf(EvolutionDateBasis.DUE_DATE) }
    var includePendingEvolution by remember { mutableStateOf(false) }
    val paidAccounts = contas.filter { it.direcao == ContaDirecao.PAGAR && it.status == ContaStatus.PAGO }
    val evolutionSourceAccounts = contas.filter { it.direcao == ContaDirecao.PAGAR }
    val evolutionCategories = evolutionSourceAccounts
        .map { it.categoria.ifBlank { "Outros" } }
        .groupBy { it.trim().lowercase(Locale.forLanguageTag("pt-BR")) }
        .values
        .sortedByDescending { it.size }
        .map { it.first().trim() }
    var evolutionCategory by remember(evolutionCategories) {
        mutableStateOf(evolutionCategories.firstOrNull() ?: "Outros")
    }
    val evolutionSubcategories = evolutionSourceAccounts
        .filter { it.categoria.ifBlank { "Outros" } == evolutionCategory }
        .mapNotNull { it.subcategoria?.takeIf(String::isNotBlank) }
        .groupBy { it.trim().lowercase(Locale.forLanguageTag("pt-BR")) }
        .values
        .sortedByDescending { it.size }
        .map { it.first().trim() }
    var evolutionSubcategory by remember(evolutionCategory) { mutableStateOf<String?>(null) }
    var showSubcategoryPicker by remember { mutableStateOf(false) }
    var subcategorySearch by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var categorySearch by remember { mutableStateOf("") }
    var selectedEvolutionMonth by remember { mutableStateOf<CategoryMonthEvolution?>(null) }
    val evolution = remember(evolutionSourceAccounts, evolutionCategory, evolutionSubcategory, evolutionPeriod, evolutionDateBasis, includePendingEvolution) {
        categoryEvolution(
            accounts = evolutionSourceAccounts,
            category = evolutionCategory,
            months = evolutionPeriod,
            subcategory = evolutionSubcategory,
            dateBasis = evolutionDateBasis,
            includePending = includePendingEvolution
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) contaList.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val monthLabel = remember(selectedMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("pt-BR"))
            .format(selectedMonth.time)
            .replaceFirstChar { it.uppercase() }
    }
    val paidThisMonth = contas.filter {
        it.direcao == ContaDirecao.PAGAR &&
            it.status == ContaStatus.PAGO &&
            paidInMonth(it, selectedMonth)
    }
    val pendingThisMonth = contas.filter {
        it.direcao == ContaDirecao.PAGAR &&
            it.status != ContaStatus.PAGO &&
            dueInMonth(it, selectedMonth)
    }
    val receivedThisMonth = contas.filter {
        it.direcao == ContaDirecao.RECEBER &&
            it.status == ContaStatus.PAGO &&
            paidInMonth(it, selectedMonth)
    }
    val receivableThisMonth = contas.filter {
        it.direcao == ContaDirecao.RECEBER &&
            it.status != ContaStatus.PAGO &&
            dueInMonth(it, selectedMonth)
    }
    val previousMonth = remember(selectedMonth) { selectedMonth.copyWithMonthOffset(-1) }
    val paidPreviousMonth = contas.filter {
        it.direcao == ContaDirecao.PAGAR &&
            it.status == ContaStatus.PAGO &&
            paidInMonth(it, previousMonth)
    }
    val paidTotal = paidThisMonth.sumOf { parseCents(it.valor) }
    val pendingTotal = pendingThisMonth.sumOf { parseCents(it.valor) }
    val receivableTotal = receivableThisMonth.sumOf { parseCents(it.valor) }
    val receivedTotal = receivedThisMonth.sumOf { parseCents(it.valor) }
    val previousPaidTotal = paidPreviousMonth.sumOf { parseCents(it.valor) }
    val expectedThisMonth = contas.filter {
        it.direcao == ContaDirecao.PAGAR && dueInMonth(it, selectedMonth)
    }
    val categoryAccounts = when (reportView) {
        ReportView.PAID -> paidThisMonth
        ReportView.PENDING -> pendingThisMonth
        ReportView.EXPECTED -> expectedThisMonth
        ReportView.RECEIVED -> receivedThisMonth
    }
    val byCategory = categoryReport(categoryAccounts)
    val byPaymentMethod = paidThisMonth.groupBy { it.formaPagamento?.takeIf(String::isNotBlank) ?: "Não informado" }
        .mapValues { entry -> entry.value.sumOf { parseCents(it.valor) } }
        .toList()
        .sortedByDescending { it.second }
    val pixPaid = paidThisMonth.filter { it.formaPagamento.equals("PIX", ignoreCase = true) }
    val pixReceived = receivedThisMonth.filter { it.formaPagamento.equals("PIX", ignoreCase = true) }
    val pixPending = pendingThisMonth.filter { !it.pixCopiaCola.isNullOrBlank() || !it.pixChave.isNullOrBlank() }
    val pixScheduled = pixPending.count { it.pixAgendamentoStatus == PixAgendamentoStatus.AGENDADO }
    val pixFailed = pixPending.count { it.pixAgendamentoStatus == PixAgendamentoStatus.FALHOU }

    Box(modifier = Modifier.fillMaxSize()) {
      LazyColumn(
          state = listState,
          modifier = Modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 48.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.onSurface)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (reportSection == ReportSection.OVERVIEW) "Relatório mensal" else "Evolução por categoria",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (reportSection == ReportSection.OVERVIEW) "Resumo financeiro do mês selecionado"
                        else "Acompanhe como seus gastos mudam com o tempo",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { ReportPdfHelper.shareMonthlyReport(context, contas, selectedMonth) },
                    modifier = Modifier.screenHelpTarget(helpTargets, "report_export")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Exportar PDF", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportSection.entries) { section ->
                    FilterChip(
                        selected = reportSection == section,
                        onClick = { reportSection = section },
                        label = { Text(section.label) }
                    )
                }
            }
        }

        if (reportSection == ReportSection.OVERVIEW) {

        item {
            MonthSelector(
                label = monthLabel,
                onPrevious = { selectedMonth = selectedMonth.copyWithMonthOffset(-1) },
                onNext = { selectedMonth = selectedMonth.copyWithMonthOffset(1) },
                modifier = Modifier.screenHelpTarget(helpTargets, "report_month")
            )
        }

        item {
            SummaryCard("Total pago", formatMoney(paidTotal), Color(0xFF0B6B2B), Modifier.screenHelpTarget(helpTargets, "report_paid"))
        }

        item {
            SummaryCard("Total pendente", formatMoney(pendingTotal), Color(0xFFB71C1C), Modifier.screenHelpTarget(helpTargets, "report_pending"))
        }

        item {
            SummaryCard("A receber", formatMoney(receivableTotal), Color(0xFF1565C0), Modifier.screenHelpTarget(helpTargets, "report_receivable"))
        }

        item {
            SummaryCard("Saldo projetado", formatMoney(receivableTotal - pendingTotal), Color(0xFF5B3FA3))
        }

        item {
            ComparisonCard(
                currentPaid = paidTotal,
                previousPaid = previousPaidTotal,
                received = receivedTotal,
                modifier = Modifier.screenHelpTarget(helpTargets, "report_comparison")
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Resumo Pix", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${pixPaid.size} pagamento(s) • ${formatMoney(pixPaid.sumOf { parseCents(it.valor) })}")
                    Text("${pixReceived.size} recebimento(s) • ${formatMoney(pixReceived.sumOf { parseCents(it.valor) })}")
                    Text("${pixPending.size} Pix pendente(s) • ${formatMoney(pixPending.sumOf { parseCents(it.valor) })}")
                    if (pixScheduled > 0 || pixFailed > 0) {
                        Text("$pixScheduled agendado(s) • $pixFailed com falha", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Text("Analise por categoria", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ReportView.entries) { view ->
                    FilterChip(
                        selected = reportView == view,
                        onClick = { reportView = view },
                        label = { Text(view.label) }
                    )
                }
            }
        }

        if (byCategory.isNotEmpty()) {
            item {
                CategoryDonutCard(
                    title = reportView.label,
                    items = byCategory,
                    modifier = Modifier.screenHelpTarget(helpTargets, "category_breakdown")
                )
            }
        }

        if (byCategory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.screenHelpTarget(helpTargets, "category_breakdown"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Nenhum pagamento registrado em $monthLabel.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(byCategory) { index, item ->
                val identity = categoryIdentity(item.category)
                Card(
                    onClick = { selectedCategory = item },
                    modifier = if (index == 0 && byCategory.isEmpty()) Modifier.screenHelpTarget(helpTargets, "category_breakdown") else Modifier,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(identity.icon, contentDescription = null, tint = identity.color)
                        Column(Modifier.weight(1f)) {
                            Text(identity.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${item.count} conta(s) • ${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", item.percentage)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(formatMoney(item.cents), fontWeight = FontWeight.Bold, color = identity.color)
                    }
                }
            }
        }

        item {
            Text("Formas de pagamento", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        if (byPaymentMethod.isEmpty()) {
            item { Text("Nenhuma forma de pagamento registrada neste mês.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            itemsIndexed(byPaymentMethod) { _, (method, cents) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(method, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(formatMoney(cents), fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                }
            }
        }
        } else {
            item {
                Text(
                    "Evolução dos gastos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Contas pagas agrupadas pelo mês de vencimento.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(3, 6, 12)) { period ->
                        FilterChip(
                            selected = evolutionPeriod == period,
                            onClick = { evolutionPeriod = period },
                            label = { Text("$period meses") }
                        )
                    }
                }
            }
            item {
                Text("Organizar por", fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EvolutionDateBasis.entries) { basis ->
                        FilterChip(
                            selected = evolutionDateBasis == basis,
                            onClick = { evolutionDateBasis = basis },
                            label = { Text(basis.label) }
                        )
                    }
                }
                Text(
                    if (evolutionDateBasis == EvolutionDateBasis.DUE_DATE) {
                        "Competência: cada conta aparece no mês em que venceu."
                    } else {
                        "Caixa: cada conta aparece no mês em que foi paga."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Incluir não pagas", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Exibe as pendências no gráfico pelo vencimento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = includePendingEvolution, onCheckedChange = { includePendingEvolution = it })
                }
            }
            if (evolutionCategories.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            "Ainda não há pagamentos confirmados para calcular a evolução.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Text("Categoria", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (evolutionCategories.size <= 7) {
                            items(evolutionCategories) { category ->
                                val identity = categoryIdentity(category)
                                FilterChip(
                                    selected = evolutionCategory.equals(category, ignoreCase = true),
                                    onClick = { evolutionCategory = category },
                                    label = { Text(identity.label) },
                                    leadingIcon = { Icon(identity.icon, null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        } else {
                            item {
                                val identity = categoryIdentity(evolutionCategory)
                                FilterChip(
                                    selected = true,
                                    onClick = {
                                        categorySearch = ""
                                        showCategoryPicker = true
                                    },
                                    label = { Text(identity.label) },
                                    leadingIcon = { Icon(identity.icon, null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                            item {
                                TextButton(onClick = {
                                    categorySearch = ""
                                    showCategoryPicker = true
                                }) { Text("Trocar categoria") }
                            }
                        }
                    }
                }
                if (evolutionSubcategories.isNotEmpty()) {
                    item {
                        Text("Subcategoria", fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = evolutionSubcategory == null,
                                    onClick = { evolutionSubcategory = null },
                                    label = { Text("Todas") }
                                )
                            }
                            if (evolutionSubcategories.size <= 6) {
                                items(evolutionSubcategories) { subcategory ->
                                    FilterChip(
                                        selected = evolutionSubcategory.equals(subcategory, ignoreCase = true),
                                        onClick = { evolutionSubcategory = subcategory },
                                        label = { Text(subcategory) }
                                    )
                                }
                            } else {
                                item {
                                    FilterChip(
                                        selected = evolutionSubcategory != null,
                                        onClick = {
                                            subcategorySearch = ""
                                            showSubcategoryPicker = true
                                        },
                                        label = { Text(evolutionSubcategory ?: "Selecionar subcategoria") }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    CategoryEvolutionSummary(
                        category = evolutionCategory,
                        evolution = evolution,
                        subcategory = evolutionSubcategory,
                        showPending = includePendingEvolution
                    )
                }
                item {
                    CategoryEvolutionChart(
                        category = evolutionCategory,
                        evolution = evolution,
                        dateBasis = evolutionDateBasis,
                        showPending = includePendingEvolution,
                        onMonthClick = { selectedEvolutionMonth = it }
                    )
                }
            }
        }
      }
      ScreenHelp(
          screenKey = "relatorio",
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp),
          steps = if (reportSection == ReportSection.OVERVIEW) {
              listOf(
                  ScreenHelpStep("Exportar relatório", "Gere e compartilhe um PDF com os totais do mês selecionado.", HelpFocus.TOP, "report_export"),
                  ScreenHelpStep("Escolha o mês", "Use as setas para consultar os pagamentos e pendências de outro mês.", HelpFocus.TOP, "report_month"),
                  ScreenHelpStep("Total pago", "Mostra o valor das contas efetivamente pagas durante o mês.", HelpFocus.TOP, "report_paid"),
                  ScreenHelpStep("Total pendente", "Mostra o valor das contas abertas com vencimento no mês.", HelpFocus.CENTER, "report_pending"),
                  ScreenHelpStep("Valores a receber", "Mostra quanto ainda está previsto para entrar no mês selecionado.", HelpFocus.CENTER, "report_receivable"),
                  ScreenHelpStep("Comparação mensal", "Compare os pagamentos do mês atual com o mês anterior e acompanhe a variação.", HelpFocus.CENTER, "report_comparison"),
                  ScreenHelpStep("Pagamentos por categoria", "Confira como o total pago está distribuído entre as categorias cadastradas.", HelpFocus.BOTTOM, "category_breakdown")
              )
          } else {
              listOf(
                  ScreenHelpStep("Período e categoria", "Escolha 3, 6 ou 12 meses e selecione a categoria que deseja acompanhar.", HelpFocus.TOP),
                  ScreenHelpStep("Evolução mensal", "Veja total, média, variação e valores agrupados pela data de vencimento.", HelpFocus.CENTER)
              )
          },
          targets = helpTargets,
          onStepChanged = { index ->
              scope.launch {
                  val positions = if (reportSection == ReportSection.OVERVIEW) {
                      listOf(0, 2, 3, 4, 5, 7, 11)
                  } else {
                      listOf(2, 5)
                  }
                  listState.animateScrollToItem(positions[index])
              }
          }
      )
    }

    selectedCategory?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedCategory = null },
            title = { Text(item.category) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${formatMoney(item.cents)} • ${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", item.percentage)}% do total")
                    val subcategories = item.accounts
                        .groupBy { it.subcategoria?.takeIf(String::isNotBlank) ?: "Sem subcategoria" }
                        .mapValues { (_, accounts) -> accounts.sumOf { parseReportCents(it.valor) } }
                        .toList()
                        .sortedByDescending { it.second }
                    val hasNamedSubcategory = subcategories.any { it.first != "Sem subcategoria" }
                    if (hasNamedSubcategory) {
                        Text("Subcategorias", fontWeight = FontWeight.Bold)
                        subcategories.take(6).forEach { (name, cents) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, modifier = Modifier.weight(1f))
                                Text(formatMoney(cents), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Text(
                            "Nenhuma subcategoria cadastrada nesta categoria.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("Contas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    item.accounts.take(8).forEach { account ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(account.fornecedor.ifBlank { account.banco }, modifier = Modifier.weight(1f))
                            Text(account.valor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (item.accounts.size > 8) Text("E mais ${item.accounts.size - 8} conta(s).")
                }
            },
            confirmButton = { TextButton(onClick = { selectedCategory = null }) { Text("Fechar") } }
        )
    }

    if (showSubcategoryPicker) {
        val filtered = evolutionSubcategories.filter {
            subcategorySearch.isBlank() || it.contains(subcategorySearch.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showSubcategoryPicker = false },
            title = { Text("Selecionar subcategoria") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subcategorySearch,
                        onValueChange = { subcategorySearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar") },
                        singleLine = true,
                        colors = hsCashOutlinedTextFieldColors()
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered) { subcategory ->
                            TextButton(
                                onClick = {
                                    evolutionSubcategory = subcategory
                                    showSubcategoryPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(subcategory, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubcategoryPicker = false }) { Text("Fechar") }
            }
        )
    }

    if (showCategoryPicker) {
        val filtered = evolutionCategories.filter {
            categorySearch.isBlank() || it.contains(categorySearch.trim(), ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Selecionar categoria") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = categorySearch,
                        onValueChange = { categorySearch = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar categoria") },
                        singleLine = true,
                        colors = hsCashOutlinedTextFieldColors()
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 390.dp)) {
                        items(filtered) { category ->
                            val identity = categoryIdentity(category)
                            TextButton(
                                onClick = {
                                    evolutionCategory = category
                                    evolutionSubcategory = null
                                    showCategoryPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(identity.icon, null, tint = identity.color, modifier = Modifier.size(20.dp))
                                Text(identity.label, modifier = Modifier.fillMaxWidth().padding(start = 10.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryPicker = false }) { Text("Fechar") } }
        )
    }

    selectedEvolutionMonth?.let { month ->
        val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("pt-BR"))
            .format(month.month.time).replaceFirstChar { it.uppercase() }
        val detailAccounts = month.paidAccounts + month.pendingAccounts
        AlertDialog(
            onDismissRequest = { selectedEvolutionMonth = null },
            title = { Text(monthLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (evolutionDateBasis == EvolutionDateBasis.PAYMENT_DATE) {
                            "Pagas pela data de pagamento; pendentes pelo vencimento."
                        } else {
                            "Contas organizadas pela data de vencimento."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detailAccounts, key = { it.id }) { account ->
                            val isPaid = account.status == ContaStatus.PAGO
                            val date = if (isPaid && evolutionDateBasis == EvolutionDateBasis.PAYMENT_DATE) {
                                account.pagoEm?.let { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(it) }
                                    ?: account.vencimento
                            } else account.vencimento
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(account.fornecedor.ifBlank { account.banco }, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${if (isPaid) "Pago" else "Não pago"} • $date",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(account.valor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedEvolutionMonth = null }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun CategoryDonutCard(
    title: String,
    items: List<CategoryReportItem>,
    modifier: Modifier = Modifier
) {
    val total = items.sumOf(CategoryReportItem::cents)
    val chartItems = if (items.size <= 6) {
        items
    } else {
        val main = items.take(5)
        val remaining = items.drop(5)
        main + CategoryReportItem(
            category = "Outras",
            cents = remaining.sumOf { it.cents },
            count = remaining.sumOf { it.count },
            percentage = remaining.sumOf { it.percentage.toDouble() }.toFloat(),
            accounts = remaining.flatMap { it.accounts }
        )
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("$title por categoria", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(142.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        chartItems.forEach { item ->
                            val sweep = item.percentage * 3.6f
                            drawArc(
                                color = categoryIdentity(item.category).color,
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMoney(total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    chartItems.forEach { item ->
                        val identity = categoryIdentity(item.category)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(identity.icon, null, tint = identity.color, modifier = Modifier.size(17.dp))
                            Text(identity.label, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            Text("${item.percentage.toInt()}%", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (items.size > 6) {
                Text(
                    "As 5 maiores categorias são exibidas separadamente; outras ${items.size - 5} foram agrupadas em Outras.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonthSelector(label: String, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Mes anterior")
            }
            Text(
                text = label,
                modifier = Modifier.padding(top = 12.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Proximo mes")
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.82f))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun ComparisonCard(
    currentPaid: Long,
    previousPaid: Long,
    received: Long,
    modifier: Modifier = Modifier
) {
    val variation = when {
        previousPaid == 0L && currentPaid == 0L -> "Sem alteração"
        previousPaid == 0L -> "Novo movimento neste mês"
        else -> {
            val percent = paymentVariationPercent(currentPaid, previousPaid) ?: 0.0
            val signal = if (percent > 0) "+" else ""
            "$signal${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", percent)}% em relação ao mês anterior"
        }
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Comparação mensal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Pago neste mês: ${formatMoney(currentPaid)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Pago no mês anterior: ${formatMoney(previousPaid)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Recebido neste mês: ${formatMoney(received)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(variation, fontWeight = FontWeight.SemiBold, color = Color(0xFF1565C0))
        }
    }
}

@Composable
private fun CategoryEvolutionSummary(
    category: String,
    evolution: List<CategoryMonthEvolution>,
    subcategory: String? = null,
    showPending: Boolean = false
) {
    val identity = categoryIdentity(category)
    val total = evolution.sumOf { it.cents }
    val pendingTotal = evolution.sumOf { it.pendingCents }
    val monthsWithValues = evolution.count { it.cents > 0L }.coerceAtLeast(1)
    val average = total / monthsWithValues
    val current = evolution.lastOrNull()?.cents ?: 0L
    val previous = evolution.dropLast(1).lastOrNull()?.cents ?: 0L
    val variation = paymentVariationPercent(current, previous)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, identity.color.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(identity.icon, null, tint = identity.color)
                Text(
                    if (subcategory == null) identity.label else "${identity.label} • $subcategory",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("Total no período: ${formatMoney(total)}", fontWeight = FontWeight.Bold)
            if (showPending) {
                Text("Ainda não pago: ${formatMoney(pendingTotal)}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
            Text("Média mensal: ${formatMoney(average)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    variation == null && current == 0L -> "Sem gastos nos dois últimos meses"
                    variation == null -> "Primeiro gasto registrado na comparação"
                    else -> {
                        val signal = if (variation > 0) "+" else ""
                        "$signal${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", variation)}% em relação ao mês anterior"
                    }
                },
                color = identity.color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CategoryEvolutionChart(
    category: String,
    evolution: List<CategoryMonthEvolution>,
    dateBasis: EvolutionDateBasis,
    showPending: Boolean,
    onMonthClick: (CategoryMonthEvolution) -> Unit
) {
    val identity = categoryIdentity(category)
    val maximum = evolution.maxOfOrNull { maxOf(it.cents, it.pendingCents) } ?: 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (dateBasis == EvolutionDateBasis.DUE_DATE) "Gastos por vencimento" else "Gastos por pagamento",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            if (showPending) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("■ Pago", color = identity.color, style = MaterialTheme.typography.bodySmall)
                    Text("■ Não pago", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(evolution, key = { it.month.timeInMillis }) { item ->
                    val ratio = if (maximum > 0L) item.cents.toFloat() / maximum else 0f
                    val pendingRatio = if (maximum > 0L) item.pendingCents.toFloat() / maximum else 0f
                    val label = SimpleDateFormat("MMM", Locale.forLanguageTag("pt-BR"))
                        .format(item.month.time)
                        .replaceFirstChar { it.uppercase() }
                    Column(
                        modifier = Modifier.width(58.dp).clickable { onMonthClick(item) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            compactMoney(item.cents),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Row(
                            modifier = Modifier.height(124.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                Modifier.width(if (showPending) 13.dp else 30.dp)
                                    .height(if (item.cents == 0L) 3.dp else (116f * ratio.coerceAtLeast(0.08f)).dp)
                                    .background(if (item.cents == 0L) MaterialTheme.colorScheme.surfaceVariant else identity.color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            )
                            if (showPending) {
                                Box(
                                    Modifier.width(13.dp)
                                        .height(if (item.pendingCents == 0L) 3.dp else (116f * pendingRatio.coerceAtLeast(0.08f)).dp)
                                        .background(if (item.pendingCents == 0L) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.error, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                )
                            }
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (showPending) "${item.count}/${item.pendingCount}" else "${item.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                (if (showPending) "A quantidade abaixo mostra pagas/não pagas." else "O número abaixo de cada mês indica a quantidade de contas.") +
                    " Toque em um mês para ver as datas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun compactMoney(cents: Long): String {
    val value = cents / 100.0
    return when {
        value >= 1_000_000 -> String.format(Locale.forLanguageTag("pt-BR"), "R$ %.1f mi", value / 1_000_000)
        value >= 1_000 -> String.format(Locale.forLanguageTag("pt-BR"), "R$ %.1f mil", value / 1_000)
        else -> String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f", value)
    }
}

internal fun categoryEvolution(
    accounts: List<ContaSalva>,
    category: String,
    months: Int,
    reference: Calendar = Calendar.getInstance(),
    subcategory: String? = null,
    dateBasis: EvolutionDateBasis = EvolutionDateBasis.DUE_DATE,
    includePending: Boolean = false
): List<CategoryMonthEvolution> {
    return (months - 1 downTo 0).map { offset ->
        val month = (reference.clone() as Calendar).apply {
            add(Calendar.MONTH, -offset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val categoryAccounts = accounts.filter {
            it.categoria.ifBlank { "Outros" } == category &&
                (subcategory == null || it.subcategoria.equals(subcategory, ignoreCase = true))
        }
        val matching = categoryAccounts.filter {
            it.status == ContaStatus.PAGO &&
                if (dateBasis == EvolutionDateBasis.DUE_DATE) dueInMonth(it, month) else paidInMonth(it, month)
        }
        val pending = if (includePending) categoryAccounts.filter {
            it.status != ContaStatus.PAGO && dueInMonth(it, month)
        } else emptyList()
        CategoryMonthEvolution(
            month = month,
            cents = matching.sumOf { parseReportCents(it.valor) },
            count = matching.size,
            pendingCents = pending.sumOf { parseReportCents(it.valor) },
            pendingCount = pending.size,
            paidAccounts = matching,
            pendingAccounts = pending
        )
    }
}

internal fun paymentVariationPercent(currentPaid: Long, previousPaid: Long): Double? {
    if (previousPaid == 0L) return null
    return (currentPaid - previousPaid) * 100.0 / previousPaid
}

private fun parseCents(value: String): Long = parseReportCents(value)

internal fun parseReportCents(value: String): Long = value.filter(Char::isDigit).toLongOrNull() ?: 0L

private fun startOfCurrentMonth(): Calendar {
    return Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun Calendar.copyWithMonthOffset(offset: Int): Calendar {
    return (clone() as Calendar).apply {
        add(Calendar.MONTH, offset)
        set(Calendar.DAY_OF_MONTH, 1)
    }
}

private fun paidInMonth(conta: ContaSalva, month: Calendar): Boolean {
    val paidAt = conta.pagoEm
    val paidDate = if (paidAt != null) {
        Calendar.getInstance().apply { timeInMillis = paidAt }
    } else {
        parseContaDate(conta.vencimento)
    } ?: return false
    return sameMonth(paidDate, month)
}

private fun dueInMonth(conta: ContaSalva, month: Calendar): Boolean {
    val dueDate = parseContaDate(conta.vencimento) ?: return false
    return sameMonth(dueDate, month)
}

private fun sameMonth(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
}

private fun formatMoney(cents: Long): String {
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal(cents).movePointLeft(2))
}
