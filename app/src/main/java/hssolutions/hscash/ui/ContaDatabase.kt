package hssolutions.hscash.ui

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "contas", indices = [Index("spaceId")])
data class ContaEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val codigo: String,
    val banco: String,
    val valor: String,
    val vencimento: String,
    val tipo: String,
    val criadaEm: Long,
    val fornecedor: String,
    val categoria: String,
    val subcategoria: String?,
    val responsavel: String?,
    val status: String,
    val pagoEm: Long?,
    val formaPagamento: String?,
    val observacaoPagamento: String?,
    val pagoPor: String?,
    val recorrente: Boolean,
    val recorrencia: String,
    val comprovantePath: String?,
    val boletoPdfPath: String?,
    val comprovanteCloudPath: String?,
    val boletoPdfCloudPath: String?,
    val direcao: String,
    val clienteWhatsapp: String?,
    val pixCopiaCola: String?,
    val pixChave: String?,
    val pixRecebedor: String?,
    val pixTxId: String?,
    val pixAgendado: Boolean,
    val pixAgendamentoStatus: String?,
    val excluidaEm: Long?,
    val atualizadaEm: Long
)

@Entity(
    tableName = "activity_logs",
    indices = [Index("spaceId"), Index("contaId"), Index("createdAt")]
)
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val contaId: String?,
    val action: String,
    val title: String,
    val details: String?,
    val actorName: String?,
    val createdAt: Long,
    val syncedAt: Long?
)

@Dao
interface ContaDao {
    @Query("SELECT * FROM contas ORDER BY criadaEm DESC")
    fun listar(): List<ContaEntity>

    @Query("SELECT * FROM contas WHERE spaceId = :spaceId ORDER BY criadaEm DESC")
    fun listarPorEspaco(spaceId: String): List<ContaEntity>

    @Query("SELECT * FROM contas WHERE id = :id LIMIT 1")
    fun buscar(id: String): ContaEntity?

    @Query("SELECT COUNT(*) FROM contas")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(conta: ContaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(contas: List<ContaEntity>)

    @Query("DELETE FROM contas WHERE id = :id")
    fun remover(id: String)

    @Query("DELETE FROM contas")
    fun limpar()

    @Query("UPDATE contas SET comprovanteCloudPath = :receiptCloudPath, boletoPdfCloudPath = :boletoCloudPath WHERE id = :id")
    fun atualizarCaminhosNuvem(id: String, receiptCloudPath: String?, boletoCloudPath: String?)

    @Transaction
    fun substituirTodas(contas: List<ContaEntity>) {
        limpar()
        upsertAll(contas)
    }
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs WHERE spaceId = :spaceId ORDER BY createdAt DESC LIMIT :limit")
    fun listarPorEspaco(spaceId: String, limit: Int = 80): List<ActivityLogEntity>

    @Query("SELECT * FROM activity_logs WHERE contaId = :contaId ORDER BY createdAt DESC LIMIT :limit")
    fun listarPorConta(contaId: String, limit: Int = 40): List<ActivityLogEntity>

    @Query("SELECT * FROM activity_logs WHERE syncedAt IS NULL ORDER BY createdAt ASC LIMIT :limit")
    fun listarPendentesSync(limit: Int = 100): List<ActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(activity: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnoreAll(activities: List<ActivityLogEntity>)

    @Query("UPDATE activity_logs SET syncedAt = :syncedAt WHERE id IN (:ids)")
    fun marcarSincronizadas(ids: List<String>, syncedAt: Long)

    @Query("DELETE FROM activity_logs WHERE createdAt < :before")
    fun removerAntigas(before: Long)
}

@Database(
    entities = [ContaEntity::class, ActivityLogEntity::class],
    version = 14,
    exportSchema = false
)
abstract class HsCashDatabase : RoomDatabase() {
    abstract fun contaDao(): ContaDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        internal const val DATABASE_NAME = "hs_cash.db"

        @Volatile private var instance: HsCashDatabase? = null

        fun get(context: Context): HsCashDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HsCashDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN direcao TEXT NOT NULL DEFAULT 'PAGAR'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN clienteWhatsapp TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN excluidaEm INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN boletoPdfPath TEXT")
                db.execSQL("UPDATE contas SET boletoPdfPath = comprovantePath, comprovantePath = NULL WHERE categoria = 'Boletos importados' AND comprovantePath IS NOT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN formaPagamento TEXT")
                db.execSQL("ALTER TABLE contas ADD COLUMN observacaoPagamento TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN spaceId TEXT NOT NULL DEFAULT '${SpaceStorage.DEFAULT_SPACE_ID}'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_contas_spaceId ON contas(spaceId)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN pixCopiaCola TEXT")
                db.execSQL("ALTER TABLE contas ADD COLUMN pixChave TEXT")
                db.execSQL("ALTER TABLE contas ADD COLUMN pixRecebedor TEXT")
                db.execSQL("ALTER TABLE contas ADD COLUMN pixTxId TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN pixAgendado INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE contas ADD COLUMN pixAgendamentoStatus TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN atualizadaEm INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN subcategoria TEXT")
            }
        }
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN responsavel TEXT")
                db.execSQL("ALTER TABLE contas ADD COLUMN pagoPor TEXT")
            }
        }
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN comprovanteCloudPath TEXT")
                db.execSQL("ALTER TABLE contas ADD COLUMN boletoPdfCloudPath TEXT")
            }
        }
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_logs (
                        id TEXT NOT NULL PRIMARY KEY,
                        spaceId TEXT NOT NULL,
                        contaId TEXT,
                        action TEXT NOT NULL,
                        title TEXT NOT NULL,
                        details TEXT,
                        actorName TEXT,
                        createdAt INTEGER NOT NULL,
                        syncedAt INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_logs_spaceId ON activity_logs(spaceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_logs_contaId ON activity_logs(contaId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_logs_createdAt ON activity_logs(createdAt)")
            }
        }
    }
}

