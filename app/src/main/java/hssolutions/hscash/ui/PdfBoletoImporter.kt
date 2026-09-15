package hssolutions.hscash.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfImportResult(val imported: Int, val duplicates: Int, val invalid: List<String>)

object PdfBoletoImporter {
    suspend fun import(context: Context, uris: List<Uri>): PdfImportResult = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context.applicationContext)
        var imported = 0
        var duplicates = 0
        val invalid = mutableListOf<String>()

        uris.forEach { uri ->
            val name = displayName(context, uri) ?: "Boleto PDF"
            val boleto = runCatching { extractBoleto(context, uri) }.getOrNull()
            if (boleto == null) {
                invalid += name
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

    private fun extractBoleto(context: Context, uri: Uri): BoletoInfo? {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document -> PDFTextStripper().getText(document) }
        } ?: return null

        return PdfBoletoTextParser.parse(text)
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString)
            }
}
