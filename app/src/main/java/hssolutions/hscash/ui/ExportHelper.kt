package hssolutions.hscash.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ExportHelper {
    fun shareCsv(context: Context, contas: List<ContaSalva>) {
        if (contas.isEmpty()) {
            Toast.makeText(context, "Nenhuma conta para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "hs_cash_contas.csv")
        file.writeText(buildCsv(contas), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Contas HS Cash")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Exportar contas"))
    }

    private fun buildCsv(contas: List<ContaSalva>): String {
        val header = listOf(
            "direcao",
            "fornecedor",
            "banco",
            "categoria",
            "valor",
            "vencimento",
            "status",
            "tipo",
            "whatsapp_cliente",
            "codigo"
        ).joinToString(";")

        val rows = contas.joinToString("\n") { conta ->
            listOf(
                if (conta.direcao == ContaDirecao.RECEBER) "receber" else "pagar",
                conta.fornecedor,
                conta.banco,
                conta.categoria,
                conta.valor,
                conta.vencimento,
                statusLabel(conta),
                conta.tipo,
                conta.clienteWhatsapp.orEmpty(),
                conta.codigo
            ).joinToString(";") { it.csvEscape() }
        }

        return "$header\n$rows"
    }

    private fun String.csvEscape(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
