package hssolutions.hscash.ui

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardSummaryTest {
    private val hoje = date(2026, Calendar.SEPTEMBER, 15)

    @Test
    fun `resumo separa vencidas atuais e anteriores com quantidades e valores`() {
        val resumo = ContaResumo.from(contas(), hoje, allMonths = false, referenceToday = hoje)

        assertEquals(2, resumo.vencidas)
        assertMoney(300_00, resumo.vencidasValor)
        assertEquals(1, resumo.vencidasNoMes)
        assertMoney(200_00, resumo.vencidasNoMesValor)
        assertEquals(1, resumo.vencidasAnteriores)
        assertMoney(100_00, resumo.vencidasAnterioresValor)
        assertEquals(1, resumo.hoje)
        assertMoney(30_00, resumo.hojeValor)
        assertEquals(1, resumo.amanha)
        assertMoney(40_00, resumo.amanhaValor)
        assertEquals(1, resumo.nesteMes)
        assertMoney(50_00, resumo.nesteMesValor)
    }

    @Test
    fun `resumo financeiro respeita mes direcao e pagamento`() {
        val resumo = ContaResumo.from(contas(), hoje, allMonths = false, referenceToday = hoje)

        assertEquals(4, resumo.totalContas)
        assertMoney(320_00, resumo.totalValor)
        assertEquals(1, resumo.totalReceberContas)
        assertMoney(500_00, resumo.totalReceber)
        assertMoney(180_00, resumo.saldoPrevisto)
        assertEquals(1, resumo.totalPagoContas)
        assertMoney(70_00, resumo.totalPagoMes)
    }

    @Test
    fun `filtro todas vencidas inclui outros meses mesmo com mes selecionado`() {
        val vencidas = filterDashboardAccounts(
            contas(), hoje, allMonths = false,
            filter = DashboardFilter.VENCIDAS,
            referenceToday = hoje
        )

        assertEquals(setOf("anterior", "mes"), vencidas.map { it.id }.toSet())
    }

    @Test
    fun `data invalida nao e classificada como vencida`() {
        val invalida = conta("invalida", "31/02/2026", 10_00)
        val resumo = ContaResumo.from(listOf(invalida), hoje, allMonths = true, referenceToday = hoje)

        assertEquals(0, resumo.vencidas)
        assertEquals(1, resumo.noPrazo)
    }

    private fun contas(): List<ContaSalva> = listOf(
        conta("anterior", "10/08/2026", 100_00),
        conta("mes", "10/09/2026", 200_00),
        conta("hoje", "15/09/2026", 30_00),
        conta("amanha", "16/09/2026", 40_00),
        conta("mes-futuro", "25/09/2026", 50_00),
        conta("outro-mes", "10/10/2026", 60_00),
        conta("receber", "20/09/2026", 500_00, direcao = ContaDirecao.RECEBER),
        conta(
            "paga", "05/09/2026", 70_00,
            status = ContaStatus.PAGO,
            pagoEm = date(2026, Calendar.SEPTEMBER, 12).timeInMillis
        )
    )

    private fun conta(
        id: String,
        vencimento: String,
        centavos: Int,
        direcao: String = ContaDirecao.PAGAR,
        status: String = ContaStatus.PENDENTE,
        pagoEm: Long? = null
    ) = ContaSalva(
        id = id,
        codigo = id,
        banco = "Banco",
        valor = "R$ ${centavos / 100},${(centavos % 100).toString().padStart(2, '0')}",
        vencimento = vencimento,
        tipo = "Teste",
        criadaEm = 1L,
        fornecedor = id,
        status = status,
        pagoEm = pagoEm,
        direcao = direcao
    )

    private fun assertMoney(expectedCents: Int, formatted: String) {
        assertEquals(expectedCents.toString(), formatted.filter(Char::isDigit))
    }

    private fun date(year: Int, month: Int, day: Int): Calendar = Calendar.getInstance().apply {
        clear()
        set(year, month, day)
    }
}
