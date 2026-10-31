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
        json.put("vehicles", JSONArray().apply { db.vehicleDao().getAll().forEach { put(it.toJson()) } })
        db.settingsDao().get()?.let { json.put("settings", it.toJson()) }
        json.put("entries", JSONArray().apply { db.fuelEntryDao().getAll().forEach { put(it.toJson()) } })
        // گشت‌یار: tracks with compact point arrays, and marked places
        val gd = db.gashtDao()
        json.put("tracks", JSONArray().apply {
            gd.getTracks().forEach { t ->
                put(JSONObject().apply {
                    put("name", t.name); put("source", t.source); put("createdAt", t.createdAt)
                    put("points", JSONArray().apply {
                        gd.points(t.id).forEach { p ->
                            put(JSONArray().apply { put(p.lat); put(p.lon); put(p.ele ?: JSONObject.NULL); put(p.time ?: JSONObject.NULL) })
                        }
                    })
                })
            }
        })
        json.put("waypoints", JSONArray().apply {
            gd.getWaypoints().forEach { w ->
                put(JSONObject().apply {
                    put("name", w.name); put("kind", w.kind); put("lat", w.lat); put("lon", w.lon); put("ele", w.ele ?: JSONObject.NULL)
                    put("note", w.note); put("photoPath", w.photoPath ?: JSONObject.NULL); put("createdAt", w.createdAt); put("source", w.source)
                })
            }
        })

        json.put("patrols", JSONArray().apply {
            gd.getPatrols().forEach { p ->
                put(JSONObject().apply {
                    put("jdn", p.jdn); put("startMin", p.startMin ?: JSONObject.NULL); put("endMin", p.endMin ?: JSONObject.NULL)
                    put("zone", p.zone); put("spot", p.spot); put("mode", p.mode); put("distanceKm", p.distanceKm ?: JSONObject.NULL)
                    put("findings", p.findings); put("violations", p.violations); put("wildlife", p.wildlife)
                    put("companions", p.companions); put("notes", p.notes); put("createdAt", p.createdAt)
                })
            }
        })

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
        val vehicles = json.optJSONArray("vehicles")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).toVehicle() } }
            ?: listOfNotNull(json.optJSONObject("vehicle")?.toVehicle())
        val settings = json.optJSONObject("settings")?.toSettings()
        val entries = json.optJSONArray("entries")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).toEntry() } }.orEmpty()
        val tracks = json.optJSONArray("tracks")
        val places = json.optJSONArray("waypoints")
        val patrols = json.optJSONArray("patrols")

        db.withTransaction {
            db.fuelEntryDao().clear()
            db.profileDao().clear()
            db.vehicleDao().clear()
            profile?.let { db.profileDao().upsert(it) }
            vehicles.forEach { db.vehicleDao().insert(it) }
            settings?.let { db.settingsDao().upsert(it) }
            entries.forEach { db.fuelEntryDao().insert(it) }
            // older backups have no گشت‌یار data: keep what is on the phone in that case
            if (tracks != null || places != null) {
                val gd = db.gashtDao()
                gd.clearPoints(); gd.clearTracks(); gd.clearWaypoints()
                tracks?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val t = arr.getJSONObject(i)
                        val id = gd.insertTrack(
                            ir.mohitban.app.data.local.TrackEntity(name = t.optString("name"), source = t.optString("source", "REC"), createdAt = t.optLong("createdAt")),
                        )
                        val pts = t.optJSONArray("points")
                        if (pts != null) {
                            gd.insertPoints((0 until pts.length()).map { k ->
                                val p = pts.getJSONArray(k)
                                ir.mohitban.app.data.local.TrackPointEntity(
                                    trackId = id, seq = k, lat = p.getDouble(0), lon = p.getDouble(1),
                                    ele = if (p.isNull(2)) null else p.getDouble(2), time = if (p.isNull(3)) null else p.getLong(3),
                                )
                            })
                        }
                    }
                }
                places?.let { arr ->
                    gd.insertWaypoints((0 until arr.length()).map { k ->
                        val w = arr.getJSONObject(k)
                        ir.mohitban.app.data.local.WaypointEntity(
                            name = w.optString("name"), kind = w.optString("kind", "OTHER"), lat = w.getDouble("lat"), lon = w.getDouble("lon"),
                            ele = if (w.isNull("ele")) null else w.optDouble("ele"), note = w.optString("note"),
                            photoPath = if (w.isNull("photoPath")) null else w.optString("photoPath"),
                            createdAt = w.optLong("createdAt"), source = w.optString("source", "MANUAL"),
                        )
                    })
                }
            }
            // patrol logbook (older backups have none: keep the phone's)
            if (patrols != null) {
                val gd = db.gashtDao()
                gd.clearPatrols()
                gd.insertPatrols((0 until patrols.length()).map { k ->
                    val o = patrols.getJSONObject(k)
                    ir.mohitban.app.data.local.PatrolLogEntity(
                        jdn = o.getInt("jdn"),
                        startMin = if (o.isNull("startMin")) null else o.optInt("startMin"),
                        endMin = if (o.isNull("endMin")) null else o.optInt("endMin"),
                        zone = o.optString("zone"), spot = o.optString("spot"), mode = o.optString("mode"),
                        distanceKm = if (o.isNull("distanceKm")) null else o.optDouble("distanceKm"),
                        findings = o.optString("findings"), violations = o.optInt("violations"), wildlife = o.optString("wildlife"),
                        companions = o.optString("companions"), notes = o.optString("notes"), createdAt = o.optLong("createdAt"),
                    )
                })
            }
        }
        // track statistics are derived; recompute after restore
        if (tracks != null) db.gashtDao().getTracks().forEach { ir.mohitban.app.gasht.GashtRepository(db, files).refreshStats(it.id) }
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
        put("id", id); put("odometer", odometer); put("photoPath", photoPath ?: JSONObject.NULL); put("createdAt", createdAt)
    }

    private fun JSONObject.toVehicle() = VehicleEntity(
        id = optLong("id", 1L), createdAt = optLong("createdAt"),
        type = str("type"), kind = str("kind"), model = str("model"), year = str("year"), plate = str("plate"),
        odometer = optLong("odometer"), photoPath = strOrNull("photoPath"),
    )

    private fun SettingsEntity.toJson() = JSONObject().apply {
        put("g1", gasolineRate1); put("g2", gasolineRate2); put("g3", gasolineRate3); put("cng", cngRate)
        put("lastBackupAt", lastBackupAt ?: JSONObject.NULL)
        put("defaultVehicleId", defaultVehicleId ?: JSONObject.NULL)
        put("reportedSeasonKey", reportedSeasonKey ?: JSONObject.NULL)
        put("shiftWork", shiftWork ?: JSONObject.NULL); put("shiftRest", shiftRest ?: JSONObject.NULL)
        put("shiftStartJdn", shiftStartJdn ?: JSONObject.NULL); put("shiftOverrides", shiftOverrides ?: JSONObject.NULL)
    }

    private fun JSONObject.toSettings() = SettingsEntity(
        gasolineRate1 = optLong("g1", 15_000), gasolineRate2 = optLong("g2", 30_000), gasolineRate3 = optLong("g3", 100_000),
        cngRate = optLong("cng", 6_568), lastBackupAt = if (isNull("lastBackupAt")) null else optLong("lastBackupAt"),
        defaultVehicleId = if (!has("defaultVehicleId") || isNull("defaultVehicleId")) null else optLong("defaultVehicleId"),
        reportedSeasonKey = if (!has("reportedSeasonKey") || isNull("reportedSeasonKey")) null else optInt("reportedSeasonKey"),
        shiftWork = if (!has("shiftWork") || isNull("shiftWork")) null else optInt("shiftWork"),
        shiftRest = if (!has("shiftRest") || isNull("shiftRest")) null else optInt("shiftRest"),
        shiftStartJdn = if (!has("shiftStartJdn") || isNull("shiftStartJdn")) null else optInt("shiftStartJdn"),
        shiftOverrides = if (!has("shiftOverrides") || isNull("shiftOverrides")) null else optString("shiftOverrides"),
    )

    private fun FuelEntryEntity.toJson() = JSONObject().apply {
        put("id", id); put("dateJdn", dateJdn); put("odometer", odometer); put("fuelType", fuelType); put("amount", amount)
        put("unitPrice", unitPrice); put("total", total); put("receiptPath", receiptPath ?: JSONObject.NULL)
        put("driverSignaturePath", driverSignaturePath); put("passengerSignaturePath", passengerSignaturePath ?: JSONObject.NULL)
        put("snapDriver", snapDriver); put("snapPlate", snapPlate); put("snapVehicleType", snapVehicleType)
        put("snapVehicleModel", snapVehicleModel); put("snapProvince", snapProvince); put("snapCity", snapCity)
        put("snapArea", snapArea); put("snapStation", snapStation); put("createdAt", createdAt)
        put("vehicleId", vehicleId ?: JSONObject.NULL)
        put("editedAt", editedAt ?: JSONObject.NULL)
    }

    private fun JSONObject.toEntry() = FuelEntryEntity(
        id = optLong("id"), dateJdn = optInt("dateJdn"), odometer = optLong("odometer"), fuelType = str("fuelType"),
        amount = optDouble("amount", 0.0), unitPrice = optLong("unitPrice"), total = optLong("total"),
        receiptPath = strOrNull("receiptPath"), driverSignaturePath = str("driverSignaturePath"),
        passengerSignaturePath = strOrNull("passengerSignaturePath"), snapDriver = str("snapDriver"), snapPlate = str("snapPlate"),
        snapVehicleType = str("snapVehicleType"), snapVehicleModel = str("snapVehicleModel"), snapProvince = str("snapProvince"),
        snapCity = str("snapCity"), snapArea = str("snapArea"), snapStation = str("snapStation"), createdAt = optLong("createdAt"),
        vehicleId = if (!has("vehicleId") || isNull("vehicleId")) 1L else optLong("vehicleId"),
        editedAt = if (!has("editedAt") || isNull("editedAt")) null else optLong("editedAt"),
    )
}
