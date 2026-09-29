package hssolutions.hscash.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySelectorTest {
    @Test
    fun companyPayablesUseBusinessSuggestions() {
        val categories = suggestedCategories(ContaDirecao.PAGAR, SpaceType.COMPANY)
        assertTrue("Fornecedores" in categories)
        assertTrue("Funcionarios e folha" in categories)
        assertFalse("Mercado" in categories)
    }

    @Test
    fun familyPayablesUseHouseholdSuggestions() {
        val categories = suggestedCategories(ContaDirecao.PAGAR, SpaceType.FAMILY)
        assertTrue("Mercado" in categories)
        assertTrue("Pets" in categories)
        assertFalse("Funcionarios e folha" in categories)
    }

    @Test
    fun companyReceivablesUseRevenueSuggestions() {
        val categories = suggestedCategories(ContaDirecao.RECEBER, SpaceType.COMPANY)
        assertTrue("Vendas" in categories)
        assertTrue("Servicos prestados" in categories)
        assertFalse("Salario" in categories)
    }
}
