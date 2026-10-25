package ir.mohitban.app.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.mohitban.app.MainActivity
import ir.mohitban.app.R
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.domain.model.ShiftPattern
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * The day before a run of duty days starts, reminds the ranger (also on the lock screen) that
 * tomorrow is a shift day. Shown once per day, during the day (08:00–22:00), fully offline.
 */
object ShiftReminder {
    const val CHANNEL_ID = "shift_reminder"
    const val OPEN_SHIFT = "shift"
    private const val NOTIFICATION_ID = 1406
    private const val PREFS = "shift_reminder"
    private const val KEY_LAST = "last_jdn"

    /** True when today is a rest day and tomorrow a shift starts. */
    fun shiftTomorrow(p: ShiftPattern?, today: JalaliDate): Boolean =
        p != null && !p.isShift(today.jdn) && p.isShift(today.jdn + 1)

    fun title() = "یادآوری شیفت"

    fun message(name: String, today: JalaliDate): String {
        val who = name.ifBlank { "محیط‌بان" }
        val tomorrow = today.plusDays(1)
        return "$who عزیز، فردا ${tomorrow.formatLong().fa()} شیفت هستید."
    }

    suspend fun check(context: Context) {
        val c = context.container
        val today = JalaliDate.today()
        val pattern = c.settingsRepository.shift.first()
        if (!shiftTomorrow(pattern, today)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_LAST, 0) == today.jdn) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in 8..21) return
        val name = c.profileRepository.getProfile()?.fullName.orEmpty()
        if (show(context, message(name, today))) prefs.edit().putInt(KEY_LAST, today.jdn).apply()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "یادآوری شیفت کاری", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "یک روز مانده به شیفت یادآوری می‌کند"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        createChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReportReminder.EXTRA_OPEN, OPEN_SHIFT)
        }
        val pi = PendingIntent.getActivity(context, 8, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(0xFF1F6B3A.toInt())
            .setContentTitle(title())
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n); true }.getOrDefault(false)
    }
}
