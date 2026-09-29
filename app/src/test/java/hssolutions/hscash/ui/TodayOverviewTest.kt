package hssolutions.hscash.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TodayOverviewTest {

    @Test
    fun `sums formatted Brazilian currency containing non breaking spaces`() {
        val first = account("water", "18/09/2026").copy(valor = "R$\u00A0187,26")
        val second = account("internet", "19/09/2026").copy(valor = "R$ 92,75")

        assertEquals(18_726L, todayMoneyInCents(first))
        assertEquals(9_275L, todayMoneyInCents(second))
        assertEquals(28_001L, listOf(first, second).sumOf(::todayMoneyInCents))
    }
    private val reference = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 19, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun `separates overdue today next seven days and overdue receivables`() {
        val overview = buildTodayOverview(
            contas = listOf(
                account("overdue", "18/09/2026"),
                account("today", "19/09/2026"),
                account("tomorrow", "20/09/2026"),
                account("seventh-day", "26/09/2026"),
                account("outside", "27/09/2026"),
                account("receivable", "10/09/2026", ContaDirecao.RECEBER),
                account("paid", "18/09/2026", status = ContaStatus.PAGO)
            ),
            referenceToday = reference
        )

        assertEquals(listOf("overdue"), overview.overduePayables.map { it.id })
        assertEquals(listOf("today"), overview.dueTodayPayables.map { it.id })
        assertEquals(listOf("tomorrow", "seventh-day"), overview.nextSevenDaysPayables.map { it.id })
        assertEquals(listOf("receivable"), overview.overdueReceivables.map { it.id })
        assertEquals(2, overview.urgentCount)
    }

    private fun account(
        id: String,
        dueDate: String,
        direction: String = ContaDirecao.PAGAR,
        status: String = ContaStatus.PENDENTE
    ) = ContaSalva(
        id = id,
        codigo = "manual-$id",
        banco = "Cadastro manual",
        valor = "R$ 100,00",
        vencimento = dueDate,
        tipo = "Conta manual",
        criadaEm = 0L,
        fornecedor = id,
        status = status,
        direcao = direction
    )
}
