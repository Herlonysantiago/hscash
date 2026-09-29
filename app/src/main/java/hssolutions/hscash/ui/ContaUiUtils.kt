package hssolutions.hscash.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun statusLabel(conta: ContaSalva): String {
    return when {
        conta.status == ContaStatus.EXCLUIDO -> "Na lixeira"
        conta.status == ContaStatus.PAGO && conta.direcao == ContaDirecao.RECEBER -> "Recebido"
        conta.status == ContaStatus.PAGO -> "Pago"
        isContaVencida(conta) -> "Atrasado"
        conta.direcao == ContaDirecao.RECEBER -> "A receber"
        else -> "Pendente"
    }
}

fun statusColorHex(conta: ContaSalva): Long {
    return when {
        conta.status == ContaStatus.EXCLUIDO -> 0xFF6B7280
        conta.status == ContaStatus.PAGO -> 0xFF388E3C
        isContaVencida(conta) -> 0xFFD32F2F
        else -> 0xFFF57C00
    }
}

fun isContaVencida(conta: ContaSalva): Boolean {
    if (conta.status == ContaStatus.PAGO || conta.status == ContaStatus.EXCLUIDO) return false
    val vencimento = parseContaDate(conta.vencimento) ?: return false
    return vencimento.before(startOfToday())
}

fun parseContaDate(value: String): Calendar? {
    val normalized = value.trim()
    val formats = listOf("dd/MM/yyyy", "dd-MM-yyyy", "dd.MM.yyyy", "yyyy-MM-dd")
    for (pattern in formats) {
        try {
            val date = SimpleDateFormat(pattern, Locale.forLanguageTag("pt-BR")).apply {
                isLenient = false
            }.parse(normalized) ?: continue
            return Calendar.getInstance().apply { time = date }
        } catch (_: Exception) {
            // Tenta o formato seguinte.
        }
    }
    return null
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
        "Bimestral" -> date.add(Calendar.MONTH, 2)
        "Trimestral" -> date.add(Calendar.MONTH, 3)
        "Semestral" -> date.add(Calendar.MONTH, 6)
        "Anual" -> date.add(Calendar.YEAR, 1)
        "Mensal" -> date.add(Calendar.MONTH, 1)
        else -> {
            val custom = Regex("A cada (\\d+) (dia|dias|semana|semanas|mês|meses|ano|anos)", RegexOption.IGNORE_CASE)
                .matchEntire(recurrence.trim()) ?: return null
            val amount = custom.groupValues[1].toIntOrNull()?.coerceIn(1, 120) ?: return null
            when (custom.groupValues[2].lowercase(Locale.forLanguageTag("pt-BR"))) {
                "dia", "dias" -> date.add(Calendar.DAY_OF_YEAR, amount)
                "semana", "semanas" -> date.add(Calendar.WEEK_OF_YEAR, amount)
                "ano", "anos" -> date.add(Calendar.YEAR, amount)
                else -> date.add(Calendar.MONTH, amount)
            }
        }
    }
    return SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(date.time)
}

fun recurrenceLabel(value: String): String = when (value) {
    "Semanal" -> "Toda semana"
    "Mensal" -> "Todo mês"
    "Bimestral" -> "A cada 2 meses"
    "Trimestral" -> "A cada 3 meses"
    "Semestral" -> "A cada 6 meses"
    "Anual" -> "A cada 12 meses"
    else -> value
}
