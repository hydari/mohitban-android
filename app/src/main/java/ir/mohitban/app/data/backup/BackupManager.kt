package ir.mohitban.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.data.local.AppDatabase
import ir.mohitban.app.data.local.FuelEntryEntity
import ir.mohitban.app.data.local.ProfileEntity
import ir.mohitban.app.data.local.SettingsEntity
import ir.mohitban.app.data.local.VehicleEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Offline backup: one .zip file with data.json + every picture (signatures, receipts, photos).
 * The user keeps it on an SD card / computer / messenger and restores it on any phone.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val files: FileStore,
) {
    private companion object {
        const val DATA = "data.json"
        const val FORMAT = 1
    }

    suspend fun export(target: Uri) {
        val json = JSONObject()
        json.put("format", FORMAT)
        json.put("app", "mohitban")
        json.put("createdAt", System.currentTimeMillis())
        db.profileDao().get()?.let { json.put("profile", it.toJson()) }
        db.vehicleDao().get()?.let { json.put("vehicle", it.toJson()) }
        db.settingsDao().get()?.let { json.put("settings", it.toJson()) }
        json.put("entries", JSONArray().apply { db.fuelEntryDao().getAll().forEach { put(it.toJson()) } })

        val out = context.contentResolver.openOutputStream(target) ?: error("cannot open target")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA))
            zip.write(json.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.allMedia().forEach { f ->
                zip.putNextEntry(ZipEntry("${FileStore.MEDIA}/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun restore(source: Uri) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var data: String? = null
        val input = context.contentResolver.openInputStream(source) ?: error("cannot open source")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                when {
                    e.isDirectory -> Unit
                    name == DATA -> data = zip.readBytes().toString(Charsets.UTF_8)
                    name.startsWith("${FileStore.MEDIA}/") && !name.contains("..") -> {
                        val f = File(staging, name.substringAfterLast('/'))
                        f.outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
            }
        }
        val json = JSONObject(data ?: error("data.json missing"))
        require(json.optString("app") == "mohitban") { "not a mohitban backup" }

        val profile = json.optJSONObject("profile")?.toProfile()
        val vehicle = json.optJSONObject("vehicle")?.toVehicle()
        val settings = json.optJSONObject("settings")?.toSettings()
        val entries = json.optJSONArray("entries")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).toEntry() } }.orEmpty()

        db.withTransaction {
            db.fuelEntryDao().clear()
            db.profileDao().clear()
            db.vehicleDao().clear()
            profile?.let { db.profileDao().upsert(it) }
            vehicle?.let { db.vehicleDao().upsert(it) }
            settings?.let { db.settingsDao().upsert(it) }
            entries.forEach { db.fuelEntryDao().insert(it) }
        }
        // replace pictures
        files.allMedia().forEach { it.delete() }
        staging.listFiles()?.forEach { it.copyTo(File(files.mediaRoot(), it.name), overwrite = true) }
        staging.deleteRecursively()
    }

    // ------------------------------------------------------------------ json mapping

    private fun JSONObject.str(key: String): String = optString(key, "")
    private fun JSONObject.strOrNull(key: String): String? = if (isNull(key) || !has(key)) null else optString(key)

    private fun ProfileEntity.toJson() = JSONObject().apply {
        put("fullName", fullName); put("fatherName", fatherName); put("nationalId", nationalId); put("mobile", mobile)
        put("rank", rank); put("photoPath", photoPath ?: JSONObject.NULL); put("province", province); put("city", city)
        put("protectedArea", protectedArea); put("station", station)
    }

    private fun JSONObject.toProfile() = ProfileEntity(
        fullName = str("fullName"), fatherName = str("fatherName"), nationalId = str("nationalId"), mobile = str("mobile"),
        rank = str("rank"), photoPath = strOrNull("photoPath"), province = str("province"), city = str("city"),
        protectedArea = str("protectedArea"), station = str("station"),
    )

    private fun VehicleEntity.toJson() = JSONObject().apply {
        put("type", type); put("kind", kind); put("model", model); put("year", year); put("plate", plate)
        put("odometer", odometer); put("photoPath", photoPath ?: JSONObject.NULL)
    }

    private fun JSONObject.toVehicle() = VehicleEntity(
        type = str("type"), kind = str("kind"), model = str("model"), year = str("year"), plate = str("plate"),
        odometer = optLong("odometer"), photoPath = strOrNull("photoPath"),
    )

    private fun SettingsEntity.toJson() = JSONObject().apply {
        put("g1", gasolineRate1); put("g2", gasolineRate2); put("g3", gasolineRate3); put("cng", cngRate)
        put("lastBackupAt", lastBackupAt ?: JSONObject.NULL)
    }

    private fun JSONObject.toSettings() = SettingsEntity(
        gasolineRate1 = optLong("g1", 10_000), gasolineRate2 = optLong("g2", 15_000), gasolineRate3 = optLong("g3", 30_000),
        cngRate = optLong("cng", 6_568), lastBackupAt = if (isNull("lastBackupAt")) null else optLong("lastBackupAt"),
    )

    private fun FuelEntryEntity.toJson() = JSONObject().apply {
        put("id", id); put("dateJdn", dateJdn); put("odometer", odometer); put("fuelType", fuelType); put("amount", amount)
        put("unitPrice", unitPrice); put("total", total); put("receiptPath", receiptPath ?: JSONObject.NULL)
        put("driverSignaturePath", driverSignaturePath); put("passengerSignaturePath", passengerSignaturePath ?: JSONObject.NULL)
        put("snapDriver", snapDriver); put("snapPlate", snapPlate); put("snapVehicleType", snapVehicleType)
        put("snapVehicleModel", snapVehicleModel); put("snapProvince", snapProvince); put("snapCity", snapCity)
        put("snapArea", snapArea); put("snapStation", snapStation); put("createdAt", createdAt)
    }

    private fun JSONObject.toEntry() = FuelEntryEntity(
        id = optLong("id"), dateJdn = optInt("dateJdn"), odometer = optLong("odometer"), fuelType = str("fuelType"),
        amount = optDouble("amount", 0.0), unitPrice = optLong("unitPrice"), total = optLong("total"),
        receiptPath = strOrNull("receiptPath"), driverSignaturePath = str("driverSignaturePath"),
        passengerSignaturePath = strOrNull("passengerSignaturePath"), snapDriver = str("snapDriver"), snapPlate = str("snapPlate"),
        snapVehicleType = str("snapVehicleType"), snapVehicleModel = str("snapVehicleModel"), snapProvince = str("snapProvince"),
        snapCity = str("snapCity"), snapArea = str("snapArea"), snapStation = str("snapStation"), createdAt = optLong("createdAt"),
    )
}
