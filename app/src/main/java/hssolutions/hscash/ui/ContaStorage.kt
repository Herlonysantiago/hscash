package hssolutions.hscash.ui

import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hssolutions.hscash.cloud.CloudSyncScheduler
import org.json.JSONArray
import org.json.JSONObject

object ContaStatus {
    const val PENDENTE = "PENDENTE"
    const val PAGO = "PAGO"
    const val EXCLUIDO = "EXCLUIDO"
}

object ContaDirecao {
    const val PAGAR = "PAGAR"
    const val RECEBER = "RECEBER"
}

object PixAgendamentoStatus {
    const val AGENDADO = "AGENDADO"
    const val EXECUTADO = "EXECUTADO"
    const val FALHOU = "FALHOU"
}

object RecurrenceKind {
    const val ACCOUNT = "CONTA"
    const val SUBSCRIPTION = "ASSINATURA"
}

data class ContaSalva(
    val id: String,
    val spaceId: String = SpaceStorage.DEFAULT_SPACE_ID,
    val codigo: String,
    val banco: String,
    val valor: String,
    val vencimento: String,
    val tipo: String,
    val criadaEm: Long,
    val fornecedor: String = "",
    val categoria: String = "Outros",
    val subcategoria: String? = null,
    val responsavel: String? = null,
    val status: String = ContaStatus.PENDENTE,
    val pagoEm: Long? = null,
    val formaPagamento: String? = null,
    val observacaoPagamento: String? = null,
    val pagoPor: String? = null,
    val recorrente: Boolean = false,
    val recorrencia: String = "Mensal",
    val comprovantePath: String? = null,
    val boletoPdfPath: String? = null,
    val comprovanteCloudPath: String? = null,
    val boletoPdfCloudPath: String? = null,
    val direcao: String = ContaDirecao.PAGAR,
    val clienteWhatsapp: String? = null,
    val pixCopiaCola: String? = null,
    val pixChave: String? = null,
    val pixRecebedor: String? = null,
    val pixTxId: String? = null,
    val pixAgendado: Boolean = false,
    val pixAgendamentoStatus: String? = null,
    val excluidaEm: Long? = null,
    val atualizadaEm: Long = criadaEm
)

object ContaStorage {
    private const val PREFS_NAME = "hs_cash_contas"
    private const val KEY_CONTAS = "contas"
    private const val KEY_ROOM_MIGRATION_COMPLETED = "room_migration_completed"
    private const val RECURRENCE_FORECAST_MONTHS = 12

    @WorkerThread
    fun salvarBoleto(
        context: Context,
        boleto: BoletoInfo,
        fornecedor: String = "",
        categoria: String = "Outros",
        subcategoria: String? = null,
        recorrente: Boolean = false,
        recorrencia: String = "Mensal",
        saveAfterUpdate: Boolean = true
    ): Boolean = databaseCall {
        val contas = listarContasInternal(context).toMutableList()
        if (contas.any { it.codigo == boleto.codigoBarras && it.vencimento == boleto.vencimento }) return@databaseCall false

        val novaConta = ContaSalva(
            id = System.currentTimeMillis().toString(),
            spaceId = SpaceStorage.activeSpaceId(context),
            codigo = boleto.codigoBarras,
            banco = boleto.bancoEmissor,
            valor = boleto.valor,
            vencimento = boleto.vencimento,
            tipo = boleto.tipo,
            criadaEm = System.currentTimeMillis(),
            fornecedor = fornecedor.trim(),
            categoria = categoria.ifBlank { "Outros" }.trim(),
            subcategoria = subcategoria?.trim()?.takeIf(String::isNotBlank),
            recorrente = recorrente,
            recorrencia = recorrencia.ifBlank { "Mensal" },
            direcao = ContaDirecao.PAGAR
        )

        dao(context).upsert(novaConta.toEntity())
        ActivityLogStorage.record(
            context = context,
            action = ActivityAction.CREATED,
            conta = novaConta,
            details = "Boleto cadastrado"
        )
        NotificationScheduler.scheduleConta(context, novaConta)
        criarRecorrenciasFuturas(context, novaConta)
        if (saveAfterUpdate) saveAutomaticBackup(context)
        return@databaseCall true
    }

