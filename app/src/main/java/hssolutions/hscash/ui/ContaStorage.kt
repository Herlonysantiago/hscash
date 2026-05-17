package hssolutions.hscash.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ContaStatus {
    const val PENDENTE = "PENDENTE"
    const val PAGO = "PAGO"
}

object ContaDirecao {
    const val PAGAR = "PAGAR"
    const val RECEBER = "RECEBER"
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
    val pagoEm: Long? = null,
    val recorrente: Boolean = false,
    val recorrencia: String = "Mensal",
    val comprovantePath: String? = null,
    val direcao: String = ContaDirecao.PAGAR
)

object ContaStorage {
    private const val PREFS_NAME = "hs_cash_contas"
    private const val KEY_CONTAS = "contas"
    private const val RECURRENCE_FORECAST_MONTHS = 12

    fun salvarBoleto(
        context: Context,
        boleto: BoletoInfo,
        fornecedor: String = "",
        categoria: String = "Outros",
        recorrente: Boolean = false,
        recorrencia: String = "Mensal"
    ): Boolean {
        val contas = listarContas(context).toMutableList()
        if (contas.any { it.codigo == boleto.codigoBarras && it.vencimento == boleto.vencimento }) return false

        val novaConta = ContaSalva(
            id = System.currentTimeMillis().toString(),
            codigo = boleto.codigoBarras,
            banco = boleto.bancoEmissor,
            valor = boleto.valor,
            vencimento = boleto.vencimento,
            tipo = boleto.tipo,
            criadaEm = System.currentTimeMillis(),
            fornecedor = fornecedor.trim(),
            categoria = categoria.ifBlank { "Outros" }.trim(),
            recorrente = recorrente,
            recorrencia = recorrencia.ifBlank { "Mensal" },
            direcao = ContaDirecao.PAGAR
        )

        dao(context).upsert(novaConta.toEntity())
        NotificationScheduler.scheduleConta(context, novaConta)
        criarRecorrenciasFuturas(context, novaConta)
        return true
    }

    fun salvarContaManual(
        context: Context,
        fornecedor: String,
        valor: String,
        vencimento: String,
        categoria: String = "Outros",
        recorrente: Boolean = false,
        recorrencia: String = "Mensal",
        direcao: String = ContaDirecao.PAGAR
    ): Boolean {
        if (fornecedor.isBlank() || valor.isBlank() || !isValidContaDate(vencimento)) return false

        val agora = System.currentTimeMillis()
        val novaConta = ContaSalva(
            id = agora.toString(),
            codigo = "manual-$agora",
            banco = "Cadastro manual",
            valor = valor.trim(),
            vencimento = vencimento.trim(),
            tipo = "Conta manual",
            criadaEm = agora,
            fornecedor = fornecedor.trim(),
            categoria = categoria.ifBlank { "Outros" }.trim(),
            recorrente = recorrente,
            recorrencia = recorrencia.ifBlank { "Mensal" },
            direcao = if (direcao == ContaDirecao.RECEBER) ContaDirecao.RECEBER else ContaDirecao.PAGAR
        )

        dao(context).upsert(novaConta.toEntity())
        NotificationScheduler.scheduleConta(context, novaConta)
        criarRecorrenciasFuturas(context, novaConta)
        return true
    }

    fun listarContas(context: Context): List<ContaSalva> {
        migratePrefsIfNeeded(context)
        return dao(context).listar().map { it.toContaSalva() }
    }

