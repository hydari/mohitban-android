package ir.mohitban.app.gasht

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.mohitban.app.MainActivity
import ir.mohitban.app.R
import ir.mohitban.app.container
import ir.mohitban.app.reminder.ReportReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the ranger is following: a track (forward or back to its start) or a single place. */
sealed interface GuideTarget {
    val name: String

    data class Track(val trackId: Long, override val name: String, val points: List<GeoPoint>) : GuideTarget
    data class Place(override val name: String, val point: GeoPoint) : GuideTarget
}

data class RecordState(
    val trackId: Long? = null,
    val paused: Boolean = false,
    val startedAt: Long = 0,
    val distance: Double = 0.0,
    val points: Int = 0,
    val last: GeoPoint? = null,
) {
    val active: Boolean get() = trackId != null
}

data class GuideState(
    val target: GuideTarget? = null,
    val alarm: Boolean = true,
    val thresholdMeters: Int = 100,
    val offRoute: Boolean = false,
    val distanceToRoute: Double? = null,
)

/** Shared between the screens and the background service (one per app process). */
object GashtSession {
    private val _record = MutableStateFlow(RecordState())
    val record: StateFlow<RecordState> = _record.asStateFlow()
    private val _guide = MutableStateFlow(GuideState())
    val guide: StateFlow<GuideState> = _guide.asStateFlow()

    internal fun setRecord(f: (RecordState) -> RecordState) = _record.update(f)
    internal fun setGuide(f: (GuideState) -> GuideState) = _guide.update(f)

    fun startRecording(context: Context) = command(context, GashtService.ACTION_RECORD)
    fun pauseRecording(context: Context) = command(context, GashtService.ACTION_PAUSE)
    fun resumeRecording(context: Context) = command(context, GashtService.ACTION_RESUME)
    fun stopRecording(context: Context) = command(context, GashtService.ACTION_STOP_RECORD)

    fun follow(context: Context, target: GuideTarget) {
        _guide.update { it.copy(target = target, offRoute = false, distanceToRoute = null) }
        command(context, GashtService.ACTION_GUIDE)
    }

    fun stopFollowing(context: Context) {
        _guide.update { it.copy(target = null, offRoute = false, distanceToRoute = null) }
        command(context, GashtService.ACTION_STOP_GUIDE)
    }

    fun setAlarm(enabled: Boolean, threshold: Int) = _guide.update { it.copy(alarm = enabled, thresholdMeters = threshold) }

    private fun command(context: Context, action: String) {
        val i = Intent(context, GashtService::class.java).setAction(action)
        runCatching { ContextCompat.startForegroundService(context, i) }
    }
}

/**
 * Keeps GPS running with the screen off while a patrol is recorded or a route is followed.
 * Points go straight into the database; leaving the route makes the phone vibrate and warn.
 */
