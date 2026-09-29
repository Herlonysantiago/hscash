package hssolutions.hscash.cloud

import android.content.Context
import hssolutions.hscash.ui.ContaDirecao
import hssolutions.hscash.ui.ContaSalva
import hssolutions.hscash.ui.ContaStatus
import hssolutions.hscash.ui.ContaStorage
import hssolutions.hscash.ui.LocalSpace
import hssolutions.hscash.ui.SpaceStorage
import hssolutions.hscash.ui.SpaceType
import io.github.jan.supabase.postgrest.from
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID

data class CloudSyncSummary(
    val createdSpaces: Int,
    val linkedSpaces: Int,
    val uploadedAccounts: Int,
    val downloadedAccounts: Int,
    val skippedInvalidAccounts: Int,
    val uploadedAttachments: Int,
    val downloadedAttachments: Int,
    val deletedAttachments: Int,
    val skippedAttachments: Int,
    val uploadedActivities: Int = 0,
    val downloadedActivities: Int = 0,
    val skippedActivities: Int = 0
)

/** Sincronização bidirecional: a alteração mais recente vence o conflito. */
object CloudSyncRepository {
    suspend fun sync(context: Context): CloudSyncSummary {
        val user = CloudAuthRepository.currentUser() ?: error("Entre na conta online antes de sincronizar.")
        val client = SupabaseProvider.client ?: error("A nuvem ainda nao esta configurada neste aparelho.")

        var remoteSpaces = client.from("spaces").select().decodeList<CloudSpace>()
        var createdSpaces = 0
        var linkedSpaces = 0

        SpaceStorage.list(context).forEach { local ->
            val linked = local.cloudId?.let { id -> remoteSpaces.firstOrNull { it.id == id } }
                ?: remoteSpaces.firstOrNull {
                    it.ownerId == user.id && it.nome.equals(local.name, ignoreCase = true) && it.tipo == local.type
                }
            val remote = linked ?: run {
                val remoteId = UUID.randomUUID().toString()
                client.from("spaces").insert(
                    CloudSpaceWrite(
                        id = remoteId,
                        nome = local.name,
                        tipo = local.type,
                        icon = local.icon,
                        color = local.color,
                        ownerId = user.id
                    )
                )
                CloudSpace(
                    id = remoteId,
                    nome = local.name,
                    tipo = local.type,
                    icon = local.icon,
                    color = local.color,
                    ownerId = user.id
                ).also {
                    remoteSpaces = remoteSpaces + it
                    createdSpaces++
                }
            }
            if (local.cloudId != remote.id) {
                SpaceStorage.linkCloudId(context, local.id, remote.id)
                linkedSpaces++
            }
        }

        remoteSpaces.forEach { remote ->
            if (SpaceStorage.list(context).none { it.cloudId == remote.id }) {
                SpaceStorage.importFromCloud(
                    context = context,
                    cloudId = remote.id,
                    name = remote.nome,
                    type = remote.tipo,
                    icon = remote.icon,
                    color = remote.color
                )
                linkedSpaces++
            }
        }

        val localSpacesById = SpaceStorage.list(context).associateBy(LocalSpace::id)
        var skippedInvalid = 0
        val localAccounts = ContaStorage.listarTodosParaSincronizacaoAsync(context)
        val remoteAccountsBeforeUpload = client.from("accounts").select().decodeList<CloudAccount>()
        val remoteByLegacyKey = remoteAccountsBeforeUpload.associateBy { remote ->
            remote.spaceId to (remote.legacyId?.takeIf(String::isNotBlank) ?: "cloud-${remote.id}")
        }
        val writes = localAccounts.mapNotNull { account ->
            val remoteSpaceId = localSpacesById[account.spaceId]?.cloudId
            if (remoteSpaceId == null) {
                skippedInvalid++
                return@mapNotNull null
            }
            val remote = remoteSpaceId?.let { remoteByLegacyKey[it to account.id] }
            val mismatchFields = remote?.let { remoteMismatchFields(account, it) }.orEmpty()
            val shouldUpload = remote == null || (
                mismatchFields.isNotEmpty() &&
                    shouldUploadLocal(
                        localUpdatedAt = account.atualizadaEm,
                        remoteUpdatedAt = remote.updatedAt?.let(::isoInstantToMillisOrNull)
                    )
                )
            if (!shouldUpload) return@mapNotNull null
            val write = account.toCloudWrite(remoteSpaceId)
            if (write == null) skippedInvalid++
            write
        }
        if (writes.isNotEmpty()) {
            client.from("accounts").upsert(writes) {
                onConflict = "space_id,legacy_id"
            }
        }

        val refreshedSpaces = SpaceStorage.list(context)
        val localByCloudId = refreshedSpaces.mapNotNull { space -> space.cloudId?.let { it to space } }.toMap()
        val currentLocalByRemoteKey = localAccounts.mapNotNull { account ->
            val cloudSpaceId = localSpacesById[account.spaceId]?.cloudId ?: return@mapNotNull null
            (cloudSpaceId to account.id) to account
        }.toMap()
        val remoteAccounts = client.from("accounts").select().decodeList<CloudAccount>()
        val incoming = remoteAccounts.mapNotNull { remote ->
            val localSpace = localByCloudId[remote.spaceId] ?: return@mapNotNull null
            val remoteLocalId = remote.legacyId?.takeIf(String::isNotBlank) ?: "cloud-${remote.id}"
            val currentLocal = currentLocalByRemoteKey[remote.spaceId to remoteLocalId]
            if (!shouldDownloadRemote(currentLocal, remote)) return@mapNotNull null
            remote.toLocal(localSpace.id)
        }
        val downloaded = ContaStorage.mesclarDaNuvemAsync(context, incoming)
        val attachmentResult = CloudAttachmentSyncRepository.sync(
            context = context,
            spaces = refreshedSpaces,
            localAccounts = ContaStorage.listarTodosParaSincronizacaoAsync(context),
            remoteAccounts = remoteAccounts
        )
        val activityResult = CloudActivityLogSyncRepository.sync(
            context = context,
            spaces = refreshedSpaces
        )

        return CloudSyncSummary(
            createdSpaces = createdSpaces,
            linkedSpaces = linkedSpaces,
            uploadedAccounts = writes.size,
            downloadedAccounts = downloaded,
            skippedInvalidAccounts = skippedInvalid,
            uploadedAttachments = attachmentResult.uploaded,
            downloadedAttachments = attachmentResult.downloaded,
            deletedAttachments = attachmentResult.deleted,
            skippedAttachments = attachmentResult.skipped,
            uploadedActivities = activityResult.uploaded,
            downloadedActivities = activityResult.downloaded,
            skippedActivities = activityResult.skipped
        )
    }
}