fun ContaEntity.toContaSalva(): ContaSalva {
    return ContaSalva(
        id = id,
        spaceId = spaceId,
        codigo = codigo,
        banco = banco,
        valor = valor,
        vencimento = vencimento,
        tipo = tipo,
        criadaEm = criadaEm,
        fornecedor = fornecedor,
        categoria = categoria,
        subcategoria = subcategoria,
        responsavel = responsavel,
        status = status,
        pagoEm = pagoEm,
        formaPagamento = formaPagamento,
        observacaoPagamento = observacaoPagamento,
        pagoPor = pagoPor,
        recorrente = recorrente,
        recorrencia = recorrencia,
        comprovantePath = comprovantePath,
        boletoPdfPath = boletoPdfPath,
        comprovanteCloudPath = comprovanteCloudPath,
        boletoPdfCloudPath = boletoPdfCloudPath,
        direcao = direcao,
        clienteWhatsapp = clienteWhatsapp,
        pixCopiaCola = pixCopiaCola,
        pixChave = pixChave,
        pixRecebedor = pixRecebedor,
        pixTxId = pixTxId,
        pixAgendado = pixAgendado,
        pixAgendamentoStatus = pixAgendamentoStatus,
        excluidaEm = excluidaEm,
        atualizadaEm = atualizadaEm
    )
}

fun ContaSalva.toEntity(): ContaEntity {
    return ContaEntity(
        id = id,
        spaceId = spaceId,
        codigo = codigo,
        banco = banco,
        valor = valor,
        vencimento = vencimento,
        tipo = tipo,
        criadaEm = criadaEm,
        fornecedor = fornecedor,
        categoria = categoria,
        subcategoria = subcategoria,
        responsavel = responsavel,
        status = status,
        pagoEm = pagoEm,
        formaPagamento = formaPagamento,
        observacaoPagamento = observacaoPagamento,
        pagoPor = pagoPor,
        recorrente = recorrente,
        recorrencia = recorrencia,
        comprovantePath = comprovantePath,
        boletoPdfPath = boletoPdfPath,
        comprovanteCloudPath = comprovanteCloudPath,
        boletoPdfCloudPath = boletoPdfCloudPath,
        direcao = direcao,
        clienteWhatsapp = clienteWhatsapp,
        pixCopiaCola = pixCopiaCola,
        pixChave = pixChave,
        pixRecebedor = pixRecebedor,
        pixTxId = pixTxId,
        pixAgendado = pixAgendado,
        pixAgendamentoStatus = pixAgendamentoStatus,
        excluidaEm = excluidaEm,
        atualizadaEm = atualizadaEm
    )
}
