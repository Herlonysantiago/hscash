package hssolutions.hscash.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WhatsAppHelper {
    fun sendCharge(context: Context, conta: ContaSalva) {
        val rawPhone = conta.clienteWhatsapp.orEmpty().filter(Char::isDigit)
        if (rawPhone.length < 10) {
            Toast.makeText(context, "Informe o WhatsApp do cliente com DDD", Toast.LENGTH_SHORT).show()
            return
        }

        val phone = if (rawPhone.startsWith("55")) rawPhone else "55$rawPhone"
        val cliente = conta.fornecedor.ifBlank { "cliente" }
        val message = "Ola, $cliente. Tudo bem? Passando para lembrar do recebimento de ${conta.valor}, previsto para ${conta.vencimento}. Obrigado."
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Nao foi possivel abrir o WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendClientCharge(context: Context, cliente: ClienteSalvo, contas: List<ContaSalva>) {
        val rawPhone = cliente.whatsapp.filter(Char::isDigit)
        if (rawPhone.length < 10) {
            Toast.makeText(context, "Informe o WhatsApp do cliente com DDD", Toast.LENGTH_SHORT).show()
            return
        }

        val total = contas.sumOf { it.valor.filter(Char::isDigit).toLongOrNull() ?: 0L }
        val totalFormatado = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("pt-BR"))
            .format(java.math.BigDecimal(total).movePointLeft(2))
        val pendencias = contas.take(4).joinToString("\n") { "- ${it.valor} com previsao ${it.vencimento}" }
        val phone = if (rawPhone.startsWith("55")) rawPhone else "55$rawPhone"
        val message = buildString {
            append("Ola, ${cliente.nome}. Tudo bem? Passando para lembrar dos valores em aberto no HS Cash.\n")
            if (pendencias.isNotBlank()) {
                append(pendencias)
                append("\n")
            }
            append("Total em aberto: ")
            append(totalFormatado)
            append(". Obrigado.")
        }
        openWhatsApp(context, phone, message)
    }

    fun sendMessage(context: Context, phoneNumber: String, message: String) {
        val rawPhone = phoneNumber.filter(Char::isDigit)
        if (rawPhone.length < 10) {
            Toast.makeText(context, "Informe o WhatsApp com DDD", Toast.LENGTH_SHORT).show()
            return
        }
        val phone = if (rawPhone.startsWith("55")) rawPhone else "55$rawPhone"
        openWhatsApp(context, phone, message)
    }

    private fun openWhatsApp(context: Context, phone: String, message: String) {
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Nao foi possivel abrir o WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
}
