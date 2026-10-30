package ir.mohitban.app.domain.repository

import ir.mohitban.app.core.jalali.Season
import ir.mohitban.app.domain.model.FuelEntry
import ir.mohitban.app.domain.model.FuelRates
import ir.mohitban.app.domain.model.QuarterSummary
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    val profile: Flow<RangerProfile?>
    val vehicles: Flow<List<Vehicle>>
    suspend fun getProfile(): RangerProfile?
    suspend fun getVehicles(): List<Vehicle>
    suspend fun getVehicle(id: Long): Vehicle?
    suspend fun isRegistered(): Boolean
    /** First registration: profile + first vehicle (becomes the default). */
    suspend fun register(profile: RangerProfile, vehicle: Vehicle? = null)
    suspend fun saveProfile(profile: RangerProfile)
    /** Inserts (id = 0) or updates a vehicle and returns its id. */
    suspend fun saveVehicle(vehicle: Vehicle): Long
    /** Deletes a vehicle; returns false when it is the last one. */
    suspend fun deleteVehicle(id: Long): Boolean
}

data class ReminderState(val reportedSeasonKey: Int?, val lastReminderJdn: Int?)

interface SettingsRepository {
    val rates: Flow<FuelRates>
    val lastBackupAt: Flow<Long?>
    val defaultVehicleId: Flow<Long?>
    val reminder: Flow<ReminderState>
    suspend fun getRates(): FuelRates
    suspend fun saveRates(rates: FuelRates)
    suspend fun markBackup(time: Long)
    suspend fun getDefaultVehicleId(): Long?
    suspend fun setDefaultVehicleId(id: Long?)
    suspend fun getReminder(): ReminderState
    suspend fun markSeasonReported(key: Int)
    suspend fun markReminderShown(jdn: Int)
    val shift: Flow<ir.mohitban.app.domain.model.ShiftPattern?>
    suspend fun saveShift(pattern: ir.mohitban.app.domain.model.ShiftPattern?)
}

interface FuelRepository {
    fun observeAll(): Flow<List<FuelEntry>>
    fun observeSeason(year: Int, season: Season): Flow<List<FuelEntry>>
    suspend fun get(id: Long): FuelEntry?
    suspend fun save(entry: FuelEntry): Long
    suspend fun delete(id: Long)
    suspend fun quarter(year: Int, season: Season): QuarterSummary
    suspend fun lastOdometer(vehicleId: Long): Long?
}
