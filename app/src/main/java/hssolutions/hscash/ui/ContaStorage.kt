package hssolutions.hscash.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ContaStatus {
    const val PENDENTE = "PENDENTE"
    const val PAGO = "PAGO"
}

data class ContaSalva(
    val id: String,
    val codigo: String,
    val banco: String,
    val valor: String,
    val vencimento: String,
    val tipo: String,
    val criadaEm: Long,
    val fornecedor: String = "",
    val categoria: String = "Outros",
    val status: String = ContaStatus.PENDENTE,
    val pagoEm: Long? = null
)

object ContaStorage {
    private const val PREFS_NAME = "hs_cash_contas"
    private const val KEY_CONTAS = "contas"

    fun salvarBoleto(
        context: Context,
        boleto: BoletoInfo,
        fornecedor: String = "",
        categoria: String = "Outros"
    ): Boolean {
        val contas = listarContas(context).toMutableList()
        if (contas.any { it.codigo == boleto.codigoBarras }) return false

        val novaConta = ContaSalva(
            id = System.currentTimeMillis().toString(),
            codigo = boleto.codigoBarras,
            banco = boleto.bancoEmissor,
            valor = boleto.valor,
            vencimento = boleto.vencimento,
            tipo = boleto.tipo,
            criadaEm = System.currentTimeMillis(),
            fornecedor = fornecedor.trim(),
            categoria = categoria.ifBlank { "Outros" }.trim()
        )

        contas.add(novaConta)
        salvarLista(context, contas)
        NotificationScheduler.scheduleConta(context, novaConta)
        return true
    }

    fun listarContas(context: Context): List<ContaSalva> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONTAS, "[]").orEmpty()
        val array = JSONArray(raw)
        val contas = mutableListOf<ContaSalva>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            contas.add(
                ContaSalva(
                    id = item.optString("id"),
                    codigo = item.optString("codigo"),
                    banco = item.optString("banco"),
                    valor = item.optString("valor"),
                    vencimento = item.optString("vencimento"),
                    tipo = item.optString("tipo"),
                    criadaEm = item.optLong("criadaEm"),
                    fornecedor = item.optString("fornecedor"),
                    categoria = item.optString("categoria", "Outros"),
                    status = item.optString("status", ContaStatus.PENDENTE),
                    pagoEm = item.optLongOrNull("pagoEm")
                )
            )
        }

        return contas.sortedByDescending { it.criadaEm }
    }

    fun buscarConta(context: Context, id: String): ContaSalva? {
        return listarContas(context).firstOrNull { it.id == id }
    }

    fun atualizarConta(context: Context, contaAtualizada: ContaSalva) {
        val contas = listarContas(context)
            .map { if (it.id == contaAtualizada.id) contaAtualizada else it }

        salvarLista(context, contas)
        if (contaAtualizada.status == ContaStatus.PAGO) {
            NotificationScheduler.cancelConta(context, contaAtualizada)
        } else {
            NotificationScheduler.scheduleConta(context, contaAtualizada)
        }
    }

    fun removerConta(context: Context, id: String) {
        val conta = buscarConta(context, id)
        if (conta != null) {
            NotificationScheduler.cancelConta(context, conta)
        }
        salvarLista(context, listarContas(context).filterNot { it.id == id })
    }

    fun marcarComoPago(context: Context, id: String) {
        val conta = buscarConta(context, id) ?: return
        atualizarConta(
            context,
            conta.copy(
                status = ContaStatus.PAGO,
                pagoEm = System.currentTimeMillis()
            )
        )
    }

    fun marcarComoPendente(context: Context, id: String) {
        val conta = buscarConta(context, id) ?: return
        atualizarConta(
            context,
            conta.copy(
                status = ContaStatus.PENDENTE,
                pagoEm = null
            )
        )
    }

    private fun salvarLista(context: Context, contas: List<ContaSalva>) {
        val array = JSONArray()
        contas.forEach { conta ->
            array.put(
                JSONObject()
                    .put("id", conta.id)
                    .put("codigo", conta.codigo)
                    .put("banco", conta.banco)
                    .put("valor", conta.valor)
                    .put("vencimento", conta.vencimento)
                    .put("tipo", conta.tipo)
                    .put("criadaEm", conta.criadaEm)
                    .put("fornecedor", conta.fornecedor)
                    .put("categoria", conta.categoria)
                    .put("status", conta.status)
                    .put("pagoEm", conta.pagoEm)
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONTAS, array.toString())
            .apply()
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    return if (has(key) && !isNull(key)) optLong(key) else null
}
