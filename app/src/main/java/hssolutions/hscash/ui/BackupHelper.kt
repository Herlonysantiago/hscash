package hssolutions.hscash.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BackupHelper {
    fun shareBackup(context: Context) {
        val contas = ContaStorage.listarContas(context)
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "hs_cash_backup.json")
        file.writeText(toJson(contas).toString(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Backup HS Cash")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar backup"))
    }

    fun importBackup(context: Context, uri: Uri): Boolean {
        return try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return false
            val contas = fromJson(JSONArray(raw))
            ContaStorage.substituirTodas(context, contas)
            Toast.makeText(context, "Backup restaurado", Toast.LENGTH_SHORT).show()
            true
        } catch (_: Exception) {
            Toast.makeText(context, "Nao foi possivel importar o backup", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun toJson(contas: List<ContaSalva>): JSONArray {
        val array = JSONArray()
        contas.forEach { conta ->
            array.put(
                JSONObject()
                    .put("id", conta.id)
                    .put("codigo", conta.codigo)
                    .put("banco", conta.banco)
                    .put("valor", conta.valor)
                    .put("vencimento", conta.vencimento)
                    .put("tipo", conta.tipo)
                    .put("criadaEm", conta.criadaEm)
                    .put("fornecedor", conta.fornecedor)
                    .put("categoria", conta.categoria)
                    .put("status", conta.status)
                    .put("pagoEm", conta.pagoEm)
                    .put("recorrente", conta.recorrente)
                    .put("recorrencia", conta.recorrencia)
                    .put("comprovantePath", conta.comprovantePath)
                    .put("direcao", conta.direcao)
            )
        }
        return array
    }

    private fun fromJson(array: JSONArray): List<ContaSalva> {
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ContaSalva(
                id = item.optString("id", System.currentTimeMillis().toString()),
                codigo = item.optString("codigo"),
                banco = item.optString("banco"),
                valor = item.optString("valor"),
                vencimento = item.optString("vencimento"),
                tipo = item.optString("tipo"),
                criadaEm = item.optLong("criadaEm", System.currentTimeMillis()),
                fornecedor = item.optString("fornecedor"),
                categoria = item.optString("categoria", "Outros"),
                status = item.optString("status", ContaStatus.PENDENTE),
                pagoEm = if (item.has("pagoEm") && !item.isNull("pagoEm")) item.optLong("pagoEm") else null,
                recorrente = item.optBoolean("recorrente", false),
                recorrencia = item.optString("recorrencia", "Mensal"),
                comprovantePath = if (item.has("comprovantePath") && !item.isNull("comprovantePath")) item.optString("comprovantePath") else null,
                direcao = item.optString("direcao", ContaDirecao.PAGAR)
            )
        }
    }
}
