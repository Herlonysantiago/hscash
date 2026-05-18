package hssolutions.hscash.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {
    private const val BACKUP_MIME_TYPE = "application/json"
    private const val AUTO_BACKUP_PREFIX = "hs_cash_auto_"
    private const val MANUAL_BACKUP_PREFIX = "hs_cash_manual_"
    private const val MANUAL_BACKUP_FILE_NAME = "hs_cash_manual_backup.json"
    private const val MAX_AUTO_BACKUPS = 10

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

    fun createLocalBackup(context: Context): Boolean {
        val created = writeBackup(
            context = context,
            contas = ContaStorage.listarContas(context),
            fileName = MANUAL_BACKUP_FILE_NAME,
            replaceExisting = true,
            pruneAutoBackups = false
        )
        Toast.makeText(
            context,
            if (created) "Backup local atualizado" else "Nao foi possivel criar o backup local",
            Toast.LENGTH_SHORT
        ).show()
        return created
    }

    fun saveAutomaticBackup(context: Context, contas: List<ContaSalva>): Boolean {
        if (!AppSettings.isAutoLocalBackupEnabled(context)) return false
        return writeBackup(
            context = context,
            contas = contas,
            fileName = "$AUTO_BACKUP_PREFIX${timestamp()}.json",
            replaceExisting = false,
            pruneAutoBackups = true
        )
    }

    fun localBackupLocationLabel(context: Context): String {
        return if (AppSettings.localBackupFolderUri(context) != null) {
            "Pasta escolhida no aparelho"
        } else {
            "Armazenamento local do app"
        }
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

    private fun writeBackup(
        context: Context,
        contas: List<ContaSalva>,
        fileName: String,
        replaceExisting: Boolean,
        pruneAutoBackups: Boolean
    ): Boolean {
        val json = toJson(contas).toString()
        val treeUri = AppSettings.localBackupFolderUri(context)

        if (treeUri != null && writeTreeBackup(context, treeUri, fileName, json, replaceExisting, pruneAutoBackups)) {
            return true
        }

        return writeAppBackup(context, fileName, json, replaceExisting, pruneAutoBackups)
    }

    private fun writeAppBackup(
        context: Context,
        fileName: String,
        json: String,
        replaceExisting: Boolean,
        pruneAutoBackups: Boolean
    ): Boolean {
        return try {
            val dir = (context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")).apply { mkdirs() }
            if (replaceExisting) deleteManualAppBackups(dir)
            File(dir, fileName).writeText(json, Charsets.UTF_8)
            if (pruneAutoBackups) pruneAppBackups(dir)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeTreeBackup(
        context: Context,
        treeUri: Uri,
        fileName: String,
        json: String,
        replaceExisting: Boolean,
        pruneAutoBackups: Boolean
    ): Boolean {
        return try {
            val resolver = context.contentResolver
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
            if (replaceExisting) deleteManualTreeBackups(context, treeUri)
            val fileUri = DocumentsContract.createDocument(resolver, parentUri, BACKUP_MIME_TYPE, fileName)
                ?: return false

            resolver.openOutputStream(fileUri, "wt")?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: return false

            if (pruneAutoBackups) pruneTreeBackups(context, treeUri)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteManualAppBackups(dir: File) {
        dir.listFiles()
            ?.filter { it.name == MANUAL_BACKUP_FILE_NAME || (it.name.startsWith(MANUAL_BACKUP_PREFIX) && it.extension == "json") }
            ?.forEach { it.delete() }
    }

    private fun pruneAppBackups(dir: File) {
        dir.listFiles()
            ?.filter { it.name.startsWith(AUTO_BACKUP_PREFIX) && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_AUTO_BACKUPS)
            ?.forEach { it.delete() }
    }

    private fun pruneTreeBackups(context: Context, treeUri: Uri) {
        treeBackupFiles(context, treeUri)
            .filter { it.name.startsWith(AUTO_BACKUP_PREFIX) && it.name.endsWith(".json") }
            .sortedWith(compareByDescending<TreeBackupFile> { it.modifiedAt }.thenByDescending { it.name })
            .drop(MAX_AUTO_BACKUPS)
            .forEach { deleteTreeFile(context, treeUri, it.documentId) }
    }

    private fun deleteManualTreeBackups(context: Context, treeUri: Uri) {
        treeBackupFiles(context, treeUri)
            .filter { it.name == MANUAL_BACKUP_FILE_NAME || (it.name.startsWith(MANUAL_BACKUP_PREFIX) && it.name.endsWith(".json")) }
            .forEach { deleteTreeFile(context, treeUri, it.documentId) }
    }

    private fun treeBackupFiles(context: Context, treeUri: Uri): List<TreeBackupFile> {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val files = mutableListOf<TreeBackupFile>()

        context.contentResolver.query(
            childrenUri,
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val modifiedIndex = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                if (idIndex < 0 || nameIndex < 0) continue
                val name = cursor.getString(nameIndex) ?: continue
                files.add(
                    TreeBackupFile(
                        documentId = cursor.getString(idIndex),
                        name = name,
                        modifiedAt = if (modifiedIndex >= 0) cursor.getLong(modifiedIndex) else 0L
                    )
                )
            }
        }

        return files
    }

    private fun deleteTreeFile(context: Context, treeUri: Uri, documentId: String) {
        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, fileUri) }
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
                    .put("clienteWhatsapp", conta.clienteWhatsapp)
                    .put("excluidaEm", conta.excluidaEm)
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
                direcao = item.optString("direcao", ContaDirecao.PAGAR),
                clienteWhatsapp = if (item.has("clienteWhatsapp") && !item.isNull("clienteWhatsapp")) item.optString("clienteWhatsapp") else null,
                excluidaEm = if (item.has("excluidaEm") && !item.isNull("excluidaEm")) item.optLong("excluidaEm") else null
            )
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private data class TreeBackupFile(
        val documentId: String,
        val name: String,
        val modifiedAt: Long
    )
}
