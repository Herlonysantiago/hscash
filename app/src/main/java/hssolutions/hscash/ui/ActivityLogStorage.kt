package hssolutions.hscash.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ActivityAction {
    const val CREATED = "CREATED"
    const val UPDATED = "UPDATED"
    const val PAID = "PAID"
    const val REOPENED = "REOPENED"
    const val TRASHED = "TRASHED"
    const val RESTORED = "RESTORED"
    const val DELETED = "DELETED"
}

data class ActivityLogItem(
    val id: String,
    val spaceId: String,
    val contaId: String?,
    val action: String,
    val title: String,
    val details: String?,
    val actorName: String?,
    val createdAt: Long,
    val syncedAt: Long?
)

object ActivityLogStorage {
    private const val MAX_LOCAL_AGE_DAYS = 180L

    fun record(
        context: Context,
        action: String,
        conta: ContaSalva?,
        title: String? = null,
        details: String? = null,
        actorName: String? = null
    ) {
        val now = System.currentTimeMillis()
        val spaceId = conta?.spaceId ?: SpaceStorage.activeSpaceId(context)
        dao(context).upsert(
            ActivityLogEntity(
                id = "act-$now-${(conta?.id ?: action).hashCode().toUInt()}",
                spaceId = spaceId,
                contaId = conta?.id,
                action = action,
                title = title ?: defaultTitle(action, conta),
                details = details?.trim()?.takeIf(String::isNotBlank),
                actorName = actorName?.trim()?.takeIf(String::isNotBlank),
                createdAt = now,
                syncedAt = null
            )
        )
        cleanupOld(context)
    }

    suspend fun listarRecentesAsync(context: Context, limit: Int = 80): List<ActivityLogItem> = withContext(Dispatchers.IO) {
        dao(context).listarPorEspaco(SpaceStorage.activeSpaceId(context), limit).map(ActivityLogEntity::toItem)
    }

    suspend fun listarPorContaAsync(context: Context, contaId: String, limit: Int = 40): List<ActivityLogItem> = withContext(Dispatchers.IO) {
        dao(context).listarPorConta(contaId, limit).map(ActivityLogEntity::toItem)
    }

    suspend fun listarPendentesSyncAsync(context: Context, limit: Int = 100): List<ActivityLogItem> = withContext(Dispatchers.IO) {
        dao(context).listarPendentesSync(limit).map(ActivityLogEntity::toItem)
    }

    suspend fun marcarSincronizadasAsync(context: Context, ids: List<String>, syncedAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) dao(context).marcarSincronizadas(ids, syncedAt)
    }

    suspend fun mesclarDaNuvemAsync(context: Context, activities: List<ActivityLogItem>): Int = withContext(Dispatchers.IO) {
        if (activities.isEmpty()) return@withContext 0
        val entities = activities.map {
            ActivityLogEntity(
                id = it.id,
                spaceId = it.spaceId,
                contaId = it.contaId,
                action = it.action,
                title = it.title,
                details = it.details,
                actorName = it.actorName,
                createdAt = it.createdAt,
                syncedAt = it.syncedAt ?: System.currentTimeMillis()
            )
        }
        dao(context).insertIgnoreAll(entities)
        cleanupOld(context)
        entities.size
    }

    private fun cleanupOld(context: Context) {
        val before = System.currentTimeMillis() - MAX_LOCAL_AGE_DAYS * 24L * 60L * 60L * 1000L
        dao(context).removerAntigas(before)
    }

    private fun defaultTitle(action: String, conta: ContaSalva?): String {
        val nome = conta?.fornecedor?.takeIf(String::isNotBlank)
            ?: conta?.banco?.takeIf(String::isNotBlank)
            ?: "Conta"
        return when (action) {
            ActivityAction.CREATED -> "$nome criada"
            ActivityAction.UPDATED -> "$nome editada"
            ActivityAction.PAID -> "$nome confirmada"
            ActivityAction.REOPENED -> "$nome voltou para pendente"
            ActivityAction.TRASHED -> "$nome enviada para a lixeira"
            ActivityAction.RESTORED -> "$nome restaurada"
            ActivityAction.DELETED -> "$nome excluida definitivamente"
            else -> nome
        }
    }

    private fun dao(context: Context): ActivityLogDao {
        return HsCashDatabase.get(context).activityLogDao()
    }
}

private fun ActivityLogEntity.toItem(): ActivityLogItem {
    return ActivityLogItem(
        id = id,
        spaceId = spaceId,
        contaId = contaId,
        action = action,
        title = title,
        details = details,
        actorName = actorName,
        createdAt = createdAt,
        syncedAt = syncedAt
    )
}
