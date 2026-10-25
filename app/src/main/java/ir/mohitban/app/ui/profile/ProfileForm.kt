package ir.mohitban.app.ui.profile

import ir.mohitban.app.core.text.Validators
import ir.mohitban.app.core.text.digitsOnly
import ir.mohitban.app.domain.model.Plate
import ir.mohitban.app.domain.model.PlateRules
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.Vehicle
import ir.mohitban.app.domain.model.VehicleType

/** Editable copy of the ranger's personal and duty-station information. */
data class ProfileForm(
    val fullName: String = "",
    val fatherName: String = "",
    val nationalId: String = "",
    val mobile: String = "",
    val rank: String = "",
    val photoPath: String? = null,
    val province: String = "آذربایجان شرقی",
    val city: String = "",
    val protectedArea: String = "",
    val station: String = "",
) {
    fun personalErrors(): Map<String, String> = buildMap {
        Validators.personName(fullName, "نام و نام خانوادگی")?.let { put("fullName", it) }
        Validators.personName(fatherName, "نام پدر")?.let { put("fatherName", it) }
        Validators.nationalId(nationalId)?.let { put("nationalId", it) }
        Validators.mobile(mobile)?.let { put("mobile", it) }
        Validators.required(rank, "درجه / سمت")?.let { put("rank", it) }
    }

    fun locationErrors(): Map<String, String> = buildMap {
        Validators.required(province, "استان")?.let { put("province", it) }
        Validators.required(city, "شهرستان")?.let { put("city", it) }
        Validators.required(protectedArea, "منطقه")?.let { put("protectedArea", it) }
        Validators.required(station, "پاسگاه")?.let { put("station", it) }
    }

    fun toDomain() = RangerProfile(
        fullName = fullName.trim(), fatherName = fatherName.trim(), nationalId = nationalId.digitsOnly(),
        mobile = mobile.digitsOnly(), rank = rank.trim(), photoPath = photoPath, province = province.trim(),
        city = city.trim(), protectedArea = protectedArea.trim(), station = station.trim(),
    )

    companion object {
        fun from(p: RangerProfile) = ProfileForm(
            p.fullName, p.fatherName, p.nationalId, p.mobile, p.rank, p.photoPath,
            p.province, p.city, p.protectedArea, p.station,
        )
    }
}

data class VehicleForm(
    val id: Long = 0,
    val type: VehicleType = VehicleType.CAR,
    val kind: String = "",
    val model: String = "",
    val year: String = "",
    val carPlate: Plate.Car = Plate.Car("", "الف", "", ""),
    val motoPlate: Plate.Motorcycle = Plate.Motorcycle("", ""),
    val odometer: String = "",
    val photoPath: String? = null,
) {
    val plate: Plate get() = if (type == VehicleType.CAR) carPlate else motoPlate

    fun plateError(): String? = PlateRules.validate(plate)

    /** True once every part of the plate has been typed (to show the green "valid" line). */
    val plateComplete: Boolean
        get() = when (type) {
            VehicleType.CAR -> carPlate.first.length == 2 && carPlate.middle.length == 3 && carPlate.iranCode.length == 2
            VehicleType.MOTORCYCLE -> motoPlate.top.length >= PlateRules.MOTO_TOP_MIN && motoPlate.bottom.length >= PlateRules.MOTO_BOTTOM_MIN
        }

    fun errors(): Map<String, String> = buildMap {
        plateError()?.let { put("plate", it) }
        val noun = if (type == VehicleType.CAR) "خودرو" else "موتورسیکلت"
        Validators.required(kind, "نوع $noun")?.let { put("kind", it) }
        Validators.required(model, "مدل $noun")?.let { put("model", it) }
        if (year.isNotBlank() && year.digitsOnly().length != 4) put("year", "سال ساخت را چهار رقمی وارد کنید")
        if (odometer.digitsOnly().isEmpty()) put("odometer", "کیلومتر فعلی را وارد کنید")
    }

    /** Switching car ↔ motorcycle clears the fields that belong to the other type. */
    fun withType(t: VehicleType): VehicleForm =
        if (t == type) this else copy(type = t, kind = "", model = "")

    fun toDomain() = Vehicle(
        id = id,
        type = type, kind = kind.trim(), model = model.trim(), year = year.digitsOnly(), plate = plate,
        odometer = odometer.digitsOnly().toLongOrNull() ?: 0L, photoPath = photoPath,
    )

    companion object {
        fun from(v: Vehicle) = VehicleForm(
            id = v.id,
            type = v.type,
            kind = v.kind,
            model = v.model,
            year = v.year,
            carPlate = (v.plate as? Plate.Car) ?: Plate.Car("", "الف", "", ""),
            motoPlate = (v.plate as? Plate.Motorcycle) ?: Plate.Motorcycle("", ""),
            odometer = v.odometer.toString(),
            photoPath = v.photoPath,
        )
    }
}

object Suggestions {
    val ranks = listOf("محیط‌بان", "محیط‌بان ارشد", "سرپرست محیط‌بانان", "رئیس پاسگاه", "کارشناس حفاظت")

    val provinces = listOf(
        "آذربایجان شرقی", "آذربایجان غربی", "اردبیل", "اصفهان", "البرز", "ایلام", "بوشهر", "تهران",
        "چهارمحال و بختیاری", "خراسان جنوبی", "خراسان رضوی", "خراسان شمالی", "خوزستان", "زنجان",
        "سمنان", "سیستان و بلوچستان", "فارس", "قزوین", "قم", "کردستان", "کرمان", "کرمانشاه",
        "کهگیلویه و بویراحمد", "گلستان", "گیلان", "لرستان", "مازندران", "مرکزی", "هرمزگان", "همدان", "یزد",
    )

    val carKinds = listOf("وانت دو کابین", "وانت تک کابین", "شاسی‌بلند", "سواری")
    val motoKinds = listOf("موتورسیکلت تریل", "موتورسیکلت معمولی")
    val carModels = listOf("تویوتا هایلوکس", "نیسان پیکاپ", "پاترول", "کاپرا", "ایسوزو دی‌مکس", "پژو پارس")
    val motoModels = listOf("هوندا ۱۲۵", "یاماها", "تریل ۲۰۰", "باجاج پالس")

    fun years(current: Int): List<String> = (current downTo current - 30).map { it.toString() }
}
