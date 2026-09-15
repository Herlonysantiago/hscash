package hssolutions.hscash.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val status: String = "PENDENTE",
    @SerialName("pago_em") val paidAt: String? = null,
    @SerialName("forma_pagamento") val paymentMethod: String? = null,
    @SerialName("observacao_pagamento") val paymentNote: String? = null,
    val recorrente: Boolean = false,
    val recorrencia: String = "Mensal",
    val direcao: String = "PAGAR",
    @SerialName("cliente_whatsapp") val clientWhatsapp: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
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
