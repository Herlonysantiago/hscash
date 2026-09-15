package hssolutions.hscash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvancedPaymentReportTest {
    @Test
    fun `variacao compara total atual com mes anterior`() {
        assertEquals(25.0, paymentVariationPercent(12_500, 10_000)!!, 0.001)
        assertEquals(-20.0, paymentVariationPercent(8_000, 10_000)!!, 0.001)
        assertNull(paymentVariationPercent(5_000, 0))
    }

    @Test
    fun `dados do pagamento sobrevivem conversao do banco`() {
        val original = ContaSalva(
            id = "pagamento-1",
            codigo = "manual-1",
            banco = "Cadastro manual",
            valor = "R$ 150,00",
            vencimento = "10/09/2026",
            tipo = "Conta manual",
            criadaEm = 1L,
            status = ContaStatus.PAGO,
            pagoEm = 2L,
            formaPagamento = "Cartão",
            observacaoPagamento = "Parcela única"
        )

        val restored = original.toEntity().toContaSalva()

        assertEquals(original.pagoEm, restored.pagoEm)
        assertEquals("Cartão", restored.formaPagamento)
        assertEquals("Parcela única", restored.observacaoPagamento)
    }
}
