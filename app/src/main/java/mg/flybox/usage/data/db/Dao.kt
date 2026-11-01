package mg.flybox.usage.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: SampleEntity)

    @Query("SELECT * FROM samples WHERE ts >= :since ORDER BY ts")
    suspend fun since(since: Long): List<SampleEntity>

    @Query("SELECT * FROM samples WHERE ts >= :since ORDER BY ts")
    fun observeSince(since: Long): Flow<List<SampleEntity>>

    /** Dernier relevé strictement avant [ts], utile pour le delta qui franchit minuit. */
    @Query("SELECT * FROM samples WHERE ts < :ts ORDER BY ts DESC LIMIT 1")
    suspend fun lastBefore(ts: Long): SampleEntity?

    @Query("SELECT * FROM samples ORDER BY ts DESC LIMIT 1")
    suspend fun last(): SampleEntity?

    @Query("SELECT * FROM samples ORDER BY ts DESC LIMIT 1")
    fun observeLast(): Flow<SampleEntity?>

    @Query("SELECT MIN(ts) FROM samples")
    suspend fun firstTs(): Long?

    @Query("DELETE FROM samples WHERE ts < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface OrangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(o: OrangeEntity)

    @Query("SELECT * FROM orange WHERE ts >= :since ORDER BY ts")
    suspend fun since(since: Long): List<OrangeEntity>

    @Query("SELECT * FROM orange WHERE ts >= :since ORDER BY ts")
    fun observeSince(since: Long): Flow<List<OrangeEntity>>

    @Query("SELECT * FROM orange ORDER BY ts DESC LIMIT 1")
    suspend fun last(): OrangeEntity?

    @Query("SELECT * FROM orange ORDER BY ts DESC LIMIT 1")
    fun observeLast(): Flow<OrangeEntity?>

    @Query("DELETE FROM orange WHERE ts < :before")
    suspend fun deleteBefore(before: Long)
}

@Database(entities = [SampleEntity::class, OrangeEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun samples(): SampleDao
    abstract fun orange(): OrangeDao
}
