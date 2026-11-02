package ir.mohitban.app.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.domain.repository.ProfileRepository
import ir.mohitban.app.domain.repository.SettingsRepository
import ir.mohitban.app.reminder.ReportReminder
import ir.mohitban.app.ui.common.appViewModel
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.EmptyState
import ir.mohitban.app.ui.components.TagBadge
import ir.mohitban.app.ui.components.TopBar
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class AlertTarget { REPORTS, SETTINGS, SHIFT }

class AlertItem(
    val icon: ImageVector,
    val title: String,
    val text: String,
    val color: Color,
    val urgent: Boolean,
    val target: AlertTarget,
)

/** Every reminder the app can show; the bell counts the urgent ones. */
object Alerts {
    fun build(
        today: JalaliDate, name: String, reportedSeasonKey: Int?, lastBackupAt: Long?,
        shift: ir.mohitban.app.domain.model.ShiftPattern? = null,
    ): List<AlertItem> {
        val list = mutableListOf<AlertItem>()
        if (ir.mohitban.app.reminder.ShiftReminder.shiftTomorrow(shift, today)) {
            list += AlertItem(
                Icons.Outlined.EventRepeat, ir.mohitban.app.reminder.ShiftReminder.title(),
                ir.mohitban.app.reminder.ShiftReminder.message(name, today),
                Color(0xFF239B56), urgent = true, target = AlertTarget.SHIFT,
            )
        }
        val due = ReportReminder.due(today, reportedSeasonKey)
        if (due != null) {
            list += AlertItem(
                Icons.Outlined.NotificationsActive, ReportReminder.title(due), ReportReminder.message(name, due),
                if (due.overdue) Color(0xFFD84B3E) else Color(0xFFD99A1E), urgent = true, target = AlertTarget.REPORTS,
            )
        } else {
            val range = today.season.jdnRange(today.year)
            val left = range.last - today.jdn
            val end = JalaliDate.fromJdn(range.last)
            list += AlertItem(
                Icons.Outlined.Event, "مهلت گزارش ${today.season.title} ${today.year.fa()}",
                "تا ${end.day.fa()} ${Jalali.MONTH_NAMES[end.month - 1]} | ${left.fa()} روز مانده است",
                MColors.Green2, urgent = false, target = AlertTarget.REPORTS,
            )
        }
        if (lastBackupAt == null || System.currentTimeMillis() - lastBackupAt > 14L * 24 * 3600 * 1000) {
            list += AlertItem(
                Icons.Outlined.Backup, "پشتیبان‌گیری",
                if (lastBackupAt == null) "هنوز از اطلاعات پشتیبان نگرفته‌اید" else "بیش از دو هفته از آخرین پشتیبان گذشته است",
                Color(0xFF2F7FC1), urgent = false, target = AlertTarget.SETTINGS,
            )
        }
        return list
    }

    /** Identity of the urgent alerts, so the bell knows whether they were already looked at. */
    fun key(list: List<AlertItem>): String = list.filter { it.urgent }.joinToString("|") { it.title + "~" + it.text }
}

/** Remembers which urgent alerts the ranger has opened (bell turns yellow and swings until then). */
object AlertSeen {
    private const val PREFS = "alerts"
    private const val KEY = "seen"
    private val state = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun flow(context: android.content.Context): kotlinx.coroutines.flow.StateFlow<String?> {
        if (state.value == null) {
            state.value = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        }
        return state
    }

    fun markSeen(context: android.content.Context, key: String) {
        if (state.value == key) return
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY, key).apply()
        state.value = key
    }
}

class AlertsViewModel(profiles: ProfileRepository, settings: SettingsRepository) : ViewModel() {
    val alerts = combine(profiles.profile, settings.reminder, settings.lastBackupAt, settings.shift) { p, r, b, sh ->
        Alerts.build(JalaliDate.today(), p?.fullName.orEmpty(), r.reportedSeasonKey, b, sh)
    }.stateIn<List<AlertItem>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun AlertsScreen(onBack: () -> Unit, onOpen: (AlertTarget) -> Unit) {
    val vm = appViewModel(key = "alerts") { c, _ -> AlertsViewModel(c.profileRepository, c.settingsRepository) }
    val loaded by vm.alerts.collectAsStateWithLifecycle()
    val alerts = loaded.orEmpty()
    val context = androidx.compose.ui.platform.LocalContext.current
    // opening this page counts as having seen the current alerts
    androidx.compose.runtime.LaunchedEffect(loaded) { loaded?.let { AlertSeen.markSeen(context, Alerts.key(it)) } }
    Column(Modifier.fillMaxSize().background(MColors.Bg)) {
        TopBar("اعلان‌ها و یادآوری‌ها", onBack = onBack)
        if (loaded != null && alerts.isEmpty()) {
            EmptyState(Icons.Outlined.NotificationsNone, "اعلانی وجود ندارد", "یادآوری‌های گزارش سه ماهه، شیفت و پشتیبان‌گیری اینجا نمایش داده می‌شود.")
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(alerts) { a ->
                AppCard(Modifier.fillMaxWidth(), onClick = { onOpen(a.target) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircleIcon(a.icon, a.color, Color.White, size = 46.dp, iconSize = 23.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                if (a.urgent) TagBadge("مهم", MColors.ErrorBg, MColors.Error, Modifier.padding(start = 6.dp))
                            }
                            Text(a.text, style = MaterialTheme.typography.bodySmall, color = MColors.Muted, modifier = Modifier.padding(top = 3.dp))
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MColors.Muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
