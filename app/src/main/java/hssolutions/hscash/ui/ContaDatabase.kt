package hssolutions.hscash.ui

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "contas")
data class ContaEntity(
    @PrimaryKey val id: String,
    val codigo: String,
    val banco: String,
    val valor: String,
    val vencimento: String,
    val tipo: String,
    val criadaEm: Long,
    val fornecedor: String,
    val categoria: String,
    val status: String,
    val pagoEm: Long?,
    val formaPagamento: String?,
    val observacaoPagamento: String?,
    val recorrente: Boolean,
    val recorrencia: String,
    val comprovantePath: String?,
    val boletoPdfPath: String?,
    val direcao: String,
    val clienteWhatsapp: String?,
    val excluidaEm: Long?
)

@Dao
interface ContaDao {
    @Query("SELECT * FROM contas ORDER BY criadaEm DESC")
    fun listar(): List<ContaEntity>

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

    @Transaction
    fun substituirTodas(contas: List<ContaEntity>) {
        limpar()
        upsertAll(contas)
    }
}

@Database(
    entities = [ContaEntity::class],
    version = 6,
    exportSchema = false
)
abstract class HsCashDatabase : RoomDatabase() {
    abstract fun contaDao(): ContaDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
    }
}

fun ContaEntity.toContaSalva(): ContaSalva {
    return ContaSalva(
        id = id,
        codigo = codigo,
        banco = banco,
        valor = valor,
        vencimento = vencimento,
        tipo = tipo,
        criadaEm = criadaEm,
        fornecedor = fornecedor,
        categoria = categoria,
        status = status,
        pagoEm = pagoEm,
        formaPagamento = formaPagamento,
        observacaoPagamento = observacaoPagamento,
        recorrente = recorrente,
        recorrencia = recorrencia,
        comprovantePath = comprovantePath,
        boletoPdfPath = boletoPdfPath,
        direcao = direcao,
        clienteWhatsapp = clienteWhatsapp,
        excluidaEm = excluidaEm
    )
}

fun ContaSalva.toEntity(): ContaEntity {
    return ContaEntity(
        id = id,
        codigo = codigo,
        banco = banco,
        valor = valor,
        vencimento = vencimento,
        tipo = tipo,
        criadaEm = criadaEm,
        fornecedor = fornecedor,
        categoria = categoria,
        status = status,
        pagoEm = pagoEm,
        formaPagamento = formaPagamento,
        observacaoPagamento = observacaoPagamento,
        recorrente = recorrente,
        recorrencia = recorrencia,
        comprovantePath = comprovantePath,
        boletoPdfPath = boletoPdfPath,
        direcao = direcao,
        clienteWhatsapp = clienteWhatsapp,
        excluidaEm = excluidaEm
    )
}