    suspend fun salvarBoletoAsync(
        context: Context,
        boleto: BoletoInfo,
        fornecedor: String = "",
        categoria: String = "Outros",
        recorrente: Boolean = false,
        recorrencia: String = "Mensal"
    ): Boolean = withContext(Dispatchers.IO) {
        salvarBoleto(
            context = context,
            boleto = boleto,
            fornecedor = fornecedor,
            categoria = categoria,
            recorrente = recorrente,
            recorrencia = recorrencia
        )
    }

    @WorkerThread
    fun salvarContaManual(
        context: Context,
        fornecedor: String,
        valor: String,
        vencimento: String,
        categoria: String = "Outros",
        recorrente: Boolean = false,
        recorrencia: String = "Mensal",
        direcao: String = ContaDirecao.PAGAR,
        clienteWhatsapp: String? = null,
        codigoBarras: String? = null,
        pixCopiaCola: String? = null,
        pixChave: String? = null,
        pixRecebedor: String? = null,
        pixTxId: String? = null,
        pixAgendado: Boolean = false,
        recurrenceKind: String = RecurrenceKind.ACCOUNT,
        subcategoria: String? = null,
        banco: String? = null,
        responsavel: String? = null
    ): Boolean = databaseCall {
        if (fornecedor.isBlank() || valor.isBlank() || !isValidContaDate(vencimento)) return@databaseCall false

        val agora = System.currentTimeMillis()
        val novaConta = ContaSalva(
            id = agora.toString(),
            spaceId = SpaceStorage.activeSpaceId(context),
            codigo = codigoBarras?.filter(Char::isDigit)?.takeIf { it.isNotBlank() } ?: "manual-$agora",
            banco = banco?.trim()?.takeIf(String::isNotBlank) ?: "Cadastro manual",
            valor = valor.trim(),
            vencimento = vencimento.trim(),
            tipo = if (recorrente && recurrenceKind == RecurrenceKind.SUBSCRIPTION) "Assinatura" else "Conta manual",
            criadaEm = agora,
            fornecedor = fornecedor.trim(),
            categoria = categoria.ifBlank { "Outros" }.trim(),
            subcategoria = subcategoria?.trim()?.takeIf(String::isNotBlank),
            responsavel = responsavel?.trim()?.takeIf(String::isNotBlank),
            recorrente = recorrente,
            recorrencia = recorrencia.ifBlank { "Mensal" },
            direcao = if (direcao == ContaDirecao.RECEBER) ContaDirecao.RECEBER else ContaDirecao.PAGAR,
            clienteWhatsapp = clienteWhatsapp?.trim()?.takeIf { it.isNotBlank() },
            pixCopiaCola = pixCopiaCola?.let(PixUtils::normalize)?.takeIf { it.isNotBlank() },
            pixChave = pixChave?.trim()?.takeIf { it.isNotBlank() },
            pixRecebedor = pixRecebedor?.trim()?.takeIf { it.isNotBlank() },
            pixTxId = pixTxId?.trim()?.takeIf { it.isNotBlank() },
            pixAgendado = pixAgendado,
            pixAgendamentoStatus = if (pixAgendado) PixAgendamentoStatus.AGENDADO else null
        )

        dao(context).upsert(novaConta.toEntity())
        ActivityLogStorage.record(
            context = context,
            action = ActivityAction.CREATED,
            conta = novaConta,
            details = if (novaConta.direcao == ContaDirecao.RECEBER) "Recebimento cadastrado" else "Conta cadastrada"
        )
        NotificationScheduler.scheduleConta(context, novaConta)
        criarRecorrenciasFuturas(context, novaConta)
        saveAutomaticBackup(context)
        return@databaseCall true
    }

    suspend fun salvarContaManualAsync(
        context: Context,
        fornecedor: String,
        valor: String,
        vencimento: String,
        categoria: String = "Outros",
        recorrente: Boolean = false,
        recorrencia: String = "Mensal",
        direcao: String = ContaDirecao.PAGAR,
        clienteWhatsapp: String? = null,
        codigoBarras: String? = null,
        pixCopiaCola: String? = null,
        pixChave: String? = null,
        pixRecebedor: String? = null,
        pixTxId: String? = null,
        pixAgendado: Boolean = false,
        recurrenceKind: String = RecurrenceKind.ACCOUNT,
        subcategoria: String? = null,
        banco: String? = null,
        responsavel: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        salvarContaManual(context, fornecedor, valor, vencimento, categoria, recorrente, recorrencia, direcao, clienteWhatsapp, codigoBarras, pixCopiaCola, pixChave, pixRecebedor, pixTxId, pixAgendado, recurrenceKind, subcategoria, banco, responsavel)
    }

