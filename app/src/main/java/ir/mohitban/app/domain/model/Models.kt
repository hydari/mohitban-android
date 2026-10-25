package ir.mohitban.app.domain.model

import ir.mohitban.app.core.jalali.JalaliDate

enum class VehicleType(val title: String) {
    CAR("خودرو"),
    MOTORCYCLE("موتورسیکلت"),
}

enum class FuelType(val title: String, val unit: String, val unitShort: String) {
    GASOLINE("بنزین", "لیتر", "لیتر"),
    CNG("گاز CNG", "کیلوگرم", "کیلو"),
}

data class RangerProfile(
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

data class Vehicle(
    val id: Long = 0,
    val type: VehicleType,
    val kind: String,
    val model: String,
    val year: String,
    val plate: Plate,
    val odometer: Long,
    val photoPath: String?,
) {
    /** "تویوتا هایلوکس" or, when the model is empty, the vehicle type. */
    val title: String get() = model.ifBlank { type.title }
}

/** Fuel prices in Rial. Editable in settings so a price change never needs a new APK. */
data class FuelRates(
    val gasoline: List<Long> = listOf(15_000L, 30_000L, 100_000L),
    val cng: Long = 6_568L,
)

/**
 * What the official form needs at the moment of the entry. Stored with each entry so that
 * later edits of the profile/vehicle do not change forms that were already printed.
 * The vehicle is shared between rangers, so the driver is whoever recorded the entry.
 */
data class EntrySnapshot(
    val driverName: String,
    val plate: Plate,
    val vehicleType: VehicleType,
    val vehicleModel: String,
    val province: String,
    val city: String,
    val protectedArea: String,
    val station: String,
)

data class FuelEntry(
    val id: Long = 0,
    val date: JalaliDate,
    val odometer: Long,
    val fuelType: FuelType,
    val amount: Double,
    val unitPrice: Long,
    val total: Long,
    val receiptPath: String?,
    val driverSignaturePath: String,
    val passengerSignaturePath: String?,
    val snapshot: EntrySnapshot,
    val createdAt: Long = System.currentTimeMillis(),
    val vehicleId: Long? = null,
)

data class QuarterSummary(
    val year: Int,
    val season: ir.mohitban.app.core.jalali.Season,
    val entries: List<FuelEntry>,
) {
    val count: Int get() = entries.size
    val gasolineLiters: Double get() = entries.filter { it.fuelType == FuelType.GASOLINE }.sumOf { it.amount }
    val cngKg: Double get() = entries.filter { it.fuelType == FuelType.CNG }.sumOf { it.amount }
    val gasolineTotal: Long get() = entries.filter { it.fuelType == FuelType.GASOLINE }.sumOf { it.total }
    val cngTotal: Long get() = entries.filter { it.fuelType == FuelType.CNG }.sumOf { it.total }
    val total: Long get() = entries.sumOf { it.total }
    val withReceipts: List<FuelEntry> get() = entries.filter { it.receiptPath != null }
    val firstOdometer: Long? get() = entries.minByOrNull { it.date.jdn * 10_000_000L + it.odometer }?.odometer
    val lastOdometer: Long? get() = entries.maxByOrNull { it.date.jdn * 10_000_000L + it.odometer }?.odometer
}
