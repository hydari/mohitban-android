package ir.mohitban.app.gasht

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.mohitban.app.MainActivity
import ir.mohitban.app.R
import ir.mohitban.app.reminder.ReportReminder
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** An emergency message received from a colleague's Mohityar. */
data class SosAlert(
    val id: Long,
    val from: String,
    val name: String,
    val grade: String,
    val station: String,
    val point: GeoPoint,
    val accuracy: Int?,
    val sentAt: Long,
    val receivedAt: Long,
)

/**
 * The emergency SMS carries one machine line besides the readable text, so a colleague's phone
 * with Mohityar can open the app on the exact place:
 *   MHY1|lat|lon|accuracy|unix seconds|name|grade|station
 * Phones without the app just see the text and the map link.
 */
object SosCodec {
    const val TAG = "MHY1|"

    private fun clean(s: String) = s.replace('|', ' ').replace('\n', ' ').trim().take(40)

    fun line(name: String, grade: String, station: String, f: Fix): String = TAG + listOf(
        String.format(Locale.US, "%.5f", f.point.lat),
        String.format(Locale.US, "%.5f", f.point.lon),
        f.accuracy?.toInt()?.toString().orEmpty(),
        (f.time / 1000).toString(),
        clean(name), clean(grade), clean(station),
    ).joinToString("|")

    fun decode(body: String, from: String, receivedAt: Long): SosAlert? {
        val i = body.indexOf(TAG)
        if (i < 0) return null
        val parts = body.substring(i + TAG.length).lineSequence().first().split('|')
        if (parts.size < 4) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val sent = parts.getOrNull(3)?.trim()?.toLongOrNull()?.times(1000) ?: receivedAt
        return SosAlert(
            id = receivedAt, from = from,
            name = parts.getOrNull(4)?.trim().orEmpty(), grade = parts.getOrNull(5)?.trim().orEmpty(), station = parts.getOrNull(6)?.trim().orEmpty(),
            point = GeoPoint(lat, lon), accuracy = parts[2].trim().toIntOrNull(), sentAt = sent, receivedAt = receivedAt,
        )
    }
}

/** The emergency alerts received on this phone (the last 20). */
object SosStore {
    private const val PREFS = "sos_alerts"

    /** Asks the open app to show an alert (set by the notification and by the alert list). */
    val open = MutableStateFlow<Long?>(null)

    private fun toJson(a: SosAlert) = JSONObject().apply {
        put("id", a.id); put("from", a.from); put("name", a.name); put("grade", a.grade); put("station", a.station)
        put("lat", a.point.lat); put("lon", a.point.lon); a.accuracy?.let { put("acc", it) }; put("sent", a.sentAt); put("recv", a.receivedAt)
    }

    private fun fromJson(o: JSONObject) = SosAlert(
        o.getLong("id"), o.optString("from"), o.optString("name"), o.optString("grade"), o.optString("station"),
        GeoPoint(o.getDouble("lat"), o.getDouble("lon")), if (o.has("acc")) o.optInt("acc") else null, o.optLong("sent"), o.optLong("recv"),
    )

    fun all(context: Context): List<SosAlert> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.sortedByDescending { it.receivedAt }
    }.getOrDefault(emptyList())

    fun get(context: Context, id: Long): SosAlert? = all(context).firstOrNull { it.id == id }

    @Synchronized
    fun add(context: Context, a: SosAlert) {
        val list = (listOf(a) + all(context)).take(20)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("list", JSONArray().apply { list.forEach { put(toJson(it)) } }.toString()).apply()
    }

    fun delete(context: Context, id: Long) {
        val list = all(context).filter { it.id != id }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("list", JSONArray().apply { list.forEach { put(toJson(it)) } }.toString()).apply()
    }

    fun canReceive(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
}

/** Picks up a colleague's emergency SMS and raises a full-screen alarm that opens the map. */
class SosReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull() ?: return
        // the parts of a long message arrive together; join them per sender
        msgs.filterNotNull().groupBy { it.originatingAddress.orEmpty() }.forEach { (from, parts) ->
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            val alert = SosCodec.decode(body, from, System.currentTimeMillis()) ?: return@forEach
            SosStore.add(context, alert)
            SosNotifier.show(context, alert)
        }
    }
}

object SosNotifier {
    private const val CHANNEL_ID = "sos_alarm"
    const val OPEN_SOS = "open_sos"
    const val EXTRA_ID = "sos_id"

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL_ID, "هشدار اضطراری همکاران", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "وقتی همکار محیط‌بان پیامک اضطراری می‌فرستد، برنامه باز می‌شود و محل او را نشان می‌دهد"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 900, 300, 900, 300, 900, 300, 1500)
            enableLights(true)
            lightColor = 0xFFE5483B.toInt()
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    fun openIntent(context: Context, id: Long): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(ReportReminder.EXTRA_OPEN, OPEN_SOS)
        putExtra(EXTRA_ID, id)
    }

    fun notificationId(id: Long) = 5000 + (id % 1000).toInt()

    @SuppressLint("MissingPermission")
    fun show(context: Context, a: SosAlert) {
        channel(context)
        val pi = PendingIntent.getActivity(context, notificationId(a.id), openIntent(context, a.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val who = a.name.ifBlank { a.from }
        val text = "$who نیاز به کمک دارد. برای دیدن محل او روی نقشه بزنید." + (a.station.takeIf { it.isNotBlank() }?.let { " (پاسگاه $it)" } ?: "")
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(0xFFE5483B.toInt())
            .setContentTitle("هشدار اضطراری محیط‌بان")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 900, 300, 900, 300, 900, 300, 1500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setAutoCancel(true)
            .setContentIntent(pi)
            // opens the app over the lock screen (or shows a heads-up banner while the phone is in use)
            .setFullScreenIntent(pi, true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(a.id), n) }
        // if the app is already open, go straight to the map
        SosStore.open.value = a.id
    }

    fun cancel(context: Context, id: Long) = runCatching { NotificationManagerCompat.from(context).cancel(notificationId(id)) }

    /** Android 14+: whether the app may open itself over the lock screen. */
    fun canFullScreen(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34) context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: true else true
}