    suspend fun existePixDuplicadoAsync(context: Context, payload: String, exceptId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val normalized = PixUtils.normalize(payload)
        if (normalized.isBlank()) return@withContext false
        listarTodasInternal(context).any {
            it.id != exceptId && it.status != ContaStatus.EXCLUIDO && PixUtils.normalize(it.pixCopiaCola.orEmpty()) == normalized
        }
    }

    @WorkerThread
    fun listarContas(context: Context): List<ContaSalva> = databaseCall {
        listarContasInternal(context)
    }

    /** Use from UI coroutines to avoid blocking the main thread. */
    suspend fun listarContasAsync(context: Context): List<ContaSalva> = withContext(Dispatchers.IO) {
        listarContasInternal(context)
    }

    private fun listarContasInternal(context: Context): List<ContaSalva> {
        migratePrefsIfNeeded(context)
        return listarTodasInternal(context).filter { it.status != ContaStatus.EXCLUIDO }
    }

    /** Todas as contas de todos os espaços, usado apenas por backup e sincronização. */
    suspend fun listarTodosEspacosAsync(context: Context): List<ContaSalva> = withContext(Dispatchers.IO) {
        migratePrefsIfNeeded(context)
        dao(context).listar().map { it.toContaSalva() }.filter { it.status != ContaStatus.EXCLUIDO }
    }

    /** Inclui itens na lixeira para que exclusões também sejam propagadas à nuvem. */
    suspend fun listarTodosParaSincronizacaoAsync(context: Context): List<ContaSalva> = withContext(Dispatchers.IO) {
        migratePrefsIfNeeded(context)
        dao(context).listar().map { it.toContaSalva() }
    }

    @WorkerThread
    internal fun atualizarCaminhosNuvem(
        context: Context,
        id: String,
        comprovanteCloudPath: String?,
        boletoPdfCloudPath: String?
    ) = databaseCall {
        dao(context).atualizarCaminhosNuvem(id, comprovanteCloudPath, boletoPdfCloudPath)
    }

    @WorkerThread
    fun listarLixeira(context: Context): List<ContaSalva> = databaseCall {
        listarTodasInternal(context).filter { it.status == ContaStatus.EXCLUIDO }
    }

    suspend fun listarLixeiraAsync(context: Context): List<ContaSalva> = withContext(Dispatchers.IO) {
        listarTodasInternal(context).filter { it.status == ContaStatus.EXCLUIDO }
    }

