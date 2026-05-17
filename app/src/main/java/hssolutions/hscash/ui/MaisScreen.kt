package hssolutions.hscash.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MaisScreen(
    onOpenCalendar: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(AppSettings.reminderDays(context)) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) BackupHelper.importBackup(context, uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mais", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        MenuCard("Calendario", "Visualizar vencimentos por dia", Icons.Default.CalendarMonth, onOpenCalendar)
        MenuCard("Relatorio mensal", "Totais pagos e categorias", Icons.Default.ShowChart, onOpenReport)
        MenuCard("Exportar backup", "Gerar arquivo JSON completo", Icons.Default.Backup) {
            BackupHelper.shareBackup(context)
        }
        MenuCard("Importar backup", "Restaurar contas de um arquivo JSON", Icons.Default.FileUpload) {
            importLauncher.launch("application/json")
        }
        MenuCard("Ajuda inicial", "Ver explicacao rapida do app", Icons.Default.Info, onOpenOnboarding)

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
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
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
