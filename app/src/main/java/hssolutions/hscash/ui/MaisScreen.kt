package hssolutions.hscash.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@Composable
fun MaisScreen(
    onOpenCalendar: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenClients: () -> Unit,
    onOpenCharges: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val helpTargets = rememberScreenHelpTargets()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Mais", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            ScreenHelp(
                screenKey = "mais",
                steps = listOf(
                    ScreenHelpStep("Clientes", "Cadastre clientes e consulte o histórico das cobranças vinculadas a eles.", HelpFocus.TOP, "more_clients"),
                    ScreenHelpStep("Relatório mensal", "Consulte os totais do mês por categoria e gere um relatório em PDF.", HelpFocus.CENTER, "more_report"),
                    ScreenHelpStep("Configurações", "Ajuste backups, widget, proteção do aplicativo e lembretes.", HelpFocus.CENTER, "more_settings"),
                    ScreenHelpStep("Ajuda permanente", "Abra este guia completo novamente sempre que precisar.", HelpFocus.BOTTOM, "more_help")
                ),
                targets = helpTargets,
                onStepChanged = { index ->
                    scope.launch {
                        val fraction = listOf(0f, 0.45f, 0.7f, 1f)[index]
                        scrollState.animateScrollTo((scrollState.maxValue * fraction).toInt())
                    }
                }
            )
        }
        MenuCard("Clientes", "Cadastro e historico de cobrancas", Icons.Default.Info, modifier = Modifier.screenHelpTarget(helpTargets, "more_clients"), onClick = onOpenClients)
        MenuCard("Cobrancas", "Recebimentos em aberto para cobrar", Icons.Default.Notifications, onClick = onOpenCharges)
        MenuCard("Lixeira", "Restaurar contas removidas", Icons.Default.Backup, onClick = onOpenTrash)
        MenuCard("Calendario", "Visualizar vencimentos por dia", Icons.Default.CalendarMonth, onClick = onOpenCalendar)
        MenuCard("Relatorio mensal", "Totais pagos e exportacao em PDF", Icons.Default.ShowChart, modifier = Modifier.screenHelpTarget(helpTargets, "more_report"), onClick = onOpenReport)
        MenuCard("Configuracoes", "Backup, seguranca, lembretes e privacidade", Icons.Default.Lock, modifier = Modifier.screenHelpTarget(helpTargets, "more_settings"), onClick = onOpenSettings)
        MenuCard("Ajuda e tutorial", "Conheça todas as funções do HS Cash", Icons.Default.Info, modifier = Modifier.screenHelpTarget(helpTargets, "more_help"), onClick = onOpenOnboarding)
    }
}

