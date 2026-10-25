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
    val vehicle: Flow<Vehicle?>
    suspend fun getProfile(): RangerProfile?
    suspend fun getVehicle(): Vehicle?
    suspend fun isRegistered(): Boolean
    suspend fun save(profile: RangerProfile, vehicle: Vehicle)
    suspend fun saveProfile(profile: RangerProfile)
    suspend fun saveVehicle(vehicle: Vehicle)
}

interface SettingsRepository {
    val rates: Flow<FuelRates>
    val lastBackupAt: Flow<Long?>
    suspend fun getRates(): FuelRates
    suspend fun saveRates(rates: FuelRates)
    suspend fun markBackup(time: Long)
}

interface FuelRepository {
    fun observeAll(): Flow<List<FuelEntry>>
    fun observeSeason(year: Int, season: Season): Flow<List<FuelEntry>>
    suspend fun get(id: Long): FuelEntry?
    suspend fun save(entry: FuelEntry): Long
    suspend fun delete(id: Long)
    suspend fun quarter(year: Int, season: Season): QuarterSummary
    suspend fun lastOdometer(): Long?
}
