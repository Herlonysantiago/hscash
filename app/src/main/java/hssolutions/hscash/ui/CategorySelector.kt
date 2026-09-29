package hssolutions.hscash.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val personalPayableCategories = listOf(
    "Agua",
    "Luz",
    "Internet",
    "Telefone",
    "Aluguel",
    "Condominio",
    "Mercado",
    "Transporte",
    "Saude",
    "Educacao",
    "Impostos",
    "Seguros",
    "Assinaturas",
    "Lazer",
    "Cartao",
    "Emprestimos",
    "Boletos importados",
    "Outros"
)

private val familyPayableCategories = listOf(
    "Agua",
    "Luz",
    "Internet",
    "Telefone",
    "Aluguel",
    "Condominio",
    "Mercado",
    "Saude",
    "Educacao",
    "Transporte",
    "Lazer",
    "Assinaturas",
    "Pets",
    "Seguros",
    "Impostos",
    "Manutencao da casa",
    "Outros"
)

private val companyPayableCategories = listOf(
    "Fornecedores",
    "Funcionarios e folha",
    "Pro-labore",
    "Aluguel comercial",
    "Energia e internet",
    "Impostos e taxas",
    "Contabilidade",
    "Marketing",
    "Sistemas e assinaturas",
    "Estoque e mercadorias",
    "Manutencao",
    "Transporte e combustivel",
    "Servicos terceirizados",
    "Emprestimos",
    "Seguros",
    "Outros"
)

private val personalReceivableCategories = listOf(
    "Cliente",
    "Servico",
    "Venda",
    "Salario",
    "Reembolso",
    "Aluguel recebido",
    "Outros"
)

private val familyReceivableCategories = listOf(
    "Salario",
    "Renda extra",
    "Reembolso",
    "Aluguel recebido",
    "Venda",
    "Pensao",
    "Outros"
)

private val companyReceivableCategories = listOf(
    "Vendas",
    "Servicos prestados",
    "Clientes",
    "Mensalidades",
    "Comissoes",
    "Reembolsos",
    "Aluguel recebido",
    "Outras receitas"
)

internal fun suggestedCategories(direction: String, spaceType: String): List<String> {
    return if (direction == ContaDirecao.RECEBER) {
        when (spaceType) {
            SpaceType.FAMILY -> familyReceivableCategories
            SpaceType.COMPANY -> companyReceivableCategories
            else -> personalReceivableCategories
        }
    } else {
        when (spaceType) {
            SpaceType.FAMILY -> familyPayableCategories
            SpaceType.COMPANY -> companyPayableCategories
            else -> personalPayableCategories
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    value: String,
    onValueChange: (String) -> Unit,
    direction: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spaceType = SpaceStorage.active(context).type
    val options = suggestedCategories(direction, spaceType)
    var expanded by remember { mutableStateOf(false) }
    var customMode by remember(direction) { mutableStateOf(value.isNotBlank() && value !in options) }

    LaunchedEffect(value, options) {
        if (value.isNotBlank()) customMode = value !in options
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = if (customMode) "Outra categoria" else value,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                label = { Text("Categoria") },
                placeholder = { Text("Selecione uma categoria") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = hsCashOutlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    val identity = categoryIdentity(option)
                    DropdownMenuItem(
                        text = { Text(option) },
                        leadingIcon = { Icon(identity.icon, contentDescription = null, tint = identity.color) },
                        onClick = {
                            customMode = false
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Outra categoria...") },
                    onClick = {
                        customMode = true
                        onValueChange("")
                        expanded = false
                    }
                )
            }
        }

        if (customMode) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Digite a categoria") },
                placeholder = { Text("Ex: Academia, Pet, Manutencao") },
                singleLine = true,
                colors = hsCashOutlinedTextFieldColors()
            )
        }
    }
}