    private fun listarTodasInternal(context: Context): List<ContaSalva> {
        migratePrefsIfNeeded(context)
        return dao(context).listarPorEspaco(SpaceStorage.activeSpaceId(context)).map { it.toContaSalva() }
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
                    spaceId = item.optString("spaceId", SpaceStorage.DEFAULT_SPACE_ID),
                    codigo = item.optString("codigo"),
                    banco = item.optString("banco"),
                    valor = item.optString("valor"),
                    vencimento = item.optString("vencimento"),
                    tipo = item.optString("tipo"),
                    criadaEm = item.optLong("criadaEm"),
                    fornecedor = item.optString("fornecedor"),
                    categoria = item.optString("categoria", "Outros"),
                    subcategoria = item.optString("subcategoria").takeIf { it.isNotBlank() && it != "null" },
                    responsavel = item.optString("responsavel").takeIf { it.isNotBlank() && it != "null" },
                    pagoPor = item.optString("pagoPor").takeIf { it.isNotBlank() && it != "null" },
                    status = item.optString("status", ContaStatus.PENDENTE),
                    pagoEm = item.optLongOrNull("pagoEm"),
                    formaPagamento = item.optStringOrNull("formaPagamento"),
                    observacaoPagamento = item.optStringOrNull("observacaoPagamento"),
                    recorrente = item.optBoolean("recorrente", false),
                    recorrencia = item.optString("recorrencia", "Mensal"),
                    comprovantePath = item.optStringOrNull("comprovantePath"),
                    boletoPdfPath = item.optStringOrNull("boletoPdfPath"),
                    direcao = item.optString("direcao", ContaDirecao.PAGAR),
                    clienteWhatsapp = item.optStringOrNull("clienteWhatsapp"),
                    pixCopiaCola = item.optStringOrNull("pixCopiaCola"),
                    pixChave = item.optStringOrNull("pixChave"),
                    pixRecebedor = item.optStringOrNull("pixRecebedor"),
                    pixTxId = item.optStringOrNull("pixTxId"),
                    pixAgendado = item.optBoolean("pixAgendado", false),
                    pixAgendamentoStatus = item.optStringOrNull("pixAgendamentoStatus"),
                    excluidaEm = item.optLongOrNull("excluidaEm"),
                    atualizadaEm = item.optLongOrNull("atualizadaEm") ?: item.optLong("criadaEm")
                )
            )
        }

        return contas.sortedByDescending { it.criadaEm }
    }

    @WorkerThread
    fun buscarConta(context: Context, id: String): ContaSalva? = databaseCall {
        buscarContaInternal(context, id)
    }

    suspend fun buscarContaAsync(context: Context, id: String): ContaSalva? = withContext(Dispatchers.IO) {
        buscarContaInternal(context, id)
    }

    private fun buscarContaInternal(context: Context, id: String): ContaSalva? {
        migratePrefsIfNeeded(context)
        return dao(context).buscar(id)?.toContaSalva()
    }

    @WorkerThread
    fun atualizarConta(
        context: Context,
        contaAtualizada: ContaSalva,
        saveAfterUpdate: Boolean = true
    ) = databaseCall {
        atualizarContaInternal(context, contaAtualizada, saveAfterUpdate)
    }

    suspend fun atualizarContaAsync(context: Context, contaAtualizada: ContaSalva) = withContext(Dispatchers.IO) {
        atualizarConta(context, contaAtualizada)
    }

    private fun atualizarContaInternal(
        context: Context,
        contaAtualizada: ContaSalva,
        saveAfterUpdate: Boolean = true,
        activityAction: String? = ActivityAction.UPDATED,
        activityDetails: String? = null,
        actorName: String? = null
    ) {
        val contaAnterior = buscarContaInternal(context, contaAtualizada.id)
        if (deveLimparRecorrenciasAntigas(contaAnterior, contaAtualizada)) {
            removerRecorrenciasFuturas(context, contaAnterior!!)
        }

        val contaComAlteracao = contaAtualizada.copy(atualizadaEm = System.currentTimeMillis())
        dao(context).upsert(contaComAlteracao.toEntity())
        activityAction?.let {
            ActivityLogStorage.record(
                context = context,
                action = it,
                conta = contaComAlteracao,
                details = activityDetails,
                actorName = actorName
            )
        }
        if (contaComAlteracao.status == ContaStatus.PAGO || contaComAlteracao.status == ContaStatus.EXCLUIDO) {
            NotificationScheduler.cancelConta(context, contaComAlteracao)
        } else {
            NotificationScheduler.scheduleConta(context, contaComAlteracao)
        }
        criarRecorrenciasFuturas(context, contaComAlteracao)
        if (saveAfterUpdate) saveAutomaticBackup(context)
    }

    @WorkerThread
    fun removerConta(context: Context, id: String) = databaseCall {
        removerContaInternal(context, id, saveAfterUpdate = true)
    }

    private fun removerContaInternal(context: Context, id: String, saveAfterUpdate: Boolean) {
        val conta = buscarContaInternal(context, id)
        if (conta != null) {
            NotificationScheduler.cancelConta(context, conta)
            dao(context).upsert(
                conta.copy(
                    status = ContaStatus.EXCLUIDO,
                    excluidaEm = System.currentTimeMillis(),
                    atualizadaEm = System.currentTimeMillis()
                ).also {
                    ActivityLogStorage.record(
                        context = context,
                        action = ActivityAction.TRASHED,
                        conta = it,
                        details = "Movida para a lixeira"
                    )
                }.toEntity()
            )
        }
        if (saveAfterUpdate) saveAutomaticBackup(context)
    }

    suspend fun removerContaAsync(context: Context, id: String) = withContext(Dispatchers.IO) {
        removerConta(context, id)
    }

    @WorkerThread
    fun restaurarConta(context: Context, id: String) = databaseCall {
        val conta = buscarContaInternal(context, id) ?: return@databaseCall
        val restaurada = conta.copy(
            status = ContaStatus.PENDENTE,
            excluidaEm = null,
            atualizadaEm = System.currentTimeMillis()
        )
        dao(context).upsert(restaurada.toEntity())
        ActivityLogStorage.record(
            context = context,
            action = ActivityAction.RESTORED,
            conta = restaurada,
            details = "Restaurada da lixeira"
        )
        NotificationScheduler.scheduleConta(context, restaurada)
        saveAutomaticBackup(context)
    }

    suspend fun restaurarContaAsync(context: Context, id: String) = withContext(Dispatchers.IO) {
        restaurarConta(context, id)
    }

    @WorkerThread
    fun removerDefinitivamente(context: Context, id: String) = databaseCall {
        val conta = buscarContaInternal(context, id)
        if (conta != null) NotificationScheduler.cancelConta(context, conta)
        dao(context).remover(id)
        if (conta != null) {
            ActivityLogStorage.record(
                context = context,
                action = ActivityAction.DELETED,
                conta = conta,
                details = "Exclusao definitiva"
            )
        }
        saveAutomaticBackup(context)
    }

    suspend fun removerDefinitivamenteAsync(context: Context, id: String) = withContext(Dispatchers.IO) {
        removerDefinitivamente(context, id)
    }

    @WorkerThread
    fun marcarComoPago(
        context: Context,
        id: String,
        pagoEm: Long = System.currentTimeMillis(),
        formaPagamento: String? = null,
        observacaoPagamento: String? = null
        ,pagoPor: String? = null
    ) = databaseCall {
        marcarComoPagoInternal(context, id, pagoEm, formaPagamento, observacaoPagamento, pagoPor, saveAfterUpdate = true)
    }

    private fun marcarComoPagoInternal(
        context: Context,
        id: String,
        pagoEm: Long = System.currentTimeMillis(),
        formaPagamento: String? = null,
        observacaoPagamento: String? = null,
        pagoPor: String? = null,
        saveAfterUpdate: Boolean
    ) {
        val conta = buscarContaInternal(context, id) ?: return
        atualizarContaInternal(
            context = context,
            conta.copy(
                status = ContaStatus.PAGO,
                pagoEm = pagoEm,
                formaPagamento = formaPagamento?.trim()?.takeIf { it.isNotBlank() },
                observacaoPagamento = observacaoPagamento?.trim()?.takeIf { it.isNotBlank() },
                pagoPor = pagoPor?.trim()?.takeIf { it.isNotBlank() },
                pixAgendamentoStatus = if (
                    formaPagamento.equals("PIX", ignoreCase = true) || !conta.pixCopiaCola.isNullOrBlank() || !conta.pixChave.isNullOrBlank()
                ) PixAgendamentoStatus.EXECUTADO else conta.pixAgendamentoStatus
            ),
            saveAfterUpdate = saveAfterUpdate,
            activityAction = ActivityAction.PAID,
            activityDetails = buildString {
                append(if (conta.direcao == ContaDirecao.RECEBER) "Recebimento confirmado" else "Pagamento confirmado")
                formaPagamento?.takeIf { it.isNotBlank() }?.let { append(" via ${it.trim()}") }
            },
            actorName = pagoPor
        )
    }

    suspend fun marcarComoPagoAsync(
        context: Context,
        id: String,
        pagoEm: Long = System.currentTimeMillis(),
        formaPagamento: String? = null,
        observacaoPagamento: String? = null
        ,pagoPor: String? = null
    ) = withContext(Dispatchers.IO) {
        marcarComoPago(context, id, pagoEm, formaPagamento, observacaoPagamento, pagoPor)
    }

    suspend fun marcarComoPagoAsync(context: Context, ids: Collection<String>) = withContext(Dispatchers.IO) {
        ids.forEach { marcarComoPagoInternal(context, it, saveAfterUpdate = false) }
        if (ids.isNotEmpty()) saveAutomaticBackup(context)
    }

    suspend fun moverParaLixeiraAsync(context: Context, ids: Collection<String>) = withContext(Dispatchers.IO) {
        ids.forEach { removerContaInternal(context, it, saveAfterUpdate = false) }
        if (ids.isNotEmpty()) saveAutomaticBackup(context)
    }

    @WorkerThread
    fun marcarComoPendente(context: Context, id: String) = databaseCall {
        val conta = buscarContaInternal(context, id) ?: return@databaseCall
        atualizarContaInternal(
            context = context,
            conta.copy(
                status = ContaStatus.PENDENTE,
                pagoEm = null,
                formaPagamento = null,
                observacaoPagamento = null,
                pagoPor = null,
                pixAgendamentoStatus = if (conta.pixAgendado) PixAgendamentoStatus.AGENDADO else null
            ),
            activityAction = ActivityAction.REOPENED,
            activityDetails = "Marcada como pendente novamente"
        )
    }

    suspend fun marcarComoPendenteAsync(context: Context, id: String) = withContext(Dispatchers.IO) {
        marcarComoPendente(context, id)
    }

    suspend fun encerrarRecorrenciaAsync(context: Context, contaReferencia: ContaSalva) = withContext(Dispatchers.IO) {
        migratePrefsIfNeeded(context)
        val agora = System.currentTimeMillis()
        val hoje = startOfToday()
        val contaDao = dao(context)
        val serie = contaDao.listarPorEspaco(contaReferencia.spaceId)
            .map(ContaEntity::toContaSalva)
            .filter { isSameRecurrenceSeries(it, contaReferencia) && it.status != ContaStatus.EXCLUIDO }

        val atualizadas = serie.map { conta ->
            val futura = parseContaDate(conta.vencimento)?.after(hoje) == true
            if (futura && conta.status == ContaStatus.PENDENTE) {
                NotificationScheduler.cancelConta(context, conta)
                conta.copy(
                    recorrente = false,
                    status = ContaStatus.EXCLUIDO,
                    excluidaEm = agora,
                    atualizadaEm = agora
                )
            } else {
                conta.copy(recorrente = false, atualizadaEm = agora)
            }
        }
        contaDao.upsertAll(atualizadas.map(ContaSalva::toEntity))
        saveAutomaticBackup(context)
    }

    @WorkerThread
    fun substituirTodas(context: Context, contas: List<ContaSalva>) = databaseCall {
        val contaDao = dao(context)
        SpaceStorage.ensureSpaces(context, contas.map(ContaSalva::spaceId))
        contaDao.substituirTodas(contas.map { it.toEntity() })
        contas.forEach { NotificationScheduler.scheduleConta(context, it) }
        salvarListaPrefs(context, contas)
        saveAutomaticBackup(context)
    }

    suspend fun substituirTodasAsync(context: Context, contas: List<ContaSalva>) = withContext(Dispatchers.IO) {
        substituirTodas(context, contas)
    }

    /** Aplica contas novas ou versões da nuvem que sejam mais recentes. */
    suspend fun mesclarDaNuvemAsync(context: Context, contas: List<ContaSalva>): Int = withContext(Dispatchers.IO) {
        if (contas.isEmpty()) return@withContext 0
        migratePrefsIfNeeded(context)
        SpaceStorage.ensureSpaces(context, contas.map(ContaSalva::spaceId))
        val contaDao = dao(context)
        val existentes = contaDao.listar().associateBy(ContaEntity::id)
        val recebidas = contas.distinctBy(ContaSalva::id).filter { remota ->
            val local = existentes[remota.id]
            local == null || remota.atualizadaEm > local.atualizadaEm
        }
        if (recebidas.isNotEmpty()) {
            contaDao.upsertAll(recebidas.map(ContaSalva::toEntity))
            recebidas.forEach {
                if (it.status == ContaStatus.PENDENTE) NotificationScheduler.scheduleConta(context, it)
                else NotificationScheduler.cancelConta(context, it)
            }
            saveAutomaticBackup(context)
        }
        recebidas.size
    }

    private fun salvarListaPrefs(context: Context, contas: List<ContaSalva>) {
        val array = JSONArray()
        contas.forEach { conta ->
            array.put(
                JSONObject()
                    .put("id", conta.id)
                    .put("spaceId", conta.spaceId)
                    .put("codigo", conta.codigo)
                    .put("banco", conta.banco)
                    .put("valor", conta.valor)
                    .put("vencimento", conta.vencimento)
                    .put("tipo", conta.tipo)
                    .put("criadaEm", conta.criadaEm)
                    .put("fornecedor", conta.fornecedor)
                    .put("categoria", conta.categoria)
                    .put("subcategoria", conta.subcategoria)
                    .put("responsavel", conta.responsavel)
                    .put("pagoPor", conta.pagoPor)
                    .put("status", conta.status)
                    .put("pagoEm", conta.pagoEm)
                    .put("formaPagamento", conta.formaPagamento)
                    .put("observacaoPagamento", conta.observacaoPagamento)
                    .put("recorrente", conta.recorrente)
                    .put("recorrencia", conta.recorrencia)
                    .put("comprovantePath", conta.comprovantePath)
                    .put("boletoPdfPath", conta.boletoPdfPath)
                    .put("direcao", conta.direcao)
                    .put("clienteWhatsapp", conta.clienteWhatsapp)
                    .put("pixCopiaCola", conta.pixCopiaCola)
                    .put("pixChave", conta.pixChave)
                    .put("pixRecebedor", conta.pixRecebedor)
                    .put("pixTxId", conta.pixTxId)
                    .put("pixAgendado", conta.pixAgendado)
                    .put("pixAgendamentoStatus", conta.pixAgendamentoStatus)
                    .put("excluidaEm", conta.excluidaEm)
                    .put("atualizadaEm", conta.atualizadaEm)
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONTAS, array.toString())
            .apply()
    }

    private fun migratePrefsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ROOM_MIGRATION_COMPLETED, false)) return

        val roomDatabaseAlreadyExists = context.getDatabasePath(HsCashDatabase.DATABASE_NAME).exists()
        val contaDao = dao(context)
        if (!roomDatabaseAlreadyExists && contaDao.count() == 0) {
            val prefsContas = listarPrefs(context)
            if (prefsContas.isNotEmpty()) {
                contaDao.upsertAll(prefsContas.map { it.toEntity() })
            }
        }

        // A migracao deve acontecer uma unica vez. Sem esta marca, esvaziar o Room
        // poderia importar novamente a copia antiga e ressuscitar contas excluidas.
        prefs.edit()
            .putBoolean(KEY_ROOM_MIGRATION_COMPLETED, true)
            .remove(KEY_CONTAS)
            .apply()
    }

    private fun dao(context: Context): ContaDao {
        return HsCashDatabase.get(context).contaDao()
    }

    private fun <T> databaseCall(block: () -> T): T {
        return block()
    }

    private fun saveAutomaticBackup(context: Context) {
        migratePrefsIfNeeded(context)
        val todas = dao(context).listar().map { it.toContaSalva() }.filter { it.status != ContaStatus.EXCLUIDO }
        BackupHelper.saveAutomaticBackup(context, todas)
        HsCashWidgetProvider.refresh(context)
        CloudSyncScheduler.enqueue(context)
    }

    @WorkerThread
    internal fun finalizarAlteracoesEmLote(context: Context) {
        saveAutomaticBackup(context)
    }

    private fun criarRecorrenciasFuturas(context: Context, conta: ContaSalva) {
        if (!conta.recorrente || !isValidContaDate(conta.vencimento)) return

        val contaDao = dao(context)
        val existentes = contaDao.listarPorEspaco(conta.spaceId).map { it.toContaSalva() }
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
                        formaPagamento = null,
                        observacaoPagamento = null,
                        criadaEm = System.currentTimeMillis() + novasContas.size,
                        atualizadaEm = System.currentTimeMillis() + novasContas.size,
                        comprovantePath = null,
                        boletoPdfPath = null
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
        val existentes = contaDao.listarPorEspaco(conta.spaceId).map { it.toContaSalva() }
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
    val spaceHash = Integer.toHexString(conta.spaceId.hashCode())
    val codigoHash = Integer.toHexString(conta.codigo.hashCode())
    val valorHash = Integer.toHexString(conta.valor.hashCode())
    val vencimentoKey = vencimento.filter(Char::isDigit)
    return "rec-$spaceHash-${conta.direcao}-$codigoHash-$valorHash-$vencimentoKey"
}

private fun isSameRecurringConta(
    existente: ContaSalva,
    origem: ContaSalva,
    vencimento: String,
    idRecorrente: String
): Boolean {
    return existente.id == idRecorrente ||
        (existente.spaceId == origem.spaceId &&
            existente.codigo == origem.codigo &&
            existente.valor == origem.valor &&
            existente.direcao == origem.direcao &&
            existente.vencimento == vencimento)
}

internal fun isSameRecurrenceSeries(first: ContaSalva, second: ContaSalva): Boolean =
    first.spaceId == second.spaceId &&
        first.codigo == second.codigo &&
        first.fornecedor.equals(second.fornecedor, ignoreCase = true) &&
        first.valor == second.valor &&
        first.direcao == second.direcao &&
        first.recorrencia == second.recorrencia

private fun JSONObject.optLongOrNull(key: String): Long? {
    return if (has(key) && !isNull(key)) optLong(key) else null
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}
