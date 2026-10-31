package ir.mohitban.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Single-row table (id = 1): the ranger who uses this phone. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val fullName: String,
    val fatherName: String,
    val nationalId: String,
    val mobile: String,
    val rank: String,
    val photoPath: String?,
    val province: String,
    val city: String,
    val protectedArea: String,
    val station: String,
)

/** Every organisational vehicle (car or motorcycle) saved on this phone, with its red plate. */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val kind: String,
    val model: String,
    val year: String,
    val plate: String,
    val odometer: Long,
    val photoPath: String?,
    val createdAt: Long,
)

/** Single-row table (id = 1): fuel prices in Rial and app state. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val gasolineRate1: Long = 15_000,
    val gasolineRate2: Long = 30_000,
    val gasolineRate3: Long = 100_000,
    val cngRate: Long = 6_568,
    val lastBackupAt: Long? = null,
    /** Vehicle selected by default in the fuel form. */
    val defaultVehicleId: Long? = null,
    /** year * 10 + season index of the last quarterly report that was sent / saved / printed. */
    val reportedSeasonKey: Int? = null,
    /** JDN of the day the last reminder notification was shown (one per day). */
    val lastReminderJdn: Int? = null,
    /** Shift pattern (null = not set up yet). */
    val shiftWork: Int? = null,
    val shiftRest: Int? = null,
    val shiftStartJdn: Int? = null,
    /** Manual changes: "jdn:1,jdn:0". */
    val shiftOverrides: String? = null,
)

@Entity(tableName = "fuel_entry", indices = [Index("dateJdn")])
data class FuelEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateJdn: Int,
    val odometer: Long,
    val fuelType: String,
    val amount: Double,
    val unitPrice: Long,
    val total: Long,
    val receiptPath: String?,
    val driverSignaturePath: String,
    val passengerSignaturePath: String?,
    @ColumnInfo(name = "snap_driver") val snapDriver: String,
    @ColumnInfo(name = "snap_plate") val snapPlate: String,
    @ColumnInfo(name = "snap_vehicle_type") val snapVehicleType: String,
    @ColumnInfo(name = "snap_vehicle_model") val snapVehicleModel: String,
    @ColumnInfo(name = "snap_province") val snapProvince: String,
    @ColumnInfo(name = "snap_city") val snapCity: String,
    @ColumnInfo(name = "snap_area") val snapArea: String,
    @ColumnInfo(name = "snap_station") val snapStation: String,
    val createdAt: Long,
    val vehicleId: Long? = null,
    val editedAt: Long? = null,
)

/** A patrol track: recorded by the app (source REC) or imported from a GPS file (source IMPORT). */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val source: String,
    val createdAt: Long,
    val distance: Double = 0.0,
    val duration: Long = 0,
    val pointCount: Int = 0,
)

@Entity(tableName = "track_points", indices = [Index("trackId")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val seq: Int,
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val time: Long? = null,
)

/** A marked place (spring, ambush spot, den, find…), saved in the field or imported. */
@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val note: String = "",
    val photoPath: String? = null,
    val createdAt: Long,
    val source: String = "MANUAL",
)

/**
 * One patrol in the ranger's logbook (دفتر گشت): when, where (protected area + spot), how,
 * what was seen and done. [findings] = comma-separated finding keys.
 */
@Entity(tableName = "patrol_logs")
data class PatrolLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jdn: Int,
    val startMin: Int? = null,
    val endMin: Int? = null,
    val zone: String,
    val spot: String = "",
    val mode: String,
    val distanceKm: Double? = null,
    val findings: String = "",
    val violations: Int = 0,
    val wildlife: String = "",
    val companions: String = "",
    val notes: String = "",
    val trackId: Long? = null,
    val createdAt: Long,
)
