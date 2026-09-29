package hssolutions.hscash.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardHelper {
    fun copy(context: Context, label: String, value: String) {
        val digits = value.filter(Char::isDigit)
        val expectedLength = when {
            label.contains("Linha", ignoreCase = true) -> setOf(47, 48)
            label.contains("barras", ignoreCase = true) -> setOf(44)
            else -> emptySet()
        }

        if (expectedLength.isNotEmpty() && digits.length !in expectedLength) {
            Toast.makeText(
                context,
                "$label invalido: ${digits.length} digitos",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val copyValue = if (expectedLength.isNotEmpty()) digits else value.trim()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, copyValue))
        Toast.makeText(
            context,
            if (expectedLength.isNotEmpty()) "$label copiado: ${digits.length} digitos" else "$label copiado",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun paste(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf(String::isNotBlank)
    }
}
