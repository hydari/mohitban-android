package ir.mohitban.app.ui.fuel

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.amountText
import ir.mohitban.app.core.text.digitsOnly
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.core.text.rial
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.domain.model.EntrySnapshot
import ir.mohitban.app.domain.model.FuelEntry
import ir.mohitban.app.domain.model.FuelRates
import ir.mohitban.app.domain.model.FuelType
import ir.mohitban.app.domain.model.RangerProfile
import ir.mohitban.app.domain.model.Vehicle
import ir.mohitban.app.domain.repository.FuelRepository
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

class FuelFormViewModel(
    private val entryId: Long,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val fuel: FuelRepository,
    private val files: FileStore,
) : ViewModel() {

    var loaded by mutableStateOf(false)
        private set
    var profile by mutableStateOf<RangerProfile?>(null)
        private set
    var vehicle by mutableStateOf<Vehicle?>(null)
        private set
    var rates by mutableStateOf(FuelRates())
        private set
    var lastOdometer by mutableStateOf<Long?>(null)
        private set

    var date by mutableStateOf(JalaliDate.today())
    var odometer by mutableStateOf("")
    var fuelType by mutableStateOf(FuelType.GASOLINE)
    var gasolineRateIndex by mutableStateOf(2)
    var amount by mutableStateOf("")
    var receiptPath by mutableStateOf<String?>(null)
        private set
    var existingDriverSignature by mutableStateOf<String?>(null)
        private set
    var existingPassengerSignature by mutableStateOf<String?>(null)
        private set

    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var showConfirm by mutableStateOf(false)
    var saving by mutableStateOf(false)
        private set
    var savedEntry by mutableStateOf<FuelEntry?>(null)
        private set

    private var original: FuelEntry? = null
    /** Files created in this session; deleted again if the form is abandoned. */
    private val createdFiles = mutableListOf<String>()
    private var committed = false

    val isEdit: Boolean get() = entryId != 0L

    init {
        viewModelScope.launch {
            profile = profiles.getProfile()
            vehicle = profiles.getVehicle()
            rates = settings.getRates()
            lastOdometer = fuel.lastOdometer() ?: vehicle?.odometer
            if (isEdit) {
                fuel.get(entryId)?.let { e ->
                    original = e
                    date = e.date
                    odometer = e.odometer.toString()
                    fuelType = e.fuelType
                    amount = formatAmount(e.amount)
                    receiptPath = e.receiptPath
                    existingDriverSignature = e.driverSignaturePath
                    existingPassengerSignature = e.passengerSignaturePath
                    if (e.fuelType == FuelType.GASOLINE) {
                        val idx = rates.gasoline.indexOf(e.unitPrice)
                        gasolineRateIndex = if (idx >= 0) idx else -1
                    }
                }
            }
            loaded = true
        }
    }

    private fun formatAmount(a: Double): String =
        if (a == a.toLong().toDouble()) a.toLong().toString() else a.toString()

    /** Price per litre / kilogram. While editing an old entry with a rate that no longer exists, the old rate is kept. */
    val unitPrice: Long
        get() = when (fuelType) {
            FuelType.CNG -> if (original?.fuelType == FuelType.CNG) original!!.unitPrice.takeIf { it > 0 } ?: rates.cng else rates.cng
            FuelType.GASOLINE -> when {
                gasolineRateIndex in rates.gasoline.indices -> rates.gasoline[gasolineRateIndex]
                original?.fuelType == FuelType.GASOLINE -> original!!.unitPrice
                else -> rates.gasoline.last()
            }
        }

    val amountValue: Double get() = amount.toDoubleOrNull() ?: 0.0
    val total: Long get() = (amountValue * unitPrice).roundToLong()

    val receiptRequired: Boolean get() = fuelType == FuelType.GASOLINE

    fun onFuelType(t: FuelType) {
        fuelType = t
        if (errors.isNotEmpty()) errors = errors - "receipt" - "amount"
    }

    fun onReceipt(path: String) {
        receiptPath?.let { old -> if (old in createdFiles) { files.delete(old); createdFiles -= old } }
        createdFiles += path
        receiptPath = path
        errors = errors - "receipt"
    }

    fun removeReceipt() { receiptPath = null }

    fun onVehiclePhoto(path: String) {
        val v = vehicle ?: return
        viewModelScope.launch {
            val updated = v.copy(photoPath = path)
            profiles.saveVehicle(updated)
            vehicle = updated
        }
    }

    /** First step of saving: validates and opens the summary dialog. */
    fun requestSave(driverEmpty: Boolean) {
        val e = buildMap {
            val odo = odometer.digitsOnly().toLongOrNull()
            if (odo == null || odo <= 0) put("odometer", "کیلومتر فعلی را وارد کنید")
            if (amountValue <= 0.0) put("amount", "مقدار سوخت را وارد کنید")
            else if (amountValue > 2000) put("amount", "مقدار وارد شده بیش از حد مجاز است")
            if (receiptRequired && receiptPath == null) put("receipt", "برای بنزین، عکس رسید کارتخوان الزامی است")
            if (driverEmpty) put("driver", "امضای راننده الزامی است")
        }
        errors = e
        if (e.isEmpty()) showConfirm = true
    }

    fun summaryLines(): List<Pair<String, String>> = listOf(
        "تاریخ" to date.formatLong().fa(),
        "کیلومتر فعلی" to (odometer.digitsOnly().toLongOrNull() ?: 0L).rial(),
        "نوع سوخت" to fuelType.title,
        "مقدار" to "${amountValue.amountText()} ${fuelType.unit}",
        "فی" to "${unitPrice.rial()} ریال",
        "مبلغ کل" to "${total.rial()} ریال",
    )

    /**
     * Saves the entry. [driverBitmap]/[passengerBitmap] are new drawings (null = keep the existing
     * signature). [passengerCleared] removes an existing passenger signature.
     */
    fun save(driverBitmap: Bitmap?, passengerBitmap: Bitmap?, passengerCleared: Boolean) {
        val p = profile ?: return
        val v = vehicle ?: return
        saving = true
        viewModelScope.launch {
            val driverPath = withContext(Dispatchers.IO) { driverBitmap?.let { files.savePng(it, "sig_driver") } }
                ?.also { createdFiles += it } ?: existingDriverSignature
            val passengerPath = when {
                passengerBitmap != null -> withContext(Dispatchers.IO) { files.savePng(passengerBitmap, "sig_passenger") }.also { createdFiles += it }
                passengerCleared -> null
                else -> existingPassengerSignature
            }
            if (driverPath == null) {
                saving = false
                errors = mapOf("driver" to "امضای راننده الزامی است")
                showConfirm = false
                return@launch
            }
            val base = original
            val entry = FuelEntry(
                id = base?.id ?: 0L,
                date = date,
                odometer = odometer.digitsOnly().toLong(),
                fuelType = fuelType,
                amount = amountValue,
                unitPrice = unitPrice,
                total = total,
                receiptPath = receiptPath,
                driverSignaturePath = driverPath,
                passengerSignaturePath = passengerPath,
                snapshot = base?.snapshot?.copy(
                    // keep the original driver, refresh nothing else so the printed form stays the same
                ) ?: EntrySnapshot(
                    driverName = p.fullName,
                    plate = v.plate,
                    vehicleType = v.type,
                    vehicleModel = v.model,
                    province = p.province,
                    city = p.city,
                    protectedArea = p.protectedArea,
                    station = p.station,
                ),
                createdAt = base?.createdAt ?: System.currentTimeMillis(),
            )
            val id = fuel.save(entry)
            committed = true
            showConfirm = false
            saving = false
            savedEntry = entry.copy(id = id)
        }
    }

    override fun onCleared() {
        if (!committed) createdFiles.forEach { files.delete(it) }
        super.onCleared()
    }
}