@Composable
fun ConfiguracoesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val helpTargets = rememberScreenHelpTargets()
    var reminders by remember { mutableStateOf(AppSettings.reminderDays(context)) }
    var todaySummary by remember { mutableStateOf(AppSettings.isTodaySummaryEnabled(context)) }
    var tomorrowSummary by remember { mutableStateOf(AppSettings.isTomorrowSummaryEnabled(context)) }
    var overdueSummary by remember { mutableStateOf(AppSettings.isOverdueSummaryEnabled(context)) }
    var overdueSummaryHours by remember { mutableStateOf(AppSettings.overdueSummaryHoursText(context)) }
    var todaySummaryHours by remember { mutableStateOf(AppSettings.todaySummaryHoursText(context)) }
    var tomorrowSummaryHours by remember { mutableStateOf(AppSettings.tomorrowSummaryHoursText(context)) }
    var autoLocalBackup by remember { mutableStateOf(AppSettings.isAutoLocalBackupEnabled(context)) }
    var localBackupLocation by remember { mutableStateOf(BackupHelper.localBackupLocationLabel(context)) }
    var appLockEnabled by remember { mutableStateOf(AppSettings.isAppLockEnabled(context)) }
    var biometricUnlock by remember { mutableStateOf(AppSettings.isBiometricUnlockEnabled(context)) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var notificationTestMessage by remember { mutableStateOf<String?>(null) }
    val biometricAvailable = remember { BiometricAuthHelper.canUseBiometric(context) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) BackupHelper.importBackup(context, uri)
    }
    val backupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            AppSettings.setLocalBackupFolderUri(context, uri)
            localBackupLocation = BackupHelper.localBackupLocationLabel(context)
            BackupHelper.createLocalBackup(context)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color(0xFF102A43))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Configuracoes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                Text("Backup, seguranca e lembretes", color = Color(0xFF6B7280))
            }
            ScreenHelp(
                screenKey = "configuracoes",
                steps = listOf(
                    ScreenHelpStep("Exportar backup", "Gere uma cópia completa das contas e dos anexos para guardar ou compartilhar.", HelpFocus.TOP, "settings_export"),
                    ScreenHelpStep("Importar backup", "Restaure um backup ZIP atual ou um arquivo JSON de versões antigas.", HelpFocus.TOP, "settings_import"),
                    ScreenHelpStep("Widget", "Adicione ou atualize o resumo do HS Cash na tela inicial do celular.", HelpFocus.TOP, "settings_widget"),
                    ScreenHelpStep("Backup automático", "Escolha uma pasta e mantenha uma cópia atualizada sempre que os dados mudarem.", HelpFocus.CENTER, "settings_auto_backup"),
                    ScreenHelpStep("Proteção do aplicativo", "Defina uma senha e, quando disponível, use a biometria para proteger seus dados.", HelpFocus.CENTER, "settings_security"),
                    ScreenHelpStep("Lembretes", "Escolha os dias, horários e resumos que serão enviados nas notificações.", HelpFocus.BOTTOM, "settings_reminders")
                ),
                targets = helpTargets,
                onStepChanged = { index ->
                    scope.launch {
                        val fraction = listOf(0f, 0f, 0.08f, 0.3f, 0.62f, 1f)[index]
                        scrollState.animateScrollTo((scrollState.maxValue * fraction).toInt())
                    }
                }
            )
        }
        MenuCard("Exportar backup", "Gerar backup completo com anexos", Icons.Default.Backup, modifier = Modifier.screenHelpTarget(helpTargets, "settings_export")) {
            BackupHelper.shareBackup(context)
        }
        MenuCard("Importar backup", "Restaurar backup ZIP ou JSON antigo", Icons.Default.FileUpload, modifier = Modifier.screenHelpTarget(helpTargets, "settings_import")) {
            importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
        }
        MenuCard("Adicionar widget a tela inicial", "Mostra o resumo das contas; toque para adicionar ou atualizar", Icons.Default.Refresh, modifier = Modifier.screenHelpTarget(helpTargets, "settings_widget")) {
            HsCashWidgetProvider.refresh(context)
            notificationTestMessage = if (HsCashWidgetProvider.requestAddToHomeScreen(context)) {
                "Confirme no Android para adicionar o widget a tela inicial."
            } else {
                "Widget atualizado. Para adiciona-lo, mantenha pressionada a tela inicial e abra Widgets."
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "settings_auto_backup"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummarySwitchRow(
                    title = "Backup automatico local",
                    subtitle = "Cria backup completo sempre que os dados mudam.",
                    checked = autoLocalBackup,
                    onCheckedChange = { checked ->
                        autoLocalBackup = checked
                        AppSettings.setAutoLocalBackupEnabled(context, checked)
                    }
                )
                Text("Destino: $localBackupLocation", color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Para maior seguranca, mantenha tambem uma copia em local seguro, como nuvem, pendrive ou outro aparelho.",
                    color = Color(0xFFB45309),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { backupFolderLauncher.launch(null) }) {
                        Text("Escolher pasta")
                    }
                    TextButton(onClick = { BackupHelper.createLocalBackup(context) }) {
                        Text("Atualizar backup")
                    }
                }
            }
        }
        MenuCard("Sobre", "Informacoes do app e da HS Solutions", Icons.Default.Info) {
            showAbout = true
        }
        MenuCard("Politica de privacidade", "Como seus dados sao usados no app", Icons.Default.Info) {
            showPrivacyPolicy = true
        }

        Card(
            modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "settings_security"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummarySwitchRow(
                    title = "Protecao por senha",
                    subtitle = if (appLockEnabled) "Exige senha ao abrir o app." else "Bloqueia o acesso ao HS Cash.",
                    checked = appLockEnabled,
                    icon = Icons.Default.Lock,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (AppSettings.hasAppLockPassword(context)) {
                                AppSettings.setAppLockEnabled(context, true)
                                appLockEnabled = true
                            } else {
                                showPasswordDialog = true
                            }
                        } else {
                            AppSettings.setAppLockEnabled(context, false)
                            appLockEnabled = false
                            biometricUnlock = false
                        }
                    }
                )
                if (appLockEnabled) {
                    SummarySwitchRow(
                        title = "Usar biometria",
                        subtitle = if (biometricAvailable) "Digital ou biometria configurada no aparelho." else "Biometria indisponivel neste aparelho.",
                        checked = biometricUnlock && biometricAvailable,
                        enabled = biometricAvailable,
                        icon = Icons.Default.Fingerprint,
                        onCheckedChange = { checked ->
                            if (!biometricAvailable) return@SummarySwitchRow
                            AppSettings.setBiometricUnlockEnabled(context, checked)
                            biometricUnlock = checked
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showPasswordDialog = true }) {
                        Text(if (AppSettings.hasAppLockPassword(context)) "Alterar senha" else "Definir senha")
                    }
                    if (AppSettings.hasAppLockPassword(context)) {
                        TextButton(
                            onClick = {
                                AppSettings.clearAppLock(context)
                                appLockEnabled = false
                                biometricUnlock = false
                            }
                        ) {
                            Text("Remover senha")
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().screenHelpTarget(helpTargets, "settings_reminders"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Lembretes", fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                Text("Escolha quando receber alertas de vencimento.", color = Color(0xFF6B7280))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 1, 0).forEach { day ->
                        val label = if (day == 0) "No dia" else "${day}d antes"
                        FilterChip(
                            selected = reminders.contains(day),
                            onClick = {
                                reminders = if (reminders.contains(day)) reminders - day else reminders + day
                                AppSettings.setReminderDays(context, reminders)
                            },
                            label = { Text(label) },
                            leadingIcon = null
                        )
                    }
                }
                SummarySwitchRow(
                    title = "Resumo de vencidas",
                    subtitle = "Contas atrasadas e total vencido.",
                    checked = overdueSummary,
                    onCheckedChange = { checked ->
                        if (checked) requestNotificationsIfNeeded()
                        overdueSummary = checked
                        AppSettings.setOverdueSummaryEnabled(context, checked)
                    }
                )
                SummaryHoursField(
                    value = overdueSummaryHours,
                    enabled = overdueSummary,
                    onValueChange = { overdueSummaryHours = it }
                )
                SummarySwitchRow(
                    title = "Resumo de hoje",
                    subtitle = "Contas que vencem no dia.",
                    checked = todaySummary,
                    onCheckedChange = { checked ->
                        if (checked) requestNotificationsIfNeeded()
                        todaySummary = checked
                        AppSettings.setTodaySummaryEnabled(context, checked)
                    }
                )
                SummaryHoursField(
                    value = todaySummaryHours,
                    enabled = todaySummary,
                    onValueChange = { todaySummaryHours = it }
                )
                SummarySwitchRow(
                    title = "Resumo de amanha",
                    subtitle = "Contas do dia seguinte.",
                    checked = tomorrowSummary,
                    onCheckedChange = { checked ->
                        if (checked) requestNotificationsIfNeeded()
                        tomorrowSummary = checked
                        AppSettings.setTomorrowSummaryEnabled(context, checked)
                    }
                )
                SummaryHoursField(
                    value = tomorrowSummaryHours,
                    enabled = tomorrowSummary,
                    onValueChange = { tomorrowSummaryHours = it }
                )
                TextButton(
                    onClick = {
                        AppSettings.setOverdueSummaryHours(context, overdueSummaryHours)
                        AppSettings.setTodaySummaryHours(context, todaySummaryHours)
                        AppSettings.setTomorrowSummaryHours(context, tomorrowSummaryHours)
                        NotificationScheduler.resetSummaryShownState(context)
                        overdueSummaryHours = AppSettings.overdueSummaryHoursText(context)
                        todaySummaryHours = AppSettings.todaySummaryHoursText(context)
                        tomorrowSummaryHours = AppSettings.tomorrowSummaryHoursText(context)
                        notificationTestMessage = "Horarios salvos."
                    }
                ) {
                    Text("Salvar horarios")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            requestNotificationsIfNeeded()
                            val shown = NotificationScheduler.showTestNotification(context)
                            notificationTestMessage = if (shown) {
                                "Notificacao de teste enviada."
                            } else {
                                "Permita notificacoes para testar."
                            }
                        }
                    ) {
                        Text("Testar notificacao agora")
                    }
                }
                notificationTestMessage?.let {
                    Text(
                        text = it,
                        color = Color(0xFF6B7280),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showPasswordDialog) {
        SetPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onSave = { password, question, answer ->
                AppSettings.setAppLockPassword(context, password, question, answer)
                appLockEnabled = true
                biometricUnlock = AppSettings.isBiometricUnlockEnabled(context)
                showPasswordDialog = false
            }
        )
    }

    if (showAbout) {
        InfoDialog(
            title = "Sobre o HS Cash",
            body = ABOUT_TEXT,
            onDismiss = { showAbout = false }
        )
    }

    if (showPrivacyPolicy) {
        InfoDialog(
            title = "Politica de privacidade",
            body = PRIVACY_TEXT,
            onDismiss = { showPrivacyPolicy = false }
        )
    }
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(body, color = Color(0xFF374151), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@Composable
private fun SetPasswordDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var securityQuestion by remember { mutableStateOf(AppSettings.securityQuestion(context)) }
    var securityAnswer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Senha de acesso", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Use pelo menos 4 caracteres. Essa senha sera pedida ao abrir o app.",
                    color = Color(0xFF6B7280),
                    style = MaterialTheme.typography.bodySmall
                )
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nova senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = hsCashOutlinedTextFieldColors()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmar senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    colors = hsCashOutlinedTextFieldColors()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = securityQuestion,
                    onValueChange = {
                        securityQuestion = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pergunta de seguranca") },
                    placeholder = { Text("Ex: Nome da minha primeira escola") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = securityAnswer,
                    onValueChange = {
                        securityAnswer = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Resposta de seguranca") },
                    singleLine = true,
                    colors = hsCashOutlinedTextFieldColors()
                )
                error?.let {
                    Text(it, color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        password.length < 4 -> error = "A senha precisa ter pelo menos 4 caracteres."
                        password != confirmation -> error = "As senhas nao conferem."
                        securityQuestion.isBlank() -> error = "Informe uma pergunta de seguranca."
                        securityAnswer.isBlank() -> error = "Informe uma resposta de seguranca."
                        else -> onSave(password, securityQuestion, securityAnswer)
                    }
                }
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun SummaryHoursField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("Horarios") },
        supportingText = { Text("Use hora ou hora:minuto. Ex: 8:00, 12:30, 18") },
        singleLine = true,
        colors = hsCashOutlinedTextFieldColors()
    )
}

