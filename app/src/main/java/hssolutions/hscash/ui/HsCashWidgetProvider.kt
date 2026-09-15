package hssolutions.hscash.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import hssolutions.hscash.MainActivity
import hssolutions.hscash.R
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HsCashWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        workerScope.launch {
            try {
                updateWidgets(context.applicationContext, manager, ids)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val pendingResult = goAsync()
            workerScope.launch {
                try {
                    updateAllWidgets(context.applicationContext)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH = "hssolutions.hscash.widget.REFRESH"
        private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun refresh(context: Context) {
            workerScope.launch { updateAllWidgets(context.applicationContext) }
        }

        /** Solicita ao launcher que fixe o widget na tela inicial, quando suportado. */
        fun requestAddToHomeScreen(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(
                ComponentName(context, HsCashWidgetProvider::class.java),
                null,
                null
            )
        }

        private suspend fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, HsCashWidgetProvider::class.java)
            updateWidgets(context, manager, manager.getAppWidgetIds(component))
        }

        private suspend fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray
        ) {
            val contas = ContaStorage.listarContasAsync(context)
            widgetIds.forEach { updateWidget(context, manager, it, contas) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            contas: List<ContaSalva>
        ) {
            val today = startOfToday()
            val abertas = contas.filter { it.direcao == ContaDirecao.PAGAR && it.status != ContaStatus.PAGO }
            val hoje = abertas.filter { parseContaDate(it.vencimento)?.let { date -> sameDay(date, today) } == true }
            val vencidas = abertas.filter { parseContaDate(it.vencimento)?.before(today) == true }
            val totalHoje = hoje.sumOf { it.valor.filter(Char::isDigit).toLongOrNull() ?: 0L }

            val views = RemoteViews(context.packageName, R.layout.widget_hs_cash).apply {
                setTextViewText(R.id.widget_today_value, money(totalHoje))
                setTextViewText(R.id.widget_today_label, if (hoje.size == 1) "1 conta vence hoje" else "${hoje.size} contas vencem hoje")
                setTextViewText(R.id.widget_overdue, if (vencidas.isEmpty()) "Nenhuma conta vencida" else "${vencidas.size} vencida(s) — confira")

                val openApp = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val refresh = PendingIntent.getBroadcast(
                    context, 1, Intent(context, HsCashWidgetProvider::class.java).setAction(ACTION_REFRESH), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, openApp)
                setOnClickPendingIntent(R.id.widget_refresh, refresh)
            }
            manager.updateAppWidget(widgetId, views)
        }

        private fun sameDay(a: Calendar, b: Calendar): Boolean =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

        private fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
            .format(BigDecimal(cents).movePointLeft(2))
    }
}
