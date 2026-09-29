package hssolutions.hscash.cloud

import io.github.jan.supabase.postgrest.from
import java.time.Instant

object CloudMemberRole {
    const val OWNER = "OWNER"
    const val ADMIN = "ADMIN"
    const val EDITOR = "EDITOR"
    const val VIEWER = "VIEWER"
    val INVITABLE = listOf(ADMIN, EDITOR, VIEWER)
}

data class CloudSpaceAccess(
    val space: CloudSpace,
    val members: List<CloudSpaceAccessMember>,
    val pendingInvites: List<CloudSpaceInvite>
)

data class CloudSpaceAccessMember(
    val userId: String,
    val name: String,
    val role: String,
    val isCurrentUser: Boolean
)

data class CloudReceivedInvite(
    val invite: CloudSpaceInvite,
    val spaceName: String
)

object CloudSpacesRepository {
    suspend fun loadAccess(spaceId: String): CloudSpaceAccess {
        val client = requireClient()
        val user = requireUser()
        val space = client.from("spaces").select {
            filter { eq("id", spaceId) }
        }.decodeSingle<CloudSpace>()
        val memberships = client.from("space_members").select {
            filter { eq("space_id", spaceId) }
        }.decodeList<CloudSpaceMember>()
        val profiles = client.from("profiles").select().decodeList<CloudProfile>().associateBy(CloudProfile::id)
        val members = buildList {
            add(
                CloudSpaceAccessMember(
                    userId = space.ownerId,
                    name = profiles[space.ownerId]?.nome.orEmpty().ifBlank { "Proprietário" },
                    role = CloudMemberRole.OWNER,
                    isCurrentUser = space.ownerId == user.id
                )
            )
            memberships.filter { it.userId != space.ownerId }.forEach { member ->
                add(
                    CloudSpaceAccessMember(
                        userId = member.userId,
                        name = profiles[member.userId]?.nome.orEmpty().ifBlank { "Integrante" },
                        role = member.role,
                        isCurrentUser = member.userId == user.id
                    )
                )
            }
        }
        val invites = client.from("space_invites").select {
            filter {
                eq("space_id", spaceId)
                eq("status", "PENDING")
            }
        }.decodeList<CloudSpaceInvite>()
        return CloudSpaceAccess(space, members, invites)
    }

    suspend fun invite(spaceId: String, email: String, role: String) {
        val user = requireUser()
        val safeEmail = email.trim().lowercase()
        require(safeEmail.contains("@") && safeEmail.contains(".")) { "Informe um e-mail válido." }
        require(!safeEmail.equals(user.email.trim(), ignoreCase = true)) {
            "Você já participa deste espaço com a conta conectada. Convide outro e-mail."
        }
        require(role in CloudMemberRole.INVITABLE) { "Escolha uma permissão válida." }
        client().from("space_invites").insert(
            CloudSpaceInviteWrite(
                spaceId = spaceId,
                email = safeEmail,
                role = role,
                invitedBy = user.id
            )
        )
    }

    suspend fun cancelInvite(inviteId: String) {
        client().from("space_invites").update(CloudSpaceInviteUpdate(status = "CANCELLED")) {
            filter { eq("id", inviteId) }
        }
    }

    suspend fun updateMemberRole(spaceId: String, userId: String, role: String) {
        require(role in CloudMemberRole.INVITABLE) { "Escolha uma permissão válida." }
        client().from("space_members").update(mapOf("role" to role)) {
            filter {
                eq("space_id", spaceId)
                eq("user_id", userId)
            }
        }
    }

    suspend fun removeMember(spaceId: String, userId: String) {
        client().from("space_members").delete {
            filter {
                eq("space_id", spaceId)
                eq("user_id", userId)
            }
        }
    }

    suspend fun receivedInvites(): List<CloudReceivedInvite> {
        val user = requireUser()
        val invites = client().from("space_invites").select {
            filter { eq("status", "PENDING") }
        }.decodeList<CloudSpaceInvite>().filter {
            it.email.trim().equals(user.email.trim(), ignoreCase = true)
        }
        if (invites.isEmpty()) return emptyList()
        val spaces = client().from("spaces").select().decodeList<CloudSpace>().associateBy(CloudSpace::id)
        return invites.map { invite ->
            CloudReceivedInvite(invite, spaces[invite.spaceId]?.nome ?: "Espaço compartilhado")
        }
    }

    suspend fun acceptInvite(invite: CloudSpaceInvite) {
        val user = requireUser()
        client().from("space_members").upsert(
            CloudSpaceMemberWrite(invite.spaceId, user.id, invite.role)
        ) {
            onConflict = "space_id,user_id"
        }
        client().from("space_invites").update(
            CloudSpaceInviteUpdate(status = "ACCEPTED", acceptedAt = Instant.now().toString())
        ) {
            filter { eq("id", invite.id) }
        }
    }

    suspend fun declineInvite(inviteId: String) {
        client().from("space_invites").update(CloudSpaceInviteUpdate(status = "CANCELLED")) {
            filter { eq("id", inviteId) }
        }
    }

    private fun client() = requireClient()

    private fun requireClient() = SupabaseProvider.client
        ?: error("A nuvem ainda não está configurada neste aparelho.")

    private fun requireUser() = CloudAuthRepository.currentUser()
        ?: error("Entre na conta online para gerenciar integrantes.")
}
