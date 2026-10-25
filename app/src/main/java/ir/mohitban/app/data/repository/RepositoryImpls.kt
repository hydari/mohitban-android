package ir.mohitban.app.data.repository

import androidx.room.withTransaction
import ir.mohitban.app.core.jalali.Season
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.data.local.AppDatabase
import ir.mohitban.app.data.local.SettingsEntity
import ir.mohitban.app.data.local.toDomain
import ir.mohitban.app.data.local.toEntity
import ir.mohitban.app.data.local.toRates
import ir.mohitban.app.domain.model.FuelEntry
import ir.mohitban.app.domain.model.FuelRates
import ir.mohitban.app.domain.model.QuarterSummary
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.Vehicle
import ir.mohitban.app.domain.repository.FuelRepository
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepositoryImpl(private val db: AppDatabase) : ProfileRepository {
    override val profile: Flow<RangerProfile?> = db.profileDao().observe().map { it?.toDomain() }
    override val vehicle: Flow<Vehicle?> = db.vehicleDao().observe().map { it?.toDomain() }

    override suspend fun getProfile() = db.profileDao().get()?.toDomain()
    override suspend fun getVehicle() = db.vehicleDao().get()?.toDomain()
    override suspend fun isRegistered() = db.profileDao().get() != null && db.vehicleDao().get() != null

    override suspend fun save(profile: RangerProfile, vehicle: Vehicle) {
        db.withTransaction {
            db.profileDao().upsert(profile.toEntity())
            db.vehicleDao().upsert(vehicle.toEntity())
        }
    }

    override suspend fun saveProfile(profile: RangerProfile) = db.profileDao().upsert(profile.toEntity())
    override suspend fun saveVehicle(vehicle: Vehicle) = db.vehicleDao().upsert(vehicle.toEntity())
}

class SettingsRepositoryImpl(private val db: AppDatabase) : SettingsRepository {
    override val rates: Flow<FuelRates> = db.settingsDao().observe().map { (it ?: SettingsEntity()).toRates() }
    override val lastBackupAt: Flow<Long?> = db.settingsDao().observe().map { it?.lastBackupAt }

    override suspend fun getRates(): FuelRates = (db.settingsDao().get() ?: SettingsEntity()).toRates()

    override suspend fun saveRates(rates: FuelRates) {
        val current = db.settingsDao().get() ?: SettingsEntity()
        db.settingsDao().upsert(
            current.copy(
                gasolineRate1 = rates.gasoline.getOrElse(0) { 10_000 },
                gasolineRate2 = rates.gasoline.getOrElse(1) { 15_000 },
                gasolineRate3 = rates.gasoline.getOrElse(2) { 30_000 },
                cngRate = rates.cng,
            ),
        )
    }

    override suspend fun markBackup(time: Long) {
        val current = db.settingsDao().get() ?: SettingsEntity()
        db.settingsDao().upsert(current.copy(lastBackupAt = time))
    }
}

class FuelRepositoryImpl(
    private val db: AppDatabase,
    private val files: FileStore,
) : FuelRepository {
    private val dao = db.fuelEntryDao()

    override fun observeAll(): Flow<List<FuelEntry>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSeason(year: Int, season: Season): Flow<List<FuelEntry>> {
        val r = season.jdnRange(year)
        return dao.observeRange(r.first, r.last).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun get(id: Long) = dao.get(id)?.toDomain()

    override suspend fun save(entry: FuelEntry): Long {
        val old = if (entry.id != 0L) dao.get(entry.id) else null
        val id = db.withTransaction {
            val newId = if (entry.id == 0L) dao.insert(entry.toEntity()) else {
                dao.update(entry.toEntity()); entry.id
            }
            // keep the vehicle's odometer at the highest reading recorded so far
            db.vehicleDao().raiseOdometer(entry.odometer)
            newId
        }
        // remove image files that were replaced during an edit
        if (old != null) {
            listOf(
                old.receiptPath to entry.receiptPath,
                old.driverSignaturePath to entry.driverSignaturePath,
                old.passengerSignaturePath to entry.passengerSignaturePath,
            ).forEach { (before, after) -> if (before != null && before != after) files.delete(before) }
        }
        return id
    }

    override suspend fun delete(id: Long) {
        val old = dao.get(id) ?: return
        dao.delete(id)
        listOf(old.receiptPath, old.driverSignaturePath, old.passengerSignaturePath).forEach { files.delete(it) }
    }

    override suspend fun quarter(year: Int, season: Season): QuarterSummary {
        val r = season.jdnRange(year)
        return QuarterSummary(year, season, dao.getRange(r.first, r.last).map { it.toDomain() })
    }

    override suspend fun lastOdometer(): Long? = dao.maxOdometer()
}
