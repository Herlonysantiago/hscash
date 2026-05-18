package hssolutions.hscash.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClienteSalvo(
    val id: String,
    val nome: String,
    val whatsapp: String,
    val observacao: String = "",
    val criadoEm: Long = System.currentTimeMillis()
)

object ClienteStorage {
    private const val PREFS_NAME = "hs_cash_clientes"
    private const val KEY_CLIENTES = "clientes"

    fun listarClientes(context: Context): List<ClienteSalvo> {
        val raw = prefs(context).getString(KEY_CLIENTES, "[]").orEmpty()
        val array = JSONArray(raw)
        val clientes = mutableListOf<ClienteSalvo>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            clientes.add(
                ClienteSalvo(
                    id = item.optString("id"),
                    nome = item.optString("nome"),
                    whatsapp = item.optString("whatsapp"),
                    observacao = item.optString("observacao"),
                    criadoEm = item.optLong("criadoEm", System.currentTimeMillis())
                )
            )
        }
        return clientes.sortedBy { it.nome.lowercase() }
    }

    fun salvarCliente(
        context: Context,
        nome: String,
        whatsapp: String,
        observacao: String = "",
        id: String = System.currentTimeMillis().toString()
    ): Boolean {
        if (nome.isBlank()) return false
        val clientes = listarClientes(context).toMutableList()
        val novo = ClienteSalvo(
            id = id,
            nome = nome.trim(),
            whatsapp = whatsapp.filter(Char::isDigit),
            observacao = observacao.trim(),
            criadoEm = clientes.firstOrNull { it.id == id }?.criadoEm ?: System.currentTimeMillis()
        )
        val index = clientes.indexOfFirst { it.id == id }
        if (index >= 0) clientes[index] = novo else clientes.add(novo)
        salvarLista(context, clientes)
        return true
    }

    fun removerCliente(context: Context, id: String) {
        salvarLista(context, listarClientes(context).filterNot { it.id == id })
    }

    private fun salvarLista(context: Context, clientes: List<ClienteSalvo>) {
        val array = JSONArray()
        clientes.forEach { cliente ->
            array.put(
                JSONObject()
                    .put("id", cliente.id)
                    .put("nome", cliente.nome)
                    .put("whatsapp", cliente.whatsapp)
                    .put("observacao", cliente.observacao)
                    .put("criadoEm", cliente.criadoEm)
            )
        }
        prefs(context).edit().putString(KEY_CLIENTES, array.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
