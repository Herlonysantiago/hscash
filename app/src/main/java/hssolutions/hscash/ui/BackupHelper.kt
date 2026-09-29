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
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BackupHelper {
    private const val BACKUP_MIME_TYPE = "application/zip"
    private const val AUTO_BACKUP_PREFIX = "hs_cash_auto_"
    private const val MANUAL_BACKUP_PREFIX = "hs_cash_manual_"
    private const val MANUAL_BACKUP_FILE_NAME = "hs_cash_manual_backup.zip"
    private const val LEGACY_MANUAL_BACKUP_FILE_NAME = "hs_cash_manual_backup.json"
    private const val MANIFEST_ENTRY_NAME = "manifest.json"
    private const val BACKUP_VERSION = 2
    private const val MAX_AUTO_BACKUPS = 10
    private const val MAX_ARCHIVE_BYTES = 300L * 1024L * 1024L
    private const val MAX_MANIFEST_BYTES = 5L * 1024L * 1024L
    private const val MAX_ATTACHMENT_BYTES = 75L * 1024L * 1024L
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun shareBackup(context: Context) {
        workerScope.launch {
            val contas = ContaStorage.listarTodosEspacosAsync(context.applicationContext)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "hs_cash_backup.zip")
            val created = runCatching {
                file.outputStream().use { writeArchive(contas, it) }
            }.getOrDefault(false)

            if (!created) {
                file.delete()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Nao foi possivel exportar o backup", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = BACKUP_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup HS Cash")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(intent, "Exportar backup"))
            }
        }
    }

    fun createLocalBackup(context: Context) {
        workerScope.launch {
            val created = writeBackup(
                context = context.applicationContext,
                contas = ContaStorage.listarTodosEspacosAsync(context.applicationContext),
                fileName = MANUAL_BACKUP_FILE_NAME,
                replaceExisting = true,
                pruneAutoBackups = false
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (created) "Backup local atualizado" else "Nao foi possivel criar o backup local",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun saveAutomaticBackup(context: Context, contas: List<ContaSalva>): Boolean {
        if (!AppSettings.isAutoLocalBackupEnabled(context)) return false
        return writeBackup(
            context = context,
            contas = contas,
            fileName = "$AUTO_BACKUP_PREFIX${timestamp()}.zip",
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

    fun importBackup(context: Context, uri: Uri) {
        workerScope.launch {
            val imported = importBackupBlocking(context.applicationContext, uri)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (imported) "Backup restaurado" else "Nao foi possivel importar o backup",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun importBackupBlocking(context: Context, uri: Uri): Boolean {
        val importDir = File(context.cacheDir, "backup_import").apply { mkdirs() }
        val source = File(importDir, "backup-${System.currentTimeMillis()}.tmp")
        val restoredFiles = mutableListOf<File>()
        var imported = false

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output -> copyWithLimit(input, output, MAX_ARCHIVE_BYTES) }
            } ?: return false

            val contas = if (isZipArchive(source)) {
                readArchive(context, source, restoredFiles)
            } else {
                val raw = source.bufferedReader(Charsets.UTF_8).use { it.readText() }
                fromJson(JSONArray(raw), allowLegacyPaths = true)
            }

            ContaStorage.substituirTodas(context, contas)
            imported = true
            true
        } catch (_: Exception) {
            false
        } finally {
            source.delete()
            if (!imported) restoredFiles.forEach { it.delete() }
        }
    }

    private fun writeBackup(
        context: Context,
        contas: List<ContaSalva>,
        fileName: String,
        replaceExisting: Boolean,
        pruneAutoBackups: Boolean
    ): Boolean {
        val treeUri = AppSettings.localBackupFolderUri(context)

        if (treeUri != null && writeTreeBackup(context, treeUri, fileName, contas, replaceExisting, pruneAutoBackups)) {
            return true
        }

        return writeAppBackup(context, fileName, contas, replaceExisting, pruneAutoBackups)
    }

    private fun writeAppBackup(
        context: Context,
        fileName: String,
        contas: List<ContaSalva>,
        replaceExisting: Boolean,
        pruneAutoBackups: Boolean
    ): Boolean {
        val dir = (context.getExternalFilesDir("backups") ?: File(context.filesDir, "backups")).apply { mkdirs() }
        val target = File(dir, fileName)
        val temporary = File(dir, ".$fileName.tmp")

        return try {
            temporary.outputStream().use { output ->
                check(writeArchive(contas, output)) { "Backup incompleto" }
            }
            if (replaceExisting) deleteManualAppBackups(dir)
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            if (pruneAutoBackups) pruneAppBackups(dir)
            true
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    private fun writeTreeBackup(
        context: Context,
        treeUri: Uri,
        fileName: String,
        contas: List<ContaSalva>,
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
                check(writeArchive(contas, output)) { "Backup incompleto" }
            } ?: return false

            if (pruneAutoBackups) pruneTreeBackups(context, treeUri)
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun writeArchive(contas: List<ContaSalva>, output: OutputStream): Boolean {
        val attachments = mutableListOf<BackupAttachment>()
        val contasJson = toJson(contas, attachments)
        val manifest = JSONObject()
            .put("version", BACKUP_VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("contas", contasJson)

        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            attachments.forEach { attachment ->
                zip.putNextEntry(ZipEntry(attachment.entryName))
                attachment.file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return true
    }

    private fun readArchive(context: Context, source: File, restoredFiles: MutableList<File>): List<ContaSalva> {
        ZipFile(source).use { archive ->
            val manifestEntry = archive.getEntry(MANIFEST_ENTRY_NAME)
                ?: error("Manifesto ausente")
            require(!manifestEntry.isDirectory) { "Manifesto invalido" }

            val manifestText = archive.getInputStream(manifestEntry).use {
                readTextWithLimit(it, MAX_MANIFEST_BYTES)
            }
            val manifest = JSONObject(manifestText)
            require(manifest.optInt("version", 0) in 1..BACKUP_VERSION) { "Versao de backup invalida" }
            val contasJson = manifest.optJSONArray("contas") ?: error("Contas ausentes")

            return (0 until contasJson.length()).mapNotNull { index ->
                val item = contasJson.optJSONObject(index) ?: return@mapNotNull null
                val contaId = item.optString("id").ifBlank { "${System.currentTimeMillis()}-$index" }
                val comprovantePath = restoreAttachment(
                    context, archive, item.optString("comprovanteBackupEntry"), contaId,
                    folder = "receipts", label = "comprovante", restoredFiles = restoredFiles
                )
                val boletoPdfPath = restoreAttachment(
                    context, archive, item.optString("boletoPdfBackupEntry"), contaId,
                    folder = "boletos", label = "boleto", restoredFiles = restoredFiles
                )
                fromJsonItem(item, contaId, comprovantePath, boletoPdfPath, allowLegacyPaths = false)
            }
        }
    }

    private fun restoreAttachment(
        context: Context,
        archive: ZipFile,
        entryName: String,
        contaId: String,
        folder: String,
        label: String,
        restoredFiles: MutableList<File>
    ): String? {
        if (entryName.isBlank() || !entryName.startsWith("attachments/")) return null
        val entry = archive.getEntry(entryName) ?: error("Anexo de backup ausente")
        require(!entry.isDirectory && entry.size <= MAX_ATTACHMENT_BYTES) { "Anexo de backup invalido" }

        val extension = safeExtension(entryName.substringAfterLast('.', "bin"))
        val safeContaId = contaId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "conta" }
        val dir = File(context.filesDir, folder).apply { mkdirs() }
        val file = File(dir, "$safeContaId-$label-${System.currentTimeMillis()}.$extension")

        return try {
            archive.getInputStream(entry).use { input ->
                file.outputStream().use { output -> copyWithLimit(input, output, MAX_ATTACHMENT_BYTES) }
            }
            restoredFiles += file
            file.absolutePath
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun deleteManualAppBackups(dir: File) {
        dir.listFiles()
            ?.filter(::isManualBackup)
            ?.forEach { it.delete() }
    }

    private fun pruneAppBackups(dir: File) {
        dir.listFiles()
            ?.filter { isAutomaticBackup(it.name) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_AUTO_BACKUPS)
            ?.forEach { it.delete() }
    }

    private fun pruneTreeBackups(context: Context, treeUri: Uri) {
        treeBackupFiles(context, treeUri)
            .filter { isAutomaticBackup(it.name) }
            .sortedWith(compareByDescending<TreeBackupFile> { it.modifiedAt }.thenByDescending { it.name })
            .drop(MAX_AUTO_BACKUPS)
            .forEach { deleteTreeFile(context, treeUri, it.documentId) }
    }

    private fun deleteManualTreeBackups(context: Context, treeUri: Uri) {
        treeBackupFiles(context, treeUri)
            .filter { isManualBackup(it.name) }
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

    private fun toJson(contas: List<ContaSalva>, attachments: MutableList<BackupAttachment>): JSONArray {
        val array = JSONArray()
        contas.forEachIndexed { index, conta ->
            val comprovante = createAttachment(index, "comprovante", conta.comprovantePath)
            val boleto = createAttachment(index, "boleto", conta.boletoPdfPath)
            if (comprovante != null) attachments += comprovante
            if (boleto != null) attachments += boleto

            array.put(
                JSONObject()
                    .put("id", conta.id)
                    .put("spaceId", conta.spaceId)
                    .put("codigo", conta.codigo)
                    .put("banco", conta.banco)
                    .put("valor", conta.valor)
                    .put("vencimento", conta.vencimento)
                    .put("tipo", conta.tipo)
                    .put("criadaEm", conta.criadaEm)
                    .put("fornecedor", conta.fornecedor)
                    .put("categoria", conta.categoria)
                    .put("subcategoria", conta.subcategoria)
                    .put("responsavel", conta.responsavel)
                    .put("pagoPor", conta.pagoPor)
                    .put("status", conta.status)
                    .put("pagoEm", conta.pagoEm)
                    .put("formaPagamento", conta.formaPagamento)
                    .put("observacaoPagamento", conta.observacaoPagamento)
                    .put("recorrente", conta.recorrente)
                    .put("recorrencia", conta.recorrencia)
                    .put("comprovanteBackupEntry", comprovante?.entryName)
                    .put("boletoPdfBackupEntry", boleto?.entryName)
                    .put("direcao", conta.direcao)
                    .put("clienteWhatsapp", conta.clienteWhatsapp)
                    .put("pixCopiaCola", conta.pixCopiaCola)
                    .put("pixChave", conta.pixChave)
                    .put("pixRecebedor", conta.pixRecebedor)
                    .put("pixTxId", conta.pixTxId)
                    .put("pixAgendado", conta.pixAgendado)
                    .put("pixAgendamentoStatus", conta.pixAgendamentoStatus)
                    .put("atualizadaEm", conta.atualizadaEm)
                    .put("excluidaEm", conta.excluidaEm)
            )
        }
        return array
    }

    private fun createAttachment(index: Int, type: String, path: String?): BackupAttachment? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile || !file.canRead()) return null
        require(file.length() <= MAX_ATTACHMENT_BYTES) { "Anexo excede o limite permitido" }
        val extension = safeExtension(file.extension)
        return BackupAttachment("attachments/${index}_$type.$extension", file)
    }

    private fun fromJson(array: JSONArray, allowLegacyPaths: Boolean): List<ContaSalva> {
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val contaId = item.optString("id").ifBlank { "${System.currentTimeMillis()}-$index" }
            fromJsonItem(item, contaId, null, null, allowLegacyPaths)
        }
    }

    private fun fromJsonItem(
        item: JSONObject,
        contaId: String,
        restoredComprovantePath: String?,
        restoredBoletoPdfPath: String?,
        allowLegacyPaths: Boolean
    ): ContaSalva {
        return ContaSalva(
            id = contaId,
            spaceId = item.optString("spaceId", SpaceStorage.DEFAULT_SPACE_ID),
            codigo = item.optString("codigo"),
            banco = item.optString("banco"),
            valor = item.optString("valor"),
            vencimento = item.optString("vencimento"),
            tipo = item.optString("tipo"),
            criadaEm = item.optLong("criadaEm", System.currentTimeMillis()),
            fornecedor = item.optString("fornecedor"),
            categoria = item.optString("categoria", "Outros"),
            subcategoria = item.optString("subcategoria").takeIf { it.isNotBlank() && it != "null" },
            responsavel = item.optString("responsavel").takeIf { it.isNotBlank() && it != "null" },
            pagoPor = item.optString("pagoPor").takeIf { it.isNotBlank() && it != "null" },
            status = item.optString("status", ContaStatus.PENDENTE),
            pagoEm = if (item.has("pagoEm") && !item.isNull("pagoEm")) item.optLong("pagoEm") else null,
            formaPagamento = if (item.has("formaPagamento") && !item.isNull("formaPagamento")) item.optString("formaPagamento") else null,
            observacaoPagamento = if (item.has("observacaoPagamento") && !item.isNull("observacaoPagamento")) item.optString("observacaoPagamento") else null,
            recorrente = item.optBoolean("recorrente", false),
            recorrencia = item.optString("recorrencia", "Mensal"),
            comprovantePath = restoredComprovantePath
                ?: legacyPath(item, "comprovantePath", allowLegacyPaths),
            boletoPdfPath = restoredBoletoPdfPath
                ?: legacyPath(item, "boletoPdfPath", allowLegacyPaths),
            direcao = item.optString("direcao", ContaDirecao.PAGAR),
            clienteWhatsapp = if (item.has("clienteWhatsapp") && !item.isNull("clienteWhatsapp")) item.optString("clienteWhatsapp") else null,
            pixCopiaCola = if (item.has("pixCopiaCola") && !item.isNull("pixCopiaCola")) item.optString("pixCopiaCola") else null,
            pixChave = if (item.has("pixChave") && !item.isNull("pixChave")) item.optString("pixChave") else null,
            pixRecebedor = if (item.has("pixRecebedor") && !item.isNull("pixRecebedor")) item.optString("pixRecebedor") else null,
            pixTxId = if (item.has("pixTxId") && !item.isNull("pixTxId")) item.optString("pixTxId") else null,
            pixAgendado = item.optBoolean("pixAgendado", false),
            pixAgendamentoStatus = if (item.has("pixAgendamentoStatus") && !item.isNull("pixAgendamentoStatus")) item.optString("pixAgendamentoStatus") else null,
            atualizadaEm = if (item.has("atualizadaEm")) item.optLong("atualizadaEm") else item.optLong("criadaEm"),
            excluidaEm = if (item.has("excluidaEm") && !item.isNull("excluidaEm")) item.optLong("excluidaEm") else null
        )
    }

    private fun legacyPath(item: JSONObject, key: String, allowed: Boolean): String? {
        if (!allowed || !item.has(key) || item.isNull(key)) return null
        return item.optString(key).takeIf { it.isNotBlank() && File(it).isFile }
    }

    private fun isZipArchive(file: File): Boolean {
        return file.inputStream().use { input -> input.read() == 0x50 && input.read() == 0x4B }
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Arquivo excede o limite permitido" }
            output.write(buffer, 0, read)
        }
    }

    private fun readTextWithLimit(input: InputStream, maxBytes: Long): String {
        val bytes = java.io.ByteArrayOutputStream()
        copyWithLimit(input, bytes, maxBytes)
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun safeExtension(extension: String): String {
        return extension.lowercase(Locale.US)
            .filter(Char::isLetterOrDigit)
            .take(10)
            .ifBlank { "bin" }
    }

    private fun isAutomaticBackup(name: String): Boolean {
        return name.startsWith(AUTO_BACKUP_PREFIX) && isSupportedBackupExtension(name)
    }

    private fun isManualBackup(file: File): Boolean = isManualBackup(file.name)

    private fun isManualBackup(name: String): Boolean {
        return name == MANUAL_BACKUP_FILE_NAME ||
            name == LEGACY_MANUAL_BACKUP_FILE_NAME ||
            (name.startsWith(MANUAL_BACKUP_PREFIX) && isSupportedBackupExtension(name))
    }

    private fun isSupportedBackupExtension(name: String): Boolean {
        return name.endsWith(".zip", ignoreCase = true) || name.endsWith(".json", ignoreCase = true)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private data class BackupAttachment(
        val entryName: String,
        val file: File
    )

    private data class TreeBackupFile(
        val documentId: String,
        val name: String,
        val modifiedAt: Long
    )
}
