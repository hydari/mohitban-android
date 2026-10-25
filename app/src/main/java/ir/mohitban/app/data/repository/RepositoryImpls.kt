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
import ir.mohitban.app.domain.repository.ReminderState
import ir.mohitban.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepositoryImpl(private val db: AppDatabase, private val files: FileStore) : ProfileRepository {
    override val profile: Flow<RangerProfile?> = db.profileDao().observe().map { it?.toDomain() }
    override val vehicles: Flow<List<Vehicle>> = db.vehicleDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfile() = db.profileDao().get()?.toDomain()
    override suspend fun getVehicles() = db.vehicleDao().getAll().map { it.toDomain() }
    override suspend fun getVehicle(id: Long) = db.vehicleDao().get(id)?.toDomain()
    override suspend fun isRegistered() = db.profileDao().get() != null && db.vehicleDao().count() > 0

    override suspend fun register(profile: RangerProfile, vehicle: Vehicle) {
        db.withTransaction {
            db.profileDao().upsert(profile.toEntity())
            val id = db.vehicleDao().insert(vehicle.copy(id = 0).toEntity())
            val s = db.settingsDao().get() ?: SettingsEntity()
            db.settingsDao().upsert(s.copy(defaultVehicleId = id))
        }
    }

    override suspend fun saveProfile(profile: RangerProfile) = db.profileDao().upsert(profile.toEntity())

    override suspend fun saveVehicle(vehicle: Vehicle): Long {
        val dao = db.vehicleDao()
        return if (vehicle.id == 0L) {
            dao.insert(vehicle.toEntity())
        } else {
            val old = dao.get(vehicle.id)
            dao.update(vehicle.toEntity(old?.createdAt ?: System.currentTimeMillis()))
            if (old?.photoPath != null && old.photoPath != vehicle.photoPath) files.delete(old.photoPath)
            vehicle.id
        }
    }

    override suspend fun deleteVehicle(id: Long): Boolean {
        val dao = db.vehicleDao()
        if (dao.count() <= 1) return false
        val old = dao.get(id) ?: return true
        dao.delete(id)
        files.delete(old.photoPath)
        val s = db.settingsDao().get()
        if (s?.defaultVehicleId == id) {
            db.settingsDao().upsert(s.copy(defaultVehicleId = dao.getAll().firstOrNull()?.id))
        }
        return true
    }
}

class SettingsRepositoryImpl(private val db: AppDatabase) : SettingsRepository {
    private val dao = db.settingsDao()
    private suspend fun current() = dao.get() ?: SettingsEntity()

    override val rates: Flow<FuelRates> = dao.observe().map { (it ?: SettingsEntity()).toRates() }
    override val lastBackupAt: Flow<Long?> = dao.observe().map { it?.lastBackupAt }
    override val defaultVehicleId: Flow<Long?> = dao.observe().map { it?.defaultVehicleId }
    override val reminder: Flow<ReminderState> = dao.observe().map { ReminderState(it?.reportedSeasonKey, it?.lastReminderJdn) }

    override suspend fun getRates(): FuelRates = current().toRates()

    override suspend fun saveRates(rates: FuelRates) {
        dao.upsert(
            current().copy(
                gasolineRate1 = rates.gasoline.getOrElse(0) { 15_000 },
                gasolineRate2 = rates.gasoline.getOrElse(1) { 30_000 },
                gasolineRate3 = rates.gasoline.getOrElse(2) { 100_000 },
                cngRate = rates.cng,
            ),
        )
    }

    override suspend fun markBackup(time: Long) = dao.upsert(current().copy(lastBackupAt = time))
    override suspend fun getDefaultVehicleId(): Long? = dao.get()?.defaultVehicleId
    override suspend fun setDefaultVehicleId(id: Long?) = dao.upsert(current().copy(defaultVehicleId = id))
    override suspend fun getReminder(): ReminderState = current().let { ReminderState(it.reportedSeasonKey, it.lastReminderJdn) }
    override suspend fun markSeasonReported(key: Int) = dao.upsert(current().copy(reportedSeasonKey = key))
    override suspend fun markReminderShown(jdn: Int) = dao.upsert(current().copy(lastReminderJdn = jdn))

    override val shift: Flow<ir.mohitban.app.domain.model.ShiftPattern?> = dao.observe().map { s ->
        val w = s?.shiftWork; val r = s?.shiftRest; val start = s?.shiftStartJdn
        if (s == null || w == null || r == null || start == null) null
        else ir.mohitban.app.domain.model.ShiftPattern(w, r, start, ir.mohitban.app.domain.model.ShiftPattern.decodeOverrides(s.shiftOverrides))
    }

    override suspend fun saveShift(pattern: ir.mohitban.app.domain.model.ShiftPattern?) {
        // keep manual changes of roughly the last year and everything ahead
        val today = ir.mohitban.app.core.jalali.JalaliDate.today().jdn
        val kept = pattern?.copy(overrides = pattern.overrides.filterKeys { it >= today - 400 })
        dao.upsert(
            current().copy(
                shiftWork = kept?.work, shiftRest = kept?.rest, shiftStartJdn = kept?.startJdn,
                shiftOverrides = kept?.encodeOverrides(),
            ),
        )
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
                dao.update(entry.copy(editedAt = System.currentTimeMillis()).toEntity()); entry.id
            }
            // keep the vehicle's odometer at the highest reading recorded so far
            entry.vehicleId?.let { db.vehicleDao().raiseOdometer(it, entry.odometer) }
            newId
        }
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

    override suspend fun lastOdometer(vehicleId: Long): Long? = dao.maxOdometer(vehicleId)
}
