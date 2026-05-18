package hssolutions.hscash.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

object ReceiptHelper {
    fun saveReceipt(context: Context, contaId: String, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "receipts").apply { mkdirs() }
            val mimeType = context.contentResolver.getType(uri)
            val extension = receiptExtension(context, uri, mimeType)
            val safeContaId = contaId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "conta" }
            val file = File(dir, "$safeContaId-${System.currentTimeMillis()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun openReceipt(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase(Locale.US))
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir comprovante"))
    }

    private fun receiptExtension(context: Context, uri: Uri, mimeType: String?): String {
        val mimeExtension = mimeType?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }
        if (!mimeExtension.isNullOrBlank()) return normalizeExtension(mimeExtension)

        val nameExtension = displayName(context, uri)
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }

        return normalizeExtension(nameExtension ?: "bin")
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            }
    }

    private fun normalizeExtension(extension: String): String {
        return extension
            .lowercase(Locale.US)
            .filter { it.isLetterOrDigit() }
            .ifBlank { "bin" }
    }
}
