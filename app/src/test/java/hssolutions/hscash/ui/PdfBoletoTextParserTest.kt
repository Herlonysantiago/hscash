package hssolutions.hscash.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfBoletoTextParserTest {
    private val linha = "23792.36702 20003.344718 89000.226509 7 15600000009333"

    @Test
    fun `numero parcial da linha nao vira valor milionário do boleto`() {
        val textoExtraido = """
            BANCO BRADESCO
            $linha
            Demonstrativo: 23792.36702
            Valor do documento R$ 23.792.367,02
        """.trimIndent()

        val boleto = PdfBoletoTextParser.parse(textoExtraido)!!

        assertEquals("9333", boleto.valor.filter(Char::isDigit))
        assertEquals("05/09/2026", boleto.vencimento)
    }

    @Test
    fun `sequencia dentro de numero maior nao e recortada como boleto`() {
        val codigoSemMascara = linha.filter(Char::isDigit)
        assertNull(PdfBoletoTextParser.parse("9${codigoSemMascara}9"))
    }

    @Test
    fun `pdf sem texto de boleto nao produz conta`() {
        assertNull(PdfBoletoTextParser.parse("Fatura sem linha digitavel disponivel"))
    }
}
