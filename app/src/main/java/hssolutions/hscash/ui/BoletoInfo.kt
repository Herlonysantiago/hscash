package hssolutions.hscash.ui

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BoletoInfo(
    val codigoOriginal: String,
    val codigoBarras: String,
    val linhaDigitavel: String,
    val bancoEmissor: String,
    val valor: String,
    val vencimento: String,
    val tipo: String,
    val observacao: String? = null
)

fun parseBoleto(rawCode: String): BoletoInfo? {
    val digits = rawCode.filter(Char::isDigit)
    if (digits.length == 44 && digits.firstOrNull() != '8') {
        if (!isValidCodigoBarrasBancario(digits)) return null
        return parseBoletoBancario(digits, rawCode)
    }
    if (digits.length == 47) {
        if (!isValidLinhaDigitavelBancaria(digits)) return null
        val codigoBarras = buildCodigoBarrasFromLinhaDigitavel(digits)
        if (!isValidCodigoBarrasBancario(codigoBarras)) return null
        return parseBoletoBancario(codigoBarras, rawCode, linhaDigitavel = digits)
    }
    if (digits.length == 44 && digits.firstOrNull() == '8') {
        if (!isValidCodigoBarrasArrecadacao(digits)) return null
        return parseArrecadacao(digits, rawCode)
    }
    if (digits.length == 48 && digits.firstOrNull() == '8') {
        if (!isValidLinhaDigitavelArrecadacao(digits)) return null
        val codigoBarras = buildCodigoBarrasArrecadacao(digits)
        if (!isValidCodigoBarrasArrecadacao(codigoBarras)) return null
        return parseArrecadacao(codigoBarras, rawCode, linhaDigitavel = digits)
    }
    return null
}

fun isValidBoletoCode(rawCode: String): Boolean = parseBoleto(rawCode) != null

fun isLikelyBoletoCode(rawCode: String): Boolean {
    return parseBoleto(rawCode) != null
}

private fun parseBoletoBancario(
    codigoBarras: String,
    original: String,
    linhaDigitavel: String? = null
): BoletoInfo {
    val bancoCodigo = codigoBarras.substring(0, 3)
    val fatorVencimento = codigoBarras.substring(5, 9).toIntOrNull() ?: 0
    val valorCentavos = codigoBarras.substring(9, 19)
    val linhaFinal = linhaDigitavel ?: linhaDigitavelFromCodigoBarras(codigoBarras) ?: codigoBarras

    return BoletoInfo(
        codigoOriginal = original,
        codigoBarras = codigoBarras,
        linhaDigitavel = linhaFinal,
        bancoEmissor = bankName(bancoCodigo),
        valor = formatMoney(valorCentavos),
        vencimento = formatDueDate(fatorVencimento),
        tipo = "Boleto bancario",
        observacao = null
    )
}

private fun parseArrecadacao(
    codigoBarras: String,
    original: String,
    linhaDigitavel: String? = null
): BoletoInfo {
    val identificadorValor = codigoBarras.getOrNull(2)
    val valor = if (identificadorValor == '6' || identificadorValor == '8') {
        formatMoney(codigoBarras.substring(4, 15))
    } else {
        "Nao informado no codigo"
    }
    val linhaFinal = linhaDigitavel ?: linhaDigitavelFromCodigoBarras(codigoBarras) ?: codigoBarras

    return BoletoInfo(
        codigoOriginal = original,
        codigoBarras = codigoBarras,
        linhaDigitavel = linhaFinal,
        bancoEmissor = segmentName(codigoBarras.getOrNull(1)),
        valor = valor,
        vencimento = "Nao informado no padrao",
        tipo = "Arrecadacao/Convenio",
        observacao = "Contas de convenio nem sempre trazem banco emissor e vencimento no codigo."
    )
}

private fun buildCodigoBarrasFromLinhaDigitavel(linha: String): String {
    return buildString {
        append(linha.substring(0, 4))
        append(linha.substring(32, 33))
        append(linha.substring(33, 37))
        append(linha.substring(37, 47))
        append(linha.substring(4, 9))
        append(linha.substring(10, 20))
        append(linha.substring(21, 31))
    }
}

private fun buildCodigoBarrasArrecadacao(linha: String): String {
    return buildString {
        append(linha.substring(0, 11))
        append(linha.substring(12, 23))
        append(linha.substring(24, 35))
        append(linha.substring(36, 47))
    }
}

fun linhaDigitavelFromCodigoBarras(codigoBarras: String): String? {
    val digits = codigoBarras.filter(Char::isDigit)
    return when {
        digits.length == 47 || digits.length == 48 -> digits
        digits.length == 44 && digits.firstOrNull() == '8' -> buildLinhaDigitavelArrecadacao(digits)
        digits.length == 44 -> buildLinhaDigitavelBancaria(digits)
        else -> null
    }
}

private fun buildLinhaDigitavelBancaria(codigoBarras: String): String {
    val campo1 = codigoBarras.substring(0, 4) + codigoBarras.substring(19, 24)
    val campo2 = codigoBarras.substring(24, 34)
    val campo3 = codigoBarras.substring(34, 44)
    val digitoGeral = codigoBarras.substring(4, 5)
    val fatorValor = codigoBarras.substring(5, 19)

    return buildString {
        append(campo1)
        append(modulo10(campo1))
        append(campo2)
        append(modulo10(campo2))
        append(campo3)
        append(modulo10(campo3))
        append(digitoGeral)
        append(fatorValor)
    }
}

