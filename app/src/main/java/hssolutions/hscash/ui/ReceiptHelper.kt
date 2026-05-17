package hssolutions.hscash.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ReceiptHelper {
    fun saveReceipt(context: Context, contaId: String, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "receipts").apply { mkdirs() }
            val file = File(dir, "$contaId-${System.currentTimeMillis()}.bin")
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
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir comprovante"))
    }
}