internal fun cloudSyncErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        message.contains("pix_", ignoreCase = true) &&
            (message.contains("column", ignoreCase = true) || message.contains("schema cache", ignoreCase = true)) ->
            "A atualização Pix ainda não foi aplicada no Supabase. Execute a migração de campos Pix."
        message.contains("relation", ignoreCase = true) && message.contains("does not exist", ignoreCase = true) ->
            "As tabelas da nuvem ainda não foram criadas no Supabase."
        message.contains("row-level security", ignoreCase = true) || message.contains("permission", ignoreCase = true) ->
            "Sua conta não tem permissão para sincronizar este espaço."
        message.contains("JWT", ignoreCase = true) || message.contains("session", ignoreCase = true) ->
            "A sessão expirou. Entre novamente na conta online."
        else -> cloudAuthErrorMessage(error)
    }
}

private fun ContaSalva.toCloudWrite(remoteSpaceId: String): CloudAccountWrite? {
    val cents = cloudMoneyToCents(valor) ?: return null
    val dueDate = cloudLocalDateToIso(vencimento) ?: return null
    return CloudAccountWrite(
        spaceId = remoteSpaceId,
        legacyId = id,
        codigo = codigo,
        banco = banco,
        valueInCents = cents,
        vencimento = dueDate,
        tipo = tipo,
        fornecedor = fornecedor,
        categoria = categoria,
        subcategoria = subcategoria,
        responsavel = responsavel,
        status = status,
        paidAt = pagoEm?.let { Instant.ofEpochMilli(it).toString() },
        paymentMethod = formaPagamento,
        paymentNote = observacaoPagamento,
        paidBy = pagoPor,
        recorrente = recorrente,
        recorrencia = recorrencia,
        direcao = direcao,
        clientWhatsapp = clienteWhatsapp,
        pixCopiaCola = pixCopiaCola,
        pixChave = pixChave,
        pixRecebedor = pixRecebedor,
        pixTxId = pixTxId,
        pixAgendado = pixAgendado,
        pixAgendamentoStatus = pixAgendamentoStatus,
        deletedAt = excluidaEm?.let { Instant.ofEpochMilli(it).toString() }
    )
}

