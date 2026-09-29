package hssolutions.hscash.cloud

import android.content.Context
import hssolutions.hscash.ui.ActivityLogItem
import hssolutions.hscash.ui.ActivityLogStorage
import hssolutions.hscash.ui.LocalSpace
import hssolutions.hscash.ui.SpaceStorage
import io.github.jan.supabase.postgrest.from
import java.time.Instant

data class CloudActivityLogSyncResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0
)

object CloudActivityLogSyncRepository {
    suspend fun sync(
        context: Context,
        spaces: List<LocalSpace>
    ): CloudActivityLogSyncResult {
        val user = CloudAuthRepository.currentUser() ?: return CloudActivityLogSyncResult()
        val client = SupabaseProvider.client ?: return CloudActivityLogSyncResult()
        val cloudSpaceByLocal = spaces.mapNotNull { space -> space.cloudId?.let { space.id to it } }.toMap()
        val localSpaceByCloud = spaces.mapNotNull { space -> space.cloudId?.let { it to space.id } }.toMap()
        val actorName = runCatching { CloudAuthRepository.loadProfile()?.nome }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: user.email

        var skipped = 0
        val pending = ActivityLogStorage.listarPendentesSyncAsync(context)
        val writes = pending.mapNotNull { activity ->
            val cloudSpaceId = cloudSpaceByLocal[activity.spaceId]
            if (cloudSpaceId == null) {
                skipped++
                return@mapNotNull null
            }
            CloudActivityLogWrite(
                id = activity.id,
                spaceId = cloudSpaceId,
                accountLegacyId = activity.contaId,
                action = activity.action,
                title = activity.title,
                details = activity.details,
                actorId = user.id,
                actorName = activity.actorName ?: actorName,
                createdAt = Instant.ofEpochMilli(activity.createdAt).toString()
            )
        }

        if (writes.isNotEmpty()) {
            client.from("activity_logs").upsert(writes) {
                onConflict = "id"
            }
            ActivityLogStorage.marcarSincronizadasAsync(context, writes.map(CloudActivityLogWrite::id))
        }

        val remote = client.from("activity_logs").select().decodeList<CloudActivityLog>()
        val incoming = remote.mapNotNull { activity ->
            val localSpaceId = localSpaceByCloud[activity.spaceId] ?: return@mapNotNull null
            ActivityLogItem(
                id = activity.id,
                spaceId = localSpaceId,
                contaId = activity.accountLegacyId,
                action = activity.action,
                title = activity.title,
                details = activity.details,
                actorName = activity.actorName,
                createdAt = cloudInstantToMillisOrNull(activity.createdAt) ?: System.currentTimeMillis(),
                syncedAt = System.currentTimeMillis()
            )
        }
        val downloaded = ActivityLogStorage.mesclarDaNuvemAsync(context, incoming)
        return CloudActivityLogSyncResult(
            uploaded = writes.size,
            downloaded = downloaded,
            skipped = skipped
        )
    }
}
