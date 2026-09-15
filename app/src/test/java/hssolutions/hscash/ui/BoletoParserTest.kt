package hssolutions.hscash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoletoParserTest {
    private val linha = "23792.36702 20003.344718 89000.226509 7 15600000009333"
    private val digits = linha.filter(Char::isDigit)

    @Test
    fun `boleto informado pelo usuario retorna valor vencimento e banco corretos`() {
        val boleto = parseBoleto(linha)
        assertNotNull(boleto)

        assertEquals("9333", boleto!!.valor.filter(Char::isDigit))
        assertEquals("05/09/2026", boleto.vencimento)
        assertEquals("Bradesco (237)", boleto.bancoEmissor)
        assertEquals("Boleto bancario", boleto.tipo)
    }

    @Test
    fun `codigo de barras e linha digitavel fazem conversao de ida e volta`() {
        val boleto = parseBoleto(digits)!!
        val linhaGerada = linhaDigitavelFromCodigoBarras(boleto.codigoBarras)
        val reparsed = parseBoleto(boleto.codigoBarras)!!

        assertEquals(digits, linhaGerada)
        assertEquals(boleto.codigoBarras, reparsed.codigoBarras)
        assertEquals(boleto.linhaDigitavel, reparsed.linhaDigitavel)
        assertEquals(boleto.valor, reparsed.valor)
        assertEquals(boleto.vencimento, reparsed.vencimento)
    }

    @Test
    fun `digito verificador alterado invalida boleto`() {
        val adulterado = digits.replaceRange(9, 10, if (digits[9] == '9') "0" else "9")

        assertFalse(isValidBoletoCode(adulterado))
        assertNull(parseBoleto(adulterado))
    }

    @Test
    fun `codigo vazio ou incompleto nao e aceito`() {
        assertFalse(isValidBoletoCode(""))
        assertFalse(isValidBoletoCode("23792.36702"))
        assertTrue(isValidBoletoCode(linha))
    }
}
