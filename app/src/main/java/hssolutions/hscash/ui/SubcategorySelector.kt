package hssolutions.hscash.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private val subcategorySuggestions = mapOf(
    "Assinaturas" to listOf("Streaming", "IPTV/TV", "Música", "Academia", "Software", "Armazenamento"),
    "Lazer" to listOf("Cinema", "Jogos", "Viagens", "Eventos", "Passeios", "Streaming"),
    "Alimentação" to listOf("Mercado", "Restaurante", "Delivery"),
    "Mercado" to listOf("Supermercado", "Feira", "Atacado"),
    "Transporte" to listOf("Combustível", "Aplicativo", "Estacionamento", "Manutenção"),
    "Saude" to listOf("Farmácia", "Consulta", "Plano de saúde", "Exames", "Odontologia"),
    "Educacao" to listOf("Escola", "Curso", "Material escolar", "Faculdade"),
    "Pets" to listOf("Ração", "Veterinário", "Medicamentos", "Banho e tosa"),
    "Impostos e taxas" to listOf("DAS", "ICMS", "ISS", "IPTU", "Taxas bancárias"),
    "Funcionarios e folha" to listOf("Salários", "Benefícios", "Comissões", "Encargos", "Férias"),
    "Marketing" to listOf("Anúncios", "Redes sociais", "Design", "Impressos"),
    "Sistemas e assinaturas" to listOf("Software", "Licenças", "Hospedagem", "Armazenamento"),
    "Estoque e mercadorias" to listOf("Compras", "Frete", "Embalagens", "Insumos"),
    "Fornecedores" to listOf("Produtos", "Matéria-prima", "Distribuidor", "Frete"),
    "Servicos terceirizados" to listOf("Limpeza", "Manutenção", "Consultoria", "Jurídico")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubcategorySelector(
    category: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = subcategorySuggestions[category].orEmpty()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                label = { Text("Subcategoria (opcional)") },
                placeholder = { Text(if (options.isEmpty()) "Ex: Cinema, Netflix, IPTV" else "Selecione ou digite") },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = hsCashOutlinedTextFieldColors()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        onValueChange(option)
                        expanded = false
                    })
                }
                if (value.isNotBlank()) {
                    DropdownMenuItem(text = { Text("Limpar subcategoria") }, onClick = {
                        onValueChange("")
                        expanded = false
                    })
                }
            }
        }
    }
}