@Composable
private fun SummarySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector = Icons.Default.Notifications,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Color(0xFF1565C0) else Color(0xFF9CA3AF))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (enabled) Color(0xFF111827) else Color(0xFF6B7280))
            Text(subtitle, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E5EC)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            Text(subtitle, color = Color(0xFF6B7280))
        }
    }
}

private val ABOUT_TEXT = """
HS Cash

Aplicativo da HS Solutions para controle local de boletos, contas a pagar e contas a receber.

Principais recursos:
- Leitura de boleto pela camera.
- Cadastro manual de contas.
- Dashboard financeiro.
- Relatorio mensal.
- Lembretes e resumos de vencimento.
- Anexo de comprovantes.
- Backup local automatico.
- Cobranca via WhatsApp para contas a receber.

Os dados ficam salvos no aparelho. Esta versao nao usa login, servidor proprio, anuncios ou analytics.
""".trimIndent()

private val PRIVACY_TEXT = """
O HS Cash usa os dados informados pelo usuario para organizar boletos, contas a pagar, contas a receber, vencimentos, comprovantes e lembretes.

Dados salvos no aparelho:
- codigos de boleto, banco, valor e vencimento;
- fornecedor, categoria e status da conta;
- cliente/origem e WhatsApp quando informado;
- comprovantes anexados;
- arquivos de backup local quando essa opcao estiver ligada;
- senha de acesso protegida por codigo criptografico local;
- pergunta e resposta de seguranca para recuperacao de senha;
- preferencia de uso de biometria, quando ativada;
- preferencias de notificacao.

Camera:
A camera e usada somente para ler codigos de barras. O app nao salva fotos da camera.

Compartilhamento:
O app nao vende dados e nao envia dados para servidor proprio nesta versao. Dados podem sair do app apenas por acao do usuario, como exportar backup, abrir comprovante em outro app, copiar codigo de boleto ou enviar cobranca pelo WhatsApp.

Backup local:
Quando o backup automatico local esta ligado, o app cria arquivos JSON no aparelho. Se o usuario escolher uma pasta, os backups ficam nessa pasta. Caso contrario, ficam no armazenamento local do app.

Recomendacao:
Para maior seguranca, mantenha tambem uma copia dos backups em local seguro, como nuvem, pendrive ou outro aparelho.

Senha e biometria:
A senha de acesso e usada apenas para bloquear o app neste aparelho. A resposta de seguranca permite redefinir a senha caso ela seja esquecida. A biometria e validada pelo Android; o HS Cash nao salva digital, rosto ou outro dado biometrico.

Retencao:
Os dados ficam no aparelho ate o usuario excluir as contas, limpar os dados do app ou desinstalar o aplicativo.

Contato:
Responsavel pelo app: HS Solutions.
email. herlonys97@gmail.com.
""".trimIndent()
