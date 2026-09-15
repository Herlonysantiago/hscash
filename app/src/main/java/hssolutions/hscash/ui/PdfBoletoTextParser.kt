package hssolutions.hscash.ui

import java.math.BigDecimal
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

/** Regras puras de leitura do texto extraido de um PDF, sem dependencia do Android. */
internal object PdfBoletoTextParser {
    fun parse(text: String): BoletoInfo? {
        val boleto = boletoCandidates(text).mapNotNull(::parseBoleto).firstOrNull() ?: return null

        // O valor validado no codigo e mais confiavel. Isso impede que trechos como
        // "23792.36702" da linha digitavel sejam confundidos com R$ 23.792.367,02.
        if (encodedAmountIsUsable(boleto.valor)) return boleto
        val displayedAmount = extractDisplayedAmount(text)
        return displayedAmount?.let { boleto.copy(valor = it) } ?: boleto
    }

    private fun encodedAmountIsUsable(value: String): Boolean =
        value.filter(Char::isDigit).toLongOrNull()?.let { it > 0L } == true

    /**
     * Aceita somente formatos completos e delimitados. Nao recorta janelas de uma
     * sequencia numerica maior, pois isso pode produzir um boleto diferente por acaso.
     */
    private fun boletoCandidates(text: String): Sequence<String> {
        val linhaBancaria = Regex(
            "(?<!\\d)\\d{5}\\.?\\d{5}\\s+\\d{5}\\.?\\d{6}\\s+\\d{5}\\.?\\d{6}\\s+\\d\\s+\\d{14}(?!\\d)"
        )
        val linhaArrecadacao = Regex("(?<!\\d)8\\d{11}(?:\\s+\\d{12}){3}(?!\\d)")
        val codigoSemMascara = Regex("(?<!\\d)(?:\\d{48}|\\d{47}|\\d{44})(?!\\d)")

        return sequenceOf(linhaBancaria, linhaArrecadacao, codigoSemMascara)
            .flatMap { pattern -> pattern.findAll(text).map { it.value.filter(Char::isDigit) } }
            .distinct()
    }

    /**
     * PDFs de boleto frequentemente trazem juros, descontos e outros valores.
     * Somente campos que representam o total/documento podem substituir o valor do codigo.
     */
    private fun extractDisplayedAmount(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val normalized = lines.map(::normalize)
        val labels = listOf(
            "valor cobrado",
            "total a pagar",
            "valor total",
            "valor do documento",
            "valor documento",
            "valor da conta",
            "valor nominal"
        )

        labels.forEach { label ->
            normalized.forEachIndexed { index, line ->
                if (label !in line) return@forEachIndexed
                val window = lines.subList(index, minOf(index + 3, lines.size)).joinToString(" ")
                parseBrazilianMoney(window)?.let { return it }
            }
        }
        return null
    }

    private fun parseBrazilianMoney(value: String): String? {
        val match = Regex("(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*|\\d+),\\d{2}")
            .findAll(value)
            .lastOrNull()
            ?.value
            ?: return null
        val decimal = match.replace("R$", "").replace(" ", "")
            .replace(".", "").replace(",", ".").toBigDecimalOrNull() ?: return null
        if (decimal <= BigDecimal.ZERO) return null
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(decimal)
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.forLanguageTag("pt-BR"))
}
