package hssolutions.hscash.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mais", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        MenuCard("Clientes", "Cadastro e historico de cobrancas", Icons.Default.Info, onOpenClients)
        MenuCard("Cobrancas", "Recebimentos em aberto para cobrar", Icons.Default.Notifications, onOpenCharges)
        MenuCard("Lixeira", "Restaurar contas removidas", Icons.Default.Backup, onOpenTrash)
        MenuCard("Calendario", "Visualizar vencimentos por dia", Icons.Default.CalendarMonth, onOpenCalendar)
        MenuCard("Relatorio mensal", "Totais pagos e exportacao em PDF", Icons.Default.ShowChart, onOpenReport)
        MenuCard("Configuracoes", "Backup, seguranca, lembretes e privacidade", Icons.Default.Lock, onOpenSettings)
        MenuCard("Ajuda inicial", "Ver explicacao rapida do app", Icons.Default.Info, onOpenOnboarding)
    }
}

@Composable
fun ConfiguracoesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Column {
                Text("Configuracoes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Backup, seguranca e lembretes", color = Color(0xFF6B7280))
            }
        }
        MenuCard("Exportar backup", "Gerar arquivo JSON completo", Icons.Default.Backup) {
            BackupHelper.shareBackup(context)
        }
        MenuCard("Importar backup", "Restaurar contas de um arquivo JSON", Icons.Default.FileUpload) {
            importLauncher.launch("application/json")
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    subtitle = "Cria backup JSON sempre que os dados mudam.",
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
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
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
                    visualTransformation = PasswordVisualTransformation()
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
                    isError = error != null
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
                    singleLine = true
                )
                androidx.compose.material3.OutlinedTextField(
                    value = securityAnswer,
                    onValueChange = {
                        securityAnswer = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Resposta de seguranca") },
                    singleLine = true
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
        singleLine = true
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
private fun MenuCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
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
