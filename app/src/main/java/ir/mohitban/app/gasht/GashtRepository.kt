package ir.mohitban.app.gasht

import androidx.room.withTransaction
import ir.mohitban.app.data.files.FileStore
import ir.mohitban.app.data.local.AppDatabase
import ir.mohitban.app.data.local.TrackEntity
import ir.mohitban.app.data.local.TrackPointEntity
import ir.mohitban.app.data.local.WaypointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** Kinds of marked places, with their Persian titles. */
enum class PlaceKind(val title: String) {
    SPRING("چشمه / آبشخور"), AMBUSH("محل کمین"), DEN("لانه / زیستگاه"), FIND("محل کشف"),
    VIOLATION("محل تخلف"), FIRE("آتش‌سوزی"), CAMP("پاسگاه / کمپ"), VEHICLE("محل خودرو"), OTHER("سایر");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

object TrackSource {
    const val RECORDED = "REC"
    const val IMPORTED = "IMPORT"
}

fun TrackPointEntity.geo() = GeoPoint(lat, lon, ele, time)
fun WaypointEntity.geo() = GeoPoint(lat, lon, ele)

class GashtRepository(private val db: AppDatabase, private val files: FileStore) {
    private val dao = db.gashtDao()

    val tracks: Flow<List<TrackEntity>> = dao.observeTracks()
    val waypoints: Flow<List<WaypointEntity>> = dao.observeWaypoints()

    suspend fun track(id: Long) = dao.getTrack(id)
    suspend fun points(trackId: Long): List<GeoPoint> = dao.points(trackId).map { it.geo() }
    fun observePoints(trackId: Long): Flow<List<GeoPoint>> = dao.observePoints(trackId).map { l -> l.map { it.geo() } }

    suspend fun startTrack(name: String): Long =
        dao.insertTrack(TrackEntity(name = name, source = TrackSource.RECORDED, createdAt = System.currentTimeMillis()))

    suspend fun append(trackId: Long, points: List<GeoPoint>) {
        if (points.isEmpty()) return
        val seq = dao.lastSeq(trackId) + 1
        dao.insertPoints(points.mapIndexed { i, p -> TrackPointEntity(trackId = trackId, seq = seq + i, lat = p.lat, lon = p.lon, ele = p.ele, time = p.time) })
    }

    /** Recomputes length, duration and point count after recording or import. */
    suspend fun refreshStats(trackId: Long) {
        val t = dao.getTrack(trackId) ?: return
        val pts = points(trackId)
        val times = pts.mapNotNull { it.time }
        dao.updateTrack(
            t.copy(
                distance = Geo.pathLength(pts),
                duration = if (times.size >= 2) times.last() - times.first() else 0,
                pointCount = pts.size,
            ),
        )
    }

    suspend fun rename(trackId: Long, name: String) = dao.renameTrack(trackId, name)

    suspend fun deleteTrack(trackId: Long) = db.withTransaction {
        dao.deletePoints(trackId)
        dao.deleteTrack(trackId)
    }

    /** Stores everything a GPS file holds; returns (tracks, places) imported. */
    suspend fun import(data: GpsData): Pair<Int, Int> = db.withTransaction {
        var tracks = 0
        for (p in data.paths) {
            if (p.points.size < 2) continue
            val id = dao.insertTrack(TrackEntity(name = p.name.take(80), source = TrackSource.IMPORTED, createdAt = System.currentTimeMillis()))
            dao.insertPoints(p.points.mapIndexed { i, q -> TrackPointEntity(trackId = id, seq = i, lat = q.lat, lon = q.lon, ele = q.ele, time = q.time) })
            refreshStats(id)
            tracks++
        }
        val now = System.currentTimeMillis()
        dao.insertWaypoints(
            data.points.mapIndexed { i, w ->
                WaypointEntity(
                    name = w.name.take(80), kind = PlaceKind.OTHER.name, lat = w.point.lat, lon = w.point.lon, ele = w.point.ele,
                    note = w.description.take(500), createdAt = now + i, source = TrackSource.IMPORTED,
                )
            },
        )
        tracks to data.points.size
    }

    suspend fun addWaypoint(w: WaypointEntity): Long = dao.insertWaypoint(w)

    suspend fun deleteWaypoint(id: Long) {
        dao.getWaypoint(id)?.photoPath?.let { files.delete(it) }
        dao.deleteWaypoint(id)
    }

    /** Writes a GPX or KML file (for Garmin units / Google Earth) into the share cache. */
    suspend fun export(trackIds: List<Long>, includeWaypoints: Boolean, kml: Boolean, title: String): File {
        val paths = trackIds.mapNotNull { id -> dao.getTrack(id)?.let { NamedPath(it.name, points(id)) } }
        val places = if (includeWaypoints) dao.getWaypoints().map { NamedPoint(it.name, it.geo(), it.note) } else emptyList()
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "gasht" }
        val f = File(files.cacheDir, if (kml) "$safe.kml" else "$safe.gpx")
        f.writeText(if (kml) GpsFiles.toKml(title, paths, places) else GpsFiles.toGpx(paths, places))
        return f
    }
}
