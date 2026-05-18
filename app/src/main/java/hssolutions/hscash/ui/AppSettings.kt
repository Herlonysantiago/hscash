package hssolutions.hscash.ui

import android.content.Context
import android.net.Uri
import java.security.MessageDigest
import java.util.UUID

object AppSettings {
    private const val PREFS_NAME = "hs_cash_settings"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_REMINDER_DAYS = "reminder_days"
    private const val KEY_TODAY_SUMMARY_ENABLED = "today_summary_enabled"
    private const val KEY_TOMORROW_SUMMARY_ENABLED = "tomorrow_summary_enabled"
    private const val KEY_AUTO_LOCAL_BACKUP_ENABLED = "auto_local_backup_enabled"
    private const val KEY_LOCAL_BACKUP_FOLDER_URI = "local_backup_folder_uri"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_APP_LOCK_PASSWORD_HASH = "app_lock_password_hash"
    private const val KEY_APP_LOCK_PASSWORD_SALT = "app_lock_password_salt"
    private const val KEY_BIOMETRIC_UNLOCK_ENABLED = "biometric_unlock_enabled"
    private const val KEY_SECURITY_QUESTION = "security_question"
    private const val KEY_SECURITY_ANSWER_HASH = "security_answer_hash"
    private const val KEY_SECURITY_ANSWER_SALT = "security_answer_salt"

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

    fun isTodaySummaryEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_TODAY_SUMMARY_ENABLED, true)
    }

    fun setTodaySummaryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TODAY_SUMMARY_ENABLED, enabled).apply()
        NotificationScheduler.scheduleDailySummaries(context)
        if (enabled) {
            NotificationScheduler.showTodaySummaryOnceIfDue(context)
        }
    }

    fun isTomorrowSummaryEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_TOMORROW_SUMMARY_ENABLED, true)
    }

    fun setTomorrowSummaryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOMORROW_SUMMARY_ENABLED, enabled).apply()
        NotificationScheduler.scheduleDailySummaries(context)
        if (enabled) {
            NotificationScheduler.showTomorrowSummaryOnceIfDue(context)
        }
    }

    fun isAutoLocalBackupEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTO_LOCAL_BACKUP_ENABLED, true)
    }

    fun setAutoLocalBackupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_LOCAL_BACKUP_ENABLED, enabled).apply()
        if (enabled) {
            BackupHelper.createLocalBackup(context)
        }
    }

    fun localBackupFolderUri(context: Context): Uri? {
        val raw = prefs(context).getString(KEY_LOCAL_BACKUP_FOLDER_URI, "").orEmpty()
        return raw.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    fun setLocalBackupFolderUri(context: Context, uri: Uri?) {
        prefs(context).edit()
            .putString(KEY_LOCAL_BACKUP_FOLDER_URI, uri?.toString().orEmpty())
            .apply()
    }

    fun isAppLockEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_APP_LOCK_ENABLED, false) && hasAppLockPassword(context)
    }

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled && hasAppLockPassword(context)).apply()
        if (!enabled) {
            setBiometricUnlockEnabled(context, false)
        }
    }

    fun hasAppLockPassword(context: Context): Boolean {
        return prefs(context).getString(KEY_APP_LOCK_PASSWORD_HASH, "").orEmpty().isNotBlank()
    }

    fun setAppLockPassword(context: Context, password: String) {
        val salt = UUID.randomUUID().toString()
        prefs(context).edit()
            .putString(KEY_APP_LOCK_PASSWORD_SALT, salt)
            .putString(KEY_APP_LOCK_PASSWORD_HASH, hashPassword(salt, password))
            .putBoolean(KEY_APP_LOCK_ENABLED, true)
            .apply()
    }

    fun setAppLockPassword(
        context: Context,
        password: String,
        securityQuestion: String,
        securityAnswer: String
    ) {
        val passwordSalt = UUID.randomUUID().toString()
        val answerSalt = UUID.randomUUID().toString()
        prefs(context).edit()
            .putString(KEY_APP_LOCK_PASSWORD_SALT, passwordSalt)
            .putString(KEY_APP_LOCK_PASSWORD_HASH, hashSecret(passwordSalt, password))
            .putString(KEY_SECURITY_QUESTION, securityQuestion.trim())
            .putString(KEY_SECURITY_ANSWER_SALT, answerSalt)
            .putString(KEY_SECURITY_ANSWER_HASH, hashSecret(answerSalt, normalizeAnswer(securityAnswer)))
            .putBoolean(KEY_APP_LOCK_ENABLED, true)
            .apply()
    }

    fun verifyAppLockPassword(context: Context, password: String): Boolean {
        val salt = prefs(context).getString(KEY_APP_LOCK_PASSWORD_SALT, "").orEmpty()
        val expectedHash = prefs(context).getString(KEY_APP_LOCK_PASSWORD_HASH, "").orEmpty()
        if (salt.isBlank() || expectedHash.isBlank()) return false
        return hashSecret(salt, password) == expectedHash
    }

    fun clearAppLock(context: Context) {
        prefs(context).edit()
            .remove(KEY_APP_LOCK_PASSWORD_SALT)
            .remove(KEY_APP_LOCK_PASSWORD_HASH)
            .remove(KEY_SECURITY_QUESTION)
            .remove(KEY_SECURITY_ANSWER_SALT)
            .remove(KEY_SECURITY_ANSWER_HASH)
            .putBoolean(KEY_APP_LOCK_ENABLED, false)
            .putBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, false)
            .apply()
    }

    fun isBiometricUnlockEnabled(context: Context): Boolean {
        return isAppLockEnabled(context) && prefs(context).getBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, false)
    }

    fun setBiometricUnlockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_BIOMETRIC_UNLOCK_ENABLED, enabled && isAppLockEnabled(context))
            .apply()
    }

    fun hasSecurityQuestion(context: Context): Boolean {
        return securityQuestion(context).isNotBlank() &&
            prefs(context).getString(KEY_SECURITY_ANSWER_HASH, "").orEmpty().isNotBlank()
    }

    fun securityQuestion(context: Context): String {
        return prefs(context).getString(KEY_SECURITY_QUESTION, "").orEmpty()
    }

    fun verifySecurityAnswer(context: Context, answer: String): Boolean {
        val salt = prefs(context).getString(KEY_SECURITY_ANSWER_SALT, "").orEmpty()
        val expectedHash = prefs(context).getString(KEY_SECURITY_ANSWER_HASH, "").orEmpty()
        if (salt.isBlank() || expectedHash.isBlank()) return false
        return hashSecret(salt, normalizeAnswer(answer)) == expectedHash
    }

    private fun normalizeAnswer(answer: String): String {
        return answer.trim().lowercase()
    }

    private fun hashPassword(salt: String, password: String): String {
        return hashSecret(salt, password)
    }

    private fun hashSecret(salt: String, secret: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$secret".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