class GashtService : Service() {
    companion object {
        const val ACTION_RECORD = "gasht.record"
        const val ACTION_PAUSE = "gasht.pause"
        const val ACTION_RESUME = "gasht.resume"
        const val ACTION_STOP_RECORD = "gasht.stop_record"
        const val ACTION_GUIDE = "gasht.guide"
        const val ACTION_STOP_GUIDE = "gasht.stop_guide"
        const val OPEN_GASHT = "gasht"
        private const val CHANNEL = "gasht_status"
        private const val ALERT_CHANNEL = "gasht_alert"
        private const val NOTE_ID = 1501
        private const val ALERT_ID = 1502
        private const val PREFS = "gasht"
        private const val KEY_TRACK = "recording_track"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gpsJob: Job? = null
    private var lastAlarmAt = 0L
    private var starting = false
    /** Work that must finish even if the service stops right away (saving a finished track). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        startInForeground()
        val repo = container.gasht
        when (intent?.action) {
            ACTION_RECORD -> if (!GashtSession.record.value.active && !starting) {
                // flag first, so the idle check below does not stop the service before the track exists
                starting = true
                scope.launch {
                    val name = "گشت ${ir.mohitban.app.core.jalali.JalaliDate.today().formatLong(false)}"
                    val id = repo.startTrack(name)
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_TRACK, id).apply()
                    GashtSession.setRecord { RecordState(trackId = id, startedAt = System.currentTimeMillis()) }
                    starting = false
                    refresh()
                }
            }
            ACTION_PAUSE -> GashtSession.setRecord { it.copy(paused = true) }
            ACTION_RESUME -> GashtSession.setRecord { it.copy(paused = false, last = null) }
            ACTION_STOP_RECORD -> {
                val id = GashtSession.record.value.trackId
                GashtSession.setRecord { RecordState() }
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_TRACK).apply()
                if (id != null) appScope.launch {
                    if (repo.track(id) != null) {
                        repo.refreshStats(id)
                        // a "track" of fewer than two points is nothing to keep
                        if ((repo.track(id)?.pointCount ?: 0) < 2) repo.deleteTrack(id)
                    }
                }
            }
            ACTION_GUIDE -> Unit
            ACTION_STOP_GUIDE -> NotificationManagerCompat.from(this).cancel(ALERT_ID)
            null -> {
                // restarted by the system after the app was killed: continue an unfinished recording
                val id = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_TRACK, 0L)
                if (id > 0 && !GashtSession.record.value.active) {
                    scope.launch {
                        val pts = repo.points(id)
                        GashtSession.setRecord {
                            RecordState(trackId = id, startedAt = pts.firstOrNull()?.time ?: System.currentTimeMillis(), distance = Geo.pathLength(pts), points = pts.size, last = pts.lastOrNull())
                        }
                    }
                }
            }
        }
        ensureGps()
        stopIfIdle()
        refresh()
        return START_STICKY
    }

    private fun idle() = !starting && !GashtSession.record.value.active && GashtSession.guide.value.target == null &&
        getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_TRACK, 0L) == 0L

    private fun stopIfIdle() {
        if (idle()) {
            gpsJob?.cancel(); gpsJob = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun ensureGps() {
        if (gpsJob != null || !GpsLocation.hasPermission(this)) return
        gpsJob = scope.launch {
            GpsLocation.updates(this@GashtService, intervalMs = 2000, minMeters = 0f).collect { onFix(it) }
        }
    }

    private suspend fun onFix(fix: Fix) {
        // recording: keep reasonably accurate points at least 5 m apart
        val rec = GashtSession.record.value
        val acc = fix.accuracy ?: 0f
        if (rec.active && !rec.paused && acc <= 40f) {
            val last = rec.last
            val step = last?.let { Geo.distance(it, fix.point) } ?: 0.0
            if (last == null || step >= 5.0) {
                val p = fix.point.copy(time = fix.time)
                container.gasht.append(rec.trackId!!, listOf(p))
                GashtSession.setRecord { it.copy(distance = it.distance + step, points = it.points + 1, last = p) }
            }
        }
        // guidance: warn when the ranger leaves the route
        val g = GashtSession.guide.value
        val target = g.target
        if (target is GuideTarget.Track) {
            val d = Geo.nearestOnPath(fix.point, target.points)?.distance
            val off = d != null && d > g.thresholdMeters && acc < g.thresholdMeters
            GashtSession.setGuide { it.copy(distanceToRoute = d, offRoute = off) }
            if (off && g.alarm && System.currentTimeMillis() - lastAlarmAt > 30_000) {
                lastAlarmAt = System.currentTimeMillis()
                alarm(d!!)
            }
            if (!off) NotificationManagerCompat.from(this).cancel(ALERT_ID)
        }
        refresh()
    }

    private fun alarm(distance: Double) {
        val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 250, 600, 250, 900), -1))
            else @Suppress("DEPRECATION") v?.vibrate(longArrayOf(0, 600, 250, 600, 250, 900), -1)
        }
        val text = "${CoordFormat.meters(distance)} از مسیر «${GashtSession.guide.value.target?.name}» فاصله گرفته‌اید."
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(0xFFC62828.toInt())
            .setContentTitle("هشدار خروج از مسیر")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(ALERT_ID, n) }
    }

    private fun openApp(): PendingIntent {
        val i = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReportReminder.EXTRA_OPEN, OPEN_GASHT)
        }
        return PendingIntent.getActivity(this, 15, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun statusText(): String {
        val r = GashtSession.record.value
        val g = GashtSession.guide.value
        val parts = mutableListOf<String>()
        if (r.active) parts += (if (r.paused) "ضبط مسیر متوقف موقت" else "در حال ضبط مسیر") + " | ${CoordFormat.meters(r.distance)}"
        g.target?.let { t ->
            parts += "راهنمای «${t.name}»" + (g.distanceToRoute?.let { " | فاصله از مسیر ${CoordFormat.meters(it)}" } ?: "")
        }
        return parts.joinToString("\n").ifEmpty { "گشت‌یار فعال است" }
    }

    private fun build() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_leaf)
        .setColor(0xFF1F6B3A.toInt())
        .setContentTitle("گشت‌یار")
        .setContentText(statusText())
        .setStyle(NotificationCompat.BigTextStyle().bigText(statusText()))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(openApp())
        .build()

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        runCatching { ServiceCompat.startForeground(this, NOTE_ID, build(), type) }
    }

    private var lastRefresh = 0L
    private fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefresh < 3000) return
        lastRefresh = now
        runCatching { NotificationManagerCompat.from(this).notify(NOTE_ID, build()) }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "گشت‌یار (ضبط و راهنمای مسیر)", NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "هشدار خروج از مسیر", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            },
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
