package hssolutions.hscash.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object SpaceType {
    const val PERSONAL = "PESSOAL"
    const val FAMILY = "FAMILIA"
    const val COMPANY = "EMPRESA"
    val ALL = setOf(PERSONAL, FAMILY, COMPANY)
}

data class LocalSpace(
    val id: String,
    val name: String,
    val type: String,
    val icon: String,
    val color: String,
    val createdAt: Long,
    val cloudId: String? = null
)

/**
 * Metadados locais dos espaços. As contas continuam no Room e usam o mesmo id
 * que será sincronizado com o Supabase quando a sincronização for ativada.
 */
object SpaceStorage {
    const val DEFAULT_SPACE_ID = "local-default"

    private const val PREFS_NAME = "hs_cash_spaces"
    private const val KEY_SPACES = "spaces"
    private const val KEY_ACTIVE_SPACE_ID = "active_space_id"

    @Synchronized
    fun list(context: Context): List<LocalSpace> {
        val spaces = read(context).ifEmpty { listOf(defaultSpace()) }
        persist(context, spaces)
        ensureActiveIsValid(context, spaces)
        return spaces.sortedBy(LocalSpace::createdAt)
    }

    fun activeSpaceId(context: Context): String {
        val spaces = list(context)
        val saved = prefs(context).getString(KEY_ACTIVE_SPACE_ID, null)
        return saved?.takeIf { id -> spaces.any { it.id == id } } ?: spaces.first().id.also {
            prefs(context).edit().putString(KEY_ACTIVE_SPACE_ID, it).apply()
        }
    }

    fun active(context: Context): LocalSpace {
        val spaces = list(context)
        return spaces.firstOrNull { it.id == activeSpaceId(context) } ?: spaces.first()
    }

    fun select(context: Context, id: String): Boolean {
        if (list(context).none { it.id == id }) return false
        prefs(context).edit().putString(KEY_ACTIVE_SPACE_ID, id).apply()
        return true
    }

    @Synchronized
    fun create(
        context: Context,
        name: String,
        type: String,
        icon: String,
        color: String
    ): LocalSpace? {
        val safeName = name.trim().take(80)
        if (safeName.isBlank()) return null
        val space = LocalSpace(
            id = UUID.randomUUID().toString(),
            name = safeName,
            type = type.takeIf { it in SpaceType.ALL } ?: SpaceType.PERSONAL,
            icon = icon.ifBlank { "wallet" },
            color = normalizeColor(color),
            createdAt = System.currentTimeMillis()
        )
        persist(context, list(context) + space)
        return space
    }

    @Synchronized
    fun update(context: Context, updated: LocalSpace): Boolean {
        val current = list(context)
        if (current.none { it.id == updated.id }) return false
        val safe = updated.copy(
            name = updated.name.trim().take(80).ifBlank { "Meu espaço" },
            type = updated.type.takeIf { it in SpaceType.ALL } ?: SpaceType.PERSONAL,
            icon = updated.icon.ifBlank { "wallet" },
            color = normalizeColor(updated.color)
        )
        persist(context, current.map { if (it.id == safe.id) safe else it })
        return true
    }

    @Synchronized
    fun linkCloudId(context: Context, localId: String, cloudId: String): Boolean {
        val current = list(context)
        if (current.none { it.id == localId }) return false
        persist(context, current.map { if (it.id == localId) it.copy(cloudId = cloudId) else it })
        return true
    }

    @Synchronized
    fun importFromCloud(
        context: Context,
        cloudId: String,
        name: String,
        type: String,
        icon: String,
        color: String
    ): LocalSpace {
        list(context).firstOrNull { it.cloudId == cloudId }?.let { return it }
        val imported = LocalSpace(
            id = "cloud-$cloudId",
            name = name.trim().take(80).ifBlank { "Espaço compartilhado" },
            type = type.takeIf { it in SpaceType.ALL } ?: SpaceType.PERSONAL,
            icon = icon.ifBlank { "wallet" },
            color = normalizeColor(color),
            createdAt = System.currentTimeMillis(),
            cloudId = cloudId
        )
        persist(context, list(context) + imported)
        return imported
    }

    /** Torna espaços vindos de um backup visíveis no seletor local. */
    @Synchronized
    fun ensureSpaces(context: Context, ids: Collection<String>) {
        val current = list(context).toMutableList()
        ids.filter(String::isNotBlank).distinct().forEachIndexed { index, id ->
            if (current.none { it.id == id }) {
                current += LocalSpace(
                    id = id,
                    name = "Espaço importado ${index + 1}",
                    type = SpaceType.PERSONAL,
                    icon = "wallet",
                    color = "#0B3D6E",
                    createdAt = System.currentTimeMillis() + index
                )
            }
        }
        persist(context, current)
    }

    private fun defaultSpace() = LocalSpace(
        id = DEFAULT_SPACE_ID,
        name = "Meu espaço",
        type = SpaceType.PERSONAL,
        icon = "wallet",
        color = "#0B3D6E",
        createdAt = 0L
    )

    private fun read(context: Context): List<LocalSpace> {
        val raw = prefs(context).getString(KEY_SPACES, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
                LocalSpace(
                    id = id,
                    name = item.optString("name", "Meu espaço").ifBlank { "Meu espaço" },
                    type = item.optString("type", SpaceType.PERSONAL),
                    icon = item.optString("icon", "wallet"),
                    color = normalizeColor(item.optString("color", "#0B3D6E")),
                    createdAt = item.optLong("createdAt", index.toLong()),
                    cloudId = item.optString("cloudId").takeIf { it.isNotBlank() }
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(context: Context, spaces: List<LocalSpace>) {
        val array = JSONArray()
        spaces.distinctBy(LocalSpace::id).forEach { space ->
            array.put(
                JSONObject()
                    .put("id", space.id)
                    .put("name", space.name)
                    .put("type", space.type)
                    .put("icon", space.icon)
                    .put("color", space.color)
                    .put("createdAt", space.createdAt)
                    .put("cloudId", space.cloudId)
            )
        }
        prefs(context).edit().putString(KEY_SPACES, array.toString()).apply()
    }

    private fun ensureActiveIsValid(context: Context, spaces: List<LocalSpace>) {
        val active = prefs(context).getString(KEY_ACTIVE_SPACE_ID, null)
        if (active == null || spaces.none { it.id == active }) {
            prefs(context).edit().putString(KEY_ACTIVE_SPACE_ID, spaces.first().id).apply()
        }
    }

    private fun normalizeColor(value: String): String {
        return value.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }?.uppercase() ?: "#0B3D6E"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
