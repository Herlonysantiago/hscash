package hssolutions.hscash.ui

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "hs_cash_settings"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_REMINDER_DAYS = "reminder_days"

    fun isOnboardingDone(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun reminderDays(context: Context): Set<Int> {
        val raw = prefs(context).getString(KEY_REMINDER_DAYS, "1,0").orEmpty()
        return raw.split(",")
            .mapNotNull { it.toIntOrNull() }
            .toSet()
            .ifEmpty { setOf(1, 0) }
    }

    fun setReminderDays(context: Context, days: Set<Int>) {
        prefs(context).edit()
            .putString(KEY_REMINDER_DAYS, days.sortedDescending().joinToString(","))
            .apply()
        ContaStorage.listarContas(context).forEach { NotificationScheduler.scheduleConta(context, it) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
