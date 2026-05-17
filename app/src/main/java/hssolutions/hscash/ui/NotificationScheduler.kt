package hssolutions.hscash.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import hssolutions.hscash.MainActivity
import hssolutions.hscash.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object NotificationScheduler {
    private const val CHANNEL_ID = "contas_vencimento"
    private const val EXTRA_CONTA_ID = "contaId"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_MESSAGE = "message"

    fun scheduleConta(context: Context, conta: ContaSalva) {
        cancelConta(context, conta)
        if (conta.status == ContaStatus.PAGO) return

        val vencimento = parseDate(conta.vencimento) ?: return
        AppSettings.reminderDays(context).forEach { daysBefore ->
            val title = when (daysBefore) {
                0 -> "Conta vence hoje"
                1 -> "Conta vence amanha"
                else -> "Conta vence em $daysBefore dias"
            }
            schedule(context, conta, vencimento, -daysBefore, title)
        }
    }

    fun cancelConta(context: Context, conta: ContaSalva) {
        listOf(-7, -3, -1, 0).forEach { cancel(context, conta, it) }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Vencimento de contas",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alertas de boletos perto do vencimento"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun schedule(context: Context, conta: ContaSalva, dueDate: Calendar, dayOffset: Int, title: String) {
        val triggerAt = dueDate.clone() as Calendar
        triggerAt.add(Calendar.DAY_OF_YEAR, dayOffset)
        triggerAt.set(Calendar.HOUR_OF_DAY, 9)
        triggerAt.set(Calendar.MINUTE, 0)
        triggerAt.set(Calendar.SECOND, 0)
        triggerAt.set(Calendar.MILLISECOND, 0)

        if (triggerAt.timeInMillis <= System.currentTimeMillis()) return

        val displayName = conta.fornecedor.ifBlank { conta.banco }
        val intent = Intent(context, NotificationReceiver::class.java)
            .putExtra(EXTRA_CONTA_ID, conta.id)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_MESSAGE, "$displayName - ${conta.valor}")

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(conta.id, dayOffset),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt.timeInMillis, pendingIntent)
    }

    private fun cancel(context: Context, conta: ContaSalva, dayOffset: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(conta.id, dayOffset),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun requestCode(id: String, dayOffset: Int): Int {
        return "$id:$dayOffset".hashCode()
    }

    private fun parseDate(value: String): Calendar? {
        return try {
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).apply {
                isLenient = false
            }.parse(value) ?: return null
            Calendar.getInstance().apply { time = date }
        } catch (_: Exception) {
            null
        }
    }

    internal fun channelId(): String = CHANNEL_ID
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationScheduler.ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = intent.getStringExtra("title") ?: "Conta perto do vencimento"
        val message = intent.getStringExtra("message") ?: "Confira suas contas no HS Cash."
        val notification = NotificationCompat.Builder(context, NotificationScheduler.channelId())
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingOpenIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(message.hashCode(), notification)
    }
}
