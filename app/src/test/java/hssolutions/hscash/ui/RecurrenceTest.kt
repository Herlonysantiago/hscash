package hssolutions.hscash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {
    @Test
    fun `recorrencia semanal acrescenta sete dias`() {
        assertEquals("22/09/2026", nextRecurrenceDate("15/09/2026", "Semanal"))
    }

    @Test
    fun `recorrencia mensal ajusta ultimo dia de fevereiro`() {
        assertEquals("28/02/2025", nextRecurrenceDate("31/01/2025", "Mensal"))
        assertEquals("29/02/2024", nextRecurrenceDate("31/01/2024", "Mensal"))
    }

    @Test
    fun `recorrencia anual ajusta ano bissexto`() {
        assertEquals("28/02/2025", nextRecurrenceDate("29/02/2024", "Anual"))
    }

    @Test
    fun `datas impossiveis sao rejeitadas`() {
        assertTrue(isValidContaDate("29/02/2024"))
        assertFalse(isValidContaDate("29/02/2025"))
        assertFalse(isValidContaDate("31/02/2026"))
        assertNull(nextRecurrenceDate("31/02/2026", "Mensal"))
    }
}