private fun buildLinhaDigitavelArrecadacao(codigoBarras: String): String {
    return codigoBarras
        .chunked(11)
        .joinToString(separator = "") { bloco ->
            val digito = arrecadacaoCheckDigit(bloco, codigoBarras.getOrNull(2))
            bloco + digito
        }
}

private fun isValidLinhaDigitavelBancaria(linha: String): Boolean {
    if (linha.length != 47 || linha.any { !it.isDigit() }) return false
    return modulo10(linha.substring(0, 9)) == linha[9].digitToIntOrNull()
        && modulo10(linha.substring(10, 20)) == linha[20].digitToIntOrNull()
        && modulo10(linha.substring(21, 31)) == linha[31].digitToIntOrNull()
}

private fun isValidCodigoBarrasBancario(codigoBarras: String): Boolean {
    if (codigoBarras.length != 44 || codigoBarras.any { !it.isDigit() }) return false
    val checkDigit = codigoBarras[4].digitToIntOrNull() ?: return false
    val semDigito = codigoBarras.substring(0, 4) + codigoBarras.substring(5)
    return modulo11Boleto(semDigito) == checkDigit
}

private fun isValidLinhaDigitavelArrecadacao(linha: String): Boolean {
    if (linha.length != 48 || linha.firstOrNull() != '8' || linha.any { !it.isDigit() }) return false
    return listOf(0, 12, 24, 36).all { start ->
        val bloco = linha.substring(start, start + 11)
        val checkDigit = linha[start + 11].digitToIntOrNull()
        val expected = arrecadacaoCheckDigit(bloco, linha.getOrNull(2))
        expected == checkDigit
    }
}

private fun isValidCodigoBarrasArrecadacao(codigoBarras: String): Boolean {
    if (codigoBarras.length != 44 || codigoBarras.firstOrNull() != '8' || codigoBarras.any { !it.isDigit() }) return false
    val checkDigit = codigoBarras[3].digitToIntOrNull() ?: return false
    val semDigito = codigoBarras.substring(0, 3) + codigoBarras.substring(4)
    return arrecadacaoCheckDigit(semDigito, codigoBarras.getOrNull(2)) == checkDigit
}

private fun arrecadacaoCheckDigit(value: String, identificadorValor: Char?): Int {
    return when (identificadorValor) {
        '6', '7' -> modulo10(value)
        '8', '9' -> modulo11Arrecadacao(value)
        else -> -1
    }
}

private fun modulo10(value: String): Int {
    var sum = 0
    var multiplier = 2

    for (index in value.length - 1 downTo 0) {
        val char = value[index]
        val digit = char.digitToIntOrNull() ?: return -1
        val result = digit * multiplier
        sum += if (result > 9) (result / 10) + (result % 10) else result
        multiplier = if (multiplier == 2) 1 else 2
    }

    val remainder = sum % 10
    return if (remainder == 0) 0 else 10 - remainder
}

private fun modulo11Boleto(value: String): Int {
    var sum = 0
    var multiplier = 2

    for (index in value.length - 1 downTo 0) {
        val char = value[index]
        val digit = char.digitToIntOrNull() ?: return -1
        sum += digit * multiplier
        multiplier = if (multiplier == 9) 2 else multiplier + 1
    }

    val digit = 11 - (sum % 11)
    return if (digit == 0 || digit == 10 || digit == 11) 1 else digit
}

private fun modulo11Arrecadacao(value: String): Int {
    var sum = 0
    var multiplier = 2

    for (index in value.length - 1 downTo 0) {
        val char = value[index]
        val digit = char.digitToIntOrNull() ?: return -1
        sum += digit * multiplier
        multiplier = if (multiplier == 9) 2 else multiplier + 1
    }

    val result = 11 - (sum % 11)
    return if (result == 10 || result == 11) 0 else result
}

private fun formatMoney(valueDigits: String): String {
    val cents = valueDigits.toLongOrNull() ?: return "Nao informado"
    val value = BigDecimal(cents).movePointLeft(2)
    return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
}

private fun formatDueDate(factor: Int): String {
    if (factor <= 0) return "Sem vencimento"

    val date = if (factor >= 1000) {
        LocalDate.of(2025, 2, 22).plusDays((factor - 1000).toLong())
    } else {
        LocalDate.of(1997, 10, 7).plusDays(factor.toLong())
    }

    return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}

private fun bankName(code: String): String {
    val name = mapOf(
        "001" to "Banco do Brasil",
        "033" to "Santander",
        "041" to "Banrisul",
        "077" to "Inter",
        "104" to "Caixa Economica Federal",
        "237" to "Bradesco",
        "260" to "Nu Pagamentos",
        "290" to "PagBank",
        "318" to "BMG",
        "341" to "Itau",
        "389" to "Mercantil do Brasil",
        "422" to "Safra",
        "623" to "PAN",
        "633" to "Rendimento",
        "655" to "Votorantim",
        "707" to "Daycoval",
        "745" to "Citibank",
        "756" to "Sicoob"
    )[code]

    return if (name == null) "Banco codigo $code" else "$name ($code)"
}

private fun segmentName(segment: Char?): String {
    return when (segment) {
        '1' -> "Prefeituras"
        '2' -> "Saneamento"
        '3' -> "Energia eletrica e gas"
        '4' -> "Telecomunicacoes"
        '5' -> "Orgaos governamentais"
        '6' -> "Carnes e assemelhados"
        '7' -> "Multas de transito"
        '9' -> "Uso exclusivo do banco"
        else -> "Convenio/Arrecadacao"
    }
}
