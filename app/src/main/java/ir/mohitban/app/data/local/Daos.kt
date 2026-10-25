package ir.mohitban.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(entity: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun clear()
}

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicle WHERE id = 1")
    fun observe(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicle WHERE id = 1")
    suspend fun get(): VehicleEntity?

    @Upsert
    suspend fun upsert(entity: VehicleEntity)

    @Query("UPDATE vehicle SET odometer = :odometer WHERE id = 1 AND odometer < :odometer")
    suspend fun raiseOdometer(odometer: Long)

    @Query("DELETE FROM vehicle")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(entity: SettingsEntity)
}

@Dao
interface FuelEntryDao {
    @Query("SELECT * FROM fuel_entry ORDER BY dateJdn DESC, odometer DESC, id DESC")
    fun observeAll(): Flow<List<FuelEntryEntity>>

    @Query("SELECT * FROM fuel_entry WHERE dateJdn BETWEEN :fromJdn AND :toJdn ORDER BY dateJdn DESC, odometer DESC, id DESC")
    fun observeRange(fromJdn: Int, toJdn: Int): Flow<List<FuelEntryEntity>>

    @Query("SELECT * FROM fuel_entry WHERE dateJdn BETWEEN :fromJdn AND :toJdn ORDER BY dateJdn ASC, odometer ASC, id ASC")
    suspend fun getRange(fromJdn: Int, toJdn: Int): List<FuelEntryEntity>

    @Query("SELECT * FROM fuel_entry ORDER BY dateJdn ASC, id ASC")
    suspend fun getAll(): List<FuelEntryEntity>

    @Query("SELECT * FROM fuel_entry WHERE id = :id")
    suspend fun get(id: Long): FuelEntryEntity?

    @Query("SELECT MAX(odometer) FROM fuel_entry")
    suspend fun maxOdometer(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FuelEntryEntity): Long

    @Update
    suspend fun update(entity: FuelEntryEntity)

    @Query("DELETE FROM fuel_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM fuel_entry")
    suspend fun clear()
}
