package hssolutions.hscash.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppSettings {
    private const val PREFS_NAME = "hs_cash_settings"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_REMINDER_DAYS = "reminder_days"
    private const val KEY_TODAY_SUMMARY_ENABLED = "today_summary_enabled"
    private const val KEY_TOMORROW_SUMMARY_ENABLED = "tomorrow_summary_enabled"
    private const val KEY_OVERDUE_SUMMARY_ENABLED = "overdue_summary_enabled"
    private const val KEY_OVERDUE_SUMMARY_HOURS = "overdue_summary_hours"
    private const val KEY_TODAY_SUMMARY_HOURS = "today_summary_hours"
    private const val KEY_TOMORROW_SUMMARY_HOURS = "tomorrow_summary_hours"
    private const val KEY_AUTO_LOCAL_BACKUP_ENABLED = "auto_local_backup_enabled"
    private const val KEY_LOCAL_BACKUP_FOLDER_URI = "local_backup_folder_uri"
    private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_APP_LOCK_PASSWORD_HASH = "app_lock_password_hash"
    private const val KEY_APP_LOCK_PASSWORD_SALT = "app_lock_password_salt"
    private const val KEY_BIOMETRIC_UNLOCK_ENABLED = "biometric_unlock_enabled"
    private const val KEY_SECURITY_QUESTION = "security_question"
    private const val KEY_SECURITY_ANSWER_HASH = "security_answer_hash"
    private const val KEY_SECURITY_ANSWER_SALT = "security_answer_salt"
    private const val PBKDF2_PREFIX = "pbkdf2-sha256"
    private const val PBKDF2_ITERATIONS = 210_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isOnboardingDone(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingDone(context: Context, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    fun isScreenHelpSeen(context: Context, screenKey: String): Boolean {
        return prefs(context).getBoolean("screen_help_seen_$screenKey", false)
    }

    fun setScreenHelpSeen(context: Context, screenKey: String) {
        prefs(context).edit().putBoolean("screen_help_seen_$screenKey", true).apply()
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
        workerScope.launch {
            ContaStorage.listarContasAsync(context.applicationContext)
                .forEach { NotificationScheduler.scheduleConta(context.applicationContext, it) }
        }
    }

    fun isTodaySummaryEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_TODAY_SUMMARY_ENABLED, true)
    }

    fun setTodaySummaryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TODAY_SUMMARY_ENABLED, enabled).apply()
        workerScope.launch {
            NotificationScheduler.scheduleDailySummaries(context.applicationContext)
            if (enabled) NotificationScheduler.showTodaySummaryOnceIfDue(context.applicationContext)
        }
    }

    fun isTomorrowSummaryEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_TOMORROW_SUMMARY_ENABLED, true)
    }

    fun setTomorrowSummaryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TOMORROW_SUMMARY_ENABLED, enabled).apply()
        workerScope.launch {
            NotificationScheduler.scheduleDailySummaries(context.applicationContext)
            if (enabled) NotificationScheduler.showTomorrowSummaryOnceIfDue(context.applicationContext)
        }
    }

    fun isOverdueSummaryEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_OVERDUE_SUMMARY_ENABLED, true)
    }

    fun setOverdueSummaryEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERDUE_SUMMARY_ENABLED, enabled).apply()
        workerScope.launch {
            NotificationScheduler.scheduleDailySummaries(context.applicationContext)
            if (enabled) NotificationScheduler.showOverdueSummaryOnceIfDue(context.applicationContext)
        }
    }

    fun overdueSummaryHours(context: Context): List<Int> {
        return summaryTimes(context, KEY_OVERDUE_SUMMARY_HOURS, "8:00")
    }

    fun setOverdueSummaryHours(context: Context, value: String) {
        setSummaryHours(context, KEY_OVERDUE_SUMMARY_HOURS, value, "8:00")
    }

    fun overdueSummaryHoursText(context: Context): String {
        return summaryHoursText(context, KEY_OVERDUE_SUMMARY_HOURS, "8:00")
    }

    fun todaySummaryHours(context: Context): List<Int> {
        return summaryTimes(context, KEY_TODAY_SUMMARY_HOURS, "8:00")
    }

    fun setTodaySummaryHours(context: Context, value: String) {
        setSummaryHours(context, KEY_TODAY_SUMMARY_HOURS, value, "8:00")
    }

    fun todaySummaryHoursText(context: Context): String {
        return summaryHoursText(context, KEY_TODAY_SUMMARY_HOURS, "8:00")
    }

    fun tomorrowSummaryHours(context: Context): List<Int> {
        return summaryTimes(context, KEY_TOMORROW_SUMMARY_HOURS, "18:00")
    }

    fun setTomorrowSummaryHours(context: Context, value: String) {
        setSummaryHours(context, KEY_TOMORROW_SUMMARY_HOURS, value, "18:00")
    }

    fun tomorrowSummaryHoursText(context: Context): String {
        return summaryHoursText(context, KEY_TOMORROW_SUMMARY_HOURS, "18:00")
    }

    private fun setSummaryHours(context: Context, key: String, value: String, defaultValue: String) {
        val sanitized = parseTimes(value).joinToString(", ") { formatTime(it) }
        prefs(context).edit().putString(key, sanitized.ifBlank { defaultValue }).apply()
        workerScope.launch { NotificationScheduler.scheduleDailySummaries(context.applicationContext) }
    }

    private fun summaryTimes(context: Context, key: String, defaultValue: String): List<Int> {
        return parseTimes(summaryHoursText(context, key, defaultValue)).ifEmpty { parseTimes(defaultValue) }
    }

    private fun summaryHoursText(context: Context, key: String, defaultValue: String): String {
        return prefs(context).getString(key, defaultValue).orEmpty().ifBlank { defaultValue }
    }

    private fun parseTimes(value: String): List<Int> {
        return value
            .split(",", ";")
            .mapNotNull { raw ->
                val part = raw.trim()
                when {
                    part.contains(":") -> {
                        val hour = part.substringBefore(":").trim().toIntOrNull()
                        val minute = part.substringAfter(":").trim().toIntOrNull()
                        if (hour != null && minute != null && hour in 0..23 && minute in 0..59) {
                            hour * 60 + minute
                        } else {
                            null
                        }
                    }
                    else -> part.toIntOrNull()?.takeIf { it in 0..23 }?.let { it * 60 }
                }
            }
            .distinct()
            .sorted()
    }

    private fun formatTime(minutesOfDay: Int): String {
        val hour = minutesOfDay / 60
        val minute = minutesOfDay % 60
        return "%d:%02d".format(hour, minute)
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
        prefs(context).edit()
            .remove(KEY_APP_LOCK_PASSWORD_SALT)
            .putString(KEY_APP_LOCK_PASSWORD_HASH, hashSecret(password))
            .putBoolean(KEY_APP_LOCK_ENABLED, true)
            .apply()
    }

    fun setAppLockPassword(
        context: Context,
        password: String,
        securityQuestion: String,
        securityAnswer: String
    ) {
        prefs(context).edit()
            .remove(KEY_APP_LOCK_PASSWORD_SALT)
            .putString(KEY_APP_LOCK_PASSWORD_HASH, hashSecret(password))
            .putString(KEY_SECURITY_QUESTION, securityQuestion.trim())
            .remove(KEY_SECURITY_ANSWER_SALT)
            .putString(KEY_SECURITY_ANSWER_HASH, hashSecret(normalizeAnswer(securityAnswer)))
            .putBoolean(KEY_APP_LOCK_ENABLED, true)
            .apply()
    }

    fun verifyAppLockPassword(context: Context, password: String): Boolean {
        val salt = prefs(context).getString(KEY_APP_LOCK_PASSWORD_SALT, "").orEmpty()
        val expectedHash = prefs(context).getString(KEY_APP_LOCK_PASSWORD_HASH, "").orEmpty()
        if (expectedHash.isBlank()) return false
        val valid = verifySecret(expectedHash, salt, password)
        if (valid && !isPbkdf2Hash(expectedHash)) {
            prefs(context).edit()
                .remove(KEY_APP_LOCK_PASSWORD_SALT)
                .putString(KEY_APP_LOCK_PASSWORD_HASH, hashSecret(password))
                .apply()
        }
        return valid
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
        if (expectedHash.isBlank()) return false
        val normalizedAnswer = normalizeAnswer(answer)
        val valid = verifySecret(expectedHash, salt, normalizedAnswer)
        if (valid && !isPbkdf2Hash(expectedHash)) {
            prefs(context).edit()
                .remove(KEY_SECURITY_ANSWER_SALT)
                .putString(KEY_SECURITY_ANSWER_HASH, hashSecret(normalizedAnswer))
                .apply()
        }
        return valid
    }

    private fun normalizeAnswer(answer: String): String {
        return answer.trim().lowercase()
    }

    private fun hashSecret(secret: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = pbkdf2(secret, salt)
        return listOf(
            PBKDF2_PREFIX,
            PBKDF2_ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP)
        ).joinToString("$")
    }

    private fun verifySecret(storedHash: String, legacySalt: String, secret: String): Boolean {
        if (!isPbkdf2Hash(storedHash)) {
            return legacySalt.isNotBlank() && legacyHash(legacySalt, secret) == storedHash
        }
        val parts = storedHash.split("$")
        if (parts.size != 4) return false
        return try {
            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            MessageDigest.isEqual(pbkdf2(secret, salt, iterations, expected.size * 8), expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun isPbkdf2Hash(value: String): Boolean = value.startsWith("$PBKDF2_PREFIX$")

    private fun pbkdf2(
        secret: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
        keyLengthBits: Int = PBKDF2_KEY_LENGTH_BITS
    ): ByteArray {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(secret.toCharArray(), salt, iterations, keyLengthBits))
            .encoded
    }

    private fun legacyHash(salt: String, secret: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$secret".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
