package ir.mohitban.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.text.fa
import ir.mohitban.app.ui.components.AppCard
import ir.mohitban.app.ui.components.CircleIcon
import ir.mohitban.app.ui.components.PillButton
import ir.mohitban.app.ui.theme.MColors
import ir.mohitban.app.update.AppRelease
import ir.mohitban.app.update.AppUpdate
import ir.mohitban.app.update.UpdateState
import kotlinx.coroutines.launch
import java.util.Calendar

/** Settings → update: checks by itself when opened (if older than a few minutes) and installs a newer version. */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppUpdate.state.collectAsState()
    LaunchedEffect(Unit) {
        AppUpdate.load(context.applicationContext)
        AppUpdate.check(context.applicationContext, maxAgeMs = 10 * 60_000L, notify = false)
    }
    val installed = "نسخه‌ی نصب‌شده: ${AppUpdate.installedName.fa()}"

    fun install(r: AppRelease) {
        if (!AppUpdate.canInstall(context)) { AppUpdate.openInstallPermission(context); return }
        scope.launch { AppUpdate.downloadAndInstall(context.applicationContext, r) }
    }

    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            when (val s = state) {
                UpdateState.Disabled -> Header(Icons.Outlined.SystemUpdate, "به‌روزرسانی خودکار", "این نسخه برای به‌روزرسانی از سرور ساخته نشده است. $installed")
                UpdateState.Idle, UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = MColors.Green, strokeWidth = 3.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("در حال بررسی نسخه‌ی جدید…", style = MaterialTheme.typography.titleSmall)
                        Text(installed, style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                is UpdateState.UpToDate -> {
                    Header(Icons.Outlined.CheckCircle, "آخرین نسخه نصب شده است", "$installed — بررسی: ${formatTime(s.checkedAt)}")
                    CheckAgain { scope.launch { AppUpdate.check(context.applicationContext, maxAgeMs = 0, notify = false) } }
                }
                is UpdateState.Offline -> {
                    Header(
                        Icons.Outlined.CloudOff, "اتصال به سرور برقرار نشد",
                        "اینترنت گوشی را روشن کنید؛ هر وقت وصل باشد خودش بررسی می‌کند. $installed" +
                            (s.lastCheckedAt?.let { "\nآخرین بررسی موفق: ${formatTime(it)}" } ?: ""),
                    )
                    CheckAgain { scope.launch { AppUpdate.check(context.applicationContext, maxAgeMs = 0, notify = false) } }
                }
                is UpdateState.Available -> {
                    Header(Icons.Outlined.SystemUpdate, "نسخه‌ی جدید ${s.release.versionName.fa()} آماده است", "$installed — حجم ${sizeText(s.release.size)}", highlight = true)
                    Notes(s.release.notes)
                    if (!AppUpdate.canInstall(context)) {
                        Text(
                            "برای نصب، یک بار اجازه‌ی «نصب برنامه‌های ناشناس» را برای محیط‌یار روشن کنید و برگردید.",
                            style = MaterialTheme.typography.labelSmall, color = MColors.Amber, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    PillButton("دانلود و نصب", { install(s.release) }, Modifier.padding(top = 12.dp), icon = Icons.Outlined.SystemUpdate)
                }
                is UpdateState.Downloading -> {
                    Header(Icons.Outlined.SystemUpdate, "در حال دانلود نسخه‌ی ${s.release.versionName.fa()}", if (s.progress >= 0) "${(s.progress * 100).toInt().fa()}٪" else "در حال اتصال…", highlight = true)
                    if (s.progress >= 0) LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), color = MColors.Green)
                    else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = MColors.Green)
                    Text("پس از دانلود، اندروید برای نصب از شما تأیید می‌گیرد. اطلاعات برنامه حفظ می‌شود.", style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 8.dp))
                }
                is UpdateState.Failed -> {
                    Header(Icons.Outlined.ErrorOutline, "به‌روزرسانی انجام نشد", s.message, error = true)
                    val r = s.release
                    if (r != null && r.versionCode > AppUpdate.installedCode) {
                        PillButton("تلاش دوباره", { install(r) }, Modifier.padding(top = 12.dp), icon = Icons.Outlined.Refresh)
                    } else {
                        CheckAgain { scope.launch { AppUpdate.check(context.applicationContext, maxAgeMs = 0, notify = false) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, highlight: Boolean = false, error: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleIcon(icon, if (error) MColors.Error.copy(alpha = 0.12f) else MColors.GreenLight, if (error) MColors.Error else MColors.Green, size = 42.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal),
                color = if (highlight) MColors.Green else if (error) MColors.Error else MColors.Ink,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MColors.Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Notes(notes: String) {
    if (notes.isBlank()) return
    Text("تغییرات: $notes", style = MaterialTheme.typography.bodySmall, color = MColors.Ink, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun CheckAgain(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) { Text("بررسی دوباره", color = MColors.Green) }
}

private fun sizeText(bytes: Long): String {
    if (bytes <= 0) return "نامشخص"
    val mb = bytes / (1024.0 * 1024.0)
    return "${String.format(java.util.Locale.US, "%.1f", mb).fa()} مگابایت"
}

private fun formatTime(ms: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val d = Jalali.fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    val hm = String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    return "${d.formatLong(withWeekday = false)} ساعت $hm".fa()
}
