package hssolutions.hscash.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CloudProfile(
    val id: String,
    val nome: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CloudProfileUpdate(
    val nome: String
)

@Serializable
data class CloudSpaceWrite(
    val id: String,
    val nome: String,
    val tipo: String,
    @SerialName("icone") val icon: String,
    @SerialName("cor") val color: String,
    @SerialName("owner_id") val ownerId: String
)

@Serializable
data class CloudSpace(
    val id: String,
    val nome: String,
    val tipo: String,
    @SerialName("icone") val icon: String = "wallet",
    @SerialName("cor") val color: String = "#0B3D6E",
    @SerialName("imagem_path") val imagePath: String? = null,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CloudSpaceMember(
    @SerialName("space_id") val spaceId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CloudSpaceMemberWrite(
    @SerialName("space_id") val spaceId: String,
    @SerialName("user_id") val userId: String,
    val role: String
)

@Serializable
data class CloudSpaceInvite(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    val email: String,
    val role: String,
    val status: String,
    @SerialName("invited_by") val invitedBy: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CloudSpaceInviteWrite(
    @SerialName("space_id") val spaceId: String,
    val email: String,
    val role: String,
    @SerialName("invited_by") val invitedBy: String
)

@Serializable
data class CloudSpaceInviteUpdate(
    val status: String,
    @SerialName("accepted_at") val acceptedAt: String? = null
)

@Serializable
data class CloudAccount(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    @SerialName("legacy_id") val legacyId: String? = null,
    val codigo: String,
    val banco: String,
    @SerialName("valor_centavos") val valueInCents: Long,
    val vencimento: String,
    val tipo: String,
    val fornecedor: String = "",
    val categoria: String = "Outros",
    val subcategoria: String? = null,
    val responsavel: String? = null,
    val status: String = "PENDENTE",
    @SerialName("pago_em") val paidAt: String? = null,
    @SerialName("forma_pagamento") val paymentMethod: String? = null,
    @SerialName("observacao_pagamento") val paymentNote: String? = null,
    @SerialName("pago_por") val paidBy: String? = null,
    val recorrente: Boolean = false,
    val recorrencia: String = "Mensal",
    val direcao: String = "PAGAR",
    @SerialName("cliente_whatsapp") val clientWhatsapp: String? = null,
    @SerialName("pix_copia_cola") val pixCopiaCola: String? = null,
    @SerialName("pix_chave") val pixChave: String? = null,
    @SerialName("pix_recebedor") val pixRecebedor: String? = null,
    @SerialName("pix_txid") val pixTxId: String? = null,
    @SerialName("pix_agendado") val pixAgendado: Boolean = false,
    @SerialName("pix_agendamento_status") val pixAgendamentoStatus: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CloudAccountWrite(
    @SerialName("space_id") val spaceId: String,
    @SerialName("legacy_id") val legacyId: String,
    val codigo: String,
    val banco: String,
    @SerialName("valor_centavos") val valueInCents: Long,
    val vencimento: String,
    val tipo: String,
    val fornecedor: String,
    val categoria: String,
    val subcategoria: String? = null,
    val responsavel: String? = null,
    val status: String,
    @SerialName("pago_em") val paidAt: String? = null,
    @SerialName("forma_pagamento") val paymentMethod: String? = null,
    @SerialName("observacao_pagamento") val paymentNote: String? = null,
    @SerialName("pago_por") val paidBy: String? = null,
    val recorrente: Boolean,
    val recorrencia: String,
    val direcao: String,
    @SerialName("cliente_whatsapp") val clientWhatsapp: String? = null,
    @SerialName("pix_copia_cola") val pixCopiaCola: String? = null,
    @SerialName("pix_chave") val pixChave: String? = null,
    @SerialName("pix_recebedor") val pixRecebedor: String? = null,
    @SerialName("pix_txid") val pixTxId: String? = null,
    @SerialName("pix_agendado") val pixAgendado: Boolean = false,
    @SerialName("pix_agendamento_status") val pixAgendamentoStatus: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CloudAttachment(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    @SerialName("account_id") val accountId: String,
    val tipo: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("tamanho_bytes") val sizeInBytes: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class CloudAttachmentWrite(
    @SerialName("space_id") val spaceId: String,
    @SerialName("account_id") val accountId: String,
    val tipo: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("tamanho_bytes") val sizeInBytes: Long
)

@Serializable
data class CloudAttachmentDeletedUpdate(
    @SerialName("deleted_at") val deletedAt: String
)

@Serializable
data class CloudActivityLog(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    @SerialName("account_legacy_id") val accountLegacyId: String? = null,
    val action: String,
    val title: String,
    val details: String? = null,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CloudActivityLogWrite(
    val id: String,
    @SerialName("space_id") val spaceId: String,
    @SerialName("account_legacy_id") val accountLegacyId: String? = null,
    val action: String,
    val title: String,
    val details: String? = null,
    @SerialName("actor_id") val actorId: String,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("created_at") val createdAt: String
)
