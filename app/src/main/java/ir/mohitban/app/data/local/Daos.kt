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
    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY id ASC")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun get(id: Long): VehicleEntity?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VehicleEntity): Long

    @Update
    suspend fun update(entity: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE vehicles SET odometer = :odometer WHERE id = :id AND odometer < :odometer")
    suspend fun raiseOdometer(id: Long, odometer: Long)

    @Query("DELETE FROM vehicles")
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

    @Query("SELECT MAX(odometer) FROM fuel_entry WHERE vehicleId = :vehicleId")
    suspend fun maxOdometer(vehicleId: Long): Long?

    @Query("SELECT COUNT(*) FROM fuel_entry WHERE vehicleId = :vehicleId")
    suspend fun countForVehicle(vehicleId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FuelEntryEntity): Long

    @Update
    suspend fun update(entity: FuelEntryEntity)

    @Query("DELETE FROM fuel_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM fuel_entry")
    suspend fun clear()
}

@Dao
interface GashtDao {
    @Query("SELECT * FROM tracks ORDER BY createdAt DESC")
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY createdAt DESC")
    suspend fun getTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrack(id: Long): TrackEntity?

    @Insert
    suspend fun insertTrack(t: TrackEntity): Long

    @Update
    suspend fun updateTrack(t: TrackEntity)

    @Query("UPDATE tracks SET name = :name WHERE id = :id")
    suspend fun renameTrack(id: Long, name: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY seq ASC")
    suspend fun points(trackId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY seq ASC")
    fun observePoints(trackId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT COALESCE(MAX(seq), -1) FROM track_points WHERE trackId = :trackId")
    suspend fun lastSeq(trackId: Long): Int

    @Insert
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deletePoints(trackId: Long)

    @Query("SELECT * FROM waypoints ORDER BY createdAt DESC")
    fun observeWaypoints(): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints ORDER BY createdAt DESC")
    suspend fun getWaypoints(): List<WaypointEntity>

    @Insert
    suspend fun insertWaypoint(w: WaypointEntity): Long

    @Insert
    suspend fun insertWaypoints(w: List<WaypointEntity>)

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getWaypoint(id: Long): WaypointEntity?

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteWaypoint(id: Long)

    @Query("DELETE FROM tracks")
    suspend fun clearTracks()

    @Query("DELETE FROM track_points")
    suspend fun clearPoints()

    @Query("DELETE FROM waypoints")
    suspend fun clearWaypoints()
}
