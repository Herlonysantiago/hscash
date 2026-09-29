package hssolutions.hscash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinancialInsightsTest {
    @Test
    fun `category report calculates value count and percentage`() {
        val items = categoryReport(
            listOf(
                account("1", "Luz", "R$ 100,00"),
                account("2", "Luz", "R$ 50,00"),
                account("3", "Internet", "R$ 50,00")
            )
        )

        assertEquals(listOf("Luz", "Internet"), items.map { it.category })
        assertEquals(2, items.first().count)
        assertEquals(15_000L, items.first().cents)
        assertEquals(75f, items.first().percentage, 0.01f)
    }

    @Test
    fun `alerts include overdue today and tomorrow but ignore paid accounts`() {
        val reference = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 20, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val alerts = buildFinancialAlerts(
            listOf(
                account("overdue", "Luz", "R$ 10,00", "19/09/2026"),
                account("today", "Agua", "R$ 20,00", "20/09/2026"),
                account("tomorrow", "Internet", "R$ 30,00", "21/09/2026"),
                account("future", "Mercado", "R$ 40,00", "22/09/2026"),
                account("paid", "Luz", "R$ 50,00", "19/09/2026", ContaStatus.PAGO)
            ),
            referenceToday = reference
        )

        assertEquals(3, alerts.size)
        assertEquals(
            listOf(FinancialAlertType.OVERDUE, FinancialAlertType.TODAY, FinancialAlertType.TOMORROW),
            alerts.map { it.type }
        )
        assertTrue(alerts.none { it.account.id == "paid" })
    }

    private fun account(
        id: String,
        category: String,
        value: String,
        dueDate: String = "20/09/2026",
        status: String = ContaStatus.PENDENTE
    ) = ContaSalva(
        id = id,
        codigo = "manual-$id",
        banco = "Cadastro manual",
        valor = value,
        vencimento = dueDate,
        tipo = "Conta manual",
        criadaEm = 0L,
        fornecedor = id,
        categoria = category,
        status = status
    )
}