    private fun listarPrefs(context: Context): List<ContaSalva> {
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
                    pagoEm = item.optLongOrNull("pagoEm"),
                    recorrente = item.optBoolean("recorrente", false),
                    recorrencia = item.optString("recorrencia", "Mensal"),
                    comprovantePath = item.optStringOrNull("comprovantePath"),
                    direcao = item.optString("direcao", ContaDirecao.PAGAR)
                )
            )
        }

        return contas.sortedByDescending { it.criadaEm }
    }

    fun buscarConta(context: Context, id: String): ContaSalva? {
        migratePrefsIfNeeded(context)
        return dao(context).buscar(id)?.toContaSalva()
    }

    fun atualizarConta(context: Context, contaAtualizada: ContaSalva) {
        val contaAnterior = buscarConta(context, contaAtualizada.id)
        if (deveLimparRecorrenciasAntigas(contaAnterior, contaAtualizada)) {
            removerRecorrenciasFuturas(context, contaAnterior!!)
        }

        dao(context).upsert(contaAtualizada.toEntity())
        if (contaAtualizada.status == ContaStatus.PAGO) {
            NotificationScheduler.cancelConta(context, contaAtualizada)
        } else {
            NotificationScheduler.scheduleConta(context, contaAtualizada)
        }
        criarRecorrenciasFuturas(context, contaAtualizada)
    }

    fun removerConta(context: Context, id: String) {
        val conta = buscarConta(context, id)
        if (conta != null) {
            NotificationScheduler.cancelConta(context, conta)
        }
        dao(context).remover(id)
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

    fun substituirTodas(context: Context, contas: List<ContaSalva>) {
        val contaDao = dao(context)
        contaDao.limpar()
        contaDao.upsertAll(contas.map { it.toEntity() })
        contas.forEach { NotificationScheduler.scheduleConta(context, it) }
        salvarListaPrefs(context, contas)
    }

    private fun salvarListaPrefs(context: Context, contas: List<ContaSalva>) {
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
                    .put("recorrente", conta.recorrente)
                    .put("recorrencia", conta.recorrencia)
                    .put("comprovantePath", conta.comprovantePath)
                    .put("direcao", conta.direcao)
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONTAS, array.toString())
            .apply()
    }

    private fun migratePrefsIfNeeded(context: Context) {
        val contaDao = dao(context)
        if (contaDao.count() > 0) return
        val prefsContas = listarPrefs(context)
        if (prefsContas.isNotEmpty()) {
            contaDao.upsertAll(prefsContas.map { it.toEntity() })
        }
    }

    private fun dao(context: Context): ContaDao {
        return HsCashDatabase.get(context).contaDao()
    }

    private fun criarRecorrenciasFuturas(context: Context, conta: ContaSalva) {
        if (!conta.recorrente || !isValidContaDate(conta.vencimento)) return

        val contaDao = dao(context)
        val existentes = contaDao.listar().map { it.toContaSalva() }
        val limite = parseContaDate(conta.vencimento)?.apply {
            add(java.util.Calendar.MONTH, RECURRENCE_FORECAST_MONTHS)
        } ?: return
        val novasContas = mutableListOf<ContaSalva>()
        var proximoVencimento = nextRecurrenceDate(conta.vencimento, conta.recorrencia)

        while (proximoVencimento != null) {
            val data = parseContaDate(proximoVencimento) ?: break
            if (data.after(limite)) break

            val idRecorrente = recurringContaId(conta, proximoVencimento)
            val jaExiste = existentes.any { isSameRecurringConta(it, conta, proximoVencimento, idRecorrente) } ||
                novasContas.any { isSameRecurringConta(it, conta, proximoVencimento, idRecorrente) }

            if (!jaExiste) {
                novasContas.add(
                    conta.copy(
                        id = idRecorrente,
                        vencimento = proximoVencimento,
                        status = ContaStatus.PENDENTE,
                        pagoEm = null,
                        criadaEm = System.currentTimeMillis() + novasContas.size,
                        comprovantePath = null
                    )
                )
            }

            proximoVencimento = nextRecurrenceDate(proximoVencimento, conta.recorrencia)
        }

        if (novasContas.isNotEmpty()) {
            contaDao.upsertAll(novasContas.map { it.toEntity() })
            novasContas.forEach { NotificationScheduler.scheduleConta(context, it) }
        }
    }

    private fun removerRecorrenciasFuturas(context: Context, conta: ContaSalva) {
        val contaDao = dao(context)
        val existentes = contaDao.listar().map { it.toContaSalva() }
        val limite = parseContaDate(conta.vencimento)?.apply {
            add(java.util.Calendar.MONTH, RECURRENCE_FORECAST_MONTHS)
        } ?: return
        var proximoVencimento = nextRecurrenceDate(conta.vencimento, conta.recorrencia)

        while (proximoVencimento != null) {
            val data = parseContaDate(proximoVencimento) ?: break
            if (data.after(limite)) break

            val idRecorrente = recurringContaId(conta, proximoVencimento)
            existentes
                .filter {
                    it.id != conta.id &&
                        it.status != ContaStatus.PAGO &&
                        isSameRecurringConta(it, conta, proximoVencimento, idRecorrente)
                }
                .forEach {
                    NotificationScheduler.cancelConta(context, it)
                    contaDao.remover(it.id)
                }

            proximoVencimento = nextRecurrenceDate(proximoVencimento, conta.recorrencia)
        }
    }
}

private fun deveLimparRecorrenciasAntigas(
    anterior: ContaSalva?,
    atualizada: ContaSalva
): Boolean {
    if (anterior?.recorrente != true) return false
    if (!atualizada.recorrente) return true
    return anterior.codigo != atualizada.codigo ||
        anterior.valor != atualizada.valor ||
        anterior.vencimento != atualizada.vencimento ||
        anterior.recorrencia != atualizada.recorrencia
}

private fun recurringContaId(conta: ContaSalva, vencimento: String): String {
    val codigoHash = Integer.toHexString(conta.codigo.hashCode())
    val valorHash = Integer.toHexString(conta.valor.hashCode())
    val vencimentoKey = vencimento.filter(Char::isDigit)
    return "rec-${conta.direcao}-$codigoHash-$valorHash-$vencimentoKey"
}

private fun isSameRecurringConta(
    existente: ContaSalva,
    origem: ContaSalva,
    vencimento: String,
    idRecorrente: String
): Boolean {
    return existente.id == idRecorrente ||
        (existente.codigo == origem.codigo &&
            existente.valor == origem.valor &&
            existente.direcao == origem.direcao &&
            existente.vencimento == vencimento)
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    return if (has(key) && !isNull(key)) optLong(key) else null
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}
