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
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import java.util.Calendar

/**
 * Wishes the ranger a happy birthday: a notification once on the day (from 08:00), and the
 * animated card on the dashboard. Born on 30 Esfand → celebrated on 29 Esfand in common years.
 */
object BirthdayReminder {
    private const val CHANNEL_ID = "birthday"
    private const val NOTIFICATION_ID = 1407
    private const val PREFS = "birthday"
    private const val KEY_YEAR = "last_year"

    fun isBirthday(birthJdn: Int, today: JalaliDate): Boolean {
        val b = JalaliDate.fromJdn(birthJdn)
        if (b.month != today.month) return false
        val day = minOf(b.day, Jalali.monthLength(today.year, today.month))
        return day == today.day && today.year > b.year
    }

    suspend fun check(context: Context) {
        val profile = context.container.profileRepository.getProfile() ?: return
        val birth = profile.birthJdn ?: return
        val today = JalaliDate.today()
        if (!isBirthday(birth, today)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_YEAR, 0) == today.year) return
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 8) return
        val first = profile.fullName.trim().split(" ").firstOrNull().orEmpty().ifEmpty { "همکار" }
        if (show(context, "$first عزیز، تولدت مبارک!", "سالی پر از سلامتی، آرامش و گشت‌های بی‌خطر برایت آرزو می‌کنیم. — محیط‌یار")) {
            prefs.edit().putInt(KEY_YEAR, today.year).apply()
        }
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "تبریک تولد", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "روز تولد شما را تبریک می‌گوید"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val open = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val pi = PendingIntent.getActivity(context, 9, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setColor(0xFFE5483B.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n); true }.getOrDefault(false)
    }
}