private fun CloudAccount.toLocal(localSpaceId: String): ContaSalva? {
    val dueDate = cloudIsoDateToLocal(vencimento) ?: return null
    val remoteUpdatedAt = updatedAt?.let(::isoInstantToMillisOrNull)
        ?: createdAt?.let(::isoInstantToMillisOrNull)
        ?: 0L
    return ContaSalva(
        id = legacyId?.takeIf(String::isNotBlank) ?: "cloud-$id",
        spaceId = localSpaceId,
        codigo = codigo,
        banco = banco,
        valor = centsToMoney(valueInCents),
        vencimento = dueDate,
        tipo = tipo,
        criadaEm = createdAt?.let(::isoInstantToMillis) ?: System.currentTimeMillis(),
        fornecedor = fornecedor,
        categoria = categoria,
        subcategoria = subcategoria,
        responsavel = responsavel,
        status = if (deletedAt != null) {
            ContaStatus.EXCLUIDO
        } else {
            status.takeIf { it in setOf(ContaStatus.PENDENTE, ContaStatus.PAGO, ContaStatus.EXCLUIDO) }
                ?: ContaStatus.PENDENTE
        },
        pagoEm = paidAt?.let(::isoInstantToMillis),
        formaPagamento = paymentMethod,
        observacaoPagamento = paymentNote,
        pagoPor = paidBy,
        recorrente = recorrente,
        recorrencia = recorrencia,
        direcao = direcao.takeIf { it in setOf(ContaDirecao.PAGAR, ContaDirecao.RECEBER) } ?: ContaDirecao.PAGAR,
        clienteWhatsapp = clientWhatsapp,
        pixCopiaCola = pixCopiaCola,
        pixChave = pixChave,
        pixRecebedor = pixRecebedor,
        pixTxId = pixTxId,
        pixAgendado = pixAgendado,
        pixAgendamentoStatus = pixAgendamentoStatus,
        excluidaEm = deletedAt?.let(::isoInstantToMillisOrNull),
        atualizadaEm = remoteUpdatedAt
    )
}

internal fun shouldUploadLocal(localUpdatedAt: Long, remoteUpdatedAt: Long?): Boolean =
    remoteUpdatedAt == null || localUpdatedAt > remoteUpdatedAt

/** Evita regravar e recontar como recebida uma conta cujo conteúdo já é idêntico. */
internal fun shouldDownloadRemote(local: ContaSalva?, remote: CloudAccount): Boolean =
    local == null || !remoteMatchesLocal(local, remote)

internal fun remoteMatchesLocal(local: ContaSalva, remote: CloudAccount): Boolean {
    return remoteMismatchFields(local, remote).isEmpty()
}

