package ir.mohitban.app.data.local

import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.domain.model.EntrySnapshot
import ir.mohitban.app.domain.model.FuelEntry
import ir.mohitban.app.domain.model.FuelRates
import ir.mohitban.app.domain.model.FuelType
import ir.mohitban.app.domain.model.Plate
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.Vehicle
import ir.mohitban.app.domain.model.VehicleType

fun ProfileEntity.toDomain() = RangerProfile(
    fullName, fatherName, nationalId, mobile, rank, photoPath, province, city, protectedArea, station,
)

fun RangerProfile.toEntity() = ProfileEntity(
    fullName = fullName, fatherName = fatherName, nationalId = nationalId, mobile = mobile, rank = rank,
    photoPath = photoPath, province = province, city = city, protectedArea = protectedArea, station = station,
)

private val FALLBACK_PLATE = Plate.Car("", "الف", "", "")

fun VehicleEntity.toDomain() = Vehicle(
    type = runCatching { VehicleType.valueOf(type) }.getOrDefault(VehicleType.CAR),
    kind = kind,
    model = model,
    year = year,
    plate = Plate.decode(plate) ?: FALLBACK_PLATE,
    odometer = odometer,
    photoPath = photoPath,
)

fun Vehicle.toEntity() = VehicleEntity(
    type = type.name, kind = kind, model = model, year = year, plate = plate.encode(),
    odometer = odometer, photoPath = photoPath,
)

fun SettingsEntity.toRates() = FuelRates(listOf(gasolineRate1, gasolineRate2, gasolineRate3), cngRate)

fun FuelEntryEntity.toDomain() = FuelEntry(
    id = id,
    date = JalaliDate.fromJdn(dateJdn),
    odometer = odometer,
    fuelType = runCatching { FuelType.valueOf(fuelType) }.getOrDefault(FuelType.GASOLINE),
    amount = amount,
    unitPrice = unitPrice,
    total = total,
    receiptPath = receiptPath,
    driverSignaturePath = driverSignaturePath,
    passengerSignaturePath = passengerSignaturePath,
    snapshot = EntrySnapshot(
        driverName = snapDriver,
        plate = Plate.decode(snapPlate) ?: FALLBACK_PLATE,
        vehicleType = runCatching { VehicleType.valueOf(snapVehicleType) }.getOrDefault(VehicleType.CAR),
        vehicleModel = snapVehicleModel,
        province = snapProvince,
        city = snapCity,
        protectedArea = snapArea,
        station = snapStation,
    ),
    createdAt = createdAt,
)

fun FuelEntry.toEntity() = FuelEntryEntity(
    id = id,
    dateJdn = date.jdn,
    odometer = odometer,
    fuelType = fuelType.name,
    amount = amount,
    unitPrice = unitPrice,
    total = total,
    receiptPath = receiptPath,
    driverSignaturePath = driverSignaturePath,
    passengerSignaturePath = passengerSignaturePath,
    snapDriver = snapshot.driverName,
    snapPlate = snapshot.plate.encode(),
    snapVehicleType = snapshot.vehicleType.name,
    snapVehicleModel = snapshot.vehicleModel,
    snapProvince = snapshot.province,
    snapCity = snapshot.city,
    snapArea = snapshot.protectedArea,
    snapStation = snapshot.station,
    createdAt = createdAt,
)
