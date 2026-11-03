package ir.mohitban.app.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.mohitban.app.MainActivity
import ir.mohitban.app.R
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.text.fa
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/**
 * A note on a calendar day, with an optional time and an optional reminder [remindBefore]
 * minutes before it (-1: no reminder).
 */
data class CalNote(
    val id: Long,
    val jdn: Int,
    val text: String,
    val minute: Int? = null,
    val remindBefore: Int = -1,
    val done: Boolean = false,
) {
    /** The moment of the note: its time, or 8:00 that day. */
    fun at(): Long {
        val (y, m, d) = Jalali.jdnToGregorian(jdn)
        val mm = minute ?: (8 * 60)
        return Calendar.getInstance().apply {
            clear(); set(y, m - 1, d, mm / 60, mm % 60, 0)
        }.timeInMillis
    }

    fun remindAt(): Long? = if (remindBefore < 0) null else at() - remindBefore * 60_000L

    fun timeText(): String? = minute?.let { String.format(Locale.US, "%02d:%02d", it / 60, it % 60).fa() }
}

object Reminders {
    /** (label, minutes before) */
    val PRESETS = listOf(
        "بدون یادآوری" to -1, "همان زمان" to 0, "۱۵ دقیقه قبل" to 15, "۱ ساعت قبل" to 60, "۳ ساعت قبل" to 180,
        "۱ روز قبل" to 1440, "۲ روز قبل" to 2880, "۳ روز قبل" to 4320, "۱ هفته قبل" to 10080,
    )

    fun label(minutes: Int): String = PRESETS.firstOrNull { it.second == minutes }?.first ?: when {
        minutes % 1440 == 0 -> "${(minutes / 1440).fa()} روز قبل"
        minutes % 60 == 0 -> "${(minutes / 60).fa()} ساعت قبل"
        else -> "${minutes.fa()} دقیقه قبل"
    }
}

/** The notes, kept on the phone. */
object NoteStore {
    private const val PREFS = "calendar_notes"
    val version = MutableStateFlow(0)

    private fun toJson(n: CalNote) = JSONObject().apply {
        put("id", n.id); put("jdn", n.jdn); put("text", n.text); n.minute?.let { put("min", it) }; put("before", n.remindBefore); put("done", n.done)
    }

    private fun fromJson(o: JSONObject) = CalNote(
        o.getLong("id"), o.getInt("jdn"), o.optString("text"), if (o.has("min")) o.getInt("min") else null, o.optInt("before", -1), o.optBoolean("done"),
    )

    fun all(context: Context): List<CalNote> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.sortedWith(compareBy({ it.jdn }, { it.minute ?: -1 }))
    }.getOrDefault(emptyList())

    private fun write(context: Context, list: List<CalNote>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("list", JSONArray().apply { list.forEach { put(toJson(it)) } }.toString()).apply()
        version.value++
    }

    @Synchronized
    fun save(context: Context, n: CalNote) {
        write(context, all(context).filter { it.id != n.id } + n)
        NoteAlarm.schedule(context, n)
    }

    @Synchronized
    fun delete(context: Context, id: Long) {
        NoteAlarm.cancel(context, id)
        write(context, all(context).filter { it.id != id })
    }

    fun get(context: Context, id: Long): CalNote? = all(context).firstOrNull { it.id == id }
}

/** Alarms for the reminders: exact when allowed, otherwise as close as Android lets. */
object NoteAlarm {
    private const val ACTION = "ir.mohitban.app.NOTE_REMINDER"
    private const val EXTRA_ID = "note_id"
    private const val CHANNEL_ID = "calendar_notes"

    private fun pi(context: Context, id: Long, create: Boolean): PendingIntent? {
        val i = Intent(context, NoteAlarmReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ID, id)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, (id % 100_000).toInt() + 20_000, i, flags)
    }

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    fun schedule(context: Context, n: CalNote) {
        cancel(context, n.id)
        val at = n.remindAt() ?: return
        if (n.done || at <= System.currentTimeMillis()) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val p = pi(context, n.id, true) ?: return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= 23 && canExact(context) -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
                Build.VERSION.SDK_INT >= 23 -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
                else -> am.setExact(AlarmManager.RTC_WAKEUP, at, p)
            }
        }
    }

    fun cancel(context: Context, id: Long) {
        val p = pi(context, id, false) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(p)
        p.cancel()
    }

    /** After a restart of the phone (alarms are lost) or of the app. */
    fun rescheduleAll(context: Context) = NoteStore.all(context).forEach { schedule(context, it) }

    @SuppressLint("MissingPermission")
    fun notify(context: Context, id: Long) {
        val n = NoteStore.get(context, id) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "یادآوری یادداشت‌های تقویم", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "یادآوری کارهایی که در تقویم شیفت نوشته‌اید"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReportReminder.EXTRA_OPEN, ShiftReminder.OPEN_SHIFT)
        }
        val content = PendingIntent.getActivity(context, (id % 100_000).toInt() + 40_000, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val date = Jalali.fromJdn(n.jdn).formatLong().fa()
        val whenText = buildString {
            append(date)
            n.timeText()?.let { append(" ساعت $it") }
            if (n.remindBefore > 0) append(" (${Reminders.label(n.remindBefore)})")
        }
        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(0xFF1F6B3A.toInt())
            .setContentTitle("یادآوری: ${n.text.lineSequence().first().take(60)}")
            .setContentText(whenText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${n.text}\n$whenText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((id % 100_000).toInt() + 60_000, note) }
    }

    internal fun idOf(intent: Intent) = intent.getLongExtra(EXTRA_ID, -1L)
    internal const val ACTION_NOTE = ACTION
}

class NoteAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NoteAlarm.ACTION_NOTE -> NoteAlarm.idOf(intent).takeIf { it >= 0 }?.let { NoteAlarm.notify(context, it) }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" ->
                NoteAlarm.rescheduleAll(context)
        }
    }
}
