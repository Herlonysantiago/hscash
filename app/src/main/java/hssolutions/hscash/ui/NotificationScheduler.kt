package hssolutions.hscash.ui

import android.annotation.SuppressLint
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
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
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object NotificationScheduler {
    private const val CHANNEL_ID = "contas_vencimento"
    private const val ACTION_TODAY_SUMMARY = "hssolutions.hscash.ACTION_TODAY_SUMMARY"
    private const val ACTION_TOMORROW_SUMMARY = "hssolutions.hscash.ACTION_TOMORROW_SUMMARY"
    private const val ACTION_OVERDUE_SUMMARY = "hssolutions.hscash.ACTION_OVERDUE_SUMMARY"
    private const val PREFS_NAME = "hs_cash_notification_state"
    private const val KEY_SCHEDULED_TODAY_SUMMARY_TIMES = "scheduled_today_summary_times"
    private const val KEY_SCHEDULED_TOMORROW_SUMMARY_TIMES = "scheduled_tomorrow_summary_times"
    private const val KEY_SCHEDULED_OVERDUE_SUMMARY_TIMES = "scheduled_overdue_summary_times"
    private const val KEY_LAST_TODAY_SUMMARY = "last_today_summary"
    private const val KEY_LAST_TOMORROW_SUMMARY = "last_tomorrow_summary"
    private const val KEY_LAST_OVERDUE_SUMMARY = "last_overdue_summary"
    private const val EXTRA_CONTA_ID = "contaId"
    const val EXTRA_DASHBOARD_FILTER = "dashboardFilter"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_MESSAGE = "message"
    private const val TODAY_SUMMARY_REQUEST_CODE = 9010
    private const val TODAY_SUMMARY_NOTIFICATION_ID = 9011
    private const val TOMORROW_SUMMARY_REQUEST_CODE = 9012
    private const val TOMORROW_SUMMARY_NOTIFICATION_ID = 9013
    private const val OVERDUE_SUMMARY_REQUEST_CODE = 9014
    private const val OVERDUE_SUMMARY_NOTIFICATION_ID = 9015

    fun scheduleConta(context: Context, conta: ContaSalva) {
        scheduleDailySummaries(context)
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

    fun scheduleDailySummaries(context: Context) {
        scheduleDailyTodaySummary(context)
        scheduleDailyTomorrowSummary(context)
        scheduleDailyOverdueSummary(context)
    }

    fun scheduleDailyTodaySummary(context: Context) {
        scheduleDailySummary(
            context = context,
            action = ACTION_TODAY_SUMMARY,
            requestCode = TODAY_SUMMARY_REQUEST_CODE,
            scheduledKey = KEY_SCHEDULED_TODAY_SUMMARY_TIMES,
            hours = AppSettings.todaySummaryHours(context),
            enabled = AppSettings.isTodaySummaryEnabled(context)
        )
    }

    fun scheduleDailyTomorrowSummary(context: Context) {
        scheduleDailySummary(
            context = context,
            action = ACTION_TOMORROW_SUMMARY,
            requestCode = TOMORROW_SUMMARY_REQUEST_CODE,
            scheduledKey = KEY_SCHEDULED_TOMORROW_SUMMARY_TIMES,
            hours = AppSettings.tomorrowSummaryHours(context),
            enabled = AppSettings.isTomorrowSummaryEnabled(context)
        )
    }

    fun scheduleDailyOverdueSummary(context: Context) {
        scheduleDailySummary(
            context = context,
            action = ACTION_OVERDUE_SUMMARY,
            requestCode = OVERDUE_SUMMARY_REQUEST_CODE,
            scheduledKey = KEY_SCHEDULED_OVERDUE_SUMMARY_TIMES,
            hours = AppSettings.overdueSummaryHours(context),
            enabled = AppSettings.isOverdueSummaryEnabled(context)
        )
    }

    private fun scheduleDailySummary(
        context: Context,
        action: String,
        requestCode: Int,
        scheduledKey: String,
        hours: List<Int>,
        enabled: Boolean
    ) {
        if (!enabled) {
            cancelDailySummary(context, action, requestCode, scheduledKey)
            return
        }

        cancelDailySummary(context, action, requestCode, scheduledKey)

        hours.forEach { minutesOfDay ->
            val triggerAt = Calendar.getInstance().apply {
                val hour = minutesOfDay / 60
                val minute = minutesOfDay % 60
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, NotificationReceiver::class.java)
                .setAction(action)

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                dailyRequestCode(requestCode, minutesOfDay),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt.timeInMillis,
                    pendingIntent
                )
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(scheduledKey, hours.joinToString(","))
            .apply()
    }

    private fun cancelDailySummary(context: Context, action: String, requestCode: Int, scheduledKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scheduledTimes = prefs.getString(scheduledKey, "").orEmpty()
            .split(",")
            .mapNotNull { it.toIntOrNull() }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduledTimes.forEach { minutesOfDay ->
            val intent = Intent(context, NotificationReceiver::class.java).setAction(action)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                dailyRequestCode(requestCode, minutesOfDay),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
        prefs.edit().remove(scheduledKey).apply()
    }

    fun showTodaySummaryOnceIfDue(context: Context): Boolean {
        return showSummaryOnceIfDue(context, AppSettings.todaySummaryHours(context), KEY_LAST_TODAY_SUMMARY) {
            showTodaySummary(it)
        }
    }

    fun showTomorrowSummaryOnceIfDue(context: Context): Boolean {
        return showSummaryOnceIfDue(context, AppSettings.tomorrowSummaryHours(context), KEY_LAST_TOMORROW_SUMMARY) {
            showTomorrowSummary(it)
        }
    }

    fun showOverdueSummaryOnceIfDue(context: Context): Boolean {
        return showSummaryOnceIfDue(context, AppSettings.overdueSummaryHours(context), KEY_LAST_OVERDUE_SUMMARY) {
            showOverdueSummary(it)
        }
    }

    fun showTodaySummary(context: Context): Boolean {
        return showDueSummary(
            context = context,
            dayOffset = 0,
            enabled = AppSettings.isTodaySummaryEnabled(context),
            singleTitle = "Conta vence hoje",
            multipleTitle = { count -> "$count contas vencem hoje" },
            totalMessage = "Total a pagar hoje",
            totalDetails = "Total hoje",
            notificationId = TODAY_SUMMARY_NOTIFICATION_ID,
            dashboardFilter = DashboardFilter.HOJE.name
        )
    }

    fun showTomorrowSummary(context: Context): Boolean {
        return showDueSummary(
            context = context,
            dayOffset = 1,
            enabled = AppSettings.isTomorrowSummaryEnabled(context),
            singleTitle = "Conta vence amanha",
            multipleTitle = { count -> "$count contas vencem amanha" },
            totalMessage = "Total para amanha",
            totalDetails = "Total amanha",
            notificationId = TOMORROW_SUMMARY_NOTIFICATION_ID,
            dashboardFilter = DashboardFilter.AMANHA.name
        )
    }

    fun showOverdueSummary(context: Context): Boolean {
        if (!AppSettings.isOverdueSummaryEnabled(context) || !canPostNotifications(context)) return false

        val today = startOfDay()
        val contas = ContaStorage.listarContas(context)
            .filter { conta ->
                conta.direcao == ContaDirecao.PAGAR &&
                    conta.status != ContaStatus.PAGO &&
                    parseDate(conta.vencimento)?.before(today) == true
            }

        if (contas.isEmpty()) return false

        ensureChannel(context)

        val total = formatMoney(contas.sumOf { parseValorCentavos(it.valor) })
        val title = if (contas.size == 1) "Conta vencida" else "${contas.size} contas vencidas"
        val message = if (contas.size == 1) {
            val conta = contas.first()
            "${displayName(conta)} - ${conta.valor}"
        } else {
            "Total vencido: $total"
        }
        val details = buildString {
            contas
                .sortedBy { parseDate(it.vencimento)?.timeInMillis ?: Long.MAX_VALUE }
                .take(4)
                .forEach { conta ->
                    append(displayName(conta))
                    append(" - ")
                    append(conta.valor)
                    append(" - venceu em ")
                    append(conta.vencimento)
                    append('\n')
                }
            if (contas.size > 4) {
                append("+")
                append(contas.size - 4)
                append(" contas\n")
            }
            append("Total vencido: ")
            append(total)
        }

        val notification = notificationBuilder(
            context = context,
            title = title,
            message = message,
            dashboardFilter = DashboardFilter.VENCIDAS.name
        )
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .build()

        return postNotification(context, OVERDUE_SUMMARY_NOTIFICATION_ID, notification)
    }

    fun showTestNotification(context: Context): Boolean {
        if (!canPostNotifications(context)) return false
        ensureChannel(context)

        val notification = notificationBuilder(
            context = context,
            title = "Teste HS Cash",
            message = "As notificacoes estao funcionando neste aparelho."
        ).build()

        return postNotification(context, "hs-cash-test".hashCode(), notification)
    }

    fun resetSummaryShownState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_TODAY_SUMMARY)
            .remove(KEY_LAST_TOMORROW_SUMMARY)
            .remove(KEY_LAST_OVERDUE_SUMMARY)
            .apply()
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

    private fun startOfDay(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun showSummaryOnceIfDue(
        context: Context,
        hours: List<Int>,
        lastShownKey: String,
        showSummary: (Context) -> Boolean
    ): Boolean {
        val dueTime = latestDueTime(hours) ?: return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "${todayKey()}-$dueTime"
        if (prefs.getString(lastShownKey, "") == key) return false

        val shown = showSummary(context)
        if (shown) {
            prefs.edit().putString(lastShownKey, key).apply()
        }
        return shown
    }

    private fun showDueSummary(
        context: Context,
        dayOffset: Int,
        enabled: Boolean,
        singleTitle: String,
        multipleTitle: (Int) -> String,
        totalMessage: String,
        totalDetails: String,
        notificationId: Int,
        dashboardFilter: String
    ): Boolean {
        if (!enabled || !canPostNotifications(context)) return false

        val contas = ContaStorage.listarContas(context)
            .filter {
                it.direcao == ContaDirecao.PAGAR &&
                    it.status != ContaStatus.PAGO &&
                    isDateOffset(it.vencimento, dayOffset)
            }

        if (contas.isEmpty()) return false

        ensureChannel(context)

        val total = formatMoney(contas.sumOf { parseValorCentavos(it.valor) })
        val title = if (contas.size == 1) singleTitle else multipleTitle(contas.size)
        val message = if (contas.size == 1) {
            val conta = contas.first()
            "${displayName(conta)} - ${conta.valor}"
        } else {
            "$totalMessage: $total"
        }
        val details = buildString {
            contas.take(4).forEach { conta ->
                append(displayName(conta))
                append(" - ")
                append(conta.valor)
                append('\n')
            }
            if (contas.size > 4) {
                append("+")
                append(contas.size - 4)
                append(" contas\n")
            }
            append(totalDetails)
            append(": ")
            append(total)
        }

        val notification = notificationBuilder(
            context = context,
            title = title,
            message = message,
            dashboardFilter = dashboardFilter
        )
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .build()

        return postNotification(context, notificationId, notification)
    }

    private fun isDateOffset(value: String, dayOffset: Int): Boolean {
        val date = parseDate(value) ?: return false
        val target = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
        return isSameDay(date, target)
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun parseValorCentavos(value: String): Long {
        return value.filter(Char::isDigit).toLongOrNull() ?: 0L
    }

    private fun formatMoney(cents: Long): String {
        val value = BigDecimal(cents).movePointLeft(2)
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value)
    }

    private fun displayName(conta: ContaSalva): String {
        return conta.fornecedor.ifBlank { conta.banco }.ifBlank { "Conta" }
    }

    private fun latestDueTime(times: List<Int>): Int? {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return times.filter { currentMinutes >= it }.maxOrNull()
    }

    private fun dailyRequestCode(baseRequestCode: Int, minutesOfDay: Int): Int {
        return "$baseRequestCode:$minutesOfDay".hashCode()
    }

    private fun todayKey(): String {
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time)
    }

    private fun notificationBuilder(
        context: Context,
        title: String,
        message: String,
        contaId: String? = null,
        dashboardFilter: String? = null
    ): NotificationCompat.Builder {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            if (!contaId.isNullOrBlank()) {
                putExtra(EXTRA_CONTA_ID, contaId)
            }
            if (!dashboardFilter.isNullOrBlank()) {
                putExtra(EXTRA_DASHBOARD_FILTER, dashboardFilter)
            }
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            contaId?.hashCode() ?: 0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingOpenIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
    }

    internal fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    internal fun postNotification(context: Context, notificationId: Int, notification: Notification): Boolean {
        if (!canPostNotifications(context)) return false

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    internal fun isTodaySummaryAction(action: String?): Boolean = action == ACTION_TODAY_SUMMARY

    internal fun isTomorrowSummaryAction(action: String?): Boolean = action == ACTION_TOMORROW_SUMMARY

    internal fun isOverdueSummaryAction(action: String?): Boolean = action == ACTION_OVERDUE_SUMMARY

    internal fun buildNotification(
        context: Context,
        title: String,
        message: String,
        contaId: String? = null,
        dashboardFilter: String? = null
    ): NotificationCompat.Builder = notificationBuilder(context, title, message, contaId, dashboardFilter)

    internal fun channelId(): String = CHANNEL_ID
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!NotificationScheduler.canPostNotifications(context)) return@launch

                when {
                    NotificationScheduler.isTodaySummaryAction(intent.action) -> {
                        NotificationScheduler.showTodaySummaryOnceIfDue(context)
                        NotificationScheduler.scheduleDailyTodaySummary(context)
                    }
                    NotificationScheduler.isTomorrowSummaryAction(intent.action) -> {
                        NotificationScheduler.showTomorrowSummaryOnceIfDue(context)
                        NotificationScheduler.scheduleDailyTomorrowSummary(context)
                    }
                    NotificationScheduler.isOverdueSummaryAction(intent.action) -> {
                        NotificationScheduler.showOverdueSummaryOnceIfDue(context)
                        NotificationScheduler.scheduleDailyOverdueSummary(context)
                    }
                    else -> {
                        NotificationScheduler.ensureChannel(context)
                        val title = intent.getStringExtra("title") ?: "Conta perto do vencimento"
                        val message = intent.getStringExtra("message") ?: "Confira suas contas no HS Cash."
                        val contaId = intent.getStringExtra("contaId")
                        val notification = NotificationScheduler.buildNotification(context, title, message, contaId)
                            .build()
                        NotificationScheduler.postNotification(context, message.hashCode(), notification)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
