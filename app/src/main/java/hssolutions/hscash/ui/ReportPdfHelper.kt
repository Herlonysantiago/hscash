package hssolutions.hscash.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReportPdfHelper {
    fun shareMonthlyReport(context: Context, contas: List<ContaSalva>, month: Calendar) {
        val file = createMonthlyReport(context, contas, month)
        if (file == null) {
            Toast.makeText(context, "Nao foi possivel gerar o PDF", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Relatorio HS Cash")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar relatorio PDF"))
    }

    private fun createMonthlyReport(context: Context, contas: List<ContaSalva>, month: Calendar): File? {
        return try {
            val label = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("pt-BR"))
                .format(month.time)
                .replaceFirstChar { it.uppercase() }
            val pagas = contas.filter { it.direcao == ContaDirecao.PAGAR && it.status == ContaStatus.PAGO && isPaidInMonth(it, month) }
            val pendentes = contas.filter { it.direcao == ContaDirecao.PAGAR && it.status != ContaStatus.PAGO && isDueInMonth(it, month) }
            val receber = contas.filter { it.direcao == ContaDirecao.RECEBER && it.status != ContaStatus.PAGO && isDueInMonth(it, month) }
            val recebidas = contas.filter { it.direcao == ContaDirecao.RECEBER && it.status == ContaStatus.PAGO && isPaidInMonth(it, month) }
            val previousMonth = (month.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            val pagasMesAnterior = contas.filter { it.direcao == ContaDirecao.PAGAR && it.status == ContaStatus.PAGO && isPaidInMonth(it, previousMonth) }
            val pagoTotal = pagas.sumOf { parseCents(it.valor) }
            val pendenteTotal = pendentes.sumOf { parseCents(it.valor) }
            val receberTotal = receber.sumOf { parseCents(it.valor) }
            val anteriorTotal = pagasMesAnterior.sumOf { parseCents(it.valor) }

            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val title = Paint().apply { textSize = 20f; isFakeBoldText = true }
            val section = Paint().apply { textSize = 14f; isFakeBoldText = true }
            val body = Paint().apply { textSize = 11f }
            var y = 48f

            canvas.drawText("HS Cash - Relatorio mensal", 40f, y, title)
            y += 24f
            canvas.drawText(label, 40f, y, body)
            y += 34f

            y = drawSummary(canvas, y, section, body, "Total pago", pagoTotal)
            y = drawSummary(canvas, y, section, body, "Total pendente", pendenteTotal)
            y = drawSummary(canvas, y, section, body, "A receber", receberTotal)
            y = drawSummary(canvas, y, section, body, "Saldo projetado", receberTotal - pendenteTotal)
            y = drawSummary(canvas, y, section, body, "Pago no mes anterior", anteriorTotal)
            y = drawSummary(canvas, y, section, body, "Recebido no mes", recebidas.sumOf { parseCents(it.valor) })
            y += 18f

            val categorias = pagas.groupBy { it.categoria.ifBlank { "Outros" } }
                .mapValues { entry -> entry.value.sumOf { parseCents(it.valor) } }
                .toList().sortedByDescending { it.second }
            val formas = pagas.groupBy { it.formaPagamento?.takeIf(String::isNotBlank) ?: "Nao informado" }
                .mapValues { entry -> entry.value.sumOf { parseCents(it.valor) } }
                .toList().sortedByDescending { it.second }
            y = drawBreakdown(canvas, y, section, body, "Pagamentos por categoria", categorias)
            y = drawBreakdown(canvas, y + 8f, section, body, "Formas de pagamento", formas)
            y = drawSection(canvas, y + 10f, section, body, "Pagas", pagas)
            y = drawSection(canvas, y + 10f, section, body, "Pendentes", pendentes)
            drawSection(canvas, y + 10f, section, body, "A receber", receber)

            document.finishPage(page)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "hs_cash_relatorio_${SimpleDateFormat("yyyyMM", Locale.US).format(month.time)}.pdf")
            file.outputStream().use { document.writeTo(it) }
            document.close()
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun drawSummary(canvas: android.graphics.Canvas, y: Float, titlePaint: Paint, bodyPaint: Paint, label: String, cents: Long): Float {
        canvas.drawText(label, 40f, y, titlePaint)
        canvas.drawText(formatMoney(cents), 220f, y, bodyPaint)
        return y + 22f
    }

    private fun drawSection(canvas: android.graphics.Canvas, startY: Float, sectionPaint: Paint, bodyPaint: Paint, title: String, contas: List<ContaSalva>): Float {
        var y = startY
        canvas.drawText(title, 40f, y, sectionPaint)
        y += 20f
        if (contas.isEmpty()) {
            canvas.drawText("Nenhum registro.", 52f, y, bodyPaint)
            return y + 18f
        }
        contas.take(8).forEach { conta ->
            val payment = if (conta.status == ContaStatus.PAGO) {
                listOfNotNull(
                    conta.pagoEm?.let { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(it)) },
                    conta.formaPagamento
                ).joinToString(" - ").takeIf { it.isNotBlank() }?.let { "  [$it]" }.orEmpty()
            } else ""
            val text = "${conta.vencimento}  ${conta.fornecedor.ifBlank { conta.banco }}  ${conta.valor}$payment"
            canvas.drawText(text.take(86), 52f, y, bodyPaint)
            y += 16f
        }
        if (contas.size > 8) {
            canvas.drawText("+${contas.size - 8} registros", 52f, y, bodyPaint)
            y += 16f
        }
        return y
    }

    private fun drawBreakdown(
        canvas: android.graphics.Canvas,
        startY: Float,
        sectionPaint: Paint,
        bodyPaint: Paint,
        title: String,
        values: List<Pair<String, Long>>
    ): Float {
        var y = startY
        canvas.drawText(title, 40f, y, sectionPaint)
        y += 20f
        if (values.isEmpty()) {
            canvas.drawText("Nenhum registro.", 52f, y, bodyPaint)
            return y + 18f
        }
        values.take(6).forEach { (label, cents) ->
            canvas.drawText("${label.take(42)}  ${formatMoney(cents)}", 52f, y, bodyPaint)
            y += 16f
        }
        return y
    }

    private fun isPaidInMonth(conta: ContaSalva, month: Calendar): Boolean {
        val paidAt = conta.pagoEm ?: return false
        val paidDate = Calendar.getInstance().apply { timeInMillis = paidAt }
        return sameMonth(paidDate, month)
    }

    private fun isDueInMonth(conta: ContaSalva, month: Calendar): Boolean {
        val dueDate = parseContaDate(conta.vencimento) ?: return false
        return sameMonth(dueDate, month)
    }

    private fun sameMonth(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.MONTH) == second.get(Calendar.MONTH)
    }

    private fun parseCents(value: String): Long = value.filter(Char::isDigit).toLongOrNull() ?: 0L

    private fun formatMoney(cents: Long): String {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            .format(BigDecimal(cents).movePointLeft(2))
    }
}
