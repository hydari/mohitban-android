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
import ir.mohitban.app.domain.model.VehicleType
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
    var vehicles by mutableStateOf<List<Vehicle>>(emptyList())
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
        private set
    /** 100,000 Rial (the third rate) is selected by default. */
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

    /** CNG is only used by cars. */
    val cngAllowed: Boolean get() = vehicle?.type != VehicleType.MOTORCYCLE

    init {
        viewModelScope.launch {
            profile = profiles.getProfile()
            vehicles = profiles.getVehicles()
            rates = settings.getRates()
            val defaultId = settings.getDefaultVehicleId()
            var selected = vehicles.firstOrNull { it.id == defaultId } ?: vehicles.firstOrNull()
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
                    e.vehicleId?.let { vid -> vehicles.firstOrNull { it.id == vid }?.let { selected = it } }
                }
            }
            applyVehicle(selected, keepFuelType = isEdit)
            loaded = true
        }
    }

    private suspend fun applyVehicle(v: Vehicle?, keepFuelType: Boolean) {
        vehicle = v
        lastOdometer = v?.let { fuel.lastOdometer(it.id) ?: it.odometer }
        if (v?.type == VehicleType.MOTORCYCLE) fuelType = FuelType.GASOLINE
    }

    fun selectVehicle(id: Long) {
        val v = vehicles.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { applyVehicle(v, keepFuelType = true) }
        errors = errors - "vehicle"
    }

    /** Reloads the list after a vehicle was added in the vehicles screen; selects [selectId] if given. */
    fun refreshVehicles(selectId: Long?) {
        viewModelScope.launch {
            vehicles = profiles.getVehicles()
            val v = vehicles.firstOrNull { it.id == (selectId ?: vehicle?.id) } ?: vehicles.firstOrNull()
            applyVehicle(v, keepFuelType = true)
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
                else -> rates.gasoline.first()
            }
        }

    val amountValue: Double get() = amount.toDoubleOrNull() ?: 0.0
    val total: Long get() = (amountValue * unitPrice).roundToLong()

    val receiptRequired: Boolean get() = fuelType == FuelType.GASOLINE

    fun onFuelType(t: FuelType) {
        if (t == FuelType.CNG && !cngAllowed) return
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
            vehicles = vehicles.map { if (it.id == updated.id) updated else it }
        }
    }

    /** First step of saving: validates and opens the summary dialog. */
    fun requestSave(driverEmpty: Boolean) {
        val e = buildMap {
            if (vehicle == null) put("vehicle", "وسیله نقلیه را انتخاب کنید")
            val odo = odometer.digitsOnly().toLongOrNull()
            if (odo == null || odo <= 0) put("odometer", "کیلومتر فعلی را وارد کنید")
            if (amountValue <= 0.0) put("amount", "مقدار سوخت را وارد کنید")
            else if (amountValue > 2000) put("amount", "مقدار وارد شده بیش از حد مجاز است")
            if (fuelType == FuelType.CNG && !cngAllowed) put("amount", "گاز CNG برای موتورسیکلت قابل ثبت نیست")
            if (receiptRequired && receiptPath == null) put("receipt", "برای بنزین، عکس رسید کارتخوان الزامی است")
            if (driverEmpty) put("driver", "امضای راننده الزامی است")
        }
        errors = e
        if (e.isEmpty()) showConfirm = true
    }

    fun summaryLines(): List<Pair<String, String>> = listOf(
        "تاریخ" to date.formatLong().fa(),
        "وسیله" to (vehicle?.let { "${it.title} | ${it.plate.displayText().fa()}" } ?: ""),
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
            val vehicleChanged = base != null && base.vehicleId != v.id
            val snapshot = if (base != null && !vehicleChanged) {
                base.snapshot
            } else {
                EntrySnapshot(
                    driverName = base?.snapshot?.driverName ?: p.fullName,
                    plate = v.plate,
                    vehicleType = v.type,
                    vehicleModel = v.title,
                    province = base?.snapshot?.province ?: p.province,
                    city = base?.snapshot?.city ?: p.city,
                    protectedArea = base?.snapshot?.protectedArea ?: p.protectedArea,
                    station = base?.snapshot?.station ?: p.station,
                )
            }
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
                snapshot = snapshot,
                createdAt = base?.createdAt ?: System.currentTimeMillis(),
                vehicleId = v.id,
            )
            val id = fuel.save(entry)
            settings.setDefaultVehicleId(v.id)
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
