package hssolutions.hscash.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfImportResult(val imported: Int, val duplicates: Int, val invalid: List<String>)

internal enum class PdfBoletoReadStatus {
    OK,
    PASSWORD_PROTECTED,
    NO_TEXT,
    NO_BOLETO,
    READ_ERROR
}

internal data class PdfBoletoInspection(
    val boleto: BoletoInfo?,
    val status: PdfBoletoReadStatus,
    val message: String
)

object PdfBoletoImporter {
    internal fun inspect(context: Context, uri: Uri): BoletoInfo? {
        PDFBoxResourceLoader.init(context.applicationContext)
        return inspectDetailed(context, uri).boleto
    }

    internal fun inspectDetailed(context: Context, uri: Uri): PdfBoletoInspection {
        PDFBoxResourceLoader.init(context.applicationContext)
        return extractBoletoInspection(context, uri, password = null)
    }

    internal fun inspectDetailed(context: Context, uri: Uri, password: String): PdfBoletoInspection {
        PDFBoxResourceLoader.init(context.applicationContext)
        return extractBoletoInspection(context, uri, password = password)
    }

    internal fun fileName(context: Context, uri: Uri): String? = displayName(context, uri)

    suspend fun import(context: Context, uris: List<Uri>): PdfImportResult = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        var imported = 0
        var duplicates = 0
        val invalid = mutableListOf<String>()

        uris.forEach { uri ->
            val name = displayName(context, uri) ?: "Boleto PDF"
            val inspection = extractBoletoInspection(context, uri, password = null)
            val boleto = inspection.boleto
            if (boleto == null) {
                invalid += "$name - ${inspection.message}"
                return@forEach
            }

            val saved = ContaStorage.salvarBoleto(
                context = context,
                boleto = boleto,
                fornecedor = name.substringBeforeLast('.').ifBlank { boleto.bancoEmissor },
                categoria = "Boletos importados",
                saveAfterUpdate = false
            )
            if (!saved) {
                duplicates += 1
                return@forEach
            }

            val conta = ContaStorage.listarContas(context)
                .firstOrNull { it.codigo == boleto.codigoBarras && it.vencimento == boleto.vencimento }
            if (conta != null) {
                ReceiptHelper.saveBoletoPdf(context, conta.id, uri)?.let { path ->
                    ContaStorage.atualizarConta(
                        context,
                        conta.copy(boletoPdfPath = path),
                        saveAfterUpdate = false
                    )
                }
            }
            imported += 1
        }
        if (imported > 0) ContaStorage.finalizarAlteracoesEmLote(context)
        PdfImportResult(imported, duplicates, invalid)
    }

    private fun extractBoletoInspection(context: Context, uri: Uri, password: String?): PdfBoletoInspection {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val document = if (password.isNullOrBlank()) {
                    PDDocument.load(input)
                } else {
                    PDDocument.load(input, password)
                }
                document.use {
                    if (it.isEncrypted && password.isNullOrBlank()) {
                        return PdfBoletoInspection(
                            boleto = null,
                            status = PdfBoletoReadStatus.PASSWORD_PROTECTED,
                            message = "Este PDF parece protegido. Informe a senha para tentar desbloquear."
                        )
                    }
                    PDFTextStripper().getText(it)
                }
            }.orEmpty()
        } catch (_: InvalidPasswordException) {
            return PdfBoletoInspection(
                boleto = null,
                status = PdfBoletoReadStatus.PASSWORD_PROTECTED,
                message = if (password.isNullOrBlank()) {
                    "Este PDF está protegido por senha. Informe a senha para tentar importar."
                } else {
                    "Senha incorreta ou PDF bloqueado para extração."
                }
            )
        } catch (_: SecurityException) {
            return PdfBoletoInspection(
                boleto = null,
                status = PdfBoletoReadStatus.READ_ERROR,
                message = "O app não recebeu permissão para ler este PDF."
            )
        } catch (_: IOException) {
            return PdfBoletoInspection(
                boleto = null,
                status = PdfBoletoReadStatus.READ_ERROR,
                message = "Não foi possível abrir este PDF."
            )
        }

        if (text.isBlank()) {
            return PdfBoletoInspection(
                boleto = null,
                status = PdfBoletoReadStatus.NO_TEXT,
                message = "Este PDF não tem texto extraível. Provavelmente precisa de OCR."
            )
        }

        val boleto = PdfBoletoTextParser.parse(text)
        return if (boleto != null) {
            PdfBoletoInspection(
                boleto = boleto,
                status = PdfBoletoReadStatus.OK,
                message = "Código validado. Confira os dados antes de salvar."
            )
        } else {
            PdfBoletoInspection(
                boleto = null,
                status = PdfBoletoReadStatus.NO_BOLETO,
                message = "Não encontramos uma linha digitável ou código de barras válido neste PDF."
            )
        }
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
            }
}
