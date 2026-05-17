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
    val recorrente: Boolean,
    val recorrencia: String,
    val comprovantePath: String?,
    val direcao: String
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
}

@Database(
    entities = [ContaEntity::class],
    version = 2,
    exportSchema = false
)
abstract class HsCashDatabase : RoomDatabase() {
    abstract fun contaDao(): ContaDao

    companion object {
        @Volatile private var instance: HsCashDatabase? = null

        fun get(context: Context): HsCashDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HsCashDatabase::class.java,
                    "hs_cash.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contas ADD COLUMN direcao TEXT NOT NULL DEFAULT 'PAGAR'")
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
        recorrente = recorrente,
        recorrencia = recorrencia,
        comprovantePath = comprovantePath,
        direcao = direcao
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
        recorrente = recorrente,
        recorrencia = recorrencia,
        comprovantePath = comprovantePath,
        direcao = direcao
    )
}