internal fun remoteMismatchFields(local: ContaSalva, remote: CloudAccount): List<String> = buildList {
    if (remote.legacyId != local.id) add("legacyId")
    if (remote.codigo != local.codigo) add("codigo")
    if (remote.banco != local.banco) add("banco")
    if (remote.valueInCents != cloudMoneyToCents(local.valor)) add("valor")
    if (remote.vencimento != cloudLocalDateToIso(local.vencimento)) add("vencimento")
    if (remote.tipo != local.tipo) add("tipo")
    if (remote.fornecedor != local.fornecedor) add("fornecedor")
    if (remote.categoria != local.categoria) add("categoria")
    if (remote.subcategoria.orEmpty() != local.subcategoria.orEmpty()) add("subcategoria")
    if (remote.responsavel.orEmpty() != local.responsavel.orEmpty()) add("responsavel")
    if (remote.status != local.status) add("status")
    if (remote.paidAt.toEpochMillisOrNull() != local.pagoEm) add("pagoEm")
    if (remote.paymentMethod.orEmpty() != local.formaPagamento.orEmpty()) add("formaPagamento")
    if (remote.paymentNote.orEmpty() != local.observacaoPagamento.orEmpty()) add("observacaoPagamento")
    if (remote.paidBy.orEmpty() != local.pagoPor.orEmpty()) add("pagoPor")
    if (remote.recorrente != local.recorrente) add("recorrente")
    if (remote.recorrencia != local.recorrencia) add("recorrencia")
    if (remote.direcao != local.direcao) add("direcao")
    if (remote.clientWhatsapp.orEmpty() != local.clienteWhatsapp.orEmpty()) add("clienteWhatsapp")
    if (remote.pixCopiaCola.orEmpty() != local.pixCopiaCola.orEmpty()) add("pixCopiaCola")
    if (remote.pixChave.orEmpty() != local.pixChave.orEmpty()) add("pixChave")
    if (remote.pixRecebedor.orEmpty() != local.pixRecebedor.orEmpty()) add("pixRecebedor")
    if (remote.pixTxId.orEmpty() != local.pixTxId.orEmpty()) add("pixTxId")
    if (remote.pixAgendado != local.pixAgendado) add("pixAgendado")
    if (remote.pixAgendamentoStatus.orEmpty() != local.pixAgendamentoStatus.orEmpty()) add("pixAgendamentoStatus")
    if ((remote.deletedAt != null) != (local.excluidaEm != null)) add("excluidaEm")
}

private fun String?.toEpochMillisOrNull(): Long? =
    cloudInstantToMillisOrNull(this)

/**
 * O Postgres/Supabase costuma devolver timestamps com offset (`+00:00`),
 * enquanto [Instant.parse] aceita apenas algumas variações desse formato.
 */
internal fun cloudInstantToMillisOrNull(value: String?): Long? = value?.let { timestamp ->
    runCatching { java.time.OffsetDateTime.parse(timestamp).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(timestamp).toEpochMilli() }
        .getOrNull()
}

internal fun cloudMoneyToCents(value: String): Long? {
    val compact = value.replace("R$", "", ignoreCase = true)
        .replace("\u00A0", "")
        .replace(" ", "")
        .filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
    if (compact.isBlank()) return null
    val normalized = when {
        compact.contains(',') -> compact.replace(".", "").replace(',', '.')
        compact.count { it == '.' } == 1 && compact.substringAfter('.').length in 1..2 -> compact
        else -> compact.replace(".", "")
    }
    val decimal = normalized.toBigDecimalOrNull() ?: return null
    if (decimal < BigDecimal.ZERO) return null
    return decimal.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
}

private val localDateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    .withResolverStyle(ResolverStyle.STRICT)

internal fun cloudLocalDateToIso(value: String): String? = runCatching {
    LocalDate.parse(value.trim(), localDateFormatter).toString()
}.getOrNull()

internal fun cloudIsoDateToLocal(value: String): String? = runCatching {
    LocalDate.parse(value).format(localDateFormatter)
}.getOrNull()

private fun isoInstantToMillis(value: String): Long =
    cloudInstantToMillisOrNull(value) ?: System.currentTimeMillis()

private fun isoInstantToMillisOrNull(value: String): Long? =
    cloudInstantToMillisOrNull(value)

private fun centsToMoney(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal(cents).movePointLeft(2))
