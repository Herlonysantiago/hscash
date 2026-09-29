package hssolutions.hscash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixUtilsTest {
    @Test
    fun generatesAndParsesStaticPix() {
        val payload = PixUtils.generateStatic(
            key = "financeiro@hscash.com.br",
            recipient = "HS Solutions",
            city = "Sao Paulo",
            amountInCents = 12590,
            txId = "CONTA123"
        )

        val parsed = PixUtils.parse(payload)
        assertTrue(parsed.error, parsed.valid)
        assertFalse(parsed.dynamic)
        assertEquals("financeiro@hscash.com.br", parsed.key)
        assertEquals("HS SOLUTIONS", parsed.recipient)
        assertEquals(12590L, parsed.amountInCents)
        assertEquals("CONTA123", parsed.txId)
    }

    @Test
    fun rejectsChangedPayload() {
        val payload = PixUtils.generateStatic("12345678909", "Teste", "Recife", 1000)
        val changed = payload.replace("10.00", "20.00")
        assertFalse(PixUtils.parse(changed).valid)
    }
}
