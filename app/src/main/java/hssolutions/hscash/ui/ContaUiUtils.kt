package hssolutions.hscash.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun statusLabel(conta: ContaSalva): String {
    return when {
        conta.status == ContaStatus.PAGO && conta.direcao == ContaDirecao.RECEBER -> "Recebido"
        conta.status == ContaStatus.PAGO -> "Pago"
        isContaVencida(conta) -> "Atrasado"
        conta.direcao == ContaDirecao.RECEBER -> "A receber"
        else -> "Pendente"
    }
}

fun statusColorHex(conta: ContaSalva): Long {
    return when {
        conta.status == ContaStatus.PAGO -> 0xFF388E3C
        isContaVencida(conta) -> 0xFFD32F2F
        else -> 0xFFF57C00
    }
}

fun isContaVencida(conta: ContaSalva): Boolean {
    if (conta.status == ContaStatus.PAGO) return false
    val vencimento = parseContaDate(conta.vencimento) ?: return false
    return vencimento.before(startOfToday())
}

fun parseContaDate(value: String): Calendar? {
    return try {
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).apply {
            isLenient = false
        }.parse(value) ?: return null
        Calendar.getInstance().apply { time = date }
    } catch (_: Exception) {
        null
    }
}

fun isValidContaDate(value: String): Boolean = parseContaDate(value) != null

fun startOfToday(): Calendar {
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

fun nextRecurrenceDate(current: String, recurrence: String): String? {
    val date = parseContaDate(current) ?: return null
    when (recurrence) {
        "Semanal" -> date.add(Calendar.DAY_OF_YEAR, 7)
        "Anual" -> date.add(Calendar.YEAR, 1)
        else -> date.add(Calendar.MONTH, 1)
    }
    return SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(date.time)
}
