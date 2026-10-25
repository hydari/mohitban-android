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
    val plate: Plate get() = if (type == VehicleType.CAR) carPlate.copy(letter = "الف") else motoPlate

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
    val ranks = listOf("راننده", "محیط‌بان", "مسئول منطقه", "کارشناس", "رئیس اداره", "مدیرکل")

    val provinces = listOf(
        "آذربایجان شرقی", "آذربایجان غربی", "اردبیل", "اصفهان", "البرز", "ایلام", "بوشهر", "تهران",
        "چهارمحال و بختیاری", "خراسان جنوبی", "خراسان رضوی", "خراسان شمالی", "خوزستان", "زنجان",
        "سمنان", "سیستان و بلوچستان", "فارس", "قزوین", "قم", "کردستان", "کرمان", "کرمانشاه",
        "کهگیلویه و بویراحمد", "گلستان", "گیلان", "لرستان", "مازندران", "مرکزی", "هرمزگان", "همدان", "یزد",
    )

    /** Counties (شهرستان) of each province; the centre first. The field still accepts typing. */
    private val citiesByProvince: Map<String, List<String>> = mapOf(
        "آذربایجان شرقی" to listOf("تبریز", "آذرشهر", "اسکو", "اهر", "بستان‌آباد", "بناب", "جلفا", "چاراویماق", "خداآفرین", "سراب", "شبستر", "عجب‌شیر", "کلیبر", "مراغه", "مرند", "ملکان", "میانه", "ورزقان", "هریس", "هشترود", "هوراند"),
        "آذربایجان غربی" to listOf("ارومیه", "اشنویه", "بوکان", "پلدشت", "پیرانشهر", "تکاب", "چالدران", "چایپاره", "خوی", "سردشت", "سلماس", "شاهین‌دژ", "شوط", "ماکو", "مهاباد", "میاندوآب", "نقده"),
        "اردبیل" to listOf("اردبیل", "اصلاندوز", "انگوت", "بیله‌سوار", "پارس‌آباد", "خلخال", "سرعین", "کوثر", "گرمی", "مشگین‌شهر", "نمین", "نیر"),
        "اصفهان" to listOf("اصفهان", "آران و بیدگل", "اردستان", "برخوار", "بویین و میاندشت", "تیران و کرون", "چادگان", "خمینی‌شهر", "خوانسار", "خور و بیابانک", "دهاقان", "سمیرم", "شاهین‌شهر و میمه", "شهرضا", "فریدن", "فریدونشهر", "فلاورجان", "کاشان", "کوهپایه", "گلپایگان", "لنجان", "مبارکه", "نایین", "نجف‌آباد", "نطنز"),
        "البرز" to listOf("کرج", "اشتهارد", "چهارباغ", "ساوجبلاغ", "طالقان", "فردیس", "نظرآباد"),
        "ایلام" to listOf("ایلام", "آبدانان", "ایوان", "بدره", "چرداول", "چوار", "دره‌شهر", "دهلران", "سیروان", "ملکشاهی", "مهران", "هلیلان"),
        "بوشهر" to listOf("بوشهر", "تنگستان", "جم", "دشتستان", "دشتی", "دیر", "دیلم", "عسلویه", "کنگان", "گناوه"),
        "تهران" to listOf("تهران", "اسلامشهر", "بهارستان", "پاکدشت", "پردیس", "پیشوا", "دماوند", "رباط‌کریم", "ری", "شمیرانات", "شهریار", "فیروزکوه", "قدس", "قرچک", "ملارد", "ورامین"),
        "چهارمحال و بختیاری" to listOf("شهرکرد", "اردل", "بروجن", "بن", "خانمیرزا", "سامان", "فارسان", "فلارد", "کوهرنگ", "کیار", "لردگان"),
        "خراسان جنوبی" to listOf("بیرجند", "بشرویه", "خوسف", "درمیان", "زیرکوه", "سرایان", "سربیشه", "طبس", "فردوس", "قائنات", "نهبندان"),
        "خراسان رضوی" to listOf("مشهد", "باخرز", "بجستان", "بردسکن", "بینالود", "تایباد", "تربت جام", "تربت حیدریه", "جغتای", "جوین", "چناران", "خلیل‌آباد", "خواف", "خوشاب", "داورزن", "درگز", "رشتخوار", "زاوه", "زبرخان", "سبزوار", "سرخس", "صالح‌آباد", "طرقبه شاندیز", "فریمان", "فیروزه", "قوچان", "کاشمر", "کلات", "کوهسرخ", "گناباد", "مه‌ولات", "نیشابور"),
        "خراسان شمالی" to listOf("بجنورد", "اسفراین", "جاجرم", "راز و جرگلان", "سملقان", "شیروان", "فاروج", "گرمه", "مانه"),
        "خوزستان" to listOf("اهواز", "آبادان", "آغاجاری", "امیدیه", "اندیکا", "اندیمشک", "ایذه", "باغملک", "باوی", "بندر ماهشهر", "بهبهان", "حمیدیه", "خرمشهر", "دزفول", "دشت آزادگان", "رامشیر", "رامهرمز", "شادگان", "شوش", "شوشتر", "کارون", "گتوند", "لالی", "مسجدسلیمان", "هفتکل", "هندیجان", "هویزه"),
        "زنجان" to listOf("زنجان", "ابهر", "ایجرود", "خدابنده", "خرمدره", "سلطانیه", "طارم", "ماهنشان"),
        "سمنان" to listOf("سمنان", "آرادان", "دامغان", "سرخه", "شاهرود", "گرمسار", "مهدی‌شهر", "میامی"),
        "سیستان و بلوچستان" to listOf("زاهدان", "ایرانشهر", "بمپور", "تفتان", "چابهار", "خاش", "دشتیاری", "دلگان", "راسک", "زابل", "زهک", "سراوان", "سرباز", "سیب و سوران", "فنوج", "قصرقند", "کنارک", "گلشن", "لاشار", "مهرستان", "میرجاوه", "نیکشهر", "نیمروز", "هامون", "هیرمند"),
        "فارس" to listOf("شیراز", "آباده", "ارسنجان", "استهبان", "اقلید", "اوز", "بختگان", "بوانات", "بیضا", "پاسارگاد", "جهرم", "خرامه", "خرم‌بید", "خنج", "داراب", "رستم", "زرقان", "زرین‌دشت", "سپیدان", "سرچهان", "سروستان", "فراشبند", "فسا", "فیروزآباد", "قیر و کارزین", "کازرون", "کوار", "کوه‌چنار", "گراش", "لارستان", "لامرد", "مرودشت", "ممسنی", "مهر", "نی‌ریز"),
        "قزوین" to listOf("قزوین", "آبیک", "آوج", "البرز", "بوئین‌زهرا", "تاکستان"),
        "قم" to listOf("قم"),
        "کردستان" to listOf("سنندج", "بانه", "بیجار", "دهگلان", "دیواندره", "سروآباد", "سقز", "قروه", "کامیاران", "مریوان"),
        "کرمان" to listOf("کرمان", "ارزوئیه", "انار", "بافت", "بردسیر", "بم", "جیرفت", "رابر", "راور", "رفسنجان", "رودبار جنوب", "ریگان", "زرند", "سیرجان", "شهربابک", "عنبرآباد", "فاریاب", "فهرج", "قلعه گنج", "کوهبنان", "کهنوج", "منوجان", "نرماشیر"),
        "کرمانشاه" to listOf("کرمانشاه", "اسلام‌آباد غرب", "پاوه", "ثلاث باباجانی", "جوانرود", "دالاهو", "روانسر", "سرپل ذهاب", "سنقر", "صحنه", "قصر شیرین", "کنگاور", "گیلانغرب", "هرسین"),
        "کهگیلویه و بویراحمد" to listOf("بویراحمد", "باشت", "بهمئی", "چرام", "دنا", "کهگیلویه", "گچساران", "لنده", "مارگون"),
        "گلستان" to listOf("گرگان", "آزادشهر", "آق‌قلا", "بندر ترکمن", "بندر گز", "رامیان", "علی‌آباد کتول", "کردکوی", "کلاله", "گالیکش", "گمیشان", "گنبد کاووس", "مراوه‌تپه", "مینودشت"),
        "گیلان" to listOf("رشت", "آستارا", "آستانه اشرفیه", "املش", "بندر انزلی", "تالش", "رضوانشهر", "رودبار", "رودسر", "سیاهکل", "شفت", "صومعه‌سرا", "فومن", "لاهیجان", "لنگرود", "ماسال"),
        "لرستان" to listOf("خرم‌آباد", "ازنا", "الیگودرز", "بروجرد", "پل‌دختر", "دلفان", "دوره", "دورود", "رومشکان", "سلسله", "کوهدشت"),
        "مازندران" to listOf("ساری", "آمل", "بابل", "بابلسر", "بهشهر", "تنکابن", "جویبار", "چالوس", "رامسر", "سوادکوه", "سوادکوه شمالی", "سیمرغ", "عباس‌آباد", "فریدونکنار", "قائم‌شهر", "کلاردشت", "گلوگاه", "محمودآباد", "میاندرود", "نکا", "نور", "نوشهر"),
        "مرکزی" to listOf("اراک", "آشتیان", "تفرش", "خمین", "خنداب", "دلیجان", "زرندیه", "ساوه", "شازند", "فراهان", "کمیجان", "محلات"),
        "هرمزگان" to listOf("بندرعباس", "ابوموسی", "بستک", "بشاگرد", "بندر خمیر", "بندر لنگه", "پارسیان", "جاسک", "حاجی‌آباد", "رودان", "سیریک", "قشم", "میناب"),
        "همدان" to listOf("همدان", "اسدآباد", "بهار", "تویسرکان", "رزن", "فامنین", "کبودرآهنگ", "ملایر", "نهاوند"),
        "یزد" to listOf("یزد", "ابرکوه", "اردکان", "اشکذر", "بافق", "بهاباد", "تفت", "خاتم", "مهریز", "میبد"),
    )

    fun citiesOf(province: String): List<String> = citiesByProvince[province.trim().removePrefix("استان").trim()].orEmpty()

    val carKinds = listOf("وانت دو کابین", "وانت تک کابین", "شاسی‌بلند", "سواری")
    val motoKinds = listOf("موتورسیکلت تریل", "موتورسیکلت معمولی")
    val carModels = listOf("تویوتا هایلوکس", "نیسان پیکاپ", "پاترول", "کاپرا", "ایسوزو دی‌مکس", "پژو پارس")
    val motoModels = listOf("نامی ۱۲۵", "نامی ۱۵۰", "نامی ۲۰۰", "هوندا ۱۲۵", "یاماها", "تریل ۲۰۰", "باجاج پالسار")

    fun years(current: Int): List<String> = (current downTo current - 30).map { it.toString() }
}
