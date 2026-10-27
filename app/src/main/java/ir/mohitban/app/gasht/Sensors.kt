package ir.mohitban.app.gasht

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One GPS reading with its quality. */
data class Fix(
    val point: GeoPoint,
    val accuracy: Float?,
    val speed: Float?,
    val course: Float?,
    val time: Long,
)

fun Location.toFix() = Fix(
    point = GeoPoint(latitude, longitude, if (hasAltitude()) altitude else null, time),
    accuracy = if (hasAccuracy()) accuracy else null,
    speed = if (hasSpeed()) speed else null,
    course = if (hasBearing()) bearing else null,
    time = time,
)

/** The phone's own GPS (satellites only; works with no internet and no SIM). */
object GpsLocation {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isGpsOn(context: Context): Boolean =
        runCatching { (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun updates(context: Context, intervalMs: Long = 1000, minMeters: Float = 0f): Flow<Fix> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hasPermission(context)) { close(); return@callbackFlow }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { trySend(location.toFix()) }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { trySend(it.toFix()) } }
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, minMeters, listener, Looper.getMainLooper()) }
        awaitClose { runCatching { lm.removeUpdates(listener) } }
    }

    /** Satellites (used in fix, visible). Android 7+. */
    @SuppressLint("MissingPermission")
    fun satellites(context: Context): Flow<Pair<Int, Int>> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !hasPermission(context)) { close(); return@callbackFlow }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val cb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
                trySend(used to status.satelliteCount)
            }
        }
        runCatching { lm.registerGnssStatusCallback(cb, android.os.Handler(Looper.getMainLooper())) }
        awaitClose { runCatching { lm.unregisterGnssStatusCallback(cb) } }
    }
}

/** Compass heading in degrees (0 = north, clockwise), smoothed. Portrait orientation. */
object Compass {
    fun available(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null || sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
    }

    fun heading(context: Context): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val r = FloatArray(9)
        val o = FloatArray(3)
        var g: FloatArray? = null
        var m: FloatArray? = null
        var smooth: Double? = null
        fun emit(deg: Float) {
            // circular low-pass so 359° → 1° does not swing round
            val rad = Math.toRadians(deg.toDouble())
            val prev = smooth
            smooth = if (prev == null) rad else {
                val dx = Math.cos(prev) * 0.85 + Math.cos(rad) * 0.15
                val dy = Math.sin(prev) * 0.85 + Math.sin(rad) * 0.15
                Math.atan2(dy, dx)
            }
            trySend(((Math.toDegrees(smooth!!) + 360) % 360).toFloat())
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(r, e.values)
                        SensorManager.getOrientation(r, o)
                        emit(Math.toDegrees(o[0].toDouble()).toFloat())
                    }
                    Sensor.TYPE_ACCELEROMETER -> g = e.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val mm = e.values.clone()
                        m = mm
                        val gg = g ?: return
                        if (SensorManager.getRotationMatrix(r, null, gg, mm)) {
                            SensorManager.getOrientation(r, o)
                            emit(Math.toDegrees(o[0].toDouble()).toFloat())
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (rotation != null) sm.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        else {
            accel?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            mag?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        }
        awaitClose { sm.unregisterListener(listener) }
    }
}
