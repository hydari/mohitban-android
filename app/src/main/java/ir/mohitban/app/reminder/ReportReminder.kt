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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.mohitban.app.MainActivity
import ir.mohitban.app.R
import ir.mohitban.app.container
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.jalali.Season
import ir.mohitban.app.core.text.fa
import java.util.concurrent.TimeUnit

/**
 * Reminds the ranger to send the quarterly (seasonal) fuel report to the head of the department:
 * every day during the last 5 days of each Jalali season, and for 10 days after the season if the
 * report of the finished season was not sent yet. The reminder stops as soon as the report of that
 * season is shared, saved or printed from the app. Works fully offline (WorkManager).
 */
object ReportReminder {
    const val CHANNEL_ID = "quarter_report"
    const val EXTRA_OPEN = "open_screen"
    const val OPEN_REPORTS = "reports"
    private const val WORK = "quarter_report_reminder"
    private const val NOTIFICATION_ID = 1405

    data class Due(val year: Int, val season: Season, val daysLeft: Int) {
        val overdue: Boolean get() = daysLeft < 0
        val key: Int get() = key(year, season)
    }

    fun key(year: Int, season: Season) = year * 10 + season.index

    /** Which report is due today, if any. */
    fun due(today: JalaliDate, reportedKey: Int?): Due? {
        val reported = reportedKey ?: 0
        val season = today.season
        val range = season.jdnRange(today.year)
        val left = range.last - today.jdn
        if (left in 0..5 && reported < key(today.year, season)) return Due(today.year, season, left)
        val prev = JalaliDate.fromJdn(range.first - 1)
        val since = today.jdn - range.first
        if (since in 0..9 && reported < key(prev.year, prev.season)) return Due(prev.year, prev.season, -(since + 1))
        return null
    }

    fun title(d: Due): String = if (d.overdue) "گزارش سه ماهه ارسال نشده است" else "یادآوری گزارش سه ماهه"

    fun message(name: String, d: Due): String {
        val who = name.ifBlank { "محیط‌بان" }
        val period = "${d.season.title} ${d.year.fa()}"
        return when {
            d.overdue -> "$who عزیز، گزارش سه ماهه $period را هرچه زودتر برای رئیس اداره بفرستید."
            d.daysLeft == 0 -> "$who عزیز، امروز آخرین روز فصل است. گزارش سه ماهه $period را برای رئیس اداره بفرستید."
            else -> "$who عزیز، ${d.daysLeft.fa()} روز تا پایان فصل مانده است. گزارش سه ماهه $period را برای رئیس اداره بفرستید."
        }
    }

    fun schedule(context: Context) {
        createChannel(context)
        // every 4 hours so the shift reminder lands during the day; UPDATE replaces the older 12 h job
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(4, TimeUnit.HOURS).build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "یادآوری گزارش سه ماهه", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "یادآوری ارسال گزارش فصلی هزینه سوخت به رئیس اداره"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Shows today's reminder once. Called by the worker and when the app opens. */
    suspend fun check(context: Context) {
        runCatching { ShiftReminder.check(context) }
        val c = context.container
        val today = JalaliDate.today()
        val state = c.settingsRepository.getReminder()
        val d = due(today, state.reportedSeasonKey) ?: return
        if (state.lastReminderJdn == today.jdn) return
        val name = c.profileRepository.getProfile()?.fullName ?: return
        if (show(context, name, d)) c.settingsRepository.markReminderShown(today.jdn)
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, name: String, d: Due): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        createChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN, OPEN_REPORTS)
        }
        val pi = PendingIntent.getActivity(context, 7, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = message(name, d)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(0xFF1F6B3A.toInt())
            .setContentTitle(title(d))
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

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { ReportReminder.check(applicationContext) }
        return Result.success()
    }
}
